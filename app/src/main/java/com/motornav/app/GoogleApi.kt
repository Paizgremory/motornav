package com.motornav.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** Penyimpanan API key Google (disimpan di HP saja). */
object GKey {
    private const val PREF = "motornav"
    private const val K = "gkey"
    @Volatile private var cached: String? = null

    fun get(ctx: Context): String {
        cached?.let { return it }
        val v = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(K, "") ?: ""
        cached = v
        return v
    }

    fun set(ctx: Context, key: String) {
        val v = key.trim()
        cached = v
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(K, v).apply()
    }
}

/**
 * Rute dari Google Routes API dan pencarian dari Places API (New).
 * Peta tetap digambar aplikasi; Google hanya memberi data rute dan hasil pencarian.
 */
object GoogleApi {
    private const val ROUTES_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
    private const val PLACES_URL = "https://places.googleapis.com/v1/places:searchText"

    private const val ROUTE_MASK =
        "routes.duration,routes.distanceMeters," +
            "routes.legs.steps.distanceMeters," +
            "routes.legs.steps.navigationInstruction," +
            "routes.legs.steps.polyline.encodedPolyline"

    // ---------- HTTP ----------
    private fun post(url: String, key: String, mask: String?, body: String, timeoutMs: Int): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        c.setRequestProperty("X-Goog-Api-Key", key)
        if (mask != null) c.setRequestProperty("X-Goog-FieldMask", mask)
        try {
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code == 200) return c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
            val err = try {
                c.errorStream?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
            } catch (e: Exception) { "" }
            val msg = try {
                JSONObject(err).getJSONObject("error").optString("message")
            } catch (e: Exception) { "" }
            throw RuntimeException("Google HTTP $code" + if (msg.isNotBlank()) ": $msg" else "")
        } finally {
            c.disconnect()
        }
    }


    // Gaya malam ala Google Maps, label road dibuat terang agar terbaca di LCD kecil.
    private const val NIGHT_STYLE = """[
{"elementType":"geometry","stylers":[{"color":"#242f3e"}]},
{"elementType":"labels.text.stroke","stylers":[{"color":"#242f3e"}]},
{"elementType":"labels.text.fill","stylers":[{"color":"#a8b0bd"}]},
{"featureType":"administrative.locality","elementType":"labels.text.fill","stylers":[{"color":"#d59563"}]},
{"featureType":"poi","elementType":"labels.text.fill","stylers":[{"color":"#d59563"}]},
{"featureType":"poi.business","stylers":[{"visibility":"off"}]},
{"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#263c3f"}]},
{"featureType":"poi.park","elementType":"labels.text.fill","stylers":[{"color":"#6b9a76"}]},
{"featureType":"road","elementType":"geometry","stylers":[{"color":"#4a5568"}]},
{"featureType":"road","elementType":"geometry.stroke","stylers":[{"visibility":"off"}]},
{"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#e1e6ee"}]},
{"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#7a6c55"}]},
{"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#1f2835"}]},
{"featureType":"road.highway","elementType":"labels.text.fill","stylers":[{"color":"#f3d19c"}]},
{"featureType":"transit","elementType":"geometry","stylers":[{"color":"#2f3948"}]},
{"featureType":"transit","elementType":"labels.icon","stylers":[{"visibility":"off"}]},
{"featureType":"water","elementType":"geometry","stylers":[{"color":"#17263c"}]},
{"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#7a869a"}]},
{"featureType":"poi","elementType":"labels.icon","stylers":[{"visibility":"off"}]},
{"featureType":"poi.business","elementType":"labels","stylers":[{"visibility":"off"}]},
{"featureType":"administrative.neighborhood","elementType":"labels","stylers":[{"visibility":"off"}]},
{"featureType":"administrative.land_parcel","elementType":"labels","stylers":[{"visibility":"off"}]},
{"featureType":"road.local","elementType":"geometry","stylers":[{"color":"#3d4859"}]},
{"featureType":"road.local","elementType":"labels","stylers":[{"visibility":"off"}]},
{"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"#5d697d"}]},
{"featureType":"road.arterial","elementType":"labels.icon","stylers":[{"visibility":"off"}]}
]"""

    /** Buat sesi ubin peta Google (gratis, berlaku sekitar 2 minggu). Coba dengan lalu lintas dulu. */
    fun createSession(key: String): String {
        val styles = JSONArray(NIGHT_STYLE)
        fun body(traffic: Boolean): String {
            val b = JSONObject()
            b.put("mapType", "roadmap")
            b.put("language", "id-ID")
            b.put("region", "ID")
            b.put("scale", "scaleFactor2x")
            b.put("styles", styles)
            if (traffic) {
                b.put("layerTypes", JSONArray().put("layerTraffic"))
                b.put("overlay", false)
            }
            return b.toString()
        }
        val url = "https://tile.googleapis.com/v1/createSession?key=$key"
        val txt = try {
            post(url, key, null, body(true), 15000)
        } catch (e: Exception) {
            post(url, key, null, body(false), 15000)
        }
        val s = JSONObject(txt).optString("session")
        if (s.isEmpty()) throw RuntimeException("Sesi peta Google kosong")
        return s
    }

    // ---------- Pencarian tujuan ----------
    fun search(q: String, key: String, biasLat: Double, biasLon: Double): List<Place> {
        val b = JSONObject()
        b.put("textQuery", q)
        b.put("languageCode", "id")
        b.put("regionCode", "ID")
        b.put("maxResultCount", 6)
        if (!biasLat.isNaN() && !biasLon.isNaN()) {
            val center = JSONObject().put("latitude", biasLat).put("longitude", biasLon)
            val circle = JSONObject().put("center", center).put("radius", 30000.0)
            b.put("locationBias", JSONObject().put("circle", circle))
        }
        val j = JSONObject(
            post(PLACES_URL, key, "places.displayName,places.formattedAddress,places.location", b.toString(), 15000)
        )
        val a = j.optJSONArray("places") ?: return emptyList()
        val out = ArrayList<Place>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val loc = o.optJSONObject("location") ?: continue
            val nm = o.optJSONObject("displayName")?.optString("text").orEmpty()
            val ad = o.optString("formattedAddress")
            val label = listOf(nm, ad).filter { it.isNotBlank() }.joinToString(", ")
                .split(", ").take(3).joinToString(", ")
            out.add(Place(label.ifBlank { "Tujuan" }, loc.getDouble("latitude"), loc.getDouble("longitude")))
        }
        return out
    }

    // ---------- Rute ----------
    /** Coba mode sepeda motor dulu; kalau ditolak, pakai mobil dengan lalu lintas. */
    fun route(fLat: Double, fLon: Double, tLat: Double, tLon: Double, key: String): Route =
        routeAlternatives(fLat, fLon, tLat, tLon, key).first()

    /** Rute + alternatif (bila Google punya lebih dari satu untuk pasangan asal-tujuan ini). */
    fun routeAlternatives(fLat: Double, fLon: Double, tLat: Double, tLon: Double, key: String): List<Route> {
        var firstErr: Exception? = null
        for (mode in listOf("TWO_WHEELER", "DRIVE")) {
            try {
                val l = routeMode(fLat, fLon, tLat, tLon, key, mode)
                if (l.isNotEmpty()) return l
            } catch (e: Exception) {
                if (firstErr == null) firstErr = e
                // 403 / API belum aktif / key salah: mode lain pasti gagal juga
                val m = e.message ?: ""
                if (m.contains("HTTP 403") || m.contains("HTTP 401") || m.contains("HTTP 429")) throw e
            }
        }
        throw firstErr ?: RuntimeException("Rute Google gagal")
    }

    private fun latLng(la: Double, lo: Double) =
        JSONObject().put("location", JSONObject().put("latLng", JSONObject().put("latitude", la).put("longitude", lo)))

    private fun routeMode(fLat: Double, fLon: Double, tLat: Double, tLon: Double, key: String, mode: String): List<Route> {
        val b = JSONObject()
        b.put("origin", latLng(fLat, fLon))
        b.put("destination", latLng(tLat, tLon))
        b.put("travelMode", mode)
        b.put("routingPreference", "TRAFFIC_AWARE")
        b.put("computeAlternativeRoutes", true)
        b.put("languageCode", "id")
        b.put("regionCode", "ID")
        b.put("units", "METRIC")
        val j = JSONObject(post(ROUTES_URL, key, ROUTE_MASK, b.toString(), 20000))
        val routes = j.optJSONArray("routes")
        if (routes == null || routes.length() == 0) throw RuntimeException("Rute tidak ditemukan ($mode)")
        val out = ArrayList<Route>()
        for (ri in 0 until routes.length()) {
            try { out.add(parseRoute(routes.getJSONObject(ri))) } catch (e: Exception) {}
        }
        if (out.isEmpty()) throw RuntimeException("Rute tidak terbaca ($mode)")
        return out
    }

    private fun parseRoute(r: JSONObject): Route {
        val sj = r.getJSONArray("legs").getJSONObject(0).getJSONArray("steps")

        val la = ArrayList<Double>()
        val lo = ArrayList<Double>()
        val endIdx = IntArray(sj.length())
        for (i in 0 until sj.length()) {
            val enc = sj.getJSONObject(i).optJSONObject("polyline")?.optString("encodedPolyline").orEmpty()
            if (enc.isNotEmpty()) decode(enc, la, lo)
            endIdx[i] = la.size - 1
        }
        val n = la.size
        if (n < 2) throw RuntimeException("Rute terlalu pendek")
        val cum = DoubleArray(n)
        for (i in 1 until n) cum[i] = cum[i - 1] + Geo.dist(la[i - 1], lo[i - 1], la[i], lo[i])

        val steps = ArrayList<Step>()
        for (i in 0 until sj.length()) {
            val s = sj.getJSONObject(i)
            val ni = s.optJSONObject("navigationInstruction")
            val man = ni?.optString("maneuver").orEmpty()
            val (type, mod) = if (i == 0) Pair("depart", "") else mapManeuver(man)
            val name = streetFrom(ni?.optString("instructions").orEmpty())
            steps.add(
                Step(type, mod, name, s.optDouble("distanceMeters", 0.0), cum[max(0, endIdx[i])], 0)
            )
        }
        // Google tidak punya langkah "tiba"; aplikasi mengharapkannya di akhir.
        steps.add(Step("arrive", "", "", 0.0, cum[n - 1], 0))

        val dur = r.optString("duration", "").removeSuffix("s").toDoubleOrNull() ?: (cum[n - 1] / 8.0)
        return Route(la.toDoubleArray(), lo.toDoubleArray(), cum, steps, cum[n - 1], dur)
    }

    private fun mapManeuver(m: String): Pair<String, String> = when (m) {
        "TURN_LEFT" -> Pair("turn", "left")
        "TURN_RIGHT" -> Pair("turn", "right")
        "TURN_SLIGHT_LEFT" -> Pair("turn", "slight left")
        "TURN_SLIGHT_RIGHT" -> Pair("turn", "slight right")
        "TURN_SHARP_LEFT" -> Pair("turn", "sharp left")
        "TURN_SHARP_RIGHT" -> Pair("turn", "sharp right")
        "UTURN_LEFT", "UTURN_RIGHT" -> Pair("turn", "uturn")
        "RAMP_LEFT" -> Pair("on ramp", "slight left")
        "RAMP_RIGHT" -> Pair("on ramp", "slight right")
        "FORK_LEFT" -> Pair("fork", "slight left")
        "FORK_RIGHT" -> Pair("fork", "slight right")
        "MERGE" -> Pair("merge", "straight")
        "ROUNDABOUT_LEFT", "ROUNDABOUT_RIGHT" -> Pair("roundabout", "straight")
        else -> Pair("continue", "straight") // STRAIGHT, NAME_CHANGE, FERRY, tak dikenal
    }

    /** Ambil nama jalan dari teks petunjuk Google, mis. "Belok kiri ke Jl. Ahmad Yani". */
    private fun streetFrom(instr: String): String {
        val line = instr.lineSequence().firstOrNull()?.trim().orEmpty()
        for (sep in listOf(" ke ", " onto ", " menuju ", " toward ")) {
            val i = line.indexOf(sep)
            if (i >= 0) return line.substring(i + sep.length).trim()
        }
        return ""
    }

    /** Dekode polyline Google (presisi 1e5), titik kembar berurutan dibuang. */
    private fun decode(s: String, la: ArrayList<Double>, lo: ArrayList<Double>) {
        var i = 0
        var y = 0
        var x = 0
        while (i < s.length) {
            var sh = 0
            var res = 0
            var c: Int
            do {
                c = s[i++].code - 63
                res = res or ((c and 0x1f) shl sh)
                sh += 5
            } while (c >= 0x20 && i < s.length)
            y += if (res and 1 != 0) (res shr 1).inv() else res shr 1
            sh = 0
            res = 0
            do {
                c = s[i++].code - 63
                res = res or ((c and 0x1f) shl sh)
                sh += 5
            } while (c >= 0x20 && i < s.length)
            x += if (res and 1 != 0) (res shr 1).inv() else res shr 1
            val ny = y / 1e5
            val nx = x / 1e5
            if (la.isNotEmpty() && abs(la.last() - ny) < 1e-9 && abs(lo.last() - nx) < 1e-9) continue
            la.add(ny)
            lo.add(nx)
        }
    }
}

/** Pemilih mesin: Google kalau ada key, jatuh ke OSRM/Nominatim kalau gagal. */
object Engine {
    @Volatile var note = ""   // keterangan untuk status, mis. "Rute: Google"
    @Volatile var mapNote = ""

    fun route(ctx: Context, fLat: Double, fLon: Double, tLat: Double, tLon: Double): Route =
        routeAlternatives(ctx, fLat, fLon, tLat, tLon).first()

    /** Rute terbaik + alternatifnya (untuk dialog pilih rute sebelum mulai navigasi). */
    fun routeAlternatives(ctx: Context, fLat: Double, fLon: Double, tLat: Double, tLon: Double): List<Route> {
        val key = GKey.get(ctx)
        if (key.isNotEmpty()) {
            try {
                val l = GoogleApi.routeAlternatives(fLat, fLon, tLat, tLon, key)
                note = "Rute: Google"
                return l
            } catch (e: Exception) {
                note = "Rute: OSRM (Google gagal: ${e.message})"
            }
        } else note = "Rute: OSRM (belum ada API key)"
        return Osrm.routeAlternatives(fLat, fLon, tLat, tLon)
    }

    fun search(ctx: Context, q: String, biasLat: Double, biasLon: Double): List<Place> {
        val t = q.trim()
        // Koordinat dan tautan Maps tetap ditangani parser lokal.
        if (Geocode.parseCoord(t) != null || t.startsWith("http")) return Geocode.search(t)
        val key = GKey.get(ctx)
        if (key.isNotEmpty()) {
            try {
                val l = GoogleApi.search(t, key, biasLat, biasLon)
                if (l.isNotEmpty()) return l
            } catch (e: Exception) {
                throw RuntimeException("Pencarian Google gagal: ${e.message}")
            }
        }
        return Geocode.search(t)
    }
}
