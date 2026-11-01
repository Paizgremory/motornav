package com.motornav.app

import android.content.Context

/**
 * Pengaturan tampilan standby/musik yang disimpan di HP dan dikirim ke ESP32 (firmware v22.23: paket [FF][28][10 byte]).
 * Urutan item HARUS sama dengan urutan cfg[] di firmware.
 */
object Cfg {
    class Item(
        val label: String, val unit: String, val min: Int, val max: Int, val def: Int,
        val toggle: Boolean = false, val scale255: Boolean = false,
        val hint: String = "", val page: Int = 2          // page 1 = tab Musik, 2 = tab Layar
    ) {
        fun toByte(v: Int): Int = if (scale255) (v * 255 + 50) / 100 else v
    }

    val items = listOf(
        Item("Opasitas gambar sampul album", "%", 0, 100, 55, scale255 = true, page = 1, hint = "Makin kecil makin transparan (wallpaper makin jelas)"),
        Item("Tampilkan gambar sampul album", "", 0, 1, 1, toggle = true, page = 1),
        Item("Keputihan jam di tengah", "%", 16, 100, 78, scale255 = true, hint = "Makin kecil jam makin transparan"),
        Item("Visualizer (batang pelangi mengikuti musik)", "", 0, 1, 1, toggle = true, page = 1),
        Item("Tinggi batang visualizer", "%", 20, 200, 100, page = 1),
        Item("Keterlihatan batang visualizer", "%", 20, 200, 100, page = 1, hint = "Makin kecil batang makin tembus pandang"),
        Item("Kecepatan putaran cincin pelangi", "%", 0, 200, 100, hint = "0 = diam"),
        Item("Kartu pemutar + slider lagu di layar", "", 0, 1, 1, toggle = true, page = 1),
        Item("Masuk standby setelah", " dtk", 5, 120, 20),
        Item("Wajah menari saat lagu diputar", "", 0, 1, 1, toggle = true, page = 1),
        Item("Kecerahan layar", "%", 10, 100, 100, hint = "Diredupkan lewat perangkat lunak (modul tanpa pin kecerahan)")
    )

    @Volatile var dirty = true               // true = perlu dikirim ke ESP32

    private fun prefs(c: Context) = c.getSharedPreferences("motornav_cfg", Context.MODE_PRIVATE)

    fun get(c: Context, i: Int): Int = prefs(c).getInt("c$i", items[i].def).coerceIn(items[i].min, items[i].max)

    fun set(c: Context, i: Int, v: Int) {
        prefs(c).edit().putInt("c$i", v.coerceIn(items[i].min, items[i].max)).apply()
        dirty = true
    }

    fun resetDefaults(c: Context) {
        val e = prefs(c).edit()
        items.indices.forEach { e.remove("c$it") }
        e.apply()
        dirty = true
    }

    fun packet(c: Context): ByteArray {
        val b = ByteArray(2 + items.size)
        b[0] = -1; b[1] = 0x28
        for (i in items.indices) b[2 + i] = items[i].toByte(get(c, i)).coerceIn(0, 255).toByte()
        return b
    }
}
