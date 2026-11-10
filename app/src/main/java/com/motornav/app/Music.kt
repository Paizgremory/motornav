package com.motornav.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.audiofx.Visualizer
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.json.JSONObject

/**
 * Memantau pemutar musik HP (Spotify, YouTube Music, dll.) lewat akses notifikasi dan meneruskan ke ESP32:
 * judul/artis/status, gambar sampul (64x64), dan spektrum audio asli untuk visualizer (izin Mikrofon/Rekam audio).
 */
object Music {
    private const val BANDS = 16
    private var lastKey = ""
    private var lastSent = 0L
    private var artKey = ""
    @Volatile private var artBusy = false
    private val exec = Executors.newSingleThreadExecutor()

    private var viz: Visualizer? = null
    private var vizFailAt = 0L
    @Volatile private var bleRef: Ble? = null
    private var peak = 1.0f
    private var lastFftOk = 0L
    @Volatile var vizStatus = "Visualizer: belum aktif"

    // Status "sedang diputar" untuk kartu pemutar di aplikasi
    @Volatile var npPlaying = false
    @Volatile var npTitle = ""
    @Volatile var npArtist = ""
    @Volatile var npPos = 0L            // ms, pada saat npAt
    @Volatile var npDur = 0L            // ms, 0 = tak diketahui
    @Volatile var npAt = 0L             // elapsedRealtime saat npPos dihitung
    @Volatile var npSpeed = 1f
    private var lastPosSent = 0L
    private var lastPosVal = -1L

    // ---------- lirik berjalan (lrclib.net, gratis tanpa API key) ----------
    private var lyricsKey = ""
    @Volatile private var lyricsBusy = false
    @Volatile var lyricsLines: List<Pair<Long, String>> = emptyList()   // ms sejak awal lagu -> baris
    @Volatile var lyricsStatus = ""   // "", "Mencari lirik...", "Lirik tidak ditemukan", "Lirik: instrumental"

    /** Baris lirik yang cocok dengan posisi lagu sekarang (atau "" bila tidak ada/lum ketemu). */
    fun currentLyricLine(): String {
        val lines = lyricsLines
        if (lines.isEmpty()) return ""
        val pos = posNow()
        var out = ""
        for ((t, txt) in lines) {
            if (t > pos) break
            out = txt
        }
        return out
    }

    private fun fetchLyrics(title: String, artist: String, durSec: Long) {
        lyricsBusy = true
        lyricsStatus = "Mencari lirik..."
        exec.execute {
            try {
                val lrc = Lrc.fetch(title, artist, durSec)
                if (lrc == null) {
                    lyricsLines = emptyList()
                    lyricsStatus = "Lirik tidak ditemukan"
                } else if (lrc.isEmpty()) {
                    lyricsLines = emptyList()
                    lyricsStatus = "Lirik: instrumental"
                } else {
                    lyricsLines = lrc
                    lyricsStatus = ""
                }
            } catch (e: Exception) {
                lyricsLines = emptyList()
                lyricsStatus = "Lirik gagal: ${e.message}"
            } finally {
                lyricsBusy = false
            }
        }
    }

    private fun sessions(ctx: Context): List<MediaController> =
        (ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager)
            .getActiveSessions(ComponentName(ctx, NotifService::class.java))

    /** Baca sesi media aktif dan isi np*. Mengembalikan pengendali yang sedang memutar (atau null). */
    fun readNow(ctx: Context): MediaController? {
        val list = try { sessions(ctx) } catch (e: Exception) { return null }      // akses notifikasi belum diberikan
        val c = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.playbackState != null && it.metadata != null }
        if (c == null) { npPlaying = false; npTitle = ""; npArtist = ""; npDur = 0; return null }
        val md = c.metadata
        val ps = c.playbackState
        npPlaying = ps?.state == PlaybackState.STATE_PLAYING
        var t = Notify.ascii(md?.getString(MediaMetadata.METADATA_KEY_TITLE), 60)
        if (t.isBlank()) t = Notify.ascii(c.packageName?.substringAfterLast('.'), 30)
        npTitle = t
        npArtist = Notify.ascii(md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST), 40)
        npDur = max(0L, md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L)
        npPos = max(0L, ps?.position ?: 0L)
        npSpeed = ps?.playbackSpeed ?: 1f
        npAt = ps?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime()
        return if (npPlaying) c else null
    }

    /** Posisi lagu sekarang (ms), diperkirakan dari waktu pembaruan terakhir. */
    fun posNow(): Long {
        var p = npPos
        if (npPlaying) p += ((SystemClock.elapsedRealtime() - npAt) * npSpeed).toLong()
        return if (npDur > 0) p.coerceIn(0L, npDur) else max(0L, p)
    }

    /** Kendali dari aplikasi: cmd = "toggle" | "next" | "prev" | "seek" (arg = ms). */
    fun control(ctx: Context, cmd: String, arg: Long = 0) {
        try {
            val list = sessions(ctx)
            val c = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull() ?: return
            val t = c.transportControls
            when (cmd) {
                "toggle" -> if (c.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
                "next" -> t.skipToNext()
                "prev" -> t.skipToPrevious()
                "seek" -> { t.seekTo(arg); lastPosSent = 0 }
            }
        } catch (e: Exception) {}
    }

    /** Panggil ±1x per detik dari loop Core. Aman dipanggil kapan saja (tanpa akses notifikasi = diam saja). */
    fun poll(ctx: Context, ble: Ble) {
        val ctl = readNow(ctx)
        if (!ble.ready) { lastKey = ""; artKey = ""; stopViz(); return }
        bleRef = ble
        val playing = npPlaying && ctl != null
        val title = npTitle
        val artist = npArtist

        if (playing) startViz(ctx) else stopViz()

        val key = "$playing|$title|$artist"
        val now = SystemClock.elapsedRealtime()

        val lk = "$title|$artist"
        if (playing && lk.isNotBlank() && lk != lyricsKey && !lyricsBusy) {
            lyricsKey = lk
            fetchLyrics(title, artist, npDur / 1000)
        } else if (!playing && title.isEmpty()) {
            lyricsKey = ""; lyricsLines = emptyList(); lyricsStatus = ""
        }

        if (!(key == lastKey && now - lastSent < 4000)) {
            if (!(!playing && lastKey == "")) {
                if (ble.sendMusic(playing, title, artist)) { lastKey = key; lastSent = now; lastPosSent = 0 }
            } else lastKey = key
        }

        if (playing) {                              // posisi & durasi untuk slider di layar: tiap 3 dtk atau saat digeser
            val pos = posNow()
            val jump = lastPosVal >= 0 && kotlin.math.abs(pos - (lastPosVal + (now - lastPosSent))) > 2500
            if (now - lastPosSent > 3000 || jump) {
                if (ble.sendPos((pos / 1000).toInt(), (npDur / 1000).toInt())) { lastPosSent = now; lastPosVal = pos }
            }
        }

        if (playing && ctl != null) {
            val ak = "$title|$artist"
            if (ak != artKey && !artBusy) {          // sampul dikirim sekali per lagu (diulang bila BLE tersambung ulang)
                artKey = ak
                artBusy = true
                val md = ctl.metadata
                exec.execute {
                    try {
                        val bmp = findArt(ctx, md)
                        if (bmp != null) bleRef?.sendArt(toRgb565(bmp))
                    } catch (e: Throwable) {
                    } finally { artBusy = false }
                }
            }
        }
    }

    fun reset() { lastKey = ""; artKey = ""; lyricsKey = ""; lyricsLines = emptyList(); lyricsStatus = "" }
    fun shutdown() { stopViz() }

    // ---------- sampul ----------
    private fun findArt(ctx: Context, md: MediaMetadata?): Bitmap? {
        if (md == null) return null
        for (k in listOf(MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON)) {
            val b = try { md.getBitmap(k) } catch (e: Exception) { null }
            if (b != null) return b
        }
        for (k in listOf(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)) {
            val u = md.getString(k) ?: continue
            val b = loadUri(ctx, u)
            if (b != null) return b
        }
        return null
    }

    private fun loadUri(ctx: Context, u: String): Bitmap? {
        return try {
            if (u.startsWith("http://") || u.startsWith("https://")) {
                val con = URL(u.replace("http://", "https://")).openConnection() as HttpURLConnection
                con.connectTimeout = 5000; con.readTimeout = 5000
                con.inputStream.use { BitmapFactory.decodeStream(it) }
            } else {
                ctx.contentResolver.openInputStream(Uri.parse(u))?.use { BitmapFactory.decodeStream(it) }
            }
        } catch (e: Exception) { null }
    }

    /** Potong tengah jadi persegi, kecilkan ke 64x64, jadikan RGB565 little-endian. */
    private fun toRgb565(src: Bitmap): ByteArray {
        val side = min(src.width, src.height)
        val sq = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val bmp = Bitmap.createScaledBitmap(sq, 64, 64, true)
        val px = IntArray(64 * 64)
        bmp.getPixels(px, 0, 64, 0, 0, 64, 64)
        val out = ByteArray(64 * 64 * 2)
        for (i in px.indices) {
            val p = px[i]
            val v = (((p shr 16) and 0xF8) shl 8) or (((p shr 8) and 0xFC) shl 3) or ((p and 0xFF) shr 3)
            out[i * 2] = (v and 255).toByte()
            out[i * 2 + 1] = ((v shr 8) and 255).toByte()
        }
        return out
    }

    // ---------- visualizer audio asli ----------
    private fun startViz(ctx: Context) {
        if (viz != null) return
        val now = SystemClock.elapsedRealtime()
        if (now - vizFailAt < 15000) return
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            vizStatus = "Visualizer: izin Rekam audio belum diberikan (memakai simulasi)"
            vizFailAt = now
            return
        }
        try {
            val v = Visualizer(0)                      // 0 = campuran semua suara keluar HP
            v.enabled = false
            val sz = Visualizer.getCaptureSizeRange()
            v.captureSize = min(1024, sz[1])
            val rate = Visualizer.getMaxCaptureRate()
            val sr = v.samplingRate / 1000.0f            // Hz
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vz: Visualizer?, w: ByteArray?, r: Int) {}
                override fun onFftDataCapture(vz: Visualizer?, fft: ByteArray?, r: Int) {
                    if (fft != null) onFft(fft, sr)
                }
            }, min(rate, 25000), false, true)
            v.enabled = true
            viz = v
            peak = 1.0f
            lastFftOk = now
            vizStatus = "Visualizer: aktif (suara asli)"
        } catch (e: Throwable) {
            vizFailAt = now
            viz = null
            vizStatus = "Visualizer gagal (${e.javaClass.simpleName}), memakai simulasi"
        }
    }

    private fun stopViz() {
        val v = viz ?: return
        viz = null
        try { v.enabled = false; v.release() } catch (e: Throwable) {}
        vizStatus = "Visualizer: berhenti"
    }

    private val edges = FloatArray(BANDS + 1) { i -> (45.0 * (12000.0 / 45.0).pow(i / BANDS.toDouble())).toFloat() }
    private val smooth = FloatArray(BANDS)

    private fun onFft(fft: ByteArray, sampleRate: Float) {
        val ble = bleRef ?: return
        val n = fft.size / 2                           // jumlah bin (fft[0],fft[1] = DC & Nyquist)
        val binHz = sampleRate / 2f / n
        val mag = FloatArray(BANDS)
        for (b in 0 until BANDS) {
            val lo = max(1, (edges[b] / binHz).toInt())
            val hi = max(lo + 1, min(n - 1, (edges[b + 1] / binHz).toInt() + 1))
            var m = 0f
            for (k in lo until hi) {
                val re = fft[2 * k].toFloat(); val im = fft[2 * k + 1].toFloat()
                m = max(m, hypot(re, im))
            }
            mag[b] = m
        }
        var mx = 0f
        for (m in mag) mx = max(mx, m)
        peak = max(peak * 0.992f, max(mx, 8f))          // penguat otomatis: volume HP tidak mempengaruhi tinggi batang
        val out = ByteArray(BANDS)
        for (b in 0 until BANDS) {
            val tilt = 1f + b * 0.04f                    // treble sedikit dikuatkan (energinya kecil)
            var lv = (mag[b] * tilt / peak).coerceIn(0f, 1f).pow(0.8f)
            smooth[b] = if (lv > smooth[b]) lv else smooth[b] * 0.7f + lv * 0.3f
            lv = smooth[b]
            out[b] = (lv * 255f).toInt().coerceIn(0, 255).toByte()
        }
        ble.sendBands(out)
    }
}
