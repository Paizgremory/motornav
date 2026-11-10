package com.motornav.app

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32

/** Koneksi BLE ke ESP32 "Motor Nav" (layanan peta 4d4e0001, karakteristik 4d4e0002). */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class Ble(private val ctx: Context, private val onMsg: (String) -> Unit) {
    companion object {
        val SVC: UUID = UUID.fromString("4d4e0001-7a1e-4c1b-9d52-6f2d4e1a0001")
        val CHR: UUID = UUID.fromString("4d4e0002-7a1e-4c1b-9d52-6f2d4e1a0001")
    }

    private val prefs = ctx.getSharedPreferences("motornav", Context.MODE_PRIVATE)
    private val adapter: BluetoothAdapter? =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val h = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var chr: BluetoothGattCharacteristic? = null
    private var scanner: BluetoothLeScanner? = null
    private val sem = Semaphore(0)
    private val rsem = Semaphore(0)
    private val txLock = Any()
    @Volatile private var lastWriteOk = true
    @Volatile private var lastStatus = -1
    @Volatile var uploading = false
    @Volatile var cancelUpload = false
    @Volatile var mtu = 23
    @Volatile var iconPkg: String? = null            // paket yang logonya sedang tersimpan di ESP32 (hindari kirim ulang)

    // ---------- update firmware (OTA) lewat BLE, v22.31+ ----------
    @Volatile var otaAvailable = false
    @Volatile var otaMaxSize = 0L
    @Volatile var otaVersion = ""
    private val otaSem = Semaphore(0)

    @Volatile var ready = false
    @Volatile var chunk = 15
    @Volatile var msg = "Belum tersambung"
    @Volatile var framesSent = 0
    private var want = false
    private var scanning = false
    private var fails = 0
    private var fid = 0

    private fun finishReady() {
        if (ready || chr == null || gatt == null) return
        ready = true
        say("Tersambung (paket $chunk B)")
        Notify.bleReset()
    }

    private fun say(t: String) {
        msg = t
        onMsg(t)
    }

    fun connect() {
        want = true
        if (adapter == null || !adapter.isEnabled) {
            say("Bluetooth HP mati. Nyalakan dulu.")
            return
        }
        if (ready || gatt != null || scanning) return
        val saved = prefs.getString("addr", null)
        if (saved != null) {
            try {
                open(adapter.getRemoteDevice(saved))
                return
            } catch (e: Exception) {
                prefs.edit().remove("addr").apply()
            }
        }
        scan()
    }

    fun disconnect() {
        want = false
        stopScan()
        try { gatt?.disconnect() } catch (e: Exception) {}
        h.postDelayed({ close() }, 300)
        ready = false
        say("Diputus")
    }

    private fun scan() {
        val sc = adapter?.bluetoothLeScanner
        if (sc == null) { say("Scanner BLE tidak tersedia"); return }
        scanner = sc
        scanning = true
        say("Mencari \"Motor Nav\"...")
        try {
            sc.startScan(scanCb)
        } catch (e: Exception) {
            scanning = false
            say("Scan gagal: ${e.message}")
            return
        }
        h.postDelayed({
            if (scanning) {
                stopScan()
                say("Motor Nav tidak ketemu. Pastikan ESP32 menyala.")
                if (want) h.postDelayed({ if (want && !ready && gatt == null) connect() }, 1500)
            }
        }, 12000)
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        try { scanner?.stopScan(scanCb) } catch (e: Exception) {}
    }

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: try { result.device.name } catch (e: Exception) { null }
            if (name == "Motor Nav") {
                stopScan()
                prefs.edit().putString("addr", result.device.address).apply()
                h.post { open(result.device) }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            say("Scan gagal (kode $errorCode)")
        }
    }

    private fun open(dev: BluetoothDevice) {
        close()
        say("Menyambung...")
        gatt = dev.connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE)
    }

    private fun close() {
        try { gatt?.close() } catch (e: Exception) {}
        gatt = null
        chr = null
        ready = false
    }

    private val cb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                fails = 0
                g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                h.postDelayed({
                    try { if (!g.requestMtu(247)) g.discoverServices() } catch (e: Exception) {}
                }, 300)
            } else {
                ready = false
                chr = null
                sem.release()
                fails++
                h.post {
                    if (gatt === g) close() else try { g.close() } catch (e: Exception) {}
                    say("Terputus (kode $status)")
                    if (fails >= 3) prefs.edit().remove("addr").apply()
                    if (want) h.postDelayed({ if (want && gatt == null && !ready) connect() }, 2000)
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mt: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) mtu = mt
            chunk = if (status == BluetoothGatt.GATT_SUCCESS) minOf(200, mt - 8) else 15
            if (chunk < 15) chunk = 15
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val c = g.getService(SVC)?.getCharacteristic(CHR)
            if (c == null) {
                say("Layanan peta tidak ditemukan. Flash firmware v22.12 dulu.")
                return
            }
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            chr = c
            iconPkg = null
            var wait = false
            try {                                                    // aktifkan notify dari ESP32 (tombol tolak telepon)
                val cccd = c.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                if (cccd != null && g.setCharacteristicNotification(c, true)) {
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    wait = g.writeDescriptor(cccd)
                }
            } catch (e: Exception) {}
            if (wait) h.postDelayed({ finishReady() }, 1500) else finishReady()
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            h.post { finishReady() }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            val v = c.value ?: return
            if (v.size >= 2 && v[0] == 0xE1.toByte()) {
                if (v[1] == 1.toByte()) Notify.rejectCall(ctx)
                else if (v[1] == 2.toByte()) Notify.espDone()
            } else if (v.size >= 6 && v[0] == 0xE4.toByte()) {              // balasan info OTA (v22.31+)
                otaAvailable = (v[1].toInt() and 1) != 0
                otaMaxSize = (v[2].toLong() and 0xFF) or ((v[3].toLong() and 0xFF) shl 8) or
                    ((v[4].toLong() and 0xFF) shl 16) or ((v[5].toLong() and 0xFF) shl 24)
                otaVersion = if (v.size > 6) String(v, 6, v.size - 6, Charsets.US_ASCII) else ""
                otaSem.release()
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            lastWriteOk = status == BluetoothGatt.GATT_SUCCESS
            sem.release()
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            val v = c.value
            lastStatus = if (status == BluetoothGatt.GATT_SUCCESS && v != null && v.isNotEmpty()) v[0].toInt() and 255 else -1
            rsem.release()
        }
    }

    private fun write(p: ByteArray, strict: Boolean = false): Boolean {
        val g = gatt ?: return false
        val c = chr ?: return false
        sem.drainPermits()
        var tries = 0
        c.value = p
        while (!g.writeCharacteristic(c)) {
            if (++tries > 30 || !ready) return false
            Thread.sleep(10)
            c.value = p
        }
        return sem.tryAcquire(1500, TimeUnit.MILLISECONDS) && (!strict || lastWriteOk)
    }

    /** Paket kontrol: [0xFF][cmd][...]. */
    fun sendControl(p: ByteArray): Boolean {
        if (!ready || uploading) return false
        return synchronized(txLock) { if (uploading) false else write(p) }
    }

    /** Kirim satu frame JPEG: tiap paket = [id][offset LE16][total LE16][data]. */
    fun sendFrame(data: ByteArray): Boolean {
        if (!ready || uploading) return false
        return synchronized(txLock) { if (uploading) false else sendFrameLocked(data) }
    }

    private fun sendFrameLocked(data: ByteArray): Boolean {
        fid = (fid + 1) % 255
        val tot = data.size
        var off = 0
        while (off < tot) {
            val n = minOf(chunk, tot - off)
            val pk = ByteArray(5 + n)
            pk[0] = fid.toByte()
            pk[1] = (off and 255).toByte()
            pk[2] = ((off shr 8) and 255).toByte()
            pk[3] = (tot and 255).toByte()
            pk[4] = ((tot shr 8) and 255).toByte()
            System.arraycopy(data, off, pk, 5, n)
            if (!write(pk)) return false
            off += n
        }
        framesSent++
        return true
    }


    // ---------- notifikasi & telepon (firmware v22.15) ----------
    private fun maxPayload() = if (mtu > 23) minOf(mtu - 3, 244) else 20

    /** Kirim notifikasi. icon: null = tanpa logo, kosong = pakai logo yang sudah ada di ESP32, isi = logo 48x48 RGB565 baru. */
    fun sendNotification(pkg: String, app: String, title: String, msg: String, icon: ByteArray?, drive: Boolean = false, more: Int = 0): Boolean {
        if (!ready || uploading) return false
        return synchronized(txLock) {
            if (uploading) return@synchronized false
            val pl = maxPayload()
            if (icon != null && icon.isNotEmpty()) {
                var off = 0
                val n = pl - 4
                while (off < icon.size) {
                    val k = minOf(n, icon.size - off)
                    val pk = ByteArray(4 + k)
                    pk[0] = -1; pk[1] = 0x21
                    pk[2] = (off and 255).toByte(); pk[3] = ((off shr 8) and 255).toByte()
                    System.arraycopy(icon, off, pk, 4, k)
                    if (!write(pk)) return@synchronized false
                    off += k
                }
                iconPkg = pkg
            }
            val hasIcon = icon != null
            val a = app.toByteArray(Charsets.US_ASCII)
            val t = title.toByteArray(Charsets.US_ASCII)
            val m = msg.toByteArray(Charsets.US_ASCII)
            val blob = ByteArray(3 + a.size + t.size + m.size)
            blob[0] = ((if (hasIcon) 1 else 0) or (if (drive) 2 else 0) or (more.coerceIn(0, 7) shl 2)).toByte()
            blob[1] = a.size.toByte(); blob[2] = t.size.toByte()
            System.arraycopy(a, 0, blob, 3, a.size)
            System.arraycopy(t, 0, blob, 3 + a.size, t.size)
            System.arraycopy(m, 0, blob, 3 + a.size + t.size, m.size)
            var off = 0
            val n = pl - 6
            while (off < blob.size) {
                val k = minOf(n, blob.size - off)
                val pk = ByteArray(6 + k)
                pk[0] = -1; pk[1] = 0x20
                pk[2] = (off and 255).toByte(); pk[3] = ((off shr 8) and 255).toByte()
                pk[4] = (blob.size and 255).toByte(); pk[5] = ((blob.size shr 8) and 255).toByte()
                System.arraycopy(blob, off, pk, 6, k)
                if (!write(pk)) return@synchronized false
                off += k
            }
            true
        }
    }

    /** Telepon masuk: label (boleh kosong), nama, nomor. Dipotong supaya muat satu paket. */
    fun sendCallStart(label: String, name: String, number: String): Boolean {
        var l = label.take(18)
        var nm = name.take(36)
        var nu = number.take(22)
        val max = maxPayload() - 4                      // FF 22 labelLen nameLen
        while (l.length + nm.length + nu.length > max) {
            if (nu.length > 0) nu = nu.dropLast(1) else if (nm.length > 4) nm = nm.dropLast(1) else l = l.dropLast(1)
        }
        val b = byteArrayOf(-1, 0x22, l.length.toByte(), nm.length.toByte()) +
            l.toByteArray(Charsets.US_ASCII) + nm.toByteArray(Charsets.US_ASCII) + nu.toByteArray(Charsets.US_ASCII)
        return sendControl(b)
    }

    fun sendCallEnd(): Boolean = sendControl(byteArrayOf(-1, 0x23))

    /** Posisi & durasi lagu (detik) untuk slider di layar: [FF][29][pos u16][dur u16]. */
    fun sendPos(posS: Int, durS: Int): Boolean {
        val p = posS.coerceIn(0, 65535); val d = durS.coerceIn(0, 65535)
        return sendControl(byteArrayOf(-1, 0x29, (p and 255).toByte(), (p shr 8).toByte(), (d and 255).toByte(), (d shr 8).toByte()))
    }

    @Volatile private var bandBusy = false

    /** Spektrum audio asli (16 band, 0..255, bass dulu) untuk visualizer. Dilewati bila kiriman sebelumnya belum selesai. */
    fun sendBands(b: ByteArray) {
        if (!ready || uploading || bandBusy) return
        bandBusy = true
        try { sendControl(byteArrayOf(-1, 0x25, b.size.toByte()) + b) } finally { bandBusy = false }
    }

    @Volatile private var vibBusy = false

    /** Intensitas "getar jalan" (0..255) dari akselerometer HP untuk efek goyang wajah standby: [FF][34][mag]. Dilewati bila kiriman sebelumnya belum selesai. */
    fun sendVib(mag: Int) {
        if (!ready || uploading || vibBusy) return
        vibBusy = true
        try { sendControl(byteArrayOf(-1, 0x34, mag.coerceIn(0, 255).toByte())) } finally { vibBusy = false }
    }

    /** Sampul album 64x64 RGB565 LE (8192 byte): [FF][26][off u16][data]..., lalu [FF][27][1]. */
    fun sendArt(rgb565: ByteArray): Boolean {
        if (!ready) return false
        val n = maxPayload() - 4
        var off = 0
        while (off < rgb565.size) {
            val k = minOf(n, rgb565.size - off)
            val pk = ByteArray(4 + k)
            pk[0] = -1; pk[1] = 0x26
            pk[2] = (off and 255).toByte(); pk[3] = ((off shr 8) and 255).toByte()
            System.arraycopy(rgb565, off, pk, 4, k)
            if (!sendControl(pk)) return false
            off += k
        }
        return sendControl(byteArrayOf(-1, 0x27, 1))
    }

    /** Info lagu untuk animasi standby (firmware v22.21): [FF][24][state][titleLen][artistLen][judul][artis]. */
    fun sendMusic(playing: Boolean, title: String, artist: String): Boolean {
        val max = maxPayload() - 5
        var t = title.take(60)
        var a = artist.take(40)
        while (t.length + a.length > max) { if (a.isNotEmpty()) a = a.dropLast(1) else t = t.dropLast(1) }
        val b = byteArrayOf(-1, 0x24, if (playing) 1 else 0, t.length.toByte(), a.length.toByte()) +
            t.toByteArray(Charsets.US_ASCII) + a.toByteArray(Charsets.US_ASCII)
        return sendControl(b)
    }

    // ---------- upload wallpaper (foto 240x240 RGB565, 115200 byte) ----------
    private fun readStatus(): Int {
        val g = gatt ?: return -1
        val c = chr ?: return -1
        rsem.drainPermits()
        lastStatus = -1
        if (!g.readCharacteristic(c)) return -1
        if (!rsem.tryAcquire(1500, TimeUnit.MILLISECONDS)) return -1
        return lastStatus
    }

    private fun le32(v: Long) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    /**
     * Kirim foto/video ke ESP32 lewat BLE. target: 0 = wallpaper standby (slot/berkas /wallpaper.mnv di SD bila ada,
     * atau partisi flash tanpa SD), 1 = animasi boot (v22.25+, /bootanim.mnv di SD; firmware tanpa SD menolaknya).
     * Mengembalikan null kalau sukses, atau teks galat. progress: 0..100.
     */
    fun uploadWallpaper(data: ByteArray, target: Int = 0, progress: (Int) -> Unit): String? {
        if (!ready) return "ESP32 belum tersambung"
        if (data.size < 64 || data.size % 4 != 0) return "Ukuran data salah"
        cancelUpload = false
        val c = chr ?: return "ESP32 belum tersambung"
        uploading = true                                   // hentikan kiriman frame peta selama upload
        try {
            return synchronized(txLock) {
                var err: String? = null
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT   // pakai konfirmasi: tak ada paket hilang
                try {
                    for (attempt in 1..(if (data.size > 300000) 2 else 3)) {
                        err = uploadOnce(data, target, progress)
                        if (err == null || !ready || cancelUpload) break
                        progress(0)
                    }
                } finally {
                    c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                }
                err
            }
        } finally {
            uploading = false
        }
    }

    private fun uploadOnce(data: ByteArray, target: Int, progress: (Int) -> Unit): String? {
        if (!write(byteArrayOf(-1, if (target == 1) 0x14 else 0x10) + le32(data.size.toLong()), true)) return "Gagal memulai upload (BLE)"
        var t = System.currentTimeMillis()
        while (true) {
            val st = readStatus()
            if (st == 2) break
            if (st == 5) return "ESP32 menolak upload"
            if (System.currentTimeMillis() - t > 15000) return "ESP32 tidak membalas. Sudah flash firmware v22.13?"
            Thread.sleep(250)
        }
        val n = maxOf(8, if (mtu > 23) minOf(240, mtu - 9) else chunk - 1)   // paket = 6 byte kepala + data, muat di MTU-3
        var off = 0
        while (off < data.size) {
            if (cancelUpload) return "Dibatalkan"
            val k = minOf(n, data.size - off)
            val pk = ByteArray(6 + k)
            pk[0] = -1
            pk[1] = 0x11
            System.arraycopy(le32(off.toLong()), 0, pk, 2, 4)
            System.arraycopy(data, off, pk, 6, k)
            if (!write(pk, true)) return "Terputus di tengah upload (${(off.toLong() * 100 / data.size).toInt()}%)"
            off += k
            progress((off.toLong() * 100 / data.size).toInt())
        }
        val crc = CRC32().also { it.update(data) }.value
        if (!write(byteArrayOf(-1, 0x12) + le32(crc), true)) return "Gagal menutup upload"
        t = System.currentTimeMillis()
        while (true) {
            val st = readStatus()
            if (st == 4) return null
            if (st == 5) return "Data rusak (CRC tidak cocok)"
            if (System.currentTimeMillis() - t > 15000) return "Tidak ada konfirmasi dari ESP32"
            Thread.sleep(200)
        }
    }

    /** Minta info OTA dari ESP32 (dibalas notify 0xE4: tersedia?, ukuran slot maks, teks versi firmware). true bila dibalas. */
    fun requestOtaInfo(): Boolean {
        if (!ready || uploading) return false
        otaSem.drainPermits()
        if (!sendControl(byteArrayOf(-1, 0x2D))) return false
        return otaSem.tryAcquire(3000, TimeUnit.MILLISECONDS)
    }

    /**
     * Kirim firmware baru (.ino.bin) ke slot OTA ESP32 (v22.31+). ESP32 menulis ke slot program yang TIDAK sedang
     * dipakai; program lama tetap utuh bila upload gagal/terputus. Sukses -> ESP32 memverifikasi lalu restart sendiri.
     * null = sukses, selain itu teks galat.
     */
    fun uploadFirmware(data: ByteArray, progress: (Int) -> Unit): String? {
        if (!ready) return "ESP32 belum tersambung"
        if (data.size < 65536) return "Berkas terlalu kecil: pastikan ini .ino.bin (Sketch > Export Compiled Binary), bukan .bootloader/.partitions/.merged.bin"
        cancelUpload = false
        val c = chr ?: return "ESP32 belum tersambung"
        uploading = true
        try {
            return synchronized(txLock) {
                var err: String? = null
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                try {
                    err = otaOnce(data, progress)
                } finally {
                    c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                }
                err
            }
        } finally {
            uploading = false
        }
    }

    private fun otaOnce(data: ByteArray, progress: (Int) -> Unit): String? {
        if (!write(byteArrayOf(-1, 0x2A) + le32(data.size.toLong()), true)) return "Gagal memulai OTA (BLE)"
        var t = System.currentTimeMillis()
        while (true) {
            val st = readStatus()
            if (st == 2) break
            if (st == 5) return "ESP32 menolak OTA (ukuran salah atau tidak ada slot program kedua)"
            if (System.currentTimeMillis() - t > 15000) return "ESP32 tidak membalas. Firmware yang terpasang sudah v22.31 ke atas?"
            Thread.sleep(250)
        }
        val n = maxOf(8, if (mtu > 23) minOf(240, mtu - 9) else chunk - 1)
        var off = 0
        while (off < data.size) {
            if (cancelUpload) return "Dibatalkan"
            val k = minOf(n, data.size - off)
            val pk = ByteArray(6 + k)
            pk[0] = -1
            pk[1] = 0x2B
            System.arraycopy(le32(off.toLong()), 0, pk, 2, 4)
            System.arraycopy(data, off, pk, 6, k)
            if (!write(pk, true)) return "Terputus di tengah upload (${(off.toLong() * 100 / data.size).toInt()}%). Firmware lama masih utuh."
            off += k
            progress((off.toLong() * 100 / data.size).toInt())
        }
        val crc = CRC32().also { it.update(data) }.value
        if (!write(byteArrayOf(-1, 0x2C) + le32(crc), true)) return "Gagal menutup OTA"
        t = System.currentTimeMillis()
        while (true) {
            val st = readStatus()
            if (st == 4) return null
            if (st == 5) return "Citra firmware tidak sah / gagal diverifikasi. Firmware lama tetap jalan."
            if (System.currentTimeMillis() - t > 15000) return "Tidak ada konfirmasi dari ESP32"
            Thread.sleep(200)
        }
    }

    /** Batalkan sesi OTA yang sedang berjalan di ESP32 (mis. sebelum berkas baru dipilih ulang). */
    fun abortOta() { sendControl(byteArrayOf(-1, 0x2E)) }

    /** Kosongkan slot wallpaper (target 0) atau animasi boot (target 1, v22.25+) di ESP32: kembali ke bawaan. null = sukses. */
    fun clearWallpaper(target: Int = 0): String? {
        if (!ready) return "ESP32 belum tersambung"
        if (uploading) return "Sedang upload"
        val c = chr ?: return "ESP32 belum tersambung"
        uploading = true
        try {
            return synchronized(txLock) {
                var res: String? = "Tidak ada konfirmasi (firmware v22.14?)"
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                try {
                    if (!write(byteArrayOf(-1, if (target == 1) 0x17 else 0x13), true)) {
                        res = "Gagal mengirim perintah"
                    } else {
                        val t = System.currentTimeMillis()
                        while (System.currentTimeMillis() - t < 8000) {
                            val st = readStatus()
                            if (st == 4) { res = null; break }
                            if (st == 5) { res = "ESP32 gagal mengosongkan"; break }
                            Thread.sleep(200)
                        }
                    }
                } finally {
                    c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                }
                res
            }
        } finally {
            uploading = false
        }
    }
}
