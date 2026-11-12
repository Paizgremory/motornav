package com.motornav.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Ambil lirik tersinkron (LRC) dari lrclib.net (gratis, tanpa API key, tanpa perlu akun).
 * https://lrclib.net/docs
 */
object Lrc {
    private const val UA = "MotorNavApp/1.0 (proyek pribadi)"

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", UA)
        try {
            val code = c.responseCode
            if (code == 404) return ""
            if (code != 200) throw RuntimeException("HTTP $code")
            return c.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
        } finally {
            c.disconnect()
        }
    }

    /**
     * null = tidak ditemukan. Daftar kosong = ditemukan tapi instrumental (tidak ada lirik).
     * Daftar isi = [(ms sejak awal lagu, baris)], terurut naik.
     */
    fun fetch(title: String, artist: String, durSec: Long): List<Pair<Long, String>>? {
        val t = URLEncoder.encode(title, "UTF-8")
        val a = URLEncoder.encode(artist, "UTF-8")

        // 1) pencocokan tepat lewat /api/get (butuh durasi untuk presisi; dicoba dulu bila ada)
        if (durSec > 0) {
            try {
                val body = get("https://lrclib.net/api/get?track_name=$t&artist_name=$a&duration=$durSec")
                if (body.isNotEmpty()) parseGetResponse(JSONObject(body))?.let { return it }
            } catch (e: Exception) {}
        }

        // 2) jatuh ke /api/search (tanpa durasi, ambil hasil pertama yang punya lirik tersinkron)
        try {
            val body = get("https://lrclib.net/api/search?track_name=$t&artist_name=$a")
            if (body.isNotEmpty()) {
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    parseGetResponse(arr.getJSONObject(i))?.let { return it }
                }
                if (arr.length() > 0) return emptyList()   // ada hasil tapi tak satupun bersinkron -> anggap instrumental/tanpa lirik
            }
        } catch (e: Exception) {}

        return null
    }

    private fun parseGetResponse(o: JSONObject): List<Pair<Long, String>>? {
        if (o.optBoolean("instrumental", false)) return emptyList()
        val synced = o.optString("syncedLyrics", "")
        if (synced.isBlank()) return null
        val out = parseLrc(synced)
        return if (out.isEmpty()) null else out
    }

    private val TS = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    private fun parseLrc(lrc: String): List<Pair<Long, String>> {
        val out = ArrayList<Pair<Long, String>>()
        for (rawLine in lrc.lineSequence()) {
            val stamps = TS.findAll(rawLine).toList()
            if (stamps.isEmpty()) continue
            val text = rawLine.substring(stamps.last().range.last + 1).trim()
            if (text.isEmpty()) continue
            for (m in stamps) {
                val mm = m.groupValues[1].toLong()
                val ss = m.groupValues[2].toLong()
                val fracStr = m.groupValues[3]
                val ms = when (fracStr.length) {
                    0 -> 0L
                    1 -> fracStr.toLong() * 100
                    2 -> fracStr.toLong() * 10
                    else -> fracStr.toLong()
                }
                out.add(Pair(mm * 60_000 + ss * 1000 + ms, text))
            }
        }
        return out.sortedBy { it.first }
    }
}
