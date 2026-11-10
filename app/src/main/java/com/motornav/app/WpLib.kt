package com.motornav.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Koleksi wallpaper di HP. Yang disimpan adalah citra siap kirim (hasil olahan foto/video) + thumbnail,
 * jadi memilih wallpaper = langsung kirim ke ESP32 tanpa mengolah ulang. Citra disimpan sebagai BERKAS (bisa ratusan MB),
 * tidak pernah dimuat utuh ke memori.
 */
object WpLib {
    class Entry(val id: String, var name: String, val kind: String, val size: Long, val crc: Long, val at: Long) {
        /** Nomor slot di perpustakaan kartu SD ESP32 (/wp/wNNNNN.mnv). Isi sama = slot sama. */
        val slot: Int get() = Wallpaper.slotOf(crc)
    }

    private fun dir(c: Context) = File(c.filesDir, "wplib").apply { mkdirs() }
    private fun idxFile(c: Context) = File(dir(c), "index.json")
    private fun prefs(c: Context) = c.getSharedPreferences("motornav", Context.MODE_PRIVATE)

    /** Folder kerja sementara (hasil olahan sebelum masuk koleksi). */
    fun tmpDir(c: Context) = File(c.cacheDir, "wptmp").apply { mkdirs() }

    /** Hapus sisa berkas sementara (mis. proses sebelumnya terhenti). */
    fun cleanTmp(c: Context) { try { tmpDir(c).listFiles()?.forEach { it.delete() } } catch (e: Exception) {} }

    @Synchronized fun list(c: Context): List<Entry> {
        val f = idxFile(c)
        if (!f.exists()) return emptyList()
        return try {
            val a = JSONArray(f.readText())
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Entry(o.getString("id"), o.getString("name"), o.getString("kind"), o.getLong("size"), o.getLong("crc"), o.getLong("at"))
            }.filter { File(dir(c), it.id + ".bin").exists() }
        } catch (e: Exception) { emptyList() }
    }

    private fun save(c: Context, l: List<Entry>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("kind", it.kind).put("size", it.size).put("crc", it.crc).put("at", it.at)) }
        idxFile(c).writeText(a.toString())
    }

    private fun moveFile(src: File, dst: File) {
        if (src.renameTo(dst)) return
        FileInputStream(src).use { i -> FileOutputStream(dst).use { o -> i.copyTo(o, 256 * 1024) } }
        src.delete()
    }

    /**
     * Tambah ke koleksi. BERKAS [data] DIAMBIL ALIH (dipindah ke koleksi, atau dihapus bila isinya sudah ada).
     * Isi yang sama persis (CRC & ukuran) tidak disimpan dua kali: entri lama dikembalikan.
     */
    @Synchronized fun add(c: Context, data: File, thumb: Bitmap?, name: String, kind: String): Entry {
        val crc = Wallpaper.crcOf(data)
        val size = data.length()
        val l = list(c)
        l.firstOrNull { it.crc == crc && it.size == size }?.let { data.delete(); return it }
        val id = System.currentTimeMillis().toString()
        moveFile(data, File(dir(c), "$id.bin"))
        if (thumb != null) {
            val t = Bitmap.createScaledBitmap(thumb, 120, 120, true)
            File(dir(c), "$id.jpg").outputStream().use { t.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        }
        val e = Entry(id, name, kind, size, crc, System.currentTimeMillis())
        save(c, l + e)
        return e
    }

    /** Berkas citra siap kirim milik entri (jangan diubah/dihapus langsung). */
    fun file(c: Context, id: String): File = File(dir(c), "$id.bin")

    fun thumb(c: Context, id: String): Bitmap? = try { BitmapFactory.decodeFile(File(dir(c), "$id.jpg").path) } catch (e: Exception) { null }

    @Synchronized fun rename(c: Context, id: String, name: String) {
        val l = list(c)
        l.firstOrNull { it.id == id }?.name = name.ifBlank { "Tanpa nama" }
        save(c, l)
    }

    @Synchronized fun delete(c: Context, id: String) {
        File(dir(c), "$id.bin").delete(); File(dir(c), "$id.jpg").delete()
        save(c, list(c).filter { it.id != id })
        if (active(c) == id) setActive(c, null)
    }

    /** id wallpaper yang terakhir berhasil dikirim ke ESP32 (null = bawaan / tidak diketahui). */
    fun active(c: Context): String? = prefs(c).getString("wp_active", null)
    fun setActive(c: Context, id: String?) { prefs(c).edit().apply { if (id == null) remove("wp_active") else putString("wp_active", id) }.apply() }
}
