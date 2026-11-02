package com.motornav.app

import android.content.Context
import android.util.Base64
import java.io.IOException
import java.security.SecureRandom
import javax.net.ssl.SSLSocketFactory

/**
 * Pemulihan kode menu Motor lewat OTP email (SMTP Gmail, port 465).
 * Penerima OTP ditanam di sini; tidak ada layar untuk menambah/ubah penerima. Untuk mengubahnya: edit daftar di bawah lalu build ulang APK.
 * Pengirim = akun Gmail + sandi aplikasi Google yang diketik di tab Motor, tersimpan di HP saja (tidak ada rahasia di dalam APK/repo).
 */
object MotorRecovery {
    /** GANTI dengan alamat email Anda (boleh lebih dari satu). */
    val RECIPIENTS: List<String> = listOf("GANTI_EMAIL_ANDA@gmail.com")

    private const val OTP_TTL_MS = 10 * 60_000L
    private const val RESEND_MS = 60_000L
    private const val MAX_TRIES = 3
    private val rnd = SecureRandom()
    @Volatile private var otp: String? = null
    @Volatile private var otpAt = 0L
    @Volatile private var lastSend = 0L
    @Volatile private var tries = 0

    private fun prefs(c: Context) = c.getSharedPreferences("motornav", Context.MODE_PRIVATE)
    fun senderUser(c: Context): String = prefs(c).getString("rec_user", "") ?: ""
    fun hasSender(c: Context) = senderUser(c).isNotEmpty() && (prefs(c).getString("rec_pass", "") ?: "").isNotEmpty()
    fun saveSender(c: Context, user: String, pass: String) {
        prefs(c).edit().putString("rec_user", user.trim()).putString("rec_pass", pass.replace(" ", "")).apply()
    }

    /** Kirim OTP 6 angka ke semua penerima. Harus dipanggil dari thread latar. Mengembalikan null bila berhasil, atau pesan galat. */
    fun sendOtp(c: Context): String? {
        if (RECIPIENTS.isEmpty() || RECIPIENTS.any { it.startsWith("GANTI_") || !it.contains("@") })
            return "Email penerima belum ditanam. Edit MotorRecovery.kt lalu build ulang."
        if (!hasSender(c)) return "Email pengirim belum diatur. Buka tab Motor (dengan kode) > Email pengirim OTP."
        val now = System.currentTimeMillis()
        if (now - lastSend < RESEND_MS) return "Tunggu ${(RESEND_MS - (now - lastSend)) / 1000 + 1} detik sebelum minta OTP lagi."
        val code = "%06d".format(rnd.nextInt(1_000_000))
        return try {
            smtp(senderUser(c), prefs(c).getString("rec_pass", "") ?: "", RECIPIENTS, "Nav160 - kode OTP menu Motor",
                "Kode OTP untuk mengganti kode menu Motor Nav160: $code\r\nBerlaku 10 menit. Abaikan email ini bila bukan Anda yang meminta.")
            otp = code; otpAt = now; lastSend = now; tries = 0
            null
        } catch (e: Exception) {
            "Gagal kirim email: ${e.message}"
        }
    }

    /** 0 benar, 1 salah, 2 belum diminta / kedaluwarsa, 3 terlalu banyak salah. OTP baru dibuang lewat consume(). */
    fun verify(input: String): Int {
        val o = otp ?: return 2
        if (System.currentTimeMillis() - otpAt > OTP_TTL_MS) { otp = null; return 2 }
        if (tries >= MAX_TRIES) { otp = null; return 3 }
        if (input != o) { tries++; if (tries >= MAX_TRIES) otp = null; return if (tries >= MAX_TRIES) 3 else 1 }
        return 0
    }

    fun consume() { otp = null }

    private fun b64(s: String) = Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun smtp(user: String, pass: String, to: List<String>, subject: String, body: String) {
        val f = SSLSocketFactory.getDefault() as SSLSocketFactory
        f.createSocket("smtp.gmail.com", 465).use { s ->
            s.soTimeout = 15000
            val r = s.getInputStream().bufferedReader(Charsets.US_ASCII)
            val w = s.getOutputStream().bufferedWriter(Charsets.US_ASCII)
            fun read(expect: String) {
                var line: String
                do { line = r.readLine() ?: throw IOException("server menutup koneksi") } while (line.length > 3 && line[3] == '-')
                if (!line.startsWith(expect)) throw IOException(if (line.startsWith("535")) "login ditolak (cek Gmail & sandi aplikasi)" else line)
            }
            fun cmd(c: String, expect: String) { w.write(c + "\r\n"); w.flush(); read(expect) }
            read("220")
            cmd("EHLO nav160", "250")
            cmd("AUTH LOGIN", "334")
            cmd(b64(user), "334")
            cmd(b64(pass), "235")
            cmd("MAIL FROM:<$user>", "250")
            to.forEach { cmd("RCPT TO:<$it>", "250") }
            cmd("DATA", "354")
            val msg = "From: Nav160 <$user>\r\nTo: ${to.joinToString(", ")}\r\nSubject: $subject\r\nMIME-Version: 1.0\r\n" +
                "Content-Type: text/plain; charset=us-ascii\r\n\r\n$body\r\n.\r\n"
            w.write(msg); w.flush(); read("250")
            cmd("QUIT", "221")
        }
    }
}
