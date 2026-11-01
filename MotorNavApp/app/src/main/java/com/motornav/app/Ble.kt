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

    @Volatile var ready = false
    @Volatile var chunk = 15
    @Volatile var msg = "Belum tersambung"
    @Volatile var framesSent = 0
    private var want = false
    private var scanning = false
    private var fails = 0
    private var fid = 0

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

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            chunk = if (status == BluetoothGatt.GATT_SUCCESS) minOf(200, mtu - 8) else 15
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
            ready = true
            say("Tersambung (paket $chunk B)")
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            sem.release()
        }
    }

    private fun write(p: ByteArray): Boolean {
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
        return sem.tryAcquire(1500, TimeUnit.MILLISECONDS)
    }

    /** Paket kontrol: [0xFF][cmd][...]. */
    fun sendControl(p: ByteArray): Boolean = ready && write(p)

    /** Kirim satu frame JPEG: tiap paket = [id][offset LE16][total LE16][data]. */
    fun sendFrame(data: ByteArray): Boolean {
        if (!ready) return false
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
}
