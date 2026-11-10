package com.motornav.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Riwayat & favorit tujuan navigasi, disimpan di HP (bukan di ESP32).
 * Riwayat terisi otomatis tiap kali rute berhasil dibuat (lihat Core.setDestination); favorit ditandai manual
 * lewat bintang di daftar dan tidak pernah tersingkir oleh batas riwayat.
 */
object History {
    class Entry(val key: String, var name: String, val lat: Double, val lon: Double, var fav: Boolean, var at: Long)

    private const val MAX_HISTORY = 25   // entri BUKAN favorit yang disimpan; favorit tidak dibatasi

    private fun file(c: Context) = File(c.filesDir, "history.json")
    private fun keyOf(lat: Double, lon: Double) = String.format(Locale.US, "%.5f,%.5f", lat, lon)

    @Synchronized
    fun list(c: Context): List<Entry> {
        val f = file(c)
        if (!f.exists()) return emptyList()
        return try {
            val a = JSONArray(f.readText())
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Entry(o.getString("key"), o.getString("name"), o.getDouble("lat"), o.getDouble("lon"),
                    o.optBoolean("fav", false), o.optLong("at", 0))
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun save(c: Context, l: List<Entry>) {
        val a = JSONArray()
        l.forEach {
            a.put(JSONObject().put("key", it.key).put("name", it.name).put("lat", it.lat)
                .put("lon", it.lon).put("fav", it.fav).put("at", it.at))
        }
        file(c).writeText(a.toString())
    }

    /** Tambah/perbarui satu tujuan ke riwayat. Dipanggil otomatis tiap kali rute berhasil dibuat. */
    @Synchronized
    fun add(c: Context, p: Place) {
        val k = keyOf(p.lat, p.lon)
        val l = list(c).toMutableList()
        val ex = l.firstOrNull { it.key == k }
        val fav = ex?.fav ?: false
        l.removeAll { it.key == k }
        l.add(0, Entry(k, p.name, p.lat, p.lon, fav, System.currentTimeMillis()))
        val kept = ArrayList<Entry>()
        var n = 0
        for (e in l) {
            if (e.fav) { kept.add(e); continue }
            if (n < MAX_HISTORY) { kept.add(e); n++ }
        }
        save(c, kept)
    }

    @Synchronized
    fun toggleFav(c: Context, key: String) {
        val l = list(c)
        l.firstOrNull { it.key == key }?.let { it.fav = !it.fav }
        save(c, l)
    }

    @Synchronized
    fun remove(c: Context, key: String) {
        save(c, list(c).filter { it.key != key })
    }
}
