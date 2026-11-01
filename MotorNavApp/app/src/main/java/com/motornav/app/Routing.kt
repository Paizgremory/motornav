package com.motornav.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.*

object Net {
    private const val UA = "MotorNavApp/1.0 (proyek pribadi)"

    fun getBytes(url: String, timeoutMs: Int = 10000): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.setRequestProperty("User-Agent", UA)
        try {
            if (c.responseCode != 200) throw RuntimeException("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    fun get(url: String, timeoutMs: Int = 10000): String = String(getBytes(url, timeoutMs), Charsets.UTF_8)

    /** Mengikuti pengalihan (mis. tautan pendek Google Maps) dan mengembalikan URL akhir. */
    fun finalUrl(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 10000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
        try {
            c.responseCode
            return c.url.toString()
        } finally {
            c.disconnect()
        }
    }
}

object Geo {
    fun dist(la1: Double, lo1: Double, la2: Double, lo2: Double): Double {
        val r = 6371000.0
        val p1 = Math.toRadians(la1)
        val p2 = Math.toRadians(la2)
        val dp = p2 - p1
        val dl = Math.toRadians(lo2 - lo1)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    fun worldX(lon: Double, z: Int): Double = (lon + 180.0) / 360.0 * 256.0 * (1 shl z)

    fun worldY(lat: Double, z: Int): Double {
        val r = Math.toRadians(lat)
        return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * 256.0 * (1 shl z)
    }

    fun angDiff(a: Float, b: Float): Float = ((a - b + 540f) % 360f) - 180f
}

data class Place(val name: String, val lat: Double, val lon: Double)

class Step(
    val type: String,
    val mod: String,
    val name: String,
    val len: Double,
    val endDist: Double,
    val exit: Int
)

class Route(
    val lat: DoubleArray,
    val lon: DoubleArray,
    val cum: DoubleArray,
    val steps: List<Step>,
    val total: Double,
    val dur: Double
)

object Txt {
    fun angle(mod: String): Float = when (mod) {
        "slight right" -> 35f
        "right" -> 90f
        "sharp right" -> 140f
        "uturn" -> 180f
        "sharp left" -> -140f
        "left" -> -90f
        "slight left" -> -35f
        else -> 0f
    }

    fun turn(s: Step): String = when {
        s.type == "arrive" -> "Tiba di tujuan"
        s.type == "roundabout" || s.type == "rotary" || s.type == "exit roundabout" ||
            s.type == "exit rotary" || s.type == "roundabout turn" ->
            if (s.exit > 0) "Bundaran, keluar ke-${s.exit}" else "Masuk bundaran"
        else -> when (s.mod) {
            "left" -> "Belok kiri"
            "right" -> "Belok kanan"
            "slight left" -> "Serong kiri"
            "slight right" -> "Serong kanan"
            "sharp left" -> "Belok tajam kiri"
            "sharp right" -> "Belok tajam kanan"
            "uturn" -> "Putar balik"
            else -> "Lurus"
        }
    }
}

object Osrm {
    fun route(fLat: Double, fLon: Double, tLat: Double, tLon: Double): Route {
        val url = "https://router.project-osrm.org/route/v1/driving/" +
            "${fmt(fLon)},${fmt(fLat)};${fmt(tLon)},${fmt(tLat)}" +
            "?overview=false&steps=true&geometries=geojson"
        val j = JSONObject(Net.get(url, 20000))
        if (j.optString("code") != "Ok") throw RuntimeException("Rute tidak ditemukan (${j.optString("code")})")
        val r = j.getJSONArray("routes").getJSONObject(0)
        val sj = r.getJSONArray("legs").getJSONObject(0).getJSONArray("steps")

        val la = ArrayList<Double>()
        val lo = ArrayList<Double>()
        val endIdx = IntArray(sj.length())
        for (i in 0 until sj.length()) {
            val co = sj.getJSONObject(i).getJSONObject("geometry").getJSONArray("coordinates")
            for (k in 0 until co.length()) {
                val p = co.getJSONArray(k)
                val x = p.getDouble(0)
                val y = p.getDouble(1)
                if (la.isNotEmpty() && abs(la.last() - y) < 1e-9 && abs(lo.last() - x) < 1e-9) continue
                la.add(y)
                lo.add(x)
            }
            endIdx[i] = la.size - 1
        }
        val n = la.size
        if (n < 2) throw RuntimeException("Rute terlalu pendek")
        val cum = DoubleArray(n)
        for (i in 1 until n) cum[i] = cum[i - 1] + Geo.dist(la[i - 1], lo[i - 1], la[i], lo[i])

        val steps = ArrayList<Step>()
        for (i in 0 until sj.length()) {
            val s = sj.getJSONObject(i)
            val m = s.getJSONObject("maneuver")
            steps.add(
                Step(
                    m.optString("type"), m.optString("modifier"), s.optString("name").trim(),
                    s.optDouble("distance", 0.0), cum[max(0, endIdx[i])], m.optInt("exit", 0)
                )
            )
        }
        return Route(
            la.toDoubleArray(), lo.toDoubleArray(), cum, steps,
            cum[n - 1], r.optDouble("duration", cum[n - 1] / 8.0)
        )
    }

    private fun fmt(d: Double) = String.format(Locale.US, "%.6f", d)
}

object Geocode {
    private val RX = listOf(
        Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)"""),
        Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)"""),
        Regex("""[?&](?:q|query|ll|destination|daddr)=(-?\d+\.\d+)(?:,|%2C|%2c)\s*(-?\d+\.\d+)"""),
        Regex("""^\s*(-?\d+\.\d+)\s*[, ]\s*(-?\d+\.\d+)\s*$""")
    )

    fun parseCoord(s: String): Place? {
        for (r in RX) {
            val m = r.find(s) ?: continue
            val la = m.groupValues[1].toDoubleOrNull() ?: continue
            val lo = m.groupValues[2].toDoubleOrNull() ?: continue
            if (abs(la) <= 90 && abs(lo) <= 180)
                return Place(String.format(Locale.US, "Titik %.5f, %.5f", la, lo), la, lo)
        }
        return null
    }

    fun search(q: String): List<Place> {
        val t = q.trim()
        parseCoord(t)?.let { return listOf(it) }
        if (t.startsWith("http")) {
            val u = Net.finalUrl(t)
            val p = parseCoord(u) ?: throw RuntimeException("Koordinat tidak ditemukan di tautan")
            return listOf(Place("Titik dari tautan", p.lat, p.lon))
        }
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&countrycodes=id&q=" +
            URLEncoder.encode(t, "UTF-8")
        val a = JSONArray(Net.get(url, 15000))
        val out = ArrayList<Place>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val nm = o.optString("display_name").split(", ").take(3).joinToString(", ")
            out.add(Place(nm, o.getDouble("lat"), o.getDouble("lon")))
        }
        return out
    }
}
