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

    /** Ikuti pengalihan (termasuk http<->https) sampai 8 kali; kembalikan URL akhir dan isi halaman (maks 600 KB). */
    const val UA_MOBILE = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    const val UA_DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
    const val UA_CURL = "curl/8.5.0"

    fun fetchFollow(url: String, ua: String = UA_MOBILE): Pair<String, String> {
        var u = url
        for (i in 0 until 8) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 10000
            c.readTimeout = 10000
            c.setRequestProperty("User-Agent", ua)
            c.setRequestProperty("Accept-Language", "id-ID,id;q=0.9,en;q=0.8")
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: return Pair(u, "")
                    u = URL(URL(u), loc).toString()
                    continue
                }
                val body = try {
                    c.inputStream.use { s ->
                        val buf = java.io.ByteArrayOutputStream()
                        val tmp = ByteArray(8192)
                        var n = s.read(tmp)
                        while (n > 0 && buf.size() < 600_000) { buf.write(tmp, 0, n); n = s.read(tmp) }
                        String(buf.toByteArray(), Charsets.UTF_8)
                    }
                } catch (e: Exception) { "" }
                return Pair(u, body)
            } finally {
                c.disconnect()
            }
        }
        return Pair(u, "")
    }

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
    val exit: Int,
    /** v3.28: gabungan StepFlag (tol, jalan bebas hambatan, feri). */
    val flags: Int = 0,
    /** v3.28: petunjuk jalur untuk belokan ini: 0 tak ada data, 1 jalur valid di kiri, 2 di kanan. */
    val lane: Int = 0
)

class Route(
    val lat: DoubleArray,
    val lon: DoubleArray,
    val cum: DoubleArray,
    val steps: List<Step>,
    val total: Double,
    val dur: Double
) {
    /** v3.28: penyedia rute bilang ada tol walau langkahnya tidak bertanda (mis. travelAdvisory.tollInfo Google). */
    var tollHint = false
    val hasToll: Boolean get() = tollHint || steps.any { it.flags and StepFlag.TOLL != 0 }
    val hasFerry: Boolean get() = steps.any { it.flags and StepFlag.FERRY != 0 }
}

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
    fun route(fLat: Double, fLon: Double, tLat: Double, tLon: Double): Route =
        routes(fLat, fLon, tLat, tLon, false).first()

    /**
     * Rute utama + alternatif (v3.28). avoid = minta server membuang tol & feri; bila server menolak parameternya, diulang tanpa itu
     * (Guard/Picker tetap memeriksa bendera tol di tiap langkah).
     */
    fun routes(fLat: Double, fLon: Double, tLat: Double, tLon: Double, avoid: Boolean): List<Route> {
        val base = "https://router.project-osrm.org/route/v1/driving/" +
            "${fmt(fLon)},${fmt(fLat)};${fmt(tLon)},${fmt(tLat)}" +
            "?overview=false&steps=true&geometries=geojson&alternatives=true"
        var j: JSONObject? = null
        if (avoid) {
            try {
                val t = JSONObject(Net.get("$base&exclude=toll,ferry", 20000))
                if (t.optString("code") == "Ok") j = t
            } catch (e: Exception) { /* server tidak mendukung exclude: ulangi tanpa */ }
        }
        if (j == null) j = JSONObject(Net.get(base, 20000))
        if (j.optString("code") != "Ok") throw RuntimeException("Rute tidak ditemukan (${j.optString("code")})")
        val ra = j.getJSONArray("routes")
        val out = ArrayList<Route>()
        for (i in 0 until ra.length()) {
            try { out.add(parseRoute(ra.getJSONObject(i))) } catch (e: Exception) { if (i == 0) throw e }
        }
        return out
    }

    internal fun parseRoute(r: JSONObject): Route {
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
            val name = s.optString("name").trim()
            var fl = 0
            if (s.optString("mode") == "ferry") fl = fl or StepFlag.FERRY
            if (Idn.looksToll(name)) fl = fl or StepFlag.TOLL
            val ints = s.optJSONArray("intersections")
            if (ints != null) {
                for (q in 0 until ints.length()) {
                    val cl = ints.getJSONObject(q).optJSONArray("classes") ?: continue
                    for (c in 0 until cl.length()) {
                        when (cl.optString(c)) {
                            "toll" -> fl = fl or StepFlag.TOLL
                            "motorway" -> fl = fl or StepFlag.MOTORWAY
                            "ferry" -> fl = fl or StepFlag.FERRY
                        }
                    }
                }
            }
            var lane = 0
            val lanes = ints?.optJSONObject(0)?.optJSONArray("lanes")
            if (lanes != null && lanes.length() >= 2) {
                lane = SmartNav.laneHint(BooleanArray(lanes.length()) { lanes.getJSONObject(it).optBoolean("valid", false) })
            }
            steps.add(
                Step(
                    m.optString("type"), m.optString("modifier"), name,
                    s.optDouble("distance", 0.0), cum[max(0, endIdx[i])], m.optInt("exit", 0), fl, lane
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
        Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)"""),
        Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)"""),
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

    private val RX_BODY = listOf(
        Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)"""),
        Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)"""),
        Regex("""center=(-?\d+\.\d+)(?:,|%2C|%2c)(-?\d+\.\d+)"""),
        Regex("""\[null,null,(-?\d+\.\d+),(-?\d+\.\d+)\]"""),
        Regex("""ll=(-?\d+\.\d+)(?:,|%2C|%2c)(-?\d+\.\d+)"""),
        Regex("""(?:q|query|destination)=(-?\d+\.\d+)(?:,|%2C|%2c)(-?\d+\.\d+)""")
    )

    private fun findCoord(s: String): Place? {
        val t = try { java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") } catch (e: Exception) { s }
        for (src in listOf(s, t)) {
            parseCoord(src)?.let { return it }
            for (rx in RX_BODY) {
                val m = rx.find(src) ?: continue
                val la = m.groupValues[1].toDoubleOrNull() ?: continue
                val lo = m.groupValues[2].toDoubleOrNull() ?: continue
                if (abs(la) <= 90 && abs(lo) <= 180 && (la != 0.0 || lo != 0.0))
                    return Place(String.format(Locale.US, "Titik %.5f, %.5f", la, lo), la, lo)
            }
        }
        return null
    }

    /** Cari URL Google Maps lengkap di dalam isi halaman (mis. dalam skrip pengalihan). */
    private fun mapsUrlsIn(body: String): List<String> {
        val t = body.replace("\\u003d", "=").replace("\\u0026", "&").replace("\\/", "/").replace("&amp;", "&")
            .replace("\\x3d", "=").replace("\\x26", "&").replace("\\x2F", "/").replace("\\x2f", "/")
        return Regex("https?://(?:www\\.|maps\\.)?google\\.[a-z.]+/maps[^\"'\\s<>\\\\]+").findAll(t).map { it.value }.distinct().take(5).toList()
    }

    /** Ambil koordinat dari tautan Maps: dari URL akhir dulu, lalu dari isi halamannya. */
    fun fromLink(link: String): Pair<Place?, String> {
        var lastFin = link
        // Coba beberapa User-Agent: tautan pendek Google kadang tidak dialihkan untuk satu jenis klien (mis. hanya menampilkan halaman perantara).
        for (ua in listOf(Net.UA_MOBILE, Net.UA_CURL, Net.UA_DESKTOP)) {
            var (fin, body) = try { Net.fetchFollow(link, ua) } catch (e: Exception) { continue }
            findCoord(fin)?.let { return Pair(it, fin) }
            if (body.isNotEmpty()) findCoord(body)?.let { return Pair(it, fin) }
            // Halaman perantara (tautan pendek yang tidak dialihkan): ambil URL Maps lengkap dari isinya
            for (u in mapsUrlsIn(body)) {
                findCoord(u)?.let { return Pair(it, u) }
                if (u.contains("/maps/place/")) { fin = u; break }
            }
            if (fin != link) return Pair(null, fin)       // sudah dialihkan ke alamat panjang (tanpa koordinat): lanjut cari lewat nama
            lastFin = fin
        }
        return Pair(null, lastFin)
    }

    fun search(q: String): List<Place> {
        val t = q.trim()
        parseCoord(t)?.let { return listOf(it) }
        if (t.startsWith("http")) {
            val p = fromLink(t).first ?: throw RuntimeException("Koordinat tidak ditemukan di tautan")
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
