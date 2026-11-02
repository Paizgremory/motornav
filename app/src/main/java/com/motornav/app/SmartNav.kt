package com.motornav.app

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.atan2
import kotlin.math.cos

/*
 * v3.28: navigasi pintar. Berkas ini SENGAJA tanpa ketergantungan Android (hanya Kotlin + Geo/Route/Step dari Routing.kt)
 * supaya logikanya bisa diuji di komputer biasa.
 *   Announcer       suara petunjuk bertahap menurut kecepatan, belokan berurutan, bundaran, percabangan, jalur, sisi tujuan
 *   Guard           pemeriksaan kewajaran rute (tol, feri, memutar, ujung rute jauh dari tujuan, waktu tak wajar)
 *   Picker          memilih satu rute dari beberapa kandidat (seimbang / tercepat / terpendek, hindari tol)
 *   Advisor         menilai apakah rute baru benar-benar lebih cepat dan berbeda dari rute yang sedang diikuti
 *   Monitor         GPS lemah / terputus, berlawanan arah dengan rute
 *   RerouteTracker  deteksi rute ulang berulang-ulang
 */

/** Bendera pada Step.flags. */
object StepFlag {
    const val TOLL = 1
    const val MOTORWAY = 2
    const val FERRY = 4
}

enum class Pref(val label: String) {
    BALANCED("Seimbang"), FASTEST("Tercepat"), SHORTEST("Terpendek");

    companion object {
        fun of(i: Int): Pref = values().getOrElse(i) { BALANCED }
    }
}

/** level: 1 info, 2 peringatan, 3 bahaya. */
class Issue(val level: Int, val text: String, val code: String = "")

/** Hasil perencanaan: rute terpilih + peringatan + keterangan mengapa rute itu dipilih. */
class Plan(val route: Route, val issues: List<Issue>, val notes: List<String>)

object Idn {
    private val id = Locale("id", "ID")

    fun km1(m: Double): String = String.format(id, "%.1f", m / 1000.0)

    /** "350 meter" atau "1,2 kilometer" (dibulatkan ke 50 m). */
    fun spokenDist(m: Double): String {
        if (m >= 950) return km1(m) + " kilometer"
        val v = ((m / 50).roundToInt() * 50).coerceAtLeast(50)
        return "$v meter"
    }

    fun spokenTotal(m: Double): String =
        if (m >= 1000) km1(m) + " kilometer" else "${(m / 10).roundToInt() * 10} meter"

    fun minutes(sec: Double): Int = max(1, (sec / 60.0).roundToInt())

    private val tollRx = Regex("""(?i)\b(?:tol|toll)\b""")
    private val ferryRx = Regex("""(?i)\b(?:feri|ferry|kapal)\b""")
    fun looksToll(s: String) = tollRx.containsMatchIn(s)
    fun looksFerry(s: String) = ferryRx.containsMatchIn(s)

    private val ordWords = listOf("pertama", "kedua", "ketiga", "keempat", "kelima", "keenam", "ketujuh", "kedelapan")

    /** Nomor jalan keluar bundaran dari teks petunjuk ("jalan keluar ke-2", "keluar kedua", "2nd exit"). 0 = tidak ketemu. */
    fun parseExit(text: String): Int {
        val t = text.lowercase(id)
        Regex("""(\d)(?:st|nd|rd|th)\s+exit""").find(t)?.let { return it.groupValues[1].toInt() }
        Regex("""(?:keluar|exit)\D{0,14}?(\d)""").find(t)?.let { return it.groupValues[1].toInt() }
        Regex("""(?:keluar|exit)\s+(?:ke-?)?(pertama|kedua|ketiga|keempat|kelima|keenam|ketujuh|kedelapan)""").find(t)?.let {
            return ordWords.indexOf(it.groupValues[1]) + 1
        }
        Regex("""(?:ke-?)(pertama|kedua|ketiga|keempat|kelima|keenam|ketujuh|kedelapan)\s+(?:jalan\s+)?keluar""").find(t)?.let {
            return ordWords.indexOf(it.groupValues[1]) + 1
        }
        return 0
    }
}

object SmartNav {
    /** Dari daftar jalur (kiri ke kanan) yang valid untuk belokan: 1 = semua valid di kiri, 2 = semua valid di kanan, 0 = tak ada petunjuk. */
    fun laneHint(valid: BooleanArray): Int {
        if (valid.size < 2) return 0
        val first = valid.indexOfFirst { it }
        val last = valid.indexOfLast { it }
        if (first < 0) return 0
        if (valid.all { it }) return 0
        val contiguous = (first..last).all { valid[it] }
        if (!contiguous) return 0
        return when {
            first == 0 && last < valid.size - 1 -> 1
            last == valid.size - 1 && first > 0 -> 2
            else -> 0
        }
    }

    /** Arah (derajat, 0 = utara) segmen rute di sekitar indeks idx. */
    fun routeBearing(r: Route, idx: Int): Float {
        val i = idx.coerceIn(0, r.lat.size - 2)
        val dy = r.lat[i + 1] - r.lat[i]
        val dx = (r.lon[i + 1] - r.lon[i]) * cos(Math.toRadians(r.lat[i]))
        return ((Math.toDegrees(atan2(dx, dy)) + 360.0) % 360.0).toFloat()
    }

    /** Di sisi mana tujuan terhadap ujung rute: -1 kiri, 1 kanan, 0 tidak jelas (terlalu dekat dengan jalan). */
    fun destSide(r: Route, dLat: Double, dLon: Double): Int {
        val n = r.lat.size
        if (n < 2) return 0
        val gap = Geo.dist(r.lat[n - 1], r.lon[n - 1], dLat, dLon)
        if (gap < 8.0 || gap > 120.0) return 0
        val c = cos(Math.toRadians(r.lat[n - 1]))
        val ax = (r.lon[n - 1] - r.lon[n - 2]) * c
        val ay = r.lat[n - 1] - r.lat[n - 2]
        val bx = (dLon - r.lon[n - 1]) * c
        val by = dLat - r.lat[n - 1]
        val cross = ax * by - ay * bx
        return if (abs(cross) < 1e-12) 0 else if (cross > 0) -1 else 1
    }
}

// =====================================================================================================================
// Suara petunjuk
// =====================================================================================================================
class Announcer {
    private var step = -1
    private var level = 0
    private var longStep = -1

    fun reset() { step = -1; level = 0; longStep = -1 }

    /** [jauh, tengah, dekat] dalam meter menurut kecepatan (m/s): makin cepat makin awal diumumkan. */
    fun levels(speedMs: Double): DoubleArray {
        val v = if (speedMs < 3.0) 8.0 else speedMs
        return doubleArrayOf((v * 50).coerceIn(350.0, 1300.0), (v * 20).coerceIn(150.0, 500.0), (v * 7).coerceIn(40.0, 130.0))
    }

    private fun isRound(t: String) = t == "roundabout" || t == "rotary" || t == "roundabout turn" || t == "exit roundabout" || t == "exit rotary"

    private fun quiet(s: Step): Boolean =
        (s.mod == "" || s.mod == "straight") && !isRound(s.type) && s.type != "arrive" && s.type != "fork"

    /** Frasa tindakan huruf kecil, tanpa jarak: "belok kiri", "masuk bundaran, ambil jalan keluar ke-2", dst. */
    fun action(s: Step): String {
        if (s.type == "arrive") return "tiba di tujuan"
        if (isRound(s.type)) return if (s.exit > 0) "masuk bundaran, ambil jalan keluar ke-${s.exit}" else "masuk bundaran"
        if (s.type == "fork") return when (s.mod) {
            "left", "slight left", "sharp left" -> "ambil kiri di percabangan"
            "right", "slight right", "sharp right" -> "ambil kanan di percabangan"
            else -> "tetap lurus di percabangan"
        }
        val base = Txt.turn(s).replaceFirstChar { it.lowercase() }
        return if (s.type == "end of road" && s.mod != "straight" && s.mod != "") "di ujung jalan, $base" else base
    }

    private fun sideWord(s: Step): String? = when {
        s.mod.contains("left") -> "kiri"
        s.mod.contains("right") -> "kanan"
        else -> null
    }

    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

    /**
     * Pesan untuk belokan berikutnya [man] (null = tak perlu bicara sekarang). [after] = langkah sesudah belokan itu.
     * Tiap level (jauh / tengah / dekat) hanya diucapkan sekali per belokan; yang terlewat (mis. GPS baru tersambung) dilompati.
     */
    fun turn(idx: Int, man: Step, after: Step?, dist: Double, speedMs: Double, destSide: Int): String? {
        if (idx != step) { step = idx; level = 0 }
        val lv = levels(speedMs)
        val now = when {
            dist <= lv[2] -> 3
            dist <= lv[1] -> 2
            dist <= lv[0] -> 1
            else -> 0
        }
        if (now <= level) return null
        level = now
        if (quiet(man)) return null
        val terse = speedMs > 16.7                                   // di atas ±60 km/j: kalimat dipendekkan
        val act = action(man)
        if (man.type == "arrive") {
            return when {
                now == 3 -> "Tujuan di depan" + when (destSide) { -1 -> ", di sebelah kiri."; 1 -> ", di sebelah kanan."; else -> "." }
                else -> "Dalam ${Idn.spokenDist(dist)}, tiba di tujuan."
            }
        }
        val onto = if (!terse && man.name.isNotBlank() && !isRound(man.type)) " ke ${man.name}" else ""
        val chain = when {
            after == null || man.len >= 250.0 -> ""
            after.type == "arrive" -> ", lalu tiba di tujuan"
            quiet(after) -> ""
            else -> ", lalu ${action(after)}"
        }
        val lane = if (!terse && man.lane != 0 && now <= 2) " Gunakan jalur ${if (man.lane == 1) "kiri" else "kanan"}." else ""
        val prep = if (!terse && now == 1 && (man.mod == "left" || man.mod == "right" || man.mod == "sharp left" || man.mod == "sharp right")) {
            " Bersiap merapat ke ${sideWord(man)}."
        } else ""
        return if (now == 3) "${cap(act)} sekarang$onto$chain."
        else "Dalam ${Idn.spokenDist(dist)}, $act$onto$chain.$prep$lane"
    }

    /** Setelah belokan selesai dan jalan lurus panjang di depan: "Ikuti Jalan X sejauh 4,2 kilometer." Sekali per langkah. */
    fun straight(idx: Int, cur: Step, alongInStep: Double, distToNext: Double): String? {
        if (idx <= 0 || idx == longStep || cur.type == "arrive") return null
        if (alongInStep > 400.0 || distToNext < 1200.0) return null
        longStep = idx
        return if (cur.name.isNotBlank()) "Ikuti ${cur.name} sejauh ${Idn.spokenTotal(distToNext)}."
        else "Lurus sejauh ${Idn.spokenTotal(distToNext)}."
    }
}

// =====================================================================================================================
// Pemeriksaan kewajaran rute
// =====================================================================================================================
object Guard {
    fun check(r: Route, fLat: Double, fLon: Double, tLat: Double, tLon: Double, avoidToll: Boolean): List<Issue> {
        val out = ArrayList<Issue>()
        val n = r.lat.size
        if (n < 2) return out
        val startGap = Geo.dist(fLat, fLon, r.lat[0], r.lon[0])
        if (startGap > 250.0) out.add(Issue(2, "Rute dimulai ${startGap.roundToInt()} meter dari posisi Anda. Periksa titik GPS.", "start"))
        val endGap = Geo.dist(tLat, tLon, r.lat[n - 1], r.lon[n - 1])
        if (endGap > 150.0) out.add(Issue(2, "Rute berakhir ${endGap.roundToInt()} meter dari titik tujuan. Mungkin tidak ada jalan sampai ke sana.", "end"))
        val straight = Geo.dist(fLat, fLon, tLat, tLon)
        if (straight > 1500.0 && r.total > straight * 2.5) {
            out.add(Issue(2, "Rute memutar jauh: ${String.format(Locale("id", "ID"), "%.1f", r.total / straight)} kali garis lurus.", "detour"))
        }
        if (r.hasToll) {
            out.add(Issue(3, if (avoidToll) "Tidak ada rute tanpa tol. Rute ini melewati jalan tol, yang umumnya dilarang untuk sepeda motor."
            else "Rute melewati jalan tol. Sepeda motor umumnya dilarang masuk tol.", "toll"))
        }
        if (r.hasFerry) out.add(Issue(2, "Rute memakai penyeberangan feri.", "ferry"))
        val u = r.steps.count { it.mod == "uturn" }
        if (u >= 2) out.add(Issue(2, "Rute berisi $u putar balik.", "uturn"))
        val v = r.total / max(r.dur, 1.0)
        if (r.total > 1000.0 && (v > 33.0 || v < 1.5)) {
            out.add(Issue(2, "Perkiraan waktu rute tampak tidak wajar (rata-rata ${(v * 3.6).roundToInt()} km/j).", "time"))
        }
        return out
    }

    /** Kalimat ringkas untuk diucapkan: hanya peringatan dan bahaya, paling banyak dua. */
    fun spoken(issues: List<Issue>): String =
        issues.filter { it.level >= 2 }.sortedByDescending { it.level }.take(2).joinToString(" ") { "Peringatan. ${it.text}" }
}

// =====================================================================================================================
// Memilih rute
// =====================================================================================================================
object Picker {
    class Pick(val route: Route, val notes: List<String>)

    private val turnTypes = setOf("turn", "end of road", "fork", "roundabout", "rotary", "roundabout turn", "on ramp", "off ramp", "merge")

    fun turns(r: Route): Int = r.steps.count { it.type in turnTypes && it.mod != "straight" }

    fun cost(r: Route, pref: Pref, avoidToll: Boolean): Double {
        var c = when (pref) {
            Pref.FASTEST -> r.dur
            Pref.SHORTEST -> r.total / 8.0
            Pref.BALANCED -> r.dur + 7.0 * turns(r) + 45.0 * r.steps.count { it.mod == "uturn" }
        }
        if (r.hasToll && avoidToll) c += 1e7
        if (r.hasFerry) c += 1e6
        return c
    }

    fun pick(cands: List<Route>, pref: Pref, avoidToll: Boolean): Pick {
        require(cands.isNotEmpty())
        val fastest = cands.minOf { it.dur }
        val pool = if (pref == Pref.SHORTEST) cands.filter { it.dur <= fastest * 1.6 }.ifEmpty { cands } else cands
        val best = pool.minByOrNull { cost(it, pref, avoidToll) } ?: cands[0]
        val notes = ArrayList<String>()
        val first = cands[0]
        if (best !== first) {
            val dMin = ((best.dur - first.dur) / 60.0).roundToInt()
            val dKm = (best.total - first.total) / 1000.0
            if (first.hasToll && !best.hasToll) {
                notes.add("Rute utama lewat tol, dipilih rute tanpa tol (${signed(dMin)} menit).")
            } else {
                notes.add("Dipilih rute ${pref.label.lowercase()} dari ${cands.size} pilihan (${signed(dMin)} menit, " +
                    String.format(Locale("id", "ID"), "%+.1f", dKm) + " km dibanding rute utama).")
            }
        } else if (cands.size > 1) {
            notes.add("Rute utama dipilih dari ${cands.size} pilihan.")
        }
        return Pick(best, notes)
    }

    private fun signed(m: Int) = if (m >= 0) "+$m" else "$m"
}

// =====================================================================================================================
// Rute alternatif saat berkendara
// =====================================================================================================================
object Advisor {
    /** Seberapa besar (0..1) [cand] berimpit dengan [cur]: titik cand tiap ±100 m yang berjarak < 40 m dari polyline cur. */
    fun overlap(cur: Route, cand: Route): Double {
        var tot = 0
        var near = 0
        var next = 0.0
        for (i in cand.lat.indices) {
            if (cand.cum[i] < next) continue
            next = cand.cum[i] + 100.0
            tot++
            if (distToRoute(cur, cand.lat[i], cand.lon[i]) < 40.0) near++
        }
        return if (tot == 0) 1.0 else near.toDouble() / tot
    }

    private fun distToRoute(r: Route, la: Double, lo: Double): Double {
        val cosL = cos(Math.toRadians(la))
        var best = Double.MAX_VALUE
        for (i in 0 until r.lat.size - 1) {
            val ax = (r.lon[i] - lo) * 111320.0 * cosL
            val ay = (r.lat[i] - la) * 110574.0
            val bx = (r.lon[i + 1] - lo) * 111320.0 * cosL
            val by = (r.lat[i + 1] - la) * 110574.0
            val dx = bx - ax
            val dy = by - ay
            val l2 = dx * dx + dy * dy
            val t = (if (l2 > 0) (-(ax * dx + ay * dy)) / l2 else 0.0).coerceIn(0.0, 1.0)
            val d = Math.hypot(ax + t * dx, ay + t * dy)
            if (d < best) best = d
        }
        return best
    }

    /**
     * Detik yang dihemat bila pindah ke [cand]; 0 bila tidak layak: harus menghemat minimal max(3 menit, 15%), tidak boleh lewat tol
     * (bila dihindari), tidak punya peringatan bahaya baru, dan harus benar-benar jalan lain (tumpang tindih < 85%).
     */
    fun savings(curRemSec: Double, cur: Route, cand: Route, avoidToll: Boolean, destGapOk: Boolean): Double {
        if (!destGapOk) return 0.0
        if (cand.hasFerry && !cur.hasFerry) return 0.0
        if (cand.hasToll && (avoidToll || !cur.hasToll)) return 0.0
        val gain = curRemSec - cand.dur
        if (gain < max(180.0, curRemSec * 0.15)) return 0.0
        if (overlap(cur, cand) > 0.85) return 0.0
        return gain
    }
}

// =====================================================================================================================
// Pemantau GPS dan arah
// =====================================================================================================================
class Monitor {
    private var weakSince = -1L
    private var wrongSince = -1L
    private var lastWeak = -1_000_000L
    private var lastWrong = -1_000_000L
    private var lastStale = -1_000_000L

    /**
     * Dipanggil ±1 kali per detik saat navigasi. now/lastFixAt dalam ms (jam yang sama). acc = akurasi GPS (m, <0 tak diketahui),
     * offDist = jarak ke rute (m), routeBearing = arah rute di titik itu. [canSpeak] false bila baru ada petunjuk belokan.
     * Mengembalikan kalimat peringatan atau null.
     */
    fun update(now: Long, acc: Float, speedMs: Float, lastFixAt: Long, heading: Float, routeBearing: Float, offDist: Double, canSpeak: Boolean): String? {
        if (lastFixAt > 0 && now - lastFixAt > 12000) {
            if (canSpeak && now - lastStale > 60000) { lastStale = now; return "Sinyal GPS terputus. Petunjuk berhenti sampai sinyal kembali." }
            return null
        }
        if (acc > 40f) {
            if (weakSince < 0L) weakSince = now
            else if (now - weakSince > 8000 && canSpeak && now - lastWeak > 120000) {
                lastWeak = now
                return "Sinyal GPS lemah. Petunjuk bisa kurang akurat."
            }
        } else weakSince = -1L
        val wrong = speedMs > 3f && offDist < 40.0 && abs(Geo.angDiff(heading, routeBearing)) > 130f
        if (wrong) {
            if (wrongSince < 0L) wrongSince = now
            else if (now - wrongSince > 4000 && canSpeak && now - lastWrong > 45000) {
                lastWrong = now
                return "Anda berlawanan arah dengan rute. Putar balik bila memungkinkan."
            }
        } else wrongSince = -1L
        return null
    }

    fun reset() { weakSince = -1L; wrongSince = -1L }
}

class RerouteTracker {
    private val times = ArrayList<Long>()

    /** Catat satu rute ulang; mengembalikan berapa kali dalam 3 menit terakhir. */
    fun hit(now: Long): Int {
        times.add(now)
        times.removeAll { now - it > 180000L }
        return times.size
    }

    fun clear() = times.clear()
}
