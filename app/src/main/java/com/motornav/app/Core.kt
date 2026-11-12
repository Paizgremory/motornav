package com.motornav.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.*

/** Pusat logika: GPS, rute, penggambaran frame, dan pengiriman BLE. Hidup selama proses (dijaga KeepAliveService). */
@SuppressLint("MissingPermission")
object Core {
    interface Listener {
        fun onStatus(text: String)
        fun onPreview(b: Bitmap)
    }

    private lateinit var app: Context
    private var inited = false
    private val ui = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    @Volatile var listener: Listener? = null
    lateinit var ble: Ble
    private lateinit var tiles: Tiles
    private lateinit var renderer: Renderer

    @Volatile var lat = Double.NaN
    @Volatile var lon = Double.NaN
    @Volatile var heading = 0f
    @Volatile var speed = 0f
    @Volatile private var acc = -1f
    private var lastLat = Double.NaN
    private var lastLon = Double.NaN

    @Volatile var zoom = 17
    @Volatile var headingUp = true
    val mapQualityNames = listOf("Rendah", "Sedang", "Tinggi")
    @Volatile var mapQuality = 1   // 0 = rendah (hemat BLE), 1 = sedang (bawaan), 2 = tinggi (lebih tajam, lebih lambat kirim)
    @Volatile var route: Route? = null
    @Volatile var dest: Place? = null
    @Volatile var mapOnly = false
    @Volatile private var rerouting = false
    @Volatile private var routeMsg = ""
    private var routeIdx = 0
    private var along = 0.0
    private var offCount = 0
    private var offSince = 0L
    private var lastReroute = 0L
    private var arrivedAt = 0L
    private var annStep = -1
    private var annLevel = 0
    @Volatile private var state: NavState? = null
    @Volatile private var dirty = true
    @Volatile private var running = false
    @Volatile private var forceState = true   // v3.22: kirim status navigasi ON/OFF ke ESP32 SEKARANG (tak nunggu jatah 2 dtk)
    private var lm: LocationManager? = null
    private var loopThread: Thread? = null

    fun init(ctx: Context) {
        if (inited) return
        app = ctx.applicationContext
        val pr = app.getSharedPreferences("motornav", Context.MODE_PRIVATE)
        zoom = pr.getInt("zoom", 17).coerceIn(15, 18)
        headingUp = pr.getBoolean("headingUp", true)
        mapQuality = pr.getInt("mapQuality", 1).coerceIn(0, mapQualityNames.size - 1)
        ble = Ble(app) { pushStatus() }
        tiles = Tiles(app) { dirty = true }
        renderer = Renderer(tiles)
        Voice.init(app)
        inited = true
    }

    /** BLE bila sudah dibuat dan tersambung (aman dipanggil dari penerima/layanan lain), selain itu null. */
    /** True saat sedang navigasi (rute atau mode peta). */
    fun isNavigating(): Boolean = inited && navOn()

    fun bleIfReady(): Ble? = if (inited && ble.ready) ble else null

    fun start() {
        if (running) return
        running = true
        startLocation()
        loopThread = Thread { loop() }.also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        try { lm?.removeUpdates(locListener) } catch (e: Exception) {}
        loopThread?.interrupt()
        Vib.stop()
        ble.disconnect()
    }

    // ---------- pengaturan ----------
    fun changeZoom(d: Int) {
        zoom = (zoom + d).coerceIn(15, 18)
        app.getSharedPreferences("motornav", Context.MODE_PRIVATE).edit().putInt("zoom", zoom).apply()
        computeNav(); dirty = true
    }

    fun toggleHeading(): Boolean {
        headingUp = !headingUp
        app.getSharedPreferences("motornav", Context.MODE_PRIVATE).edit().putBoolean("headingUp", headingUp).apply()
        computeNav(); dirty = true
        return headingUp
    }

    /** Ganti tingkat kualitas peta yang dikirim ke ESP32 (rendah/sedang/tinggi). Mempengaruhi target kompresi JPEG. */
    fun cycleMapQuality(): Int {
        mapQuality = (mapQuality + 1) % mapQualityNames.size
        app.getSharedPreferences("motornav", Context.MODE_PRIVATE).edit().putInt("mapQuality", mapQuality).apply()
        dirty = true
        return mapQuality
    }

    // ---------- tujuan & rute ----------
    fun setDestination(p: Place) {
        if (lat.isNaN()) { routeMsg = "Tunggu GPS dulu sebelum memilih tujuan."; pushStatus(); return }
        dest = p
        mapOnly = false
        route = null
        routeMsg = "Mencari rute ke ${p.name}..."
        rerouting = true
        forceState = true
        pushStatus()
        val fl = lat; val fo = lon
        exec.execute {
            try {
                val r = Engine.route(app, fl, fo, p.lat, p.lon)
                routeIdx = 0; along = 0.0; offCount = 0; offSince = 0L
                annStep = -1; annLevel = 0
                route = r
                History.add(app, p)   // riwayat & favorit tujuan (disimpan di HP)
                Voice.speak("Navigasi dimulai. Jarak ${spokenTotal(r.total)}, sekitar ${max(1, (r.dur / 60).roundToInt())} menit.")
                routeMsg = ""
            } catch (e: Exception) {
                dest = null
                routeMsg = "Gagal membuat rute: ${e.message}"
            } finally {
                rerouting = false
                lastReroute = SystemClock.elapsedRealtime()
                forceState = true
                computeNav(); dirty = true; pushStatus()
            }
        }
    }

    /** Pakai rute yang sudah dipilih pengguna (dari dialog pilih rute alternatif) tanpa fetch ulang. */
    fun setDestinationWithRoute(p: Place, r: Route) {
        dest = p
        mapOnly = false
        routeIdx = 0; along = 0.0; offCount = 0; offSince = 0L
        annStep = -1; annLevel = 0
        route = r
        History.add(app, p)
        Voice.speak("Navigasi dimulai. Jarak ${spokenTotal(r.total)}, sekitar ${max(1, (r.dur / 60).roundToInt())} menit.")
        routeMsg = ""
        rerouting = false
        lastReroute = SystemClock.elapsedRealtime()
        forceState = true
        computeNav(); dirty = true; pushStatus()
    }

    fun startMapOnly() {
        route = null; dest = null; mapOnly = true; routeMsg = ""
        forceState = true
        computeNav(); dirty = true
    }

    fun stopNav() {
        route = null; dest = null; mapOnly = false; routeMsg = ""; rerouting = false
        state = null
        forceState = true   // beri tahu ESP32 SEKARANG (jangan nunggu jatah 2 dtk) supaya layar nav tidak nyangkut/kedip sebelum balik ke wajah
        pushStatus()
    }

    private fun doReroute() {
        val d = dest ?: return
        rerouting = true
        offSince = 0L
        lastReroute = SystemClock.elapsedRealtime()
        Voice.speak("Keluar jalur. Mencari rute baru.")
        val fl = lat; val fo = lon
        exec.execute {
            try {
                val r = Engine.route(app, fl, fo, d.lat, d.lon)
                routeIdx = 0; along = 0.0; offCount = 0; offSince = 0L
                annStep = -1; annLevel = 0
                route = r
                Voice.speak("Rute baru ditemukan.")
                routeMsg = ""
            } catch (e: Exception) {
                routeMsg = "Rute ulang gagal: ${e.message}"
            } finally {
                rerouting = false
                lastReroute = SystemClock.elapsedRealtime()
                computeNav(); dirty = true
            }
        }
    }

    // ---------- GPS ----------
    private val locListener = object : LocationListener {
        override fun onLocationChanged(l: Location) = onLocation(l)
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private fun startLocation() {
        try {
            val m = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm = m
            m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locListener, Looper.getMainLooper())
            val last = m.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: m.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (last != null && lat.isNaN()) onLocation(last)
        } catch (e: SecurityException) {
            routeMsg = "Izin lokasi belum diberikan."
        } catch (e: Exception) {
            routeMsg = "GPS gagal dimulai: ${e.message}"
        }
    }

    private fun onLocation(l: Location) {
        lat = l.latitude
        lon = l.longitude
        acc = if (l.hasAccuracy()) l.accuracy else -1f
        speed = if (l.hasSpeed() && l.speed > 0f) l.speed else 0f
        var h: Float? = null
        if (l.hasBearing() && speed > 1.0f) {
            h = l.bearing
        } else if (!lastLat.isNaN() && Geo.dist(lastLat, lastLon, lat, lon) > 6.0) {
            val dy = lat - lastLat
            val dx = (lon - lastLon) * cos(Math.toRadians(lat))
            h = ((Math.toDegrees(atan2(dx, dy)).toFloat()) + 360f) % 360f
        }
        if (lastLat.isNaN() || Geo.dist(lastLat, lastLon, lat, lon) > 6.0) { lastLat = lat; lastLon = lon }
        if (h != null) heading = (heading + Geo.angDiff(h, heading) * 0.5f + 360f) % 360f
        computeNav()
    }

    // ---------- proyeksi posisi ke rute ----------
    private class Proj(val idx: Int, val along: Double, val lat: Double)

    private fun project(r: Route, la: Double, lo: Double): Proj {
        val n = r.lat.size
        val cosL = cos(Math.toRadians(la))
        fun scan(a: Int, b: Int): Proj? {
            var best: Proj? = null
            var bd = Double.MAX_VALUE
            for (i in a until b) {
                val ax = (r.lon[i] - lo) * 111320.0 * cosL
                val ay = (r.lat[i] - la) * 110574.0
                val bx = (r.lon[i + 1] - lo) * 111320.0 * cosL
                val by = (r.lat[i + 1] - la) * 110574.0
                val dx = bx - ax
                val dy = by - ay
                val l2 = dx * dx + dy * dy
                val t = (if (l2 > 0) (-(ax * dx + ay * dy)) / l2 else 0.0).coerceIn(0.0, 1.0)
                val d = hypot(ax + t * dx, ay + t * dy)
                if (d < bd) { bd = d; best = Proj(i, r.cum[i] + t * (r.cum[i + 1] - r.cum[i]), d) }
            }
            return best
        }
        var res = scan(max(0, routeIdx - 5), min(n - 1, routeIdx + 300))
        if (res == null || res.lat > 60.0) {
            val full = scan(0, n - 1)
            if (full != null && (res == null || full.lat < res.lat)) res = full
        }
        return res ?: Proj(0, 0.0, 0.0)
    }

    @Synchronized
    private fun computeNav() {
        val la = lat; val lo = lon
        if (la.isNaN()) return
        val spd = (speed * 3.6f).roundToInt()
        val r = route
        if (r == null) {
            if (mapOnly || rerouting) {
                state = NavState(la, lo, heading, spd, zoom, headingUp, null, 0, 0.0, if (rerouting) 2 else 0,
                    0.0, 0f, 0, "", "", false, 0f, 0, 0.0, 0.0)
            }
            return
        }
        val pr = project(r, la, lo)
        routeIdx = pr.idx
        along = pr.along
        // Re-routing lebih halus: selain hitungan sampel GPS di luar jalur (offCount), disyaratkan juga sudah
        // keluar jalur selama jeda minimum (offMinMs) secara waktu nyata, supaya telat belok sebentar atau lonjakan
        // sinyal GPS tunggal tidak langsung memicu hitung ulang rute (yang terasa "mondar-mandir" bila dekat jalan lain).
        val offMinMs = 4000L
        if (pr.lat > 50.0 && (acc < 0f || acc < 50f)) {
            if (offSince == 0L) offSince = SystemClock.elapsedRealtime()
            offCount++
        } else {
            offSince = 0L
            offCount = 0
        }
        val offDur = if (offSince != 0L) SystemClock.elapsedRealtime() - offSince else 0L
        if (offCount >= 3 && offDur >= offMinMs && !rerouting && SystemClock.elapsedRealtime() - lastReroute > 10000) doReroute()

        val rem = max(0.0, r.total - along)
        var k = 0
        while (k < r.steps.size - 1 && r.steps[k].endDist <= along + 1.0) k++
        val man = r.steps.getOrNull(k + 1)
        val arrived = man == null || rem < 25.0
        if (arrived) {
            if (arrivedAt == 0L) { arrivedAt = SystemClock.elapsedRealtime(); Voice.speak("Anda telah tiba di tujuan.") }
            if (SystemClock.elapsedRealtime() - arrivedAt > 8000) {
                ui.post { stopNav() }
            }
        } else arrivedAt = 0L

        val remSec = r.dur * rem / max(r.total, 1.0)
        val mode = if (arrived) 3 else if (rerouting) 2 else 1
        var street = dest?.name?.split(", ")?.firstOrNull() ?: ""
        var instr = ""
        var mAngle = 0f
        var mKind = 0
        var mDist = 0.0
        var hasThen = false
        var thenAngle = 0f
        var thenKind = 0
        if (man != null && !arrived) {
            mDist = max(0.0, r.steps[k].endDist - along)
            instr = Txt.turn(man)
            mAngle = Txt.angle(man.mod)
            if (man.type == "arrive") {
                mKind = 1
                street = dest?.name?.split(", ")?.firstOrNull() ?: "Tujuan"
            } else street = man.name.ifBlank { instr }
            announce(k + 1, man, instr, mDist)
            val nx = r.steps.getOrNull(k + 2)
            if (nx != null && man.type != "arrive" && man.len < 350.0) {
                hasThen = true
                thenAngle = Txt.angle(nx.mod)
                thenKind = if (nx.type == "arrive") 1 else 0
            }
        }
        state = NavState(la, lo, heading, spd, zoom, headingUp, r, pr.idx, along, mode,
            mDist, mAngle, mKind, street, instr, hasThen, thenAngle, thenKind, rem, remSec)
    }

    // ---------- suara petunjuk ----------
    private fun spokenDist(m: Double): String {
        if (m >= 950) return String.format(Locale("id", "ID"), "%.1f kilometer", m / 1000)
        val v = ((m / 50).roundToInt() * 50).coerceAtLeast(50)
        return "$v meter"
    }

    private fun spokenTotal(m: Double): String =
        if (m >= 1000) String.format(Locale("id", "ID"), "%.1f kilometer", m / 1000) else "${(m / 10).roundToInt() * 10} meter"

    /** Level 1 = sekitar 500 m, 2 = sekitar 200 m, 3 = sebentar lagi (80 m). Tiap level diucapkan sekali per belokan. */
    private fun announce(idx: Int, man: Step, instr: String, dist: Double) {
        if (idx != annStep) { annStep = idx; annLevel = 0 }
        val level = when {
            dist <= 80.0 -> 3
            dist <= 200.0 -> 2
            dist <= 500.0 -> 1
            else -> 0
        }
        if (level <= annLevel) return
        annLevel = level
        if (instr == "Lurus") return
        val low = instr.replaceFirstChar { it.lowercase() }
        val onto = if (man.type != "arrive" && man.name.isNotBlank() && !man.type.startsWith("round")) " ke ${man.name}" else ""
        val msg = when {
            man.type == "arrive" -> if (level == 3) "Tujuan di depan." else "Dalam ${spokenDist(dist)}, tiba di tujuan."
            level == 3 -> "$instr sekarang$onto."
            else -> "Dalam ${spokenDist(dist)}, $low$onto."
        }
        Voice.speak(msg)
    }

    // ---------- loop gambar + kirim ----------
    private fun navOn(): Boolean = (route != null || mapOnly || rerouting) && !lat.isNaN() && state != null

    private fun sigOf(s: NavState) =
        "${(s.lat * 1e5).toLong()}|${(s.lon * 1e5).toLong()}|${(s.heading / 3).toInt()}|${s.zoom}|${s.headingUp}|" +
            "${s.mode}|${(s.mDist / 10).toInt()}|${s.street}|${s.speedKmh}|${(s.remSec / 60).toInt()}|${(s.remDist / 100).toInt()}"

    private fun loop() {
        var lastSig = ""
        var lastRender = 0L
        var lastState = 0L
        var lastTime = 0L
        var lastStatus = 0L
        var lastBatt = 0L
        var lastMusic = 0L
        var lastCfg = 0L
        while (running) {
            try {
                val now = SystemClock.elapsedRealtime()
                val on = navOn()
                if (ble.ready) {
                    if (forceState || now - lastState > 2000) {
                        if (ble.sendControl(byteArrayOf(-1, 2, if (on) 1 else 0))) forceState = false
                        lastState = now
                    }
                    if (now - lastTime > 20000) {
                        val c = Calendar.getInstance()
                        ble.sendControl(byteArrayOf(-1, 1, c.get(Calendar.HOUR_OF_DAY).toByte(),
                            c.get(Calendar.MINUTE).toByte(), c.get(Calendar.SECOND).toByte()))
                        lastTime = now
                    }
                }
                if (now - lastMusic > 1000) { lastMusic = now; Music.poll(app, ble) }
                Vib.poll(app, ble, Cfg.get(app, 12) == 1)   // efek getar jalan (gimmick): menyala/mati sendiri, murah dipanggil tiap tick
                if (Cfg.dirty && ble.ready && now - lastCfg > 300) {            // pengaturan tampilan: kirim bila berubah / baru tersambung
                    lastCfg = now
                    if (ble.sendControl(Cfg.packet(app))) Cfg.dirty = false
                }
                val st = state
                if (on && st != null) {
                    val sig = sigOf(st)
                    if (dirty || sig != lastSig || now - lastRender > 1500) {
                        dirty = false
                        val bmp = renderer.render(st)
                        pushPreview(bmp)
                        if (ble.ready) {
                            val jpg = encode(bmp)
                            if (jpg != null) ble.sendFrame(jpg)
                        }
                        lastSig = sig
                        lastRender = SystemClock.elapsedRealtime()
                    }
                }
                if (now - lastBatt > 30000) { lastBatt = now; Notify.batteryCheck(app) }
                if (now - lastStatus > 1000) { pushStatus(); lastStatus = now }
            } catch (e: InterruptedException) {
                return
            } catch (e: Exception) {
                // abaikan, coba lagi di putaran berikut
            }
            try { Thread.sleep(250) } catch (e: InterruptedException) { return }
        }
    }

    /** Cari kualitas JPEG tertinggi yang masih muat di batas buffer firmware (12 KB), dibatasi atas oleh mapQuality. */
    private fun encode(b: Bitmap): ByteArray? {
        val limit = 11500
        var lo = 20
        var hi = when (mapQuality) { 0 -> 55; 2 -> 92; else -> 75 }
        var best: ByteArray? = null
        while (lo <= hi) {
            val q = (lo + hi) / 2
            val bo = ByteArrayOutputStream(12000)
            b.compress(Bitmap.CompressFormat.JPEG, q, bo)
            if (bo.size() <= limit) { best = bo.toByteArray(); lo = q + 1 } else hi = q - 1
        }
        if (best != null) return best
        for (q in intArrayOf(15, 10)) {
            val bo = ByteArrayOutputStream(12000)
            b.compress(Bitmap.CompressFormat.JPEG, q, bo)
            if (bo.size() <= limit) return bo.toByteArray()
        }
        return null
    }

    private fun pushPreview(b: Bitmap) {
        val l = listener ?: return
        val c = b.copy(Bitmap.Config.ARGB_8888, false)
        ui.post { l.onPreview(c) }
    }

    private fun pushStatus() {
        val l = listener ?: return
        val sb = StringBuilder()
        sb.append("ESP32: ").append(ble.msg).append('\n')
        if (lat.isNaN()) sb.append("GPS: menunggu sinyal...\n")
        else sb.append(String.format(Locale.US, "GPS: %.5f, %.5f  (\u00B1%d m)  %d km/j\n", lat, lon, acc.roundToInt(), (speed * 3.6f).roundToI
