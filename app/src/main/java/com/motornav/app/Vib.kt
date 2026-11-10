package com.motornav.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * Efek "getar jalan" di wajah standby (gimmick, v22.36). Modul MPU6050 di motor sudah dicabut (lihat catatan versi
 * di firmware), jadi sebagai gantinya dipakai akselerometer bawaan HP: bila HP menempel/diam di dudukan motor,
 * guncangan jalan ikut terbaca dan dikirim ke ESP32 lewat BLE ([0xFF][0x34][intensitas 0..255]) supaya wajah
 * standby ikut bergoyang kecil. Murni gimmick -- tidak terhubung ke navigasi atau data lain.
 *
 * Aktif hanya bila diaktifkan lewat pengaturan (Cfg, item "Efek getar jalan di wajah standby") dan BLE tersambung.
 */
object Vib {
    private var sm: SensorManager? = null
    @Volatile private var bleRef: Ble? = null
    @Volatile private var running = false
    private var lastSend = 0L

    // "high-pass" sederhana: gx/gy/gz melacak gravitasi (low-pass), selisihnya = percepatan gerak/guncangan
    private var gx = 0f
    private var gy = 0f
    private var gz = 0f
    private var gravInited = false
    private var level = 0f

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val ax = e.values[0]; val ay = e.values[1]; val az = e.values[2]
            if (!gravInited) { gx = ax; gy = ay; gz = az; gravInited = true }
            val a = 0.85f
            gx = gx * a + ax * (1 - a); gy = gy * a + ay * (1 - a); gz = gz * a + az * (1 - a)
            val jx = ax - gx; val jy = ay - gy; val jz = az - gz
            val mag = sqrt(jx * jx + jy * jy + jz * jz)
            level = if (mag > level) mag else level * 0.8f + mag * 0.2f   // naik cepat (guncangan kerasa), turun pelan

            val now = SystemClock.elapsedRealtime()
            if (now - lastSend < 65) return                                // ~15x/dtk, cukup untuk efek halus
            lastSend = now
            val v = (level * 26f).toInt().coerceIn(0, 255)                 // skala kasar: jalan rusak -> ~3..10 m/s^2 selisih
            bleRef?.sendVib(v)
        }
        override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
    }

    /** Panggil tiap tick dari loop Core (murah): menyala/mati sendiri sesuai pengaturan & status BLE. */
    fun poll(ctx: Context, ble: Ble, enabled: Boolean) {
        bleRef = ble
        if (enabled && ble.ready) start(ctx) else stop()
    }

    private fun start(ctx: Context) {
        if (running) return
        val m = ctx.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val s = m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        gravInited = false; level = 0f
        if (!m.registerListener(listener, s, SensorManager.SENSOR_DELAY_GAME)) return
        sm = m
        running = true
    }

    fun stop() {
        if (!running) return
        try { sm?.unregisterListener(listener) } catch (e: Exception) {}
        sm = null
        running = false
    }
}
