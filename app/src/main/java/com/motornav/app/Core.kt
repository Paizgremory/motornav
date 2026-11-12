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
    @Volatile var route: Route? = null
    @Volatile var dest: Place? = null
    @Volatile var mapOnly = false
    @Volatile private var rerouting = false
    @Volatile private var routeMsg = ""
    private var routeIdx = 0
    private var along = 0.0
    private var offCount = 0
    private var lastReroute = 0L
    private var arrivedAt = 0L
    private val announcer = Announcer()                       // v3.28: suara petunjuk bertahap menurut kecepatan
    private val monitor = Monitor()                           // v3.28: GPS lemah / berlawanan arah
    private val rrTracker = RerouteTracker()                  // v3.28: rute ulang berulang
    private var destSide = 0                                  // sisi tujuan terhadap ujung rute (-1 kiri, 1 kanan)
    @Volatile private var lastFixAt = 0L
    private var curOff = 0.0                                  // jarak posisi ke rute (m), dari computeNav
    private var curBearing = 0f                               // arah rute di posisi sekarang
    @Volatile private var routeWarn = ""                      // peringatan + keterangan pilihan rute, tampil di status
    private var lastAltCheck = 0L
    @Volatile private var altBusy = false
    @Volatile private var state: NavState? = null
    @Volatile private var dirty = true
    @Volatile private var running = false
    private var lm: LocationManager? = null
    private var loopThread: Thread? = null
    private var txThread: Thread? = null
    private val txQ = java.util.concurrent.ArrayBlockingQueue<ByteArray>(1)   // frame terbaru yang menunggu dikirim (yang lama dibuang)
    @Volatile private var txBusy = false
    private var encQ = 60                                                     // kualitas JPEG terakhir yang dipakai (titik awal pencarian berikutnya)

    fun init(ctx: Context) {
        if (inited) return
        app = ctx.applicationContext
        val pr = app.getSharedPreferences("motornav", Context.MODE_PRIVATE)
        zoom = pr.getInt("zoom", 17).coerceIn(15, 18)
        headingUp = pr.getBoolean("headingUp", true)
        mapQuality = pr.getInt("mapq", 0).coerceIn(0, 2)
        ble = Ble(app) { pushStatus() }
        tiles = Tiles(app) { dirty = true }
        renderer = Renderer(tiles)
        NavPrefs.load(app)
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
        txQ.clear()
        txThread = Thread { txLoop() }.also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        try { lm?.removeUpdates(locListener) } catch (e: Exception) {}
        loopThread?.interrupt()
        txThread?.interrupt()
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

    // ---------- tujuan & rute ----------
    fun setDestination(p: Place) {
        if (lat.isNaN()) { routeMsg = "Tunggu GPS dulu sebelum memilih tujuan."; pushStatus(); return }
        dest = p
        mapOnly = false
        route = null
        routeMsg = "Mencari rute ke ${p.name}..."
        rerouting = true
        pushStatus()
        val fl = lat; val fo = lon
        exec.execute {
            try {
                val pl = Engine.plan(app, fl, fo, p.lat, p.lon)
                val r = pl.route
                rrTracker.clear()
                adopt(pl)
                var msg = "Navigasi dimulai. Jarak ${Idn.spokenTotal(r.total)}, sekitar ${Idn.minutes(r.dur)} menit."
                if (pl.notes.any { it.contains("tanpa tol") }) msg += " Rute dipilih tanpa tol."
                if (NavPrefs.guardVoice) Guard.spoken(pl.issues).let { if (it.isNotEmpty()) msg += " $it" }
                Voice.speak(msg)
                routeMsg = ""
            } catch (e: Exception) {
                dest = null
                routeMsg = "Gagal membuat rute: ${e.message}"
            } finally {
                rerouting = false
                lastReroute = SystemClock.elapsedRealtime()
                computeNav(); dirty = true; pushStatus()
            }
        }
    }

    fun startMapOnly() {
        route = null; dest = null; mapOnly = true; routeMsg = ""
        computeNav(); dirty = true
    }

    fun stopNav() {
        route = null; dest = null; mapOnly = false; routeMsg = ""; rerouting = false
        routeWarn = ""; announcer.reset(); monitor.reset(); rrTracker.clear()
        state = null
        pushStatus()
    }

    /** Pasang rencana rute baru: reset posisi di rute, pengumuman, dan peringatan. */
    private fun adopt(pl: Plan) {
        val r = pl.route
        routeIdx = 0; along = 0.0; offCount = 0
        announcer.reset(); monitor.reset()
        destSide = dest?.let { SmartNav.destSide(r, it.lat, it.lon) } ?: 0
        routeWarn = (pl.issues.map { it.text } + pl.notes).joinToString("\n")
        lastAltCheck = SystemClock.elapsedRealtime()
        route = r
    }

    private fun doReroute() {
        val d = dest ?: return
        rerouting = true
        lastReroute = SystemClock.elapsedRealtime()
        val many = rrTracker.hit(lastReroute) >= 3
        Voice.speak(if (many) "Keluar jalur lagi. Mencari rute baru. Rute sudah berubah beberapa kali, periksa jalan atau tujuan Anda." else "Keluar jalur. Mencari rute baru.")
        val fl = lat; val fo = lon
        exec.execute {
            try {
                val pl = Engine.plan(app, fl, fo, d.lat, d.lon)
                adopt(pl)
                var msg = "Rute baru ditemukan."
                if (NavPrefs.guardVoice) Guard.spoken(pl.issues).let { if (it.isNotEmpty()) msg += " $it" }
                Voice.speak(msg)
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
        lastFixAt = SystemClock.elapsedRealtime()
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
        curOff = pr.lat
        curBearing = SmartNav.routeBearing(r, pr.idx)
        if (pr.lat > 50.0 && (acc < 0f || acc < 50f)) offCount++ else offCount = 0
        if (offCount >= 3 && !rerouting && SystemClock.elapsedRealtime() - lastReroute > 10000) doReroute()

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
            smartAnnounce(k, r, man, mDist)
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

    // ---------- suara petunjuk (v3.28: Announcer) ----------
    private fun smartAnnounce(k: Int, r: Route, man: Step, dist: Double) {
        val msg = announcer.turn(k + 1, man, r.steps.getOrNull(k + 2), dist, speed.toDouble(), destSide)
            ?: announcer.straight(k, r.steps[k], along - (if (k > 0) r.steps[k - 1].endDist else 0.0), dist)
        if (msg != null) Voice.speak(msg)
    }

    /** ±1 kali per detik: pantau GPS / arah, dan cek rute yang jauh lebih cepat. */
    private fun monitorTick() {
        if (route == null || rerouting) return
        val now = SystemClock.elapsedRealtime()
        if (NavPrefs.guardVoice) {
            val msg = monitor.update(now, acc, speed, lastFixAt, heading, curBearing, curOff, now - Voice.lastAt > 6000L)
            if (msg != null) Voice.speak(msg)
        }
        altCheck(now)
    }

    private fun altCheck(now: Long) {
        val r = route ?: return
        val d = dest ?: return
        val st = state ?: return
        if (!NavPrefs.autoAlt || altBusy || st.mode != 1 || lat.isNaN()) return
        if (now - lastAltCheck < 150000L) return
        if (speed < 2f || st.remDist < 3000.0 || st.mDist < 600.0) return     // hanya saat melaju, masih jauh, dan belokan berikutnya belum dekat
        lastAltCheck = now
        altBusy = true
        val fl = lat; val fo = lon; val remSec = st.remSec
        exec.execute {
            try {
                val pl = Engine.plan(app, fl, fo, d.lat, d.lon)
                if (route !== r || dest !== d || rerouting) return@execute      // rute berubah selagi menunggu: buang
                val endOk = pl.issues.none { it.code == "end" }
                val gain = Advisor.savings(remSec, r, pl.route, NavPrefs.avoidToll, endOk)
                if (gain > 0.0) {
                    adopt(pl)
                    Voice.speak("Ada rute lebih cepat, hemat ${Idn.minutes(gain)} menit. Mengalihkan rute.")
                    routeMsg = ""
                    computeNav(); dirty = true
                }
            } catch (e: Exception) {
                // abaikan: rute sekarang tetap dipakai
            } finally {
                altBusy = false
            }
        }
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
                    if (now - lastState > 2000) {
                        // v22.42: heartbeat navigasi -- INI yang bikin ESP32 tahu HP masih nyambung (appLinked(), timeout 8 dtk).
                        // lastState cuma diperbarui kalau sendControl() benar-benar terkirim; kalau gagal/terlewat (mis. lock
                        // sendang dipakai kirim frame peta), coba lagi secepatnya di putaran berikut (40 ms), bukan nunggu 2 dtk
                        // lagi -- supaya heartbeat tidak ikut telat berturut-turut sampai ESP32 menyangka HP terputus dan
                        // navigasi tiba-tiba keluar sendiri (muncul sebentar lalu balik ke standby).
                        if (ble.sendControl(byteArrayOf(-1, 2, if (on) 1 else 0))) lastState = now
                    }
                    if (now - lastTime > 20000) {
                        val c = Calendar.getInstance()
                        ble.sendControl(byteArrayOf(-1, 1, c.get(Calendar.HOUR_OF_DAY).toByte(),
                            c.get(Calendar.MINUTE).toByte(), c.get(Calendar.SECOND).toByte()))
                        lastTime = now
                    }
                }
                if (now - lastMusic > 1000) { lastMusic = now; Music.poll(app, ble) }
                if (Cfg.dirty && ble.ready && now - lastCfg > 300) {            // pengaturan tampilan: kirim bila berubah / baru tersambung
                    lastCfg = now
                    if (ble.sendControl(Cfg.packet(app))) Cfg.dirty = false
                }
                val st = state
                if (on && st != null) {
                    val sig = sigOf(st)
                    // Render + encode berjalan selagi frame sebelumnya masih dikirim (thread kirim terpisah); frame yang
                    // tertinggal dibuang, jadi yang sampai ke LCD selalu yang terbaru. Saat kirim masih jalan, render dijarangkan.
                    val gap = if (txBusy) 150L else 0L
                    if ((dirty || sig != lastSig || now - lastRender > 1500) && now - lastRender >= gap) {
                        dirty = false
                        val bmp = renderer.render(st)
                        pushPreview(bmp)
                        if (ble.ready) {
                            // bergerak: frame lebih kecil (kirim ±40% lebih cepat); diam: kualitas penuh
                            val jpg = encode(bmp, frameLimit(st.speedKmh >= 8))
                            if (jpg != null) { txQ.clear(); txQ.offer(jpg) }
                        }
                        lastSig = sig
                        lastRender = SystemClock.elapsedRealtime()
                    }
                }
                if (now - lastBatt > 30000) { lastBatt = now; Notify.batteryCheck(app) }
                if (now - lastStatus > 1000) { pushStatus(); lastStatus = now; monitorTick() }
            } catch (e: InterruptedException) {
  
