package com.motornav.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
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
    private val wpExec = Executors.newSingleThreadExecutor()
    private var wpSrc: Bitmap? = null
    private var wpData: ByteArray? = null
    private lateinit var ivWp: ImageView
    private lateinit var tvWp: TextView
    private lateinit var btnWpSend: Button
    private lateinit var sbDim: SeekBar
    private lateinit var sbZoom: SeekBar
    private lateinit var sbPanX: SeekBar
    private lateinit var sbPanY: SeekBar
    @Volatile private var wpGen = 0

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

        root.addView(CheckBox(this).apply {
            text = "Suara petunjuk belokan"
            isChecked = Voice.enabled
            setOnCheckedChangeListener { _, on -> Voice.setOn(on) }
        })
        root.addView(row(button("Tes suara") {
            if (!Voice.langOk) Toast.makeText(this, "Suara bahasa Indonesia belum ada. Pasang di Pengaturan > Teks ke ucapan.", Toast.LENGTH_LONG).show()
            Voice.speak("Dalam 200 meter, belok kiri ke Jalan Ahmad Yani.")
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
        // ---------- Wallpaper layar standby (kirim lewat Bluetooth) ----------
        root.addView(TextView(this).apply { text = "Wallpaper layar standby"; textSize = 18f; setPadding(0, dp(18), 0, dp(4)) })
        root.addView(TextView(this).apply {
            text = "Pilih foto, atur, lalu kirim lewat Bluetooth (tanpa mode WiFi). Butuh firmware v22.13. Layar fisik 240x240, jadi foto diproses dengan kualitas terbaik: diperkecil halus, dipertajam, dan dithering agar gradasi tidak berpita."
            textSize = 12f
        })
        ivWp = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(220), dp(220)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8) }
            setBackgroundColor(0xFF000000.toInt())
        }
        root.addView(ivWp)
        fun seek(label: String, max: Int, init: Int): SeekBar {
            root.addView(TextView(this).apply { text = label; textSize = 12f })
            return SeekBar(this).apply {
                this.max = max; progress = init
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { if (u) wpRender() }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
                root.addView(this)
            }
        }
        sbDim = seek("Gelapkan tengah (supaya jam terbaca)", 60, 35)
        sbZoom = seek("Perbesar (zoom)", 30, 0)
        sbPanX = seek("Geser kiri-kanan", 200, 100)
        sbPanY = seek("Geser atas-bawah", 200, 100)
        tvWp = TextView(this).apply { text = "Belum ada foto dipilih."; textSize = 13f; setPadding(0, dp(6), 0, dp(4)) }
        root.addView(tvWp)
        btnWpSend = button("Kirim ke ESP32") { wpSend() }
        root.addView(row(button("Pilih foto") { pickPhoto() }, btnWpSend))
        btnWpSend.isEnabled = false

        root.addView(TextView(this).apply {
            text = "Peta, rute & pencarian: Google (jika API key diisi). Cadangan: © OpenStreetMap contributors, OSRM, Nominatim."
            textSize = 11f
            setPadding(0, dp(14), 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(root) })
        handleIntent(intent)
    }

    // ---------- wallpaper ----------
    private fun pickPhoto() {
        val i = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }
        try { startActivityForResult(Intent.createChooser(i, "Pilih foto wallpaper"), 77) }
        catch (e: Exception) { Toast.makeText(this, "Tidak ada aplikasi galeri.", Toast.LENGTH_LONG).show() }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 77 || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        tvWp.text = "Membaca foto..."
        wpExec.execute {
            val b = try { Wallpaper.load(applicationContext, uri) } catch (e: Throwable) { null }
            runOnUiThread {
                if (b == null) { tvWp.text = "Foto tidak bisa dibaca."; return@runOnUiThread }
                wpSrc = b
                sbZoom.progress = 0; sbPanX.progress = 100; sbPanY.progress = 100
                wpRender()
            }
        }
    }

    private fun wpRender() {
        val src = wpSrc ?: return
        val gen = ++wpGen
        val dim = sbDim.progress
        val zoom = 1f + sbZoom.progress / 10f
        val px = (sbPanX.progress - 100) / 100f
        val py = (sbPanY.progress - 100) / 100f
        wpExec.execute {
            if (gen != wpGen) return@execute                       // sudah ada pengaturan yang lebih baru
            val r = try { Wallpaper.prepare(src, dim, zoom, px, py) } catch (e: Throwable) { null }
            runOnUiThread {
                if (r == null || gen != wpGen) return@runOnUiThread
                wpData = r.first
                ivWp.setImageBitmap(r.second)
                btnWpSend.isEnabled = true
                tvWp.text = "Siap dikirim (${src.width}x${src.height} -> 240x240)."
            }
        }
    }

    private fun wpSend() {
        val d = wpData ?: return
        if (!Core.ble.ready) { Toast.makeText(this, "Sambungkan ESP32 dulu (tombol 1).", Toast.LENGTH_LONG).show(); return }
        btnWpSend.isEnabled = false
        tvWp.text = "Mengirim... 0%"
        Thread {
            val err = try {
                Core.ble.uploadWallpaper(d) { p -> runOnUiThread { tvWp.text = "Mengirim... $p%" } }
            } catch (e: Exception) { "Galat: ${e.message}" }
            runOnUiThread {
                tvWp.text = if (err == null) "Selesai. Wallpaper tampil di layar standby." else "Gagal: $err"
                btnWpSend.isEnabled = true
            }
        }.also { it.isDaemon = true }.start()
    }

    // ---------- terima "Bagikan" dari Google Maps ----------
    private var pendingShare: String? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i == null || i.action != Intent.ACTION_SEND) return
        val txt = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
        setIntent(Intent())   // jangan diproses ulang saat activity dibuat ulang
        processShare(txt)
    }

    private fun processShare(txt: String) {
        if (!startIfNeeded()) { pendingShare = txt; return }
        Toast.makeText(this, "Menerima tujuan dari Maps...", Toast.LENGTH_SHORT).show()
        exec.execute {
            try {
                val p = resolveShared(txt)
                if (p == null) {
                    runOnUiThread { Toast.makeText(this, if (lastErr.isNotEmpty()) lastErr.take(220) else "Tujuan tidak ditemukan di Google maupun OSM. Tautan akhir: ${lastLinkInfo.take(100)}", Toast.LENGTH_LONG).show() }
                    return@execute
                }
                var waited = 0
                while (Core.lat.isNaN() && waited < 15000) { Thread.sleep(500); waited += 500 }
                runOnUiThread { etSearch.setText(p.name) }
                Core.setDestination(p)
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Gagal baca tujuan: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun resolveShared(txt: String): Place? {
        lastErr = ""
        val url = Regex("https?://\\S+").find(txt)?.value
        val nameLines = txt.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("http") }.toList()
        var p: Place? = null
        if (url != null) {
            val r = try { Geocode.fromLink(url) } catch (e: Exception) { Pair(null, "galat: ${e.message}") }
            p = r.first
            lastLinkInfo = r.second
        } else p = Geocode.parseCoord(txt)
        if (p != null) {
            val nm = nameLines.firstOrNull()
            return if (nm != null && p.name.startsWith("Titik")) Place(nm, p.lat, p.lon) else p
        }
        // Tautan tanpa koordinat: cari lewat nama. Sumber nama: alamat akhir tautan (/maps/place/NAMA,+ALAMAT/), lalu teks yang dibagikan.
        val tries = ArrayList<String>()
        Regex("/maps/place/([^/@?]+)").find(lastLinkInfo)?.groupValues?.get(1)?.let {
            val d = try { java.net.URLDecoder.decode(it, "UTF-8") } catch (e: Exception) { it.replace('+', ' ') }
            if (d.isNotBlank()) {
                tries.add(d)
                tries.add(d.split(",").first().trim())
            }
        }
        if (nameLines.isNotEmpty()) {
            tries.add(nameLines.joinToString(" "))
            tries.add(nameLines.first())
        }
        for (q in tries.distinct()) {
            try {
                val l = Engine.search(applicationContext, q, Core.lat, Core.lon)
                if (l.isNotEmpty()) return l.first()
            } catch (e: Exception) {
                lastErr = e.message ?: "galat"
                break   // galat dari Google (key/API) akan sama untuk semua percobaan
            }
        }
        return null
    }

    private var lastErr = ""
    private var lastLinkInfo = ""

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val s = pendingShare ?: return
        if (missing().isEmpty()) { pendingShare = null; processShare(s) }
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
