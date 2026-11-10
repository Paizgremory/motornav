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
import java.io.File
import java.util.zip.CRC32

/** Satu wallpaper di kartu SD ESP32. kind: 0 rusak, 1 foto, 2 video. */
class WpItem(val slot: Int, val kind: Int, val kb: Long)

/** Daftar wallpaper di kartu SD ESP32. active = slot yang sedang tampil (0 = bawaan). */
class WpList(val sd: Boolean, val active: Int, val totalMb: Int, val items: List<WpItem>)

/** Info update firmware dari ESP32 (notify 0xE4, firmware v22.31). ota = tabel partisi punya slot program kedua; maxSize = ukuran slot (byte). */
class FwInfo(val ota: Boolean, val maxSize: Long, val version: String)

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

    // daftar wallpaper dari ESP32 (notify 0xE2, firmware v22.29)
    private val lsSem = Semaphore(0)
    private val lsItems = java.util.Collections.synchronizedList(ArrayList<WpItem>())
    @Volatile private var lsCount = -1
    @Volatile private var lsSd = false
    @Volatile private var lsActive = 0
    @Volatile private var lsTotal = 0

    // info update firmware (notify 0xE4, firmware v22.31)
    private val fwSem = Semaphore(0)
    @Volatile private var fwInfoRes: FwInfo? = null

    // kendali motor (firmware v22.33, notify 0xE5): kunci kontak & starter lewat relay
    @Volatile var ignOn = false
    @Volatile var starterOn = false
    @Volatile var motorErr = 0                       // 0 ok, 1 PIN salah, 2 kunci kontak mati, 3 terlalu cepat
    @Volatile var codeAck = 0                        // balasan ganti kode: 1 berhasil, 2 ditolak (lewat 2 menit), 3 tidak valid
    @Volatile var motorSeen = false                  // sudah pernah menerima status dari ESP32
    @Volatile var onMotor: (() -> Unit)? = null

    @Volatile var ready = false
    @Volatile var chunk = 15
    @Volatile var msg = "Belum tersambung"
    @Volatile var framesSent = 0
    @Volatile var lastFrameBytes = 0                      // ukuran & lama kirim frame terakhir (untuk diagnosis kecepatan)
    @Volatile var lastFrameMs = 0L
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
            chunk = if (status == BluetoothGatt.GATT_SUCCESS) minOf(239, mt - 8) else 15   // 5 byte kepala + 3 byte ATT <= MTU (maks 247): paket lebih besar = lebih sedikit putaran tunggu
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
            val isList = v.isNotEmpty() && (v[0] == 0xE2.toByte() || v[0] == 0xE3.toByte())   // 0xE2 = daftar wallpaper, 0xE3 = daftar animasi boot (firmware v22.30)
            if (v.size >= 8 && isList && v[1] == 0.toByte()) {                         // kepala daftar
                lsItems.clear()
                lsSd = v[2].toInt() != 0
                lsCount = v[3].toInt() and 255
                lsActive = (v[4].toInt() and 255) or ((v[5].toInt() and 255) shl 8)
                lsTotal = (v[6].toInt() and 255) or ((v[7].toInt() and 255) shl 8)
                if (lsCount == 0) lsSem.release()
            } else if (v.size >= 10 && isList && v[1] == 1.toByte()) {                  // satu berkas
                val slot = (v[3].toInt() and 255) or ((v[4].toInt() and 255) shl 8)
                val kb = (v[6].toLong() and 255) or ((v[7].toLong() and 255) shl 8) or ((v[8].toLong() and 255) shl 16) or ((v[9].toLong() and 255) shl 24)
                lsItems.add(WpItem(slot, v[5].toInt() and 255, kb))
                if (lsCount >= 0 && lsItems.size >= lsCount) lsSem.release()
            }
            if (v.size >= 6 && v[0] == 0xE4.toByte()) {                                  // v3.27: info update firmware
                val mx = (v[2].toLong() and 255) or ((v[3].toLong() and 255) shl 8) or ((v[4].toLong() and 255) shl 16) or ((v[5].toLong() and 255) shl 24)
                fwInfoRes = FwInfo((v[1].toInt() and 1) != 0, mx, String(v, 6, v.size - 6, Charsets.US_ASCII))
                fwSem.release()
            }
            if (v.size >= 4 && v[0] == 0xE5.toByte()) {                                  // v3.29: status relay motor
                ignOn = v[1].toInt() != 0
                starterOn = v[2].toInt() != 0
                motorErr = v[3].toInt() and 255
                motorSeen = true
                when (motorErr) { 5 -> codeAck = 1; 4 -> codeAck = 2; 6 -> codeAck = 3 }
                h.post { onMotor?.invoke() }
            }
            if (v.size >= 2 && v[0] == 0xE1.toByte()) {
                if (v[1] == 1.toByte()) Notify.rejectCall(ctx)
                else if (v[1] == 2.toByte()) Notify.espDone()
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
        val t0 = System.nanoTime()
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
        lastFrameBytes = tot
        lastFrameMs = (System.nanoTime() - t0) / 1_000_000
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

    private fun pinBytes(pin: String): ByteArray = pin.padEnd(6, '0').take(6).toByteArray(Charsets.US_ASCII)

    /** Kunci kontak ON/OFF lewat relay 1: [FF][30][on][kode 6 karakter]. */
    fun motorIgnition(on: Boolean, pin: String): Boolean =
        sendControl(byteArrayOf(-1, 0x30, if (on) 1 else 0) + pinBytes(pin))

    /** Starter lewat relay 2 selama ms milidetik (firmware membatasi 300..3000): [FF][31][ms u16][PIN]. */
    fun motorStarter(ms: Int, pin: String): Boolean {
        val m = ms.coerceIn(300, 3000)
        return sendControl(byteArrayOf(-1, 0x31, (m and 255).toByte(), (m shr 8).toByte()) + pinBytes(pin))
    }

    /** Ganti kode di ESP32 tanpa kode lama (lupa kode). ESP32 hanya menerima 2 menit pertama setelah menyala. */
    fun motorSetCode(code: String): Boolean = sendControl(byteArrayOf(-1, 0x33) + code.take(6).toByteArray(Charsets.US_ASCII))

    /** Minta status relay (dibalas notify 0xE5). */
    fun motorQuery(): Boolean = sendControl(byteArrayOf(-1, 0x32, 0))

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
     * Kirim citra (berkas) ke ESP32. slot > 0 = simpan ke perpustakaan kartu SD sebagai wallpaper nomor slot dan langsung dipakai
     * (firmware v22.29). slot = 0 / boot = cara lama. Mengembalikan null kalau sukses, atau teks galat. progress: 0..100.
     */
    fun uploadWallpaper(file: File, boot: Boolean = false, slot: Int = 0, progress: (Int) -> Unit): String? {
        if (!ready) return "ESP32 belum tersambung"
        val size = file.length()
        if (size < 64 || size % 4 != 0L) return "Ukuran data salah"
        cancelUpload = false
        val c = chr ?: return "ESP32 belum tersambung"
        uploading = true                                   // hentikan kiriman frame peta selama upload
        try {
            return synchronized(txLock) {
                var err: String? = null
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT   // pakai konfirmasi: tak ada paket hilang
                try {
                    for (attempt in 1..(if (size > 300000) 2 else 3)) {
                        err = uploadOnce(file, boot, slot, progress)
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

    private fun uploadOnce(file: File, boot: Boolean, slot: Int, progress: (Int) -> Unit): String? {
        val size = file.length()
        val begin = when {
            boot && slot > 0 -> byteArrayOf(-1, 0x1B) + le32(size) + byteArrayOf(slot.toByte(), (slot shr 8).toByte())   // v22.30: animasi boot ke slot
            boot -> byteArrayOf(-1, 0x14) + le32(size)
            slot > 0 -> byteArrayOf(-1, 0x15) + le32(size) + byteArrayOf(slot.toByte(), (slot shr 8).toByte())
            else -> byteArrayOf(-1, 0x10) + le32(size)
        }
        if (!write(begin, true)) return "Gagal memulai upload (BLE)"
        var t = System.currentTimeMillis()
        while (true) {
            val st = readStatus()
            if (st == 2) break
            if (st == 5) return if (boot) "ESP32 menolak. Sudah flash firmware v22.30 dan memasang microSD? (tanpa SD batas animasi boot 512 KB)" else "ESP32 menolak upload. Butuh microSD + firmware v22.30 (tanpa SD video maks 1,5 MB)"
            if (System.currentTimeMillis() - t > 15000) return "ESP32 tidak membalas. Sudah flash firmware v22.30?"
            Thread.sleep(250)
        }
        val n = maxOf(8, if (mtu > 23) minOf(240, mtu - 9) else chunk - 1)   // paket = 6 byte kepala + data, muat di MTU-3
        val crc = CRC32()
        var off = 0L
        var lastPct = -1
        java.io.DataInputStream(java.io.BufferedInputStream(java.io.FileInputStream(file), 64 * 1024)).use { ins ->
            while (off < size) {
                if (cancelUpload) return "Dibatalkan"
                val k = minOf(n.toLong(), size - off).toInt()
                val pk = ByteArray(6 + k)
                pk[0] = -1
                pk[1] = 0x11
                System.arraycopy(le32(off), 0, pk, 2, 4)
                ins.readFully(pk, 6, k)
                crc.update(pk, 6, k)
                if (!write(pk, true)) return "Terputus di tengah upload (${(off * 100 / size).toInt()}%)"
                off += k
                val pct = (off * 100 / size).toInt()
                if (pct != lastPct) { lastPct = pct; progress(pct) }
            }
        }
        if (!write(byteArrayOf(-1, 0x12) + le32(crc.value), true)) return "Gagal menutup upload"
        t = System.currentTimeMillis()
        while (true) {
            val st = readStatus()
            if (st == 4) return null
            if (st == 5) return "Data rusak (CRC tidak cocok) atau kartu SD penuh/gagal menulis"
            if (System.currentTimeMillis() - t > 60000) return "Tidak ada konfirmasi dari ESP32"
            Thread.sleep(200)
        }
    }

    /** Minta daftar wallpaper di kartu SD ESP32 (firmware v22.29). null = gagal / tidak ada balasan. */
    fun listWallpapers(): WpList? {
        if (!ready || uploading) return null
        lsCount = -1
        lsItems.clear()
        lsSem.drainPermits()
        if (!sendControl(byteArrayOf(-1, 0x18))) return null
        if (!lsSem.tryAcquire(8, TimeUnit.SECONDS)) return null
        val items = synchronized(lsItems) { ArrayList(lsItems) }.sortedBy { it.slot }
        return WpList(lsSd, lsActive, lsTotal, items)
    }

    /** Minta daftar animasi boot di kartu SD ESP32 (firmware v22.30). active = slot terpilih (0 = tanpa animasi). null = gagal / tidak ada balasan. */
    fun listBoot(): WpList? {
        if (!ready || uploading) return null
        lsCount = -1
        lsItems.clear()
        lsSem.drainPermits()
        if (!sendControl(byteArrayOf(-1, 0x1C))) return null
        if (!lsSem.tryAcquire(8, TimeUnit.SECONDS)) return null
        val items = synchronized(lsItems) { ArrayList(lsItems) }.sortedBy { it.slot }
        return WpList(lsSd, lsActive, lsTotal, items)
    }

    /** Pilih animasi boot yang sudah ada di kartu SD (slot 0 = tanpa animasi). null = sukses. */
    fun selectBoot(slot: Int): String? =
        statusCommand(byteArrayOf(-1, 0x1D, slot.toByte(), (slot shr 8).toByte()), "Gagal memilih (berkas tak ada di SD? firmware v22.30?)")

    /** Hapus berkas animasi boot di kartu SD. null = sukses. */
    fun deleteBoot(slot: Int): String? =
        statusCommand(byteArrayOf(-1, 0x1E, slot.toByte(), (slot shr 8).toByte()), "Gagal menghapus di SD")

    /** Pilih wallpaper yang sudah ada di kartu SD (slot 0 = foto bawaan). null = sukses. */
    fun selectWallpaper(slot: Int): String? =
        statusCommand(byteArrayOf(-1, 0x19, slot.toByte(), (slot shr 8).toByte()), "Gagal memilih (berkas tak ada di SD? firmware v22.29?)")

    /** Hapus berkas wallpaper di kartu SD. null = sukses. */
    fun deleteWallpaper(slot: Int): String? =
        statusCommand(byteArrayOf(-1, 0x1A, slot.toByte(), (slot shr 8).toByte()), "Gagal menghapus di SD")

    private fun statusCommand(p: ByteArray, failMsg: String): String? {
        if (!ready) return "ESP32 belum tersambung"
        if (uploading) return "Sedang upload"
        val c = chr ?: return "ESP32 belum tersambung"
        uploading = true
        try {
            return synchronized(txLock) {
                var res: String? = "Tidak ada konfirmasi (butuh firmware v22.29)"
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                try {
                    if (!write(p, true)) {
                        res = "Gagal mengirim perintah"
                    } else {
                        val t = System.currentTimeMillis()
                        while (System.currentTimeMillis() - t < 25000) {   // video panjang: ESP32 membaca tabel frame dulu
                            val st = readStatus()
                            if (st == 4) { res = null; break }
                            if (st == 5) { res = failMsg; break }
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

    /** Kosongkan slot wallpaper (ESP32 kembali ke foto bawaan). null = sukses. */
    fun clearWallpaper(boot: Boolean = false): String? {
        if (!ready) return "ESP32 belum tersambung"
        if (uploading) return "Sedang upload"
        val c = chr ?: return "ESP32 belum tersambung"
        uploading = true
        try {
            return synchronized(txLock) {
                var res: String? = "Tidak ada konfirmasi (firmware v22.14?)"
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                try {
                    if (!write(byteArrayOf(-1, (if (boot) 0x17 else 0x13).toByte()), true)) {
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

    // ---------- v3.27: update firmware ESP32 (OTA) lewat Bluetooth, butuh firmware v22.31 ----------
    /** Tanya versi firmware + apakah ESP32 punya slot OTA. null = tak ada balasan (firmware lama sebelum v22.31 / terputus). */
    fun fwInfo(): FwInfo? {
        if (!ready || uploading) return null
        return synchronized(txLock) {
            fwSem.drainPermits()
            fwInfoRes = null
            if (!write(byteArrayOf(-1, 0x2D))) null
            else if (!fwSem.tryAcquire(4, TimeUnit.SECONDS)) null
            else fwInfoRes
        }
    }

    /**
     * Kirim berkas firmware (.ino.bin dari Arduino IDE) ke slot OTA ESP32. null = sukses (ESP32 restart sendiri ±2,5 dtk kemudian),
     * selain itu teks galat. Program lama di ESP32 tetap utuh bila gagal / dibatalkan. progress: 0..100, status: teks tahap.
     */
    fun uploadFirmware(file: File, progress: (Int) -> Unit, status: (String) -> Unit): String? {
        if (!ready) return "ESP32 belum tersambung"
        cancelUpload = false
        val c = chr ?: return "ESP32 belum tersambung"
        uploading = true                                   // hentikan kiriman frame peta selama update
        try {
            return synchronized(txLock) {
                var err: String? = null
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT   // pakai konfirmasi: tak ada paket hilang
                try {
                    err = otaOnce(file, progress, status)
                } finally {
                    c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                }
                err
            }
        } finally {
            uploading = false
        }
    }

    private fun otaOnce(file: File, progress: (Int) -> Unit, status: (String) -> Unit): String? {
        val size = file.length()
        if (size < 65536) return "Berkas terlalu kecil untuk firmware ESP32 (${size} byte)"
        val head = ByteArray(36)
        java.io.FileInputStream(file).use { ins -> if (ins.read(head) < 36) return "Berkas terlalu kecil" }
        val magic = (head[32].toLong() and 255) or ((head[33].toLong() and 255) shl 8) or ((head[34].toLong() and 255) shl 16) or ((head[35].toLong() and 255) shl 24)
        if ((head[0].toInt() and 255) != 0xE9 || magic != 0xABCD5432L)
            return "Ini bukan berkas program ESP32. Pakai hasil Sketch > Export Compiled Binary yang berakhiran .ino.bin (bukan .bootloader.bin, .partitions.bin atau .merged.bin)."

        status("Menyiapkan slot di ESP32...")
        if (!write(byteArrayOf(-1, 0x2A) + le32(size), true)) return "Gagal memulai update (BLE)"
        var t = System.currentTimeMillis()
        while (true) {
            if (cancelUpload) { write(byteArrayOf(-1, 0x2E)); return "Dibatalkan" }
            val st = readStatus()
            if (st == 2) break
            if (st == 5) return "ESP32 menolak. Tabel partisi belum punya dua slot program (flash firmware v22.31 + partitions.csv baru lewat kabel USB dulu), atau berkas lebih besar dari slot."
            if (System.currentTimeMillis() - t > 60000) return "ESP32 tidak membalas. Sudah flash firmware v22.31?"
            Thread.sleep(250)
        }

        status("Mengirim firmware...")
        val n = maxOf(8, if (mtu > 23) minOf(240, mtu - 9) else chunk - 1)   // paket = 6 byte kepala + data, muat di MTU-3
        val crc = CRC32()
        var off = 0L
        var lastPct = -1
        java.io.DataInputStream(java.io.BufferedInputStream(java.io.FileInputStream(file), 64 * 1024)).use { ins ->
            while (off < size) {
                if (cancelUpload) { write(byteArrayOf(-1, 0x2E)); return "Dibatalkan. Program lama di ESP32 tetap utuh." }
                val k = minOf(n.toLong(), size - off).toInt()
                val pk = ByteArray(6 + k)
                pk[0] = -1
                pk[1] = 0x2B
                System.arraycopy(le32(off), 0, pk, 2, 4)
                ins.readFully(pk, 6, k)
                crc.update(pk, 6, k)
                if (!write(pk, true)) return "Terputus di tengah update (${(off * 100 / size).toInt()}%). Program lama di ESP32 tetap utuh; sambungkan lagi lalu coba ulang."
                off += k
                val pct = (off * 100 / size).toInt()
                if (pct != lastPct) { lastPct = pct; progress(pct) }
            }
        }
        status("Memeriksa firmware di ESP32...")
        if (!write(byteArrayOf(-1, 0x2C) + le32(crc.value), true)) return "Gagal menutup update"
        t = System.currentTimeMillis()
        while (true) {
            val st = readStatus()
            if (st == 4) return null
            if (st == 5) return "ESP32 menolak firmware (CRC tidak cocok atau citra tidak sah untuk chip ini). Program lama tetap utuh; coba ulang atau cek berkas .ino.bin-nya."
            if (System.currentTimeMillis() - t > 90000) return "Tidak ada konfirmasi dari ESP32"
            Thread.sleep(200)
        }
    }
}
