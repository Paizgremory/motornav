package com.motornav.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import java.io.File
import java.util.concurrent.Executors

/**
 * Ubin peta. Kalau API key Google ada: ubin Google (gaya malam) hanya di memori
 * (aturan Google melarang menyimpan ubin). Kalau tidak: OpenStreetMap + cache disk.
 */
class Tiles(ctx: Context, private val onLoaded: () -> Unit) {
    private val app = ctx.applicationContext
    private val dir = File(ctx.cacheDir, "tiles").apply { mkdirs() }
    private val mem = object : LinkedHashMap<String, Bitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > 40
    }
    private val pending = HashSet<String>()
    private val failedAt = HashMap<String, Long>()
    private val pool = Executors.newFixedThreadPool(3)
    private val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }

    private val gLock = Any()
    @Volatile private var gSession = ""
    @Volatile private var gSessionAt = 0L
    @Volatile private var gBadUntil = 0L

    init {
        pool.execute { trim() }
    }

    /** True bila ubin yang sedang dipakai berasal dari Google. */
    fun googleActive(): Boolean = GKey.get(app).isNotEmpty() && SystemClock.elapsedRealtime() >= gBadUntil

    @Synchronized
    fun get(z: Int, x: Int, y: Int): Bitmap? {
        val n = 1 shl z
        if (y < 0 || y >= n) return null
        val xx = ((x % n) + n) % n
        val g = googleActive()
        val key = (if (g) "g/" else "o/") + "$z/$xx/$y"
        mem[key]?.let { return it }
        val f = failedAt[key]
        if (f != null && SystemClock.elapsedRealtime() - f < 10000) return null
        if (pending.add(key)) pool.execute { load(key, g, z, xx, y) }
        return null
    }

    private fun decode(b: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(b, 0, b.size, opts)

    private fun session(k: String): String {
        val now = System.currentTimeMillis()
        synchronized(gLock) {
            if (gSession.isNotEmpty() && now - gSessionAt < 10L * 24 * 3600 * 1000) return gSession
            gSession = GoogleApi.createSession(k)
            gSessionAt = now
            return gSession
        }
    }

    private fun googleFailed(e: Exception) {
        val m = e.message ?: ""
        if (!m.contains("HTTP 4")) Engine.mapNote = "Peta: gagal ($m)"
        // Hanya galat dari Google (key salah, API belum aktif, kuota): pakai OSM sementara.
        if (m.contains("HTTP 4")) {
            synchronized(gLock) { gSession = "" }
            gBadUntil = SystemClock.elapsedRealtime() + 30000
            Engine.mapNote = "Peta: OSM (ubin Google gagal: $m)"
        }
    }

    private fun load(key: String, g: Boolean, z: Int, x: Int, y: Int) {
        var ok = false
        try {
            var bmp: Bitmap? = null
            if (g) {
                val k = GKey.get(app)
                val s = session(k)
                val data = Net.getBytes("https://tile.googleapis.com/v1/2dtiles/$z/$x/$y?session=$s&key=$k", 10000)
                bmp = decode(data)
                if (bmp != null) Engine.mapNote = "Peta: Google"
            } else {
                val f = File(dir, "osm_${z}_${x}_$y.png")
                if (f.exists()) bmp = decode(f.readBytes())
                if (bmp == null) {
                    val data = Net.getBytes("https://tile.openstreetmap.org/$z/$x/$y.png", 10000)
                    bmp = decode(data)
                    if (bmp != null) f.writeBytes(data)
                }
            }
            if (bmp != null) {
                synchronized(this) { mem[key] = bmp }
                ok = true
            }
        } catch (e: Exception) {
            if (g) googleFailed(e)
        } finally {
            synchronized(this) {
                pending.remove(key)
                if (!ok) failedAt[key] = SystemClock.elapsedRealtime()
            }
            if (ok) onLoaded()
        }
    }

    private fun trim() {
        try {
            val files = dir.listFiles() ?: return
            var total = files.sumOf { it.length() }
            val limit = 200L * 1024 * 1024
            if (total <= limit) return
            for (f in files.sortedBy { it.lastModified() }) {
                total -= f.length()
                f.delete()
                if (total <= limit * 8 / 10) break
            }
        } catch (e: Exception) {
        }
    }
}
