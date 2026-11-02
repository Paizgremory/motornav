package com.motornav.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Upload wallpaper / animasi boot lewat WiFi (firmware v22.28; slot perpustakaan v22.29).
 * Di layar motor: Pengaturan > UPLOAD WIFI. ESP32 membuat WiFi "MotorNav", HP tersambung ke sana (Android 10+ otomatis, muncul
 * dialog persetujuan sekali), lalu berkas dikirim HTTP POST ke http://192.168.4.1/up. Internet HP tidak terputus: hanya koneksi
 * ini yang lewat WiFi MotorNav (Network.openConnection), yang lain tetap lewat jaringan biasa.
 */
object WifiUp {
    const val SSID = "MotorNav"
    const val PASS = "motornav1234"
    const val HOST = "192.168.4.1"
    @Volatile var cancel = false

    private fun wifiNetworks(cm: ConnectivityManager): List<Network> =
        cm.allNetworks.filter { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }

    /** null = sukses, selain itu teks galat. */
    fun upload(ctx: Context, file: File, boot: Boolean, slot: Int, status: (String) -> Unit, progress: (Int) -> Unit): String? {
        cancel = false
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var cb: ConnectivityManager.NetworkCallback? = null
        try {
            var net: Network? = null
            if (Build.VERSION.SDK_INT >= 29) {
                status("Menyambung ke WiFi $SSID... (setujui dialog bila muncul)")
                val spec = WifiNetworkSpecifier.Builder().setSsid(SSID).setWpa2Passphrase(PASS).build()
                val req = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(spec).build()
                val latch = CountDownLatch(1)
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) { net = network; latch.countDown() }
                    override fun onUnavailable() { latch.countDown() }
                }
                cb = callback
                cm.requestNetwork(req, callback, 40_000)
                latch.await(45, TimeUnit.SECONDS)
            } else {
                net = wifiNetworks(cm).firstOrNull()               // Android 8-9: sambungkan manual ke WiFi MotorNav dulu
            }
            val n = net ?: return "Tidak tersambung ke WiFi $SSID. Di layar motor buka Pengaturan > UPLOAD WIFI dulu" +
                (if (Build.VERSION.SDK_INT < 29) " dan sambungkan HP ke WiFi $SSID (sandi $PASS) secara manual." else ".")
            if (cancel) return "Dibatalkan"

            status("Terhubung. Memeriksa berkas...")
            val size = file.length()
            val crc = Wallpaper.crcOf(file)
            if (cancel) return "Dibatalkan"
            status("Terhubung. Mengirim...")
            val url = URL("http://$HOST/up?tgt=${if (boot) 1 else 0}&crc=${java.lang.Long.toHexString(crc)}" + (if (slot > 0) "&slot=$slot" else ""))
            val c = n.openConnection(url) as HttpURLConnection
            try {
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 8000
                c.readTimeout = 90000
                c.setFixedLengthStreamingMode(size)
                c.setRequestProperty("Content-Type", "application/octet-stream")
                c.outputStream.use { out ->
                    FileInputStream(file).use { ins ->
                        val buf = ByteArray(16 * 1024)
                        var off = 0L
                        var lastPct = -1
                        while (off < size) {
                            if (cancel) return "Dibatalkan"
                            val k = ins.read(buf, 0, minOf(buf.size.toLong(), size - off).toInt())
                            if (k < 0) return "Berkas terpotong"
                            out.write(buf, 0, k)
                            off += k
                            val pct = (off * 100 / size).toInt()
                            if (pct != lastPct) { lastPct = pct; progress(pct) }
                        }
                    }
                    out.flush()
                }
                val code = c.responseCode
                val body = try { (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: "" } catch (e: Exception) { "" }
                return if (code == 200 && body.trim() == "OK") null else "ESP32 menolak ($code): $body"
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            return "Gagal kirim lewat WiFi: ${e.message}"
        } finally {
            try { cb?.let { cm.unregisterNetworkCallback(it) } } catch (e: Exception) {}
        }
    }
}
