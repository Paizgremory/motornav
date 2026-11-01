package com.motornav.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.concurrent.Executors

class MainActivity : Activity(), Core.Listener {
    private lateinit var tvStatus: TextView
    private lateinit var ivPreview: ImageView
    private lateinit var etSearch: EditText
    private lateinit var results: LinearLayout
    private lateinit var btnHead: Button
    private val exec = Executors.newSingleThreadExecutor()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun button(t: String, onClick: () -> Unit): Button = Button(this).apply {
        text = t
        isAllCaps = false
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun row(vararg v: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        v.forEach { addView(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Core.init(applicationContext)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(24))
        }
        root.addView(TextView(this).apply { text = "MotorNav"; textSize = 24f })
        root.addView(TextView(this).apply {
            text = "Navigasi ke layar motor tanpa Chronos"
            textSize = 13f
        })

        ivPreview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(220), dp(220)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(10) }
            setBackgroundColor(0xFF000000.toInt())
        }
        root.addView(ivPreview)

        tvStatus = TextView(this).apply { text = "Belum jalan."; textSize = 13f; setPadding(0, dp(10), 0, dp(6)) }
        root.addView(tvStatus)

        root.addView(row(
            button("1. Sambungkan ESP32") { go() },
            button("Putuskan") { Core.ble.disconnect() }
        ))

        val etKey = EditText(this).apply {
            hint = "Google API key (kosong = pakai OSRM)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            setText(GKey.get(applicationContext))
        }
        root.addView(etKey)
        root.addView(row(button("Simpan API key") {
            GKey.set(applicationContext, etKey.text.toString())
            Toast.makeText(this, if (etKey.text.isBlank()) "Key dihapus: pakai OSRM." else "Key disimpan: pakai Google.", Toast.LENGTH_SHORT).show()
        }))

        etSearch = EditText(this).apply {
            hint = "Cari tujuan, 'lat,lon', atau tempel link Maps"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        root.addView(etSearch)
        root.addView(row(
            button("2. Cari tujuan") { doSearch() },
            button("Peta saja") { startIfNeeded(); Core.startMapOnly() }
        ))
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(results)

        root.addView(row(
            button("Zoom −") { Core.changeZoom(-1) },
            button("Zoom +") { Core.changeZoom(1) }
        ))
        btnHead = button(if (Core.headingUp) "Arah: mengikuti jalan" else "Arah: utara di atas") {
            val up = Core.toggleHeading()
            btnHead.text = if (up) "Arah: mengikuti jalan" else "Arah: utara di atas"
        }
        root.addView(row(btnHead, button("Berhenti navigasi") { Core.stopNav() }))
        root.addView(row(button("Keluar & matikan layanan") {
            stopService(Intent(this, KeepAliveService::class.java))
            Core.ble.disconnect()
            tvStatus.text = "Layanan dimatikan."
        }))
        root.addView(TextView(this).apply {
            text = "Peta © OpenStreetMap contributors © CARTO. Rute & pencarian: Google (jika API key diisi), cadangan OSRM/Nominatim."
            textSize = 11f
            setPadding(0, dp(14), 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        Core.listener = this
    }

    override fun onPause() {
        Core.listener = null
        super.onPause()
    }

    override fun onStatus(text: String) { tvStatus.text = text }
    override fun onPreview(b: Bitmap) { ivPreview.setImageBitmap(b) }

    // ---------- izin & layanan ----------
    private fun missing(): List<String> {
        val l = ArrayList<String>()
        l.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) {
            l.add(Manifest.permission.BLUETOOTH_SCAN)
            l.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        return l.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    }

    private fun startIfNeeded(): Boolean {
        val m = missing()
        if (m.isNotEmpty()) {
            val all = ArrayList(m)
            if (Build.VERSION.SDK_INT >= 33) all.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(all.toTypedArray(), 1)
            Toast.makeText(this, "Izinkan lokasi & Bluetooth, lalu tekan tombol lagi.", Toast.LENGTH_LONG).show()
            return false
        }
        startForegroundService(Intent(this, KeepAliveService::class.java))
        return true
    }

    private fun go() {
        if (startIfNeeded()) Core.ble.connect()
    }

    private fun doSearch() {
        val q = etSearch.text.toString().trim()
        if (q.isEmpty()) return
        if (!startIfNeeded()) return
        results.removeAllViews()
        Toast.makeText(this, "Mencari...", Toast.LENGTH_SHORT).show()
        exec.execute {
            try {
                val list = Engine.search(applicationContext, q, Core.lat, Core.lon)
                runOnUiThread {
                    if (list.isEmpty()) Toast.makeText(this, "Tidak ada hasil.", Toast.LENGTH_SHORT).show()
                    for (p in list) {
                        results.addView(Button(this).apply {
                            text = p.name
                            isAllCaps = false
                            setOnClickListener {
                                results.removeAllViews()
                                Core.setDestination(p)
                            }
                        })
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Gagal cari: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }
}
