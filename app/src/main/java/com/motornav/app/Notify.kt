package com.motornav.app

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.ContactsContract
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import java.text.Normalizer
import java.util.concurrent.Executors
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper

/**
 * Meneruskan notifikasi & panggilan HP ke layar ESP32 lewat BLE.
 * Teks dibersihkan jadi ASCII (font layar tidak mendukung emoji), logo aplikasi dikirim 48x48 RGB565.
 */
object Notify {
    private const val PREF = "motornav"
    private val exec = Executors.newSingleThreadExecutor()
    private var lastKey = ""
    private var lastKeyAt = 0L
    @Volatile private var ringing = false
    @Volatile private var ringKey: String? = null        // kunci notifikasi panggilan aplikasi lain (WhatsApp, dll.)
    @Volatile private var ringNumber: String? = null
    @Volatile private var answered = false
    @Volatile private var rejected = false
    @Volatile private var ringName = ""
    @Volatile private var ringNum = ""
    @Volatile private var ringSbn: StatusBarNotification? = null

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ---------- pengaturan ----------
    fun notifOn(c: Context) = prefs(c).getBoolean("nt_on", true)
    fun setNotifOn(c: Context, v: Boolean) = prefs(c).edit().putBoolean("nt_on", v).apply()
    fun bodyOn(c: Context) = prefs(c).getBoolean("nt_body", true)
    fun setBodyOn(c: Context, v: Boolean) = prefs(c).edit().putBoolean("nt_body", v).apply()
    fun callOn(c: Context) = prefs(c).getBoolean("call_on", true)
    fun setCallOn(c: Context, v: Boolean) = prefs(c).edit().putBoolean("call_on", v).apply()
    fun driveOn(c: Context) = prefs(c).getBoolean("drive_on", true)
    fun setDriveOn(c: Context, v: Boolean) = prefs(c).edit().putBoolean("drive_on", v).apply()
    /** Aplikasi prioritas saat berkendara. null = belum dipilih (pakai bawaan: chat populer). */
    fun drivePri(c: Context): Set<String>? = prefs(c).getStringSet("drive_apps", null)
    fun setDrivePri(c: Context, s: Set<String>) = prefs(c).edit().putStringSet("drive_apps", HashSet(s)).apply()
    private val defaultPri = listOf("whatsapp", "telegram", "messag", "mms", "sms", "signal", "jp.naver.line", "instagram.android", "facebook.orca")
    fun isPriority(c: Context, pkg: String): Boolean {
        val set = drivePri(c)
        return if (set != null) pkg in set else defaultPri.any { pkg.contains(it) }
    }

    fun blocked(c: Context): Set<String> = prefs(c).getStringSet("nt_blocked", emptySet()) ?: emptySet()
    fun setBlocked(c: Context, s: Set<String>) = prefs(c).edit().putStringSet("nt_blocked", HashSet(s)).apply()

    /** Daftar aplikasi yang pernah mengirim notifikasi: paket -> nama. */
    fun seen(c: Context): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (e in prefs(c).getStringSet("nt_seen", emptySet()) ?: emptySet()) {
            val i = e.indexOf('|')
            if (i > 0) out[e.substring(0, i)] = e.substring(i + 1)
        }
        return out
    }

    private fun markSeen(c: Context, pkg: String, label: String) {
        val p = prefs(c)
        val cur = HashSet(p.getStringSet("nt_seen", emptySet()) ?: emptySet())
        if (cur.any { it.startsWith("$pkg|") }) return
        cur.add("$pkg|$label")
        p.edit().putStringSet("nt_seen", cur).apply()
    }

    // ---------- teks ----------
    /** Ubah ke ASCII: aksen dibuang, tanda baca khusus diganti, emoji dibuang, spasi dirapatkan. */
    fun ascii(s: CharSequence?, max: Int): String {
        if (s == null) return ""
        val n = Normalizer.normalize(s.toString(), Normalizer.Form.NFD)
        val sb = StringBuilder()
        for (ch in n) {
            when {
                ch.code in 32..126 -> sb.append(ch)
                ch == '\n' || ch == '\r' || ch == '\t' || ch == ' ' -> sb.append(' ')
                ch.code in 0x300..0x36F -> {}
                ch == '‘' || ch == '’' -> sb.append('\'')
                ch == '“' || ch == '”' -> sb.append('"')
                ch == '–' || ch == '—' -> sb.append('-')
                ch == '…' -> sb.append("...")
                ch == '•' -> sb.append('-')
                ch == '×' -> sb.append('x')
            }
        }
        val t = sb.toString().replace(Regex(" {2,}"), " ").trim()
        return if (t.length > max) t.substring(0, max - 3).trimEnd() + "..." else t
    }

    // ---------- logo aplikasi ----------
    private fun drawableToIcon(d: Drawable): ByteArray {
        val big = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, 96, 96)
        d.draw(Canvas(big))
        val bmp = Bitmap.createScaledBitmap(big, 48, 48, true)
        val px = IntArray(48 * 48)
        bmp.getPixels(px, 0, 48, 0, 0, 48, 48)
        val out = ByteArray(48 * 48 * 2)
        for (y in 0 until 48) for (x in 0 until 48) {
            val p = px[y * 48 + x]
            val dx = x - 23.5f; val dy = y - 23.5f
            var v = 0
            if ((p ushr 24) >= 128 && dx * dx + dy * dy <= 24f * 24f) {
                v = (((p shr 16) and 0xF8) shl 8) or (((p shr 8) and 0xFC) shl 3) or ((p and 0xFF) shr 3)
                if (v == 0) v = 0x0841                      // 0 = transparan, jadi hitam murni digeser sedikit
            }
            out[(y * 48 + x) * 2] = (v and 255).toByte()
            out[(y * 48 + x) * 2 + 1] = ((v shr 8) and 255).toByte()
        }
        if (!bmp.isRecycled && bmp !== big) bmp.recycle()
        big.recycle()
        return out
    }

    private fun iconFor(c: Context, sbn: StatusBarNotification?, pkg: String): ByteArray? {
        try {
            return drawableToIcon(c.packageManager.getApplicationIcon(pkg))
        } catch (e: Exception) {}
        try {
            val d = sbn?.notification?.smallIcon?.loadDrawable(c)
            if (d != null) {
                d.setTint(0xFFFFFFFF.toInt())
                return drawableToIcon(d)
            }
        } catch (e: Exception) {}
        return null
    }

    private fun appLabel(c: Context, pkg: String, n: Notification?): String {
        try {
            val pm = c.packageManager
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {}
        val sub = n?.extras?.getString("android.substName")
        if (!sub.isNullOrBlank()) return sub
        return pkg.substringAfterLast('.')
    }

    // ---------- notifikasi ----------
    private val skipCat = setOf("progress", "transport", "service", "navigation", "status", "sys", "call", "alarm")

    @Suppress("DEPRECATION")
    private fun bodyOf(n: Notification): String {
        val ex = n.extras ?: return ""
        try {
            val arr = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
            val b = if (arr != null && arr.isNotEmpty()) arr[arr.size - 1] as? Bundle else null
            if (b != null) {
                val t = b.getCharSequence("text")
                if (!t.isNullOrBlank()) {
                    var who = b.getCharSequence("sender")?.toString()
                    if (who.isNullOrBlank()) {
                        try { who = b.getParcelable<android.app.Person>("sender_person")?.name?.toString() } catch (e: Throwable) {}
                    }
                    val grp = ex.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
                    return if (grp && !who.isNullOrBlank() && !t.startsWith(who)) "$who: $t" else t.toString()
                }
            }
        } catch (e: Throwable) {}
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
        if (!big.isNullOrBlank()) return big.toString()
        val txt = ex.getCharSequence(Notification.EXTRA_TEXT)
        if (!txt.isNullOrBlank()) return txt.toString()
        val lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (lines != null && lines.isNotEmpty()) return lines[lines.size - 1].toString()
        return ""
    }

    fun onPosted(c: Context, sbn: StatusBarNotification, rank: android.service.notification.NotificationListenerService.RankingMap?) {
        val n = sbn.notification ?: return
        val pkg = sbn.packageName ?: return
        if (pkg == c.packageName) return
        if (n.category == Notification.CATEGORY_CALL) { callNotif(c, sbn, true); return }
        if (n.category == "missed_call" && callOn(c) &&
            c.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) return   // sudah ditangani status telepon
        if (!notifOn(c)) return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if (n.category != null && n.category in skipCat) return
        try {
            val r = android.service.notification.NotificationListenerService.Ranking()
            if (rank != null && rank.getRanking(sbn.key, r)) {
                if (r.importance < android.app.NotificationManager.IMPORTANCE_DEFAULT) return   // senyap: lewati
                if (!r.matchesInterruptionFilter()) return                                     // dibungkam Jangan Ganggu
            }
        } catch (e: Exception) {}

        val label = appLabel(c, pkg, n)
        markSeen(c, pkg, label)
        if (pkg in blocked(c)) return
        val drive = driveOn(c) && Core.isNavigating()
        if (drive && !isPriority(c, pkg)) return                       // berkendara: hanya aplikasi prioritas

        val ex = n.extras
        var title = ex?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        if (title.isNullOrBlank()) title = ex?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        title = title?.replace(Regex("\\s*\\(\\d+ [^)]*\\)\\s*$"), "")        // "Budi (3 pesan)" -> "Budi"
        var body = bodyOf(n)
        val tClean = ascii(title, 40)
        var bClean = ascii(body, 220)
        if (bClean.isEmpty() && body.isNotBlank()) bClean = "(emoji / stiker)"
        if (tClean.isEmpty() && bClean.isEmpty()) return
        if (!bodyOn(c)) bClean = "Pesan baru"

        val key = "$pkg|$tClean|$bClean"
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (key == lastKey && now - lastKeyAt < 5000) return
            lastKey = key; lastKeyAt = now
        }
        val msgApp = ascii(label, 22).ifEmpty { "Notifikasi" }
        enqueue(c, Pending(pkg, msgApp, tClean, bClean, drive, sbn))
    }

    // ---------- antrean notifikasi ----------
    // ESP32 hanya menampilkan satu notifikasi sekali waktu. Notifikasi berikutnya menunggu di antrean HP (maks 5, yang terlama dibuang)
    // dan dikirim setelah ESP32 memberi kabar "selesai" ([0xE1][0x02]); bila kabar itu hilang, dikirim setelah perkiraan lama tampil.
    private class Pending(val pkg: String, val app: String, val title: String, val body: String, val drive: Boolean, val sbn: StatusBarNotification?)
    private val queue = ArrayDeque<Pending>()
    @Volatile private var busyUntil = 0L
    @Volatile private var appCtx: Context? = null
    private val mainH = Handler(Looper.getMainLooper())

    private fun estimateMs(p: Pending): Long {
        if (p.drive) return 4500L
        val lines = (p.body.length + 10) / 11
        val pages = maxOf(1, (lines + 3) / 4)
        return (1800L + pages * 3400L).coerceIn(7000L, 26000L)
    }

    private fun enqueue(c: Context, p: Pending) {
        appCtx = c.applicationContext
        exec.execute {
            synchronized(queue) {
                queue.addLast(p)
                while (queue.size > 5) queue.removeFirst()
            }
            pump()
        }
    }

    private fun pump() {
        val c = appCtx ?: return
        val ble = Core.bleIfReady() ?: return
        if (ringing) return                                          // telepon berdering: tahan dulu
        if (SystemClock.elapsedRealtime() < busyUntil) return
        val p = synchronized(queue) { queue.removeFirstOrNull() } ?: return
        val more = synchronized(queue) { queue.size }
        val icon = if (ble.iconPkg == p.pkg) ByteArray(0) else iconFor(c, p.sbn, p.pkg)
        if (ble.sendNotification(p.pkg, p.app, p.title, p.body, icon, p.drive, more)) {
            val est = estimateMs(p) + 1500L
            busyUntil = SystemClock.elapsedRealtime() + est
            mainH.postDelayed({ exec.execute { pump() } }, est + 200L)     // jaga-jaga bila kabar "selesai" hilang
        }
    }

    /** Kabar dari ESP32: notifikasi selesai ditampilkan -> kirim yang berikutnya. */
    fun espDone() {
        busyUntil = 0L
        mainH.postDelayed({ exec.execute { pump() } }, 400L)
    }

    /** Dipanggil saat BLE tersambung ulang. */
    fun bleReset() {
        Music.reset()
        Cfg.dirty = true
        busyUntil = 0L
        exec.execute { pump() }
    }

    // ---------- baterai HP rendah ----------
    fun battOn(c: Context) = prefs(c).getBoolean("batt_on", true)
    fun setBattOn(c: Context, v: Boolean) = prefs(c).edit().putBoolean("batt_on", v).apply()
    private val battSent = HashSet<Int>()

    /** Dipanggil berkala dari Core. Peringatan sekali tiap melewati 20%, 10%, 5% (selama tidak diisi daya). */
    fun batteryCheck(c: Context) {
        if (!battOn(c)) return
        val i = (try { c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) } catch (e: Exception) { null }) ?: return
        val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (lvl < 0 || sc <= 0) return
        val pct = lvl * 100 / sc
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (plugged || pct > 25) { battSent.clear(); return }
        val hit = listOf(5, 10, 20).firstOrNull { pct <= it } ?: return
        if (hit in battSent) return
        battSent.addAll(listOf(5, 10, 20).filter { it >= hit })
        val body = if (hit <= 5) "Hampir habis! Isi daya sekarang." else "Segera isi daya supaya navigasi tidak berhenti."
        enqueue(c, Pending("batt.warn", "Baterai HP", "Baterai HP $pct%", body, Core.isNavigating(), null))
    }

    /** Tes dari tombol di aplikasi (memakai logo MotorNav sendiri). */
    fun test(c: Context) {
        val body = "Bro, nanti malam jadi ketemu di warung kopi dekat bengkel? Jangan lupa bawa helm cadangan dan jaket ya, soalnya kemungkinan hujan deras sampai larut malam."
        enqueue(c, Pending(c.packageName, "WhatsApp", "Budi Santoso", body, false, null))
        enqueue(c, Pending(c.packageName, "WhatsApp", "Grup Bengkel", "Andi: oli sudah ready ya", false, null))
        enqueue(c, Pending(c.packageName, "Telegram", "Rina", "Sudah sampai?", false, null))
    }

    // ---------- panggilan ----------
    private fun lookupName(c: Context, number: String?): String {
        if (number.isNullOrBlank()) return ""
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            c.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) return it.getString(0) ?: ""
            }
        } catch (e: Exception) {}
        return ""
    }

    /** Telepon biasa (dari penerima PHONE_STATE). */
    fun phoneRinging(c: Context, number: String?) {
        if (!callOn(c)) return
        if (ringing && number.isNullOrBlank()) return
        if (ringing && number == ringNumber) return
        if (!ringing) { answered = false; rejected = false }
        ringing = true
        ringNumber = number
        exec.execute {
            val name = ascii(lookupName(c, number), 36)
            val num = ascii(number, 22)
            ringName = name; ringNum = num
            Core.bleIfReady()?.sendCallStart("", name.ifEmpty { num }, if (name.isEmpty()) "" else num)
        }
    }

    /** Telepon diangkat (OFFHOOK): tutup layar telepon di ESP32 dan catat bahwa tidak terlewat. */
    fun phoneAnswered() {
        if (ringing) answered = true
        phoneIdle(null)
    }

    /** Selesai (IDLE). Bila c != null dan telepon tak diangkat/ditolak: tampilkan "tidak terjawab". */
    fun phoneIdle(c: Context? = null) {
        if (!ringing && ringKey == null) return
        val missed = ringing && ringKey == null && !answered && !rejected
        val nm = ringName; val nu = ringNum
        ringing = false; ringNumber = null; ringKey = null; ringSbn = null
        ringName = ""; ringNum = ""
        exec.execute { Core.bleIfReady()?.sendCallEnd() }
        if (missed && c != null && callOn(c)) sendMissed(c, nm, nu)
        mainH.postDelayed({ exec.execute { pump() } }, 2500L)           // lanjutkan antrean notifikasi setelah telepon
    }

    private fun sendMissed(c: Context, name: String, num: String) {
        var dial: String? = null
        try { dial = (c.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage } catch (e: Exception) {}
        val who = name.ifEmpty { num }.ifEmpty { "Tak dikenal" }
        val hhmm = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())
        val body = (if (name.isNotEmpty() && num.isNotEmpty()) "$num. " else "") + "Panggilan tidak terjawab, pukul $hhmm"
        enqueue(c, Pending(dial ?: "missed.call", "Tidak terjawab", who, body, Core.isNavigating(), null))
    }

    /** Tolak telepon yang sedang berdering (dipicu tahan sensor di motor). */
    fun rejectCall(c: Context) {
        rejected = true
        exec.execute {
            val sbn = ringSbn
            if (ringKey != null && sbn != null) {                    // panggilan aplikasi lain: tekan tombol "Tolak" di notifikasinya
                try {
                    val acts = sbn.notification?.actions
                    val keys = listOf("tolak", "decline", "reject", "dismiss", "abaikan")
                    val a = acts?.firstOrNull { x -> val t = x.title?.toString()?.lowercase() ?: ""; keys.any { k -> t.contains(k) } }
                    a?.actionIntent?.send()
                } catch (e: Exception) {}
                return@execute
            }
            try {
                if (c.checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                    (c.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).endCall()
            } catch (e: Exception) {}
        }
    }

    /** Panggilan lewat aplikasi lain (WhatsApp, Telegram, ...) terbaca dari notifikasinya. */
    fun callNotif(c: Context, sbn: StatusBarNotification, posted: Boolean) {
        if (!callOn(c)) return
        val pkg = sbn.packageName ?: return
        try {
            val dial = (c.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage
            if (pkg == dial || pkg.contains("dialer") || pkg.contains("incallui") || pkg.contains("telecom")) return   // sudah ditangani PHONE_STATE
        } catch (e: Exception) {}
        if (!posted) {
            if (ringKey == sbn.key) phoneIdle()
            return
        }
        val n = sbn.notification ?: return
        val callAnswered = n.extras?.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) == true
        if (callAnswered) { if (ringKey == sbn.key) phoneIdle(); return }
        if (ringKey == sbn.key) return
        ringKey = sbn.key
        ringSbn = sbn
        answered = false; rejected = false
        ringing = true
        val label = appLabel(c, pkg, n)
        val who = ascii(n.extras?.getCharSequence(Notification.EXTRA_TITLE), 36)
        exec.execute { Core.bleIfReady()?.sendCallStart(ascii(label, 14), who, "") }
    }

    fun testCall() {
        exec.execute {
            val ble = Core.bleIfReady() ?: return@execute
            ble.sendCallStart("", "Budi Santoso", "+62 812 3456 7890")
            try { Thread.sleep(6000) } catch (e: InterruptedException) {}
            ble.sendCallEnd()
        }
    }
}
