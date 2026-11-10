package com.motornav.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Menyiapkan wallpaper (foto / video) untuk layar GC9A01 240x240.
 * Hasil berupa "citra partisi" yang dikirim apa adanya lewat BLE ke slot wallpaper ESP32:
 *   foto  = "MNPHOTO1" + 8 byte 0xFF + 115200 byte RGB565 little-endian
 *   video = "MNVIDEO2" + header 8 KB (jumlah frame, fps, tabel) + frame JPEG 240x240 (rata 4 byte)
 * Layar fisik 240x240, jadi "HD" = kualitas terbaik di ukuran itu: foto/frame dibaca dari sumber asli, diperkecil bertahap,
 * dipertajam, lalu foto dikonversi ke RGB565 dengan dithering Floyd-Steinberg (gradasi tidak berpita).
 */
object Wallpaper {
    const val SIZE = 240
    const val CAP = 512 * 1024 * 1024               // kapasitas video wallpaper di microSD (firmware v22.29; firmware menerima sampai 1800 MB). Tanpa SD, ESP32 menolak yang > 1,5 MB
    const val BOOT_CAP = 128 * 1024 * 1024          // kapasitas animasi boot di microSD. Tanpa SD, ESP32 menolak yang > 512 KB
    const val VID_HDR = 8192
    const val VID_MAXF = 900                        // batas jumlah frame format MNVIDEO2 (header 8 KB)
    const val VID_MAXF3 = 65000                     // batas jumlah frame format MNVIDEO3 (khusus SD; firmware maks 65535)
    const val BUDGET: Long = (CAP.toLong() - VID_HDR - 8192) * 97 / 100
    fun budgetFor(cap: Int): Long = (cap.toLong() - VID_HDR - 8192) * 97 / 100

    /** Nomor slot di perpustakaan SD ESP32 (1..65000) diturunkan dari CRC isi, jadi isi yang sama selalu masuk slot yang sama. */
    fun slotOf(crc: Long): Int = 1 + (crc % 65000L).toInt()

    /** CRC-32 sebuah berkas (dibaca bertahap, hemat memori). */
    fun crcOf(f: File): Long {
        val c = java.util.zip.CRC32()
        val buf = ByteArray(256 * 1024)
        FileInputStream(f).use { ins ->
            while (true) { val n = ins.read(buf); if (n < 0) break; c.update(buf, 0, n) }
        }
        return c.value
    }

    /** Tulis byte (mis. citra foto kecil) ke berkas sementara supaya jalur kirim sama dengan video. */
    fun toTemp(dir: File, data: ByteArray): File {
        dir.mkdirs()
        val f = File(dir, "wp_${System.nanoTime()}.tmp")
        f.writeBytes(data)
        return f
    }

    /** Tampilan: gelap tengah 0..60 (%), zoom >= 1, geser -1..1, ketajaman. */
    class Look(val dim: Int, val zoom: Float, val panX: Float, val panY: Float, val sharpen: Boolean = true)

    fun isVideo(ctx: Context, uri: Uri): Boolean = (ctx.contentResolver.getType(uri) ?: "").startsWith("video")

    // ---------- foto ----------
    /** Baca foto sebesar mungkin yang masih wajar (sisi pendek >= 960 px), putar sesuai EXIF. */
    fun loadPhoto(ctx: Context, uri: Uri): Bitmap? {
        val cr = ctx.contentResolver
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var sample = 1
        while (min(o.outWidth, o.outHeight) / (sample * 2) >= 960) sample *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) } ?: return null
        val rot = try {
            cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) { 0f }
        if (rot != 0f) {
            val m = Matrix().apply { postRotate(rot) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        return bmp
    }

    /** Foto -> (citra partisi untuk dikirim, bitmap pratinjau persis seperti di layar). */
    fun preparePhoto(src: Bitmap, look: Look): Pair<ByteArray, Bitmap> {
        val ch = planes(src, look)
        val q = IntArray(SIZE * SIZE * 3)
        val chans = ch
        for (y in 0 until SIZE) {
            val ltr = y % 2 == 0
            val dir = if (ltr) 1 else -1
            var x = if (ltr) 0 else SIZE - 1
            while (x in 0 until SIZE) {
                val i = y * SIZE + x
                if (hypot(x - 119.5, y - 119.5) > 121) { x += dir; continue }
                for (c in 0..2) {
                    val cc = chans[c]
                    val old = cc[i].coerceIn(0f, 255f)
                    val lv = if (c == 1) 63 else 31
                    val qi = (old / 255f * lv).roundToInt().coerceIn(0, lv)
                    q[i * 3 + c] = qi
                    val err = old - qi * 255f / lv
                    if (x + dir in 0 until SIZE) cc[i + dir] += err * 7f / 16f
                    if (y + 1 < SIZE) {
                        if (x - dir in 0 until SIZE) cc[i + SIZE - dir] += err * 3f / 16f
                        cc[i + SIZE] += err * 5f / 16f
                        if (x + dir in 0 until SIZE) cc[i + SIZE + dir] += err * 1f / 16f
                    }
                }
                x += dir
            }
        }
        val img = ByteArray(16 + SIZE * SIZE * 2)
        "MNPHOTO1".toByteArray().copyInto(img)
        for (k in 8 until 16) img[k] = -1
        val pv = IntArray(SIZE * SIZE)
        for (i in 0 until SIZE * SIZE) {
            val rq = q[i * 3]; val gq = q[i * 3 + 1]; val bq = q[i * 3 + 2]
            val v = (rq shl 11) or (gq shl 5) or bq
            img[16 + i * 2] = (v and 255).toByte()
            img[16 + i * 2 + 1] = (v shr 8).toByte()
            pv[i] = (0xFF shl 24) or ((rq * 255 / 31) shl 16) or ((gq * 255 / 63) shl 8) or (bq * 255 / 31)
        }
        return Pair(img, Bitmap.createBitmap(pv, SIZE, SIZE, Bitmap.Config.ARGB_8888))
    }

    // ---------- pemrosesan bersama ----------
    private fun square480(src: Bitmap, l: Look): IntArray {
        val side0 = min(src.width, src.height)
        val side = max(1, (side0 / l.zoom.coerceIn(1f, 4f)).toInt())
        val cx = src.width / 2f + l.panX.coerceIn(-1f, 1f) * (src.width - side) / 2f
        val cy = src.height / 2f + l.panY.coerceIn(-1f, 1f) * (src.height - side) / 2f
        val x0 = (cx - side / 2f).toInt().coerceIn(0, src.width - side)
        val y0 = (cy - side / 2f).toInt().coerceIn(0, src.height - side)
        var b = Bitmap.createBitmap(src, x0, y0, side, side)
        val mid = SIZE * 2
        while (b.width / 2 >= mid) b = Bitmap.createScaledBitmap(b, b.width / 2, b.height / 2, true)   // tiap langkah <= 2x
        if (b.width != mid) b = Bitmap.createScaledBitmap(b, mid, mid, true)
        val px = IntArray(mid * mid)
        b.getPixels(px, 0, mid, 0, 0, mid, mid)
        return px
    }

    /** Foto/frame -> 3 bidang warna 240x240 (0..255): potong, perkecil, pertajam, gelapkan tengah, potong lingkaran. */
    private fun planes(src: Bitmap, l: Look): Array<FloatArray> {
        val big = square480(src, l)
        val mid = SIZE * 2
        val ch = Array(3) { FloatArray(SIZE * SIZE) }
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            var sr = 0; var sg = 0; var sb = 0
            for (dy in 0..1) for (dx in 0..1) {
                val p = big[(y * 2 + dy) * mid + x * 2 + dx]
                sr += (p shr 16) and 255; sg += (p shr 8) and 255; sb += p and 255
            }
            val i = y * SIZE + x
            ch[0][i] = sr / 4f; ch[1][i] = sg / 4f; ch[2][i] = sb / 4f
        }
        if (l.sharpen) for (c in ch) {                              // unsharp mask 3x3
            val o = c.copyOf()
            for (y in 1 until SIZE - 1) for (x in 1 until SIZE - 1) {
                val i = y * SIZE + x
                val blur = (o[i - 1] + o[i + 1] + o[i - SIZE] + o[i + SIZE]) * 0.125f + o[i] * 0.5f
                c[i] = (o[i] + 0.6f * (o[i] - blur)).coerceIn(0f, 255f)
            }
        }
        val k = l.dim.coerceIn(0, 60) / 100f
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val i = y * SIZE + x
            val d = hypot(x - 119.5, y - 119.5)
            if (d > 121) { ch[0][i] = 0f; ch[1][i] = 0f; ch[2][i] = 0f; continue }
            val f = 1f - k * max(0.0, 1.0 - d / 110.0).toFloat()
            ch[0][i] *= f; ch[1][i] *= f; ch[2][i] *= f
        }
        return ch
    }

    private fun toBitmap(ch: Array<FloatArray>): Bitmap {
        val pv = IntArray(SIZE * SIZE)
        for (i in 0 until SIZE * SIZE) {
            val r = ch[0][i].roundToInt().coerceIn(0, 255)
            val g = ch[1][i].roundToInt().coerceIn(0, 255)
            val b = ch[2][i].roundToInt().coerceIn(0, 255)
            pv[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pv, SIZE, SIZE, Bitmap.Config.ARGB_8888)
    }

    fun previewOnly(src: Bitmap, look: Look): Bitmap = toBitmap(planes(src, look))

    private fun jpeg(ch: Array<FloatArray>, q: Int): ByteArray {
        val bo = ByteArrayOutputStream(16000)
        toBitmap(ch).compress(Bitmap.CompressFormat.JPEG, q, bo)
        return bo.toByteArray()
    }

    private fun pad(n: Int) = (n + 3) and 3.inv()

    // ---------- video ----------
    class VideoSource(val r: MediaMetadataRetriever, val durSec: Double)

    fun openVideo(ctx: Context, uri: Uri): VideoSource? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (ms <= 0) { r.release(); null } else VideoSource(r, ms / 1000.0)
        } catch (e: Exception) { try { r.release() } catch (x: Exception) {}; null }
    }

    fun frameAt(v: VideoSource, sec: Double): Bitmap? = try {
        v.r.getFrameAtTime((min(max(sec, 0.0), v.durSec - 0.02) * 1_000_000).toLong(), MediaMetadataRetriever.OPTION_CLOSEST)
    } catch (e: Exception) { null }

    /** Contoh frame (tersebar rata) untuk pratinjau dan perkiraan ukuran. */
    fun sampleFrames(v: VideoSource, n: Int): List<Bitmap> {
        val l = ArrayList<Bitmap>()
        for (j in 0 until n) {
            var f = frameAt(v, v.durSec * (j + 0.5) / n) ?: continue
            val sh = min(f.width, f.height)
            if (sh > 960) {                                           // hemat memori: contoh cukup 960 px
                val k = 960f / sh
                f = Bitmap.createScaledBitmap(f, (f.width * k).toInt(), (f.height * k).toInt(), true)
            }
            l.add(f)
        }
        return l
    }

    /** Rata-rata ukuran satu frame JPEG (byte, sudah rata 4) pada pengaturan ini. */
    fun frameSize(samples: List<Bitmap>, look: Look, quality: Int): Double {
        if (samples.isEmpty()) return 0.0
        var s = 0.0
        for (b in samples) s += pad(jpeg(planes(b, look), quality).size)
        return s / samples.size
    }

    class VideoOpts(val look: Look, val fps: Int, val quality: Int, val auto: Boolean, val cap: Int = CAP)
    class VideoResult(val file: File, val size: Long, val frames: Int, val fps: Int, val seconds: Double, val trimmed: Boolean)

    /**
     * Ubah video jadi citra "MNVIDEO2/3" yang ditulis ke BERKAS di outDir (bukan di memori, jadi ratusan MB aman).
     * Manual: fps & kualitas dipakai apa adanya, video dipotong (dari awal) bila tak muat. Otomatis: fps diturunkan (min 6) agar seluruh
     * video muat. Kualitas JPEG per frame disesuaikan otomatis bila ukuran mepet. Pemanggil bertanggung jawab menghapus berkas hasil.
     */
    fun encodeVideo(v: VideoSource, samples: List<Bitmap>, vo: VideoOpts, outDir: File, progress: (Int, Int) -> Unit, cancelled: () -> Boolean): VideoResult? {
        val dur = v.durSec
        val s = frameSize(samples, vo.look, vo.quality)
        if (s <= 0.0) return null
        val budget = budgetFor(vo.cap)
        val nFit = max(1L, (budget / (s * 1.05)).toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val fps = if (vo.auto) max(6, min(vo.fps, floor(nFit / dur).toInt())) else vo.fps
        val wanted = max(1, floor(dur * fps).toInt())
        val n = min(wanted, min(nFit, VID_MAXF3))
        var q = vo.quality
        outDir.mkdirs()
        val body = File(outDir, "enc_body_${System.nanoTime()}.tmp")
        val out = File(outDir, "enc_out_${System.nanoTime()}.tmp")
        val offs = IntArray(n)
        val lens = IntArray(n)
        val pads = IntArray(n)
        var cnt = 0
        var total = 0L
        var lastCh: Array<FloatArray>? = null
        var ok = false
        try {
            java.io.BufferedOutputStream(FileOutputStream(body), 256 * 1024).use { bo ->
                for (i in 0 until n) {
                    if (cancelled()) return null
                    progress(i, n)
                    val bmp = frameAt(v, (i + 0.5) / fps)
                    val ch = if (bmp != null) planes(bmp, vo.look) else (lastCh ?: continue)
                    lastCh = ch
                    var b = jpeg(ch, q)
                    var qq = q
                    while (b.size > 60000 && qq > 20) { qq -= 8; b = jpeg(ch, qq) }       // batas firmware 64 KB per frame
                    val sz = pad(b.size)
                    val per = (budget - total).toDouble() / (n - i)
                    offs[cnt] = total.toInt(); lens[cnt] = b.size; pads[cnt] = sz
                    bo.write(b)
                    for (k in b.size until sz) bo.write(0xFF)
                    cnt++; total += sz
                    if (sz > per) q = max(20, q - (if (sz > per * 1.25) 8 else 4))
                    else if (sz < per * 0.8) q = min(vo.quality, q + 2)
                }
            }
            while (total > budget && cnt > 1) { cnt--; total -= pads[cnt] }   // buang frame terakhir bila melewati anggaran
            if (cnt == 0) return null
            val v3 = cnt > VID_MAXF                                    // lebih dari 900 frame: format MNVIDEO3 (tabel sebesar jumlah frame)
            val hdr = if (v3) ((16 + cnt * 8 + 4095) / 4096) * 4096 else VID_HDR
            val head = ByteArray(hdr)
            java.util.Arrays.fill(head, 0xFF.toByte())
            val bb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
            (if (v3) "MNVIDEO3" else "MNVIDEO2").toByteArray().copyInto(head)
            bb.putShort(8, cnt.toShort()); bb.putShort(10, fps.toShort()); bb.putInt(12, total.toInt())
            for (i in 0 until cnt) { bb.putInt(16 + i * 8, offs[i]); bb.putInt(20 + i * 8, lens[i]) }
            FileOutputStream(out).use { fo ->
                fo.write(head)
                FileInputStream(body).use { fi ->
                    val buf = ByteArray(256 * 1024)
                    var left = total
                    while (left > 0) {
                        val r = fi.read(buf, 0, min(buf.size.toLong(), left).toInt())
                        if (r < 0) break
                        fo.write(buf, 0, r); left -= r
                    }
                    if (left > 0) return null
                }
            }
            progress(n, n)
            ok = true
            return VideoResult(out, hdr + total, cnt, fps, cnt.toDouble() / fps, cnt < floor(dur * fps).toInt())
        } finally {
            body.delete()
            if (!ok) out.delete()
        }
    }
}
