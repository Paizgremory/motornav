package com.motornav.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32

/**
 * v3.21: kirim foto/video wallpaper atau animasi boot ke ESP32 lewat WiFi (bukan BLE).
 * ESP32 harus sudah di mode UPLOAD WIFI (menu LCD: Pengaturan > WALLPAPER / ANIMASI BOOT > TAHAN):
 * ia membuat WiFi "MotorNav" (sandi motornav1234), alamat 192.168.4.1, menerima POST /up?tgt=0|1&crc=<hex>.
 */
object WifiUp {
    const val SSID = "MotorNav"
    const val PASS = "motornav1234"
    private const val HOST = "http://192.168.4.1"

    fun upload(ctx: Context, data: ByteArray, target: Int, progress: (Int) -> Unit, cancelled: () -> Boolean): String? {
        if (data.size < 64 || data.size % 4 != 0) return "Ukuran data salah"
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var cb: ConnectivityManager.NetworkCallback? = null
        try {
            var net: Network? = null
            if (Build.VERSION.SDK_INT >= 29) {
                val spec = WifiNetworkSpecifier.Builder().setSsid(SSID).setWpa2Passphrase(PASS).build()
                val req = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).setNetworkSpecifier(spec).build()
                val latch = CountDownLatch(1)
                cb = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(n: Network) { net = n; latch.countDown() }
                    override fun onUnavailable() { latch.countDown() }
                }
                cm.requestNetwork(req, cb, 40000)
                var waited = 0
                while (!latch.await(500, TimeUnit.MILLISECONDS)) {
                    if (cancelled()) return "Dibatalkan"
                    if (++waited > 90) break
                }
                if (net == null) return "Tidak bisa tersambung ke WiFi \"$SSID\". Di layar ESP32: Pengaturan > WALLPAPER/ANIMASI BOOT > TAHAN dulu, lalu izinkan sambungan di HP."
            }
            // Android 9 ke bawah: HP harus sudah disambungkan manual ke WiFi MotorNav
            fun open(path: String): HttpURLConnection {
                val u = URL(HOST + path)
                return (net?.openConnection(u) ?: u.openConnection()) as HttpURLConnection
            }
            var ok = false
            for (i in 1..15) {
                if (cancelled()) return "Dibatalkan"
                try {
                    val c = open("/ping"); c.connectTimeout = 2000; c.readTimeout = 2000
                    if (c.responseCode == 200) { ok = true; c.disconnect(); break }
                    c.disconnect()
                } catch (_: Exception) { Thread.sleep(700) }
            }
            if (!ok) return "ESP32 tidak menjawab di 192.168.4.1 (sudah di mode UPLOAD WIFI?)"
            val crc = CRC32().also { it.update(data) }.value
            val c = open("/up?tgt=${if (target == 1) 1 else 0}&crc=" + java.lang.Long.toHexString(crc))
            c.requestMethod = "POST"; c.doOutput = true
            c.setFixedLengthStreamingMode(data.size.toLong())
            c.connectTimeout = 5000; c.readTimeout = 30000
            c.setRequestProperty("Content-Type", "application/octet-stream")
            c.outputStream.use { o ->
                var off = 0
                while (off < data.size) {
                    if (cancelled()) { c.disconnect(); return "Dibatalkan" }
                    val k = minOf(4096, data.size - off)
                    o.write(data, off, k)
                    off += k
                    progress((off.toLong() * 100 / data.size).toInt())
                }
                o.flush()
            }
            val code = c.responseCode
            val body = try { (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: "" } catch (_: Exception) { "" }
            c.disconnect()
            return if (code == 200 && body.trim() == "OK") null else "ESP32 menolak: ${body.ifBlank { "HTTP $code" }}"
        } catch (e: Exception) {
            return "Galat WiFi: ${e.message}"
        } finally {
            try { cb?.let { cm.unregisterNetworkCallback(it) } } catch (_: Exception) {}
        }
    }
}
