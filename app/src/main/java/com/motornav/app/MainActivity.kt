package com.motornav.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.app.AlertDialog
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.*
import java.util.concurrent.Executors

class MainActivity : Activity(), Core.Listener {
    private lateinit var tvStatus: TextView
    private lateinit var ivPreview: ImageView
    private lateinit var etSearch: EditText
    private lateinit var results: LinearLayout
    private lateinit var btnHead: Button
    private lateinit var tvNotif: TextView
    private val exec = Executors.newSingleThreadExecutor()
    private val wpExec = Executors.newSingleThreadExecutor()
    private var wpPhoto: Bitmap? = null                 // foto sumber
    private var wpVideo: Wallpaper.VideoSource? = null  // video sumber
    private var wpSamples: List<Bitmap> = emptyList()   // contoh frame video (pratinjau + perkiraan ukuran)
    private var wpImage: ByteArray? = null              // citra foto siap kirim
    private lateinit var ivWp: ImageView
    private lateinit var tvWp: TextView
    private lateinit var tvWpInfo: TextView
    private lateinit var btnWpSend: Button
    private lateinit var cbWifi: CheckBox
    private var pendingExport: java.io.File? = null
    private var pendingExportTemp = false               // berkas ekspor sementara (hapus setelah disalin)
    private var espList: WpList? = null                 // isi kartu SD ESP32 terakhir yang diterima (null = belum diketahui)
    private lateinit var btnWpCancel: Button
    private lateinit var videoBox: LinearLayout
    private lateinit var cbAuto: CheckBox
    private lateinit var sbDim: SeekBar
    private lateinit var sbZoom: SeekBar
    private lateinit var sbPanX: SeekBar
    private lateinit var sbPanY: SeekBar
    private lateinit var sbFps: SeekBar
    private lateinit var sbQual: SeekBar
    private var wpThumb: Bitmap? = null                 // pratinjau 240x240 hasil olahan (untuk thumbnail koleksi)
    private lateinit var btnWpSave: Button
    private var bootVideo: Wallpaper.VideoSource? = null   // video sumber animasi boot
    private var bootSamples: List<Bitmap> = emptyList()
    private lateinit var ivBoot: ImageView
    private lateinit var tvBoot: TextView
    private lateinit var btnBootSend: Button
    private lateinit var btnBootSave: Button
    private lateinit var tvBootLib: TextView
    private lateinit var bootLibBox: LinearLayout
    private var bootThumb: Bitmap? = null               // pratinjau video animasi boot (thumbnail koleksi)
    private var espBoot: WpList? = null                 // isi folder /boot di kartu SD ESP32 terakhir yang diterima (null = belum diketahui)
    private lateinit var tvLib: TextView
    private lateinit var libBox: LinearLayout
    @Volatile private var wpGen = 0
    @Volatile private var wpCancel = false
    @Volatile private var wpBusy = false

    // ---------- v3.27: update firmware ESP32 (OTA) lewat Bluetooth ----------
    private lateinit var tvFw: TextView
    private lateinit var pbFw: ProgressBar
    private lateinit var btnFwSend: Button
    private lateinit var btnFwCancel: Button
    private var fwFile: java.io.File? = null
    private var fwName = ""
    @Volatile private var fwBusy = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val ACC = 0xFFFF9F1C.toInt()

    private fun button(t: String, primary: Boolean = false, onClick: () -> Unit): Button = Button(this).apply {
        text = t
        isAllCaps = false
        if (primary) {
            setBackgroundResource(R.drawable.btn_primary)
            setTextColor(resources.getColorStateList(R.color.btn_text_primary, theme))
        }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
    }

    private fun row(vararg v: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        v.forEach { addView(it) }
    }

    /** Judul bagian: aksen oranye, tebal. */
    private fun heading(t: String): TextView = TextView(this).apply {
        text = t
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(ACC)
        setPadding(0, dp(22), 0, dp(6))
    }

    /** Potong tampilan jadi lingkaran, sesuai layar bulat GC9A01. */
    private fun circle(v: View) {
        v.clipToOutline = true
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) { outline.setOval(0, 0, view.width, view.height) }
        }
    }

    private fun styleInput(e: EditText) {
        e.setBackgroundResource(R.drawable.input_bg)
        e.setPadding(dp(14), dp(12), dp(14), dp(12))
    }

    private fun inputParams() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        .apply { setMargins(0, dp(8), 0, dp(4)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Core.init(applicationContext)
        WpLib.cleanTmp(applicationContext)                 // buang sisa berkas olahan sementara dari proses yang terhenti

        var root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(24))
        }
        val top = root
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(10))
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_logo)
                layoutParams = LinearLayout.LayoutParams(dp(54), dp(54))
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
                addView(TextView(context).apply {
                    text = "Nav160"
                    textSize = 27f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(0xFFF2F5FA.toInt())
                })
                addView(TextView(context).apply {
                    text = "Navigasi ke layar motor"
                    textSize = 13f
                    setTextColor(0xFF9AA6B6.toInt())
                })
            })
        })

        ivPreview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(220), dp(220)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(10) }
            setBackgroundColor(0xFF000000.toInt())
        }
        circle(ivPreview)
        root.addView(ivPreview)

        tvStatus = TextView(this).apply {
            text = "Belum jalan."
            textSize = 13f
            setTextColor(0xFFB8C2D0.toInt())
            setBackgroundResource(R.drawable.card_bg)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        root.addView(tvStatus, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(0, dp(14), 0, dp(8)) })

        root.addView(row(
            button("1. Sambungkan ESP32", true) { go() },
            button("Putuskan") { Core.ble.disconnect() }
        ))

        // ---------- tab: Navigasi | Musik | Layar | Notifikasi ----------
        fun newPage() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pageNav = newPage(); val pageMusic = newPage(); val pageScreen = newPage(); val pageNotif = newPage(); val pageMotor = newPage()
        val pages = listOf(pageNav, pageMusic, pageScreen, pageNotif, pageMotor)
        val tabBtns = ArrayList<Button>()
        fun showTab(i: Int) {
            pages.forEachIndexed { k, pg -> pg.visibility = if (k == i) View.VISIBLE else View.GONE }
            if (::motorBox.isInitialized) motorUnlock(false)   // setiap kali pindah tab, menu Motor terkunci lagi
            tabBtns.forEachIndexed { k, b ->
                b.alpha = 1f
                b.setBackgroundResource(if (k == i) R.drawable.btn_primary else R.drawable.btn_bg)
                b.setTextColor(resources.getColorStateList(if (k == i) R.color.btn_text_primary else R.color.btn_text, theme))
            }
        }
        listOf("Navigasi", "Musik", "Layar", "Notifikasi", "Motor").forEachIndexed { k, n ->
            tabBtns.add(button(n) { showTab(k) }.apply { textSize = 12f })
        }
        root.addView(row(*tabBtns.toTypedArray()))
        pages.forEach { root.addView(it) }
        showTab(0)
        buildMusicPage(pageMusic)
        buildMotorPage(pageMotor)
        root = pageNav

        val etKey = EditText(this).apply {
            hint = "Google API key (kosong = pakai OSRM)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            setText(GKey.get(applicationContext))
        }
        styleInput(etKey)
        root.addView(etKey, inputParams())
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

        // ---------- v3.28: navigasi pintar ----------
        root.addView(heading("Navigasi pintar"))
        val prefBtn = arrayOfNulls<Button>(1)
        prefBtn[0] = button("Pilihan rute: ${NavPrefs.pref.label} (ketuk untuk ganti)") {
            val p = NavPrefs.cyclePref(applicationContext)
            prefBtn[0]?.text = "Pilihan rute: ${p.label} (ketuk untuk ganti)"
        }
        root.addView(row(prefBtn[0]!!))
        root.addView(CheckBox(this).apply {
            text = "Hindari jalan tol dan feri (motor umumnya dilarang masuk tol)"
            isChecked = NavPrefs.avoidToll
            setOnCheckedChangeListener { _, on -> NavPrefs.setAvoidToll(applicationContext, on) }
        })
        root.addView(CheckBox(this).apply {
            text = "Beralih otomatis bila ada rute yang jauh lebih cepat (cek tiap ±2,5 menit; memakai kuota Google/OSRM)"
            isChecked = NavPrefs.autoAlt
            setOnCheckedChangeListener { _, on -> NavPrefs.setAutoAlt(applicationContext, on) }
        })
        root.addView(CheckBox(this).apply {
            text = "Peringatan suara: GPS lemah, salah arah, rute berisiko"
            isChecked = NavPrefs.guardVoice
            setOnCheckedChangeListener { _, on -> NavPrefs.setGuardVoice(applicationContext, on) }
        })

        etSearch = EditText(this).apply {
            hint = "Cari tujuan, 'lat,lon', atau tempel link Maps"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        styleInput(etSearch)
        root.addView(etSearch, inputParams())
        root.addView(row(
            button("2. Cari tujuan", true) { doSearch() },
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
        root = pageNotif
        // ---------- Notifikasi & telepon ke layar motor ----------
        root.addView(heading("Notifikasi & telepon di layar motor"))
        root.addView(TextView(this).apply {
            text = "Notifikasi HP tampil di layar ESP32 lengkap dengan logo aplikasi, nama pengirim, dan isi pesan yang bergulir otomatis bila panjang. " +
                "Telepon masuk tampil dengan nama kontak. Butuh firmware v22.15 dan Nav160 tersambung ke ESP32."
            textSize = 12f
        })
        tvNotif = TextView(this).apply { textSize = 13f; setPadding(0, dp(6), 0, dp(4)) }
        root.addView(tvNotif)
        root.addView(row(
            button("1. Beri akses notifikasi") {
                try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                catch (e: Exception) { Toast.makeText(this, "Buka manual: Pengaturan > Notifikasi > Akses notifikasi", Toast.LENGTH_LONG).show() }
            },
            button("2. Izin telepon") { askPhonePerms() }
        ))
        root.addView(CheckBox(this).apply {
            text = "Teruskan notifikasi ke layar motor"
            isChecked = Notify.notifOn(applicationContext)
            setOnCheckedChangeListener { _, on -> Notify.setNotifOn(applicationContext, on) }
        })
        root.addView(CheckBox(this).apply {
            text = "Tampilkan isi pesan (matikan = hanya \"Pesan baru\")"
            isChecked = Notify.bodyOn(applicationContext)
            setOnCheckedChangeListener { _, on -> Notify.setBodyOn(applicationContext, on) }
        })
        root.addView(CheckBox(this).apply {
            text = "Peringatan baterai HP rendah (20%, 10%, 5%)"
            isChecked = Notify.battOn(applicationContext)
            setOnCheckedChangeListener { _, on -> Notify.setBattOn(applicationContext, on) }
        })
        root.addView(CheckBox(this).apply {
            text = "Teruskan telepon masuk ke layar motor"
            isChecked = Notify.callOn(applicationContext)
            setOnCheckedChangeListener { _, on -> Notify.setCallOn(applicationContext, on) }
        })
        root.addView(CheckBox(this).apply {
            text = "Mode berkendara: saat navigasi aktif, hanya aplikasi prioritas, tampil singkat (4,5 dtk, 1 halaman)"
            isChecked = Notify.driveOn(applicationContext)
            setOnCheckedChangeListener { _, on -> Notify.setDriveOn(applicationContext, on) }
        })
        root.addView(row(button("Aplikasi prioritas berkendara") { pickPriorityApps() }))
        root.addView(row(
            button("Pilih aplikasi") { pickNotifApps() },
            button("Tes notifikasi") { testNotif(false) },
            button("Tes telepon") { testNotif(true) }
        ))

        root = pageScreen
        buildScreenPage(pageScreen)
        // ---------- Wallpaper layar standby (foto / video, lewat Bluetooth) ----------
        root.addView(heading("Tambah wallpaper baru (foto / video)"))
        root.addView(TextView(this).apply {
            text = "Pilih foto ATAU video, atur, lalu Kirim (otomatis masuk Koleksi di atas) atau Simpan saja. Layar fisik 240x240: gambar dibaca dari sumber asli, diperkecil halus, dipertajam; foto diberi dithering agar gradasi tidak berpita."
            textSize = 12f
        })
        ivWp = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(220), dp(220)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8) }
            setBackgroundColor(0xFF000000.toInt())
        }
        circle(ivWp)
        root.addView(ivWp)
        fun seek(label: String, max: Int, init: Int, parent: LinearLayout): SeekBar {
            val tv = TextView(this).apply { text = label; textSize = 12f }
            parent.addView(tv)
            return SeekBar(this).apply {
                this.max = max; progress = init
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { if (u) wpRender() }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
                parent.addView(this)
            }
        }
        sbDim = seek("Gelapkan tengah (supaya jam terbaca)", 60, 35, root)
        sbZoom = seek("Perbesar (zoom)", 30, 0, root)
        sbPanX = seek("Geser kiri-kanan", 200, 100, root)
        sbPanY = seek("Geser atas-bawah", 200, 100, root)
        videoBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        videoBox.addView(TextView(this).apply { text = "Pengaturan video"; textSize = 14f; setPadding(0, dp(8), 0, 0) })
        sbFps = seek("FPS (5-60). Makin tinggi makin halus tapi makin besar file. ESP32 realistisnya ±25-30 fps, di atas itu frame dilewati", 55, 10, videoBox)
        sbQual = seek("Kualitas/detail JPEG (30-95). Makin tinggi makin tajam tapi makin pendek", 65, 40, videoBox)
        cbAuto = CheckBox(this).apply {
            text = "Otomatis: muat seluruh video (FPS diturunkan bila perlu)"
            isChecked = true
            setOnCheckedChangeListener { _, _ -> wpRender() }
        }
        videoBox.addView(cbAuto)
        root.addView(videoBox)
        tvWpInfo = TextView(this).apply { text = ""; textSize = 12f; setPadding(0, dp(4), 0, 0) }
        root.addView(tvWpInfo)
        tvWp = TextView(this).apply { text = "Belum ada foto/video dipilih."; textSize = 13f; setPadding(0, dp(6), 0, dp(4)) }
        root.addView(tvWp)
        cbWifi = CheckBox(this).apply {
            text = "Kirim lewat WiFi (jauh lebih cepat). Di layar motor: Pengaturan > UPLOAD WIFI dulu (butuh firmware v22.30). Berlaku juga untuk animasi boot. Setelah selesai ESP32 restart."
            isChecked = try { getSharedPreferences("motornav", MODE_PRIVATE).getBoolean("wifiup", false) } catch (e: Exception) { false }
            setOnCheckedChangeListener { _, on -> try { getSharedPreferences("motornav", MODE_PRIVATE).edit().putBoolean("wifiup", on).apply() } catch (e: Exception) {} }
        }
        root.addView(cbWifi)
        btnWpSend = button("Kirim ke ESP32", true) { wpSend(true) }
        btnWpSave = button("Simpan saja") { wpSend(false) }
        btnWpCancel = button("Batal") { wpCancel = true; Core.ble.cancelUpload = true; WifiUp.cancel = true }
        btnWpCancel.isEnabled = false
        root.addView(row(button("Pilih foto/video") { pickMedia() }, btnWpSend, btnWpSave, btnWpCancel))
        wpEnable(false)
        root.addView(row(button("Hapus wallpaper (kembali bawaan)") { wpClear() }))

        // ---------- Animasi boot: video yang diputar sekali tiap ESP32 dinyalakan (koleksi slot di microSD, firmware v22.30) ----------
        root.addView(heading("Animasi boot (video saat dinyalakan)"))
        buildBootLibrary(root)
        root.addView(TextView(this).apply {
            text = "Tambah animasi boot baru: pilih video lalu Kirim (otomatis masuk Koleksi di atas dan ke kartu SD) atau Simpan saja. " +
                "Video diputar sekali di layar motor setiap kali ESP32 dinyalakan, sebelum masuk tampilan biasa (sentuh sensor = lewati). " +
                "Bagian tengah video dipotong persegi. FPS diturunkan otomatis bila video terlalu besar. Butuh firmware v22.30 + modul microSD (tanpa SD hanya muat 0,5 MB, tanpa slot)."
            textSize = 12f
            setPadding(0, dp(14), 0, 0)
        })
        ivBoot = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(160), dp(160)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8) }
            setBackgroundColor(0xFF000000.toInt())
        }
        circle(ivBoot)
        root.addView(ivBoot)
        tvBoot = TextView(this).apply { text = "Belum ada video dipilih."; textSize = 13f; setPadding(0, dp(6), 0, dp(4)) }
        root.addView(tvBoot)
        btnBootSend = button("Kirim ke ESP32", true) { bootSend(true) }
        btnBootSend.isEnabled = false
        btnBootSave = button("Simpan saja") { bootSend(false) }
        btnBootSave.isEnabled = false
        root.addView(row(
            button("Pilih video") { pickBootVideo() }, btnBootSend, btnBootSave,
            button("Batal") { wpCancel = true; Core.ble.cancelUpload = true; WifiUp.cancel = true }
        ))
        root.addView(row(button("Simpan animasi boot sebagai file .mnv (untuk kartu SD)") { bootExport() }))

        // ---------- Update firmware ESP32 lewat Bluetooth (OTA, firmware v22.31) ----------
        root.addView(heading("Update firmware ESP32 (OTA Bluetooth)"))
        root.addView(TextView(this).apply {
            text = "Kirim berkas firmware baru langsung dari HP, tanpa kabel USB. Di Arduino IDE: Sketch > Export Compiled Binary, lalu pilih berkas yang berakhiran .ino.bin " +
                "(BUKAN .bootloader.bin / .partitions.bin / .merged.bin). Butuh firmware v22.31 dengan partisi dua slot yang di-flash lewat kabel SEKALI saja. " +
                "Bila gagal atau terputus di tengah jalan, program lama di ESP32 tetap utuh. Jaga HP dekat ESP32 dan jangan tutup aplikasi selama pengiriman (beberapa menit)."
            textSize = 12f
            setPadding(0, 0, 0, dp(6))
        })
        tvFw = TextView(this).apply { text = "Belum ada berkas firmware dipilih."; textSize = 13f; setPadding(0, dp(4), 0, dp(4)) }
        root.addView(tvFw)
        pbFw = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0 }
        root.addView(pbFw)
        btnFwSend = button("Kirim & update", true) { fwSend() }
        btnFwSend.isEnabled = false
        btnFwCancel = button("Batal") { Core.ble.cancelUpload = true }
        btnFwCancel.isEnabled = false
        root.addView(row(button("Pilih .bin firmware") { fwPick() }, button("Cek versi ESP32") { fwCheck() }))
        root.addView(row(btnFwSend, btnFwCancel))

        root = pageNav
        root.addView(TextView(this).apply {
            text = "Peta, rute & pencarian: Google (jika API key diisi). Cadangan: © OpenStreetMap contributors, OSRM, Nominatim."
            textSize = 11f
            setPadding(0, dp(14), 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(top) })
        handleIntent(intent)
    }


    // ---------- tab Musik & tab Layar ----------
    private val cfgRefresh = ArrayList<() -> Unit>()
    private val ui = Handler(Looper.getMainLooper())
    private var tick: Runnable? = null
    private var seeking = false
    private lateinit var tvNow: TextView
    private lateinit var tvPos: TextView
    private lateinit var sbSong: SeekBar
    private lateinit var btnPlay: Button

    private fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

    private fun title(p: LinearLayout, t: String) =
        p.addView(heading(t).apply { textSize = 17f; setPadding(0, dp(16), 0, dp(4)) })

    private fun buildCfg(p: LinearLayout, page: Int) {
        Cfg.items.forEachIndexed { i, item ->
            if (item.page != page) return@forEachIndexed
            if (item.toggle) {
                val cb = CheckBox(this).apply {
                    text = item.label
                    isChecked = Cfg.get(applicationContext, i) == 1
                    setOnCheckedChangeListener { _, on -> Cfg.set(applicationContext, i, if (on) 1 else 0) }
                }
                p.addView(cb)
                cfgRefresh.add { cb.isChecked = Cfg.get(applicationContext, i) == 1 }
            } else {
                val tv = TextView(this).apply { textSize = 13f; setPadding(0, dp(8), 0, 0) }
                fun show(v: Int) { tv.text = item.label + ": " + v + item.unit + (if (item.hint.isNotEmpty()) "\n(" + item.hint + ")" else "") }
                val sb = SeekBar(this).apply {
                    max = item.max - item.min
                    progress = Cfg.get(applicationContext, i) - item.min
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) {
                            show(v + item.min)
                            if (u) Cfg.set(applicationContext, i, v + item.min)
                        }
                        override fun onStartTrackingTouch(s: SeekBar?) {}
                        override fun onStopTrackingTouch(s: SeekBar?) {}
                    })
                }
                show(Cfg.get(applicationContext, i))
                p.addView(tv); p.addView(sb)
                cfgRefresh.add { sb.progress = Cfg.get(applicationContext, i) - item.min; show(Cfg.get(applicationContext, i)) }
            }
        }
    }

    private fun buildMusicPage(p: LinearLayout) {
        title(p, "Sedang diputar")
        tvNow = TextView(this).apply { textSize = 15f; text = "Tidak ada lagu diputar" }
        p.addView(tvNow)
        sbSong = SeekBar(this).apply {
            max = 1000
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) {
                    if (u && Music.npDur > 0) tvPos.text = fmt(Music.npDur * v / 1000) + " / " + fmt(Music.npDur)
                }
                override fun onStartTrackingTouch(s: SeekBar?) { seeking = true }
                override fun onStopTrackingTouch(s: SeekBar?) {
                    seeking = false
                    if (Music.npDur > 0) Music.control(applicationContext, "seek", Music.npDur * (s?.progress ?: 0) / 1000)
                }
            })
        }
        p.addView(sbSong)
        tvPos = TextView(this).apply { textSize = 12f; text = "0:00 / 0:00" }
        p.addView(tvPos)
        btnPlay = button("Putar/Jeda") { Music.control(applicationContext, "toggle") }
        p.addView(row(
            button("⏮ Sebelumnya") { Music.control(applicationContext, "prev") },
            btnPlay,
            button("Berikutnya ⏭") { Music.control(applicationContext, "next") }
        ))
        p.addView(TextView(this).apply {
            text = "Butuh Akses notifikasi (tab Notifikasi, tombol 1). Slider lagu yang sama tampil di layar standby motor."
            textSize = 11f
        })

        title(p, "Tampilan musik di layar motor")
        p.addView(TextView(this).apply {
            text = "Perubahan langsung dikirim ke ESP32 dan tersimpan di sana. Butuh firmware v22.24."
            textSize = 12f
        })
        buildCfg(p, 1)
        p.addView(row(button("Izin Rekam audio (visualizer asli)") {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this, "Izin Rekam audio sudah diberikan.", Toast.LENGTH_SHORT).show()
            else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 3)
        }))
        p.addView(TextView(this).apply {
            text = "Tanpa izin Rekam audio, visualizer memakai simulasi."
            textSize = 11f
        })
    }

    // ---------- kendali motor: kunci kontak & starter lewat relay (firmware v22.33) ----------
    private lateinit var tvMotor: TextView
    private lateinit var btnIgn: Button
    private lateinit var btnStart: Button
    private lateinit var btnAuto: Button
    @Volatile private var motorBusy = false

    private fun motorPin(): String = getSharedPreferences("motornav", MODE_PRIVATE).getString("motor_code", "258525") ?: "258525"   // kode menu Motor (awal 258525 = RLY_PIN_CODE firmware)
    private fun motorStartMs(): Int = getSharedPreferences("motornav", MODE_PRIVATE).getInt("motor_start_ms", 2000)

    private fun motorRefresh() {
        val b = Core.ble
        if (!b.ready) { tvMotor.text = "Belum tersambung ke ESP32. Tekan \"1. Sambungkan ESP32\" di atas."; btnIgn.isEnabled = false; btnStart.isEnabled = false; btnAuto.isEnabled = false; return }
        btnIgn.isEnabled = true
        btnAuto.isEnabled = !motorBusy
        btnStart.isEnabled = b.ignOn && !b.starterOn && !motorBusy
        btnIgn.text = if (b.ignOn) "Matikan kunci kontak" else "Nyalakan kunci kontak"
        val err = when (b.motorErr) { 1 -> "\nPIN salah (samakan dengan RLY_PIN_CODE di firmware)."; 2 -> "\nKunci kontak masih mati."; 3 -> "\nStarter ditolak: terlalu cepat / sedang berjalan. Tunggu 4 detik."; 4 -> "\nGanti kode ditolak: sudah lewat 2 menit sejak ESP32 menyala."; 5 -> "\nKode di ESP32 sudah diganti."; else -> "" }
        tvMotor.text = (if (!b.motorSeen) "Status belum diterima (firmware v22.33 terpasang?)" else
            "Kunci kontak: " + (if (b.ignOn) "ON" else "OFF") + "   |   Starter: " + (if (b.starterOn) "MENEKAN..." else "lepas")) + err
    }

    private fun motorDo(what: String, body: () -> Boolean) {
        if (!Core.ble.ready) { Toast.makeText(this, "ESP32 belum tersambung.", Toast.LENGTH_SHORT).show(); return }
        Thread {
            val ok = body()
            runOnUiThread { if (!ok) Toast.makeText(this, "Gagal mengirim: $what", Toast.LENGTH_SHORT).show(); motorRefresh() }
        }.start()
    }

    private lateinit var motorBox: LinearLayout
    private lateinit var motorLock: LinearLayout
    private fun motorUnlock(on: Boolean) {
        motorBox.visibility = if (on) View.VISIBLE else View.GONE
        motorLock.visibility = if (on) View.GONE else View.VISIBLE
        if (on) { motorRefresh(); Thread { Core.ble.motorQuery() }.start() }
    }

    private fun buildMotorPage(p: LinearLayout) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val lock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        motorBox = box; motorLock = lock
        title(lock, "Menu Motor terkunci")
        val etCode = EditText(this).apply {
            hint = "Masukkan kode keamanan"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        }
        styleInput(etCode)
        lock.addView(etCode, inputParams())
        lock.addView(row(button("Buka kunci", true) {
            if (etCode.text.toString() == motorPin()) {
                etCode.setText(""); motorUnlock(true)
            } else {
                etCode.setText("")
                Toast.makeText(this, "Kode salah.", Toast.LENGTH_SHORT).show()
            }
        }))
        val forgot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        lock.addView(row(button("Lupa kode? Kirim OTP ke email") {
            Toast.makeText(this, "Mengirim OTP...", Toast.LENGTH_SHORT).show()
            Thread {
                val err = MotorRecovery.sendOtp(applicationContext)
                runOnUiThread {
                    if (err == null) { forgot.visibility = View.VISIBLE; Toast.makeText(this, "OTP dikirim ke email terdaftar (berlaku 10 menit).", Toast.LENGTH_LONG).show() }
                    else Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                }
            }.start()
        }))
        val etOtp = EditText(this).apply {
            hint = "OTP dari email (6 angka)"
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        }
        val etNew = EditText(this).apply {
            hint = "Kode baru (6 angka)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        }
        styleInput(etOtp); styleInput(etNew)
        forgot.addView(etOtp, inputParams()); forgot.addView(etNew, inputParams())
        forgot.addView(TextView(this).apply {
            text = "ESP32 harus tersambung dan baru dinyalakan (maksimal 2 menit sejak menyala). Bila lewat, matikan lalu nyalakan lagi ESP32 dan coba ulang."
            textSize = 11f
        })
        forgot.addView(row(button("Ganti kode", true) {
            val nw = etNew.text.toString()
            if (nw.length != 6) { Toast.makeText(this, "Kode baru harus 6 angka.", Toast.LENGTH_SHORT).show(); return@button }
            if (!Core.ble.ready) { Toast.makeText(this, "Sambungkan ESP32 dulu.", Toast.LENGTH_LONG).show(); return@button }
            when (MotorRecovery.verify(etOtp.text.toString())) {
                0 -> {}
                1 -> { Toast.makeText(this, "OTP salah.", Toast.LENGTH_SHORT).show(); return@button }
                3 -> { forgot.visibility = View.GONE; Toast.makeText(this, "Terlalu banyak salah. Minta OTP baru.", Toast.LENGTH_LONG).show(); return@button }
                else -> { forgot.visibility = View.GONE; Toast.makeText(this, "OTP belum diminta / kedaluwarsa.", Toast.LENGTH_LONG).show(); return@button }
            }
            Thread {
                val b = Core.ble
                b.codeAck = 0
                val sent = b.motorSetCode(nw)
                var w = 0
                while (sent && b.codeAck == 0 && w < 30) { Thread.sleep(100); w++ }
                runOnUiThread {
                    when {
                        b.codeAck == 1 -> {
                            getSharedPreferences("motornav", MODE_PRIVATE).edit().putString("motor_code", nw).apply()
                            MotorRecovery.consume()
                            etOtp.setText(""); etNew.setText(""); forgot.visibility = View.GONE
                            Toast.makeText(this, "Kode berhasil diganti.", Toast.LENGTH_LONG).show()
                        }
                        b.codeAck == 2 -> Toast.makeText(this, "Ditolak ESP32: sudah lewat 2 menit sejak menyala. Matikan-nyalakan ESP32 lalu coba lagi.", Toast.LENGTH_LONG).show()
                        b.codeAck == 3 -> Toast.makeText(this, "Kode baru tidak valid (harus 6 angka).", Toast.LENGTH_LONG).show()
                        else -> Toast.makeText(this, "Tidak ada balasan dari ESP32 (firmware v22.33?).", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }))
        lock.addView(forgot)
        title(box, "Hidupkan motor dari HP")
        box.addView(TextView(this).apply {
            text = "Relay 1 = kunci kontak, relay 2 = starter (otomatis lepas). Menu ini terkunci dengan kode keamanan. " +
                "Kunci kontak asli tetap bisa dipakai (relay dipasang paralel)."
            textSize = 12f
        })
        tvMotor = TextView(this).apply { textSize = 15f; setPadding(0, dp(10), 0, dp(6)) }
        box.addView(tvMotor)
        box.addView(TextView(this).apply {
            text = "Email pengirim OTP (akun Gmail + sandi aplikasi Google 16 karakter). Penerima OTP sudah ditanam di aplikasi dan tidak bisa diubah dari sini."
            textSize = 12f; setPadding(0, dp(10), 0, 0)
        })
        val etUser = EditText(this).apply { hint = "Gmail pengirim"; inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or InputType.TYPE_CLASS_TEXT; setText(MotorRecovery.senderUser(applicationContext)) }
        val etAppPw = EditText(this).apply { hint = "Sandi aplikasi Google"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        styleInput(etUser); styleInput(etAppPw)
        box.addView(etUser, inputParams()); box.addView(etAppPw, inputParams())
        box.addView(row(button("Simpan email pengirim") {
            if (etUser.text.isBlank() || etAppPw.text.isBlank()) { Toast.makeText(this, "Isi Gmail dan sandi aplikasi.", Toast.LENGTH_SHORT).show(); return@button }
            MotorRecovery.saveSender(applicationContext, etUser.text.toString(), etAppPw.text.toString())
            etAppPw.setText("")
            Toast.makeText(this, "Tersimpan di HP ini.", Toast.LENGTH_SHORT).show()
        }))
        box.addView(row(button("Cek status") { motorDo("status") { Core.ble.motorQuery() } }))

        val tvDur = TextView(this).apply { textSize = 13f; setPadding(0, dp(10), 0, 0) }
        val sbDur = SeekBar(this).apply {
            max = 20                                   // 1,0 .. 3,0 detik
            progress = (motorStartMs() - 1000) / 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) {
                    tvDur.text = "Lama starter: %.1f detik".format((1000 + v * 100) / 1000.0)
                    if (u) getSharedPreferences("motornav", MODE_PRIVATE).edit().putInt("motor_start_ms", 1000 + v * 100).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        tvDur.text = "Lama starter: %.1f detik".format(motorStartMs() / 1000.0)
        box.addView(tvDur); box.addView(sbDur)

        btnIgn = button("Nyalakan kunci kontak") {
            val turnOn = !Core.ble.ignOn
            val go = { motorDo("kunci kontak") { Core.ble.motorIgnition(turnOn, motorPin()) } }
            if (turnOn) go() else AlertDialog.Builder(this).setTitle("Matikan kunci kontak?")
                .setMessage("Mesin akan mati. Jangan lakukan saat motor berjalan.")
                .setPositiveButton("Matikan") { _, _ -> go() }.setNegativeButton("Batal", null).show()
        }
        btnStart = button("Starter") { motorDo("starter") { Core.ble.motorStarter(motorStartMs(), motorPin()) } }
        btnAuto = button("HIDUPKAN MOTOR (kontak + starter)", true) {
            if (motorBusy) return@button
            motorBusy = true; motorRefresh()
            Thread {
                val b = Core.ble
                var ok = true
                if (!b.ignOn) { ok = b.motorIgnition(true, motorPin()); Thread.sleep(1200) }
                if (ok) ok = b.motorStarter(motorStartMs(), motorPin())
                motorBusy = false
                runOnUiThread { if (!ok) Toast.makeText(this, "Gagal mengirim perintah.", Toast.LENGTH_SHORT).show(); motorRefresh() }
            }.start()
        }
        box.addView(row(btnAuto))
        box.addView(row(btnIgn, btnStart))
        box.addView(TextView(this).apply {
            text = "Keamanan: starter hanya jalan bila kunci kontak ON, maksimal 3 detik, jeda 4 detik antar starter, dan langsung lepas bila Bluetooth putus."
            textSize = 11f; setPadding(0, dp(10), 0, 0)
        })
        Core.ble.onMotor = { motorRefresh() }
        motorRefresh()
        p.addView(lock); p.addView(box)
    }

    private fun buildScreenPage(p: LinearLayout) {
        buildLibrary(p)
        title(p, "Kecepatan peta navigasi")
        p.addView(TextView(this).apply {
            text = "Peta dikirim ke LCD lewat Bluetooth sebagai gambar. Makin kecil gambarnya makin cepat update-nya (tapi sedikit lebih buram). " +
                "Cepat = paling cepat (±3x lebih cepat dari Tajam saat bergerak). Ketuk tombol untuk berganti. Ukuran & waktu kirim frame terakhir tampil di status."
            textSize = 12f
        })
        val btnMapQ = button("Kualitas peta: " + Core.mapQualityNames[Core.mapQuality]) {}
        btnMapQ.setOnClickListener { btnMapQ.text = "Kualitas peta: " + Core.mapQualityNames[Core.cycleMapQuality()] }
        p.addView(row(btnMapQ))
        title(p, "Tampilan layar")
        p.addView(TextView(this).apply {
            text = "Kecerahan, jam, dan standby. Langsung dikirim ke ESP32 dan tersimpan di sana."
            textSize = 12f
        })
        buildCfg(p, 2)
        p.addView(row(button("Kembalikan semua pengaturan ke bawaan") {
            Cfg.resetDefaults(applicationContext)
            cfgRefresh.forEach { f -> f() }
            Toast.makeText(this, "Pengaturan dikembalikan ke bawaan.", Toast.LENGTH_SHORT).show()
        }))
    }


    // ---------- koleksi wallpaper ----------
    private fun buildLibrary(p: LinearLayout) {
        title(p, "Koleksi wallpaper")
        p.addView(TextView(this).apply {
            text = "Ketuk Pakai untuk mengganti wallpaper di layar motor. Wallpaper yang SUDAH ada di kartu SD ESP32 (tanda \"ada di kartu SD\") " +
                "langsung ganti tanpa kirim ulang; yang belum akan dikirim dulu (foto 10-30 dtk, video lewat Bluetooth lama, lewat WiFi jauh lebih cepat). " +
                "Wallpaper baru (di bawah) otomatis masuk sini dan ke kartu SD. Pilihan juga bisa diganti di layar motor: Pengaturan > WALLPAPER (tahan)."
            textSize = 12f
        })
        p.addView(row(button("Perbarui daftar kartu SD") { refreshEsp() }))
        tvLib = TextView(this).apply { textSize = 12f; setPadding(0, dp(4), 0, dp(4)) }
        p.addView(tvLib)
        libBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        p.addView(libBox)
        refreshLib()
    }

    private fun refreshLib() {
        if (!::libBox.isInitialized) return
        libBox.removeAllViews()
        val act = WpLib.active(applicationContext)
        val esp = espList
        fun onSd(slot: Int) = esp?.items?.any { it.slot == slot } == true
        fun kindName(k: Int) = when (k) { 1 -> "Foto"; 2 -> "Video"; else -> "Berkas rusak" }
        fun addRow(thumb: Bitmap?, name: String, info: String, on: Boolean, use: () -> Unit, del: (() -> Unit)?, ren: (() -> Unit)?, exp: (() -> Unit)? = null) =
            libRow(libBox, thumb, name, info, on, use, del, ren, exp)
        addRow(null, "Foto bawaan", "Kembali ke wallpaper dari firmware", if (esp != null) esp.active == 0 else act == null, { wpClear() }, null, null)
        val local = WpLib.list(applicationContext).sortedByDescending { it.at }
        for (e in local) {
            addRow(WpLib.thumb(applicationContext, e.id), e.name,
                (if (e.kind == "video") "Video" else "Foto") + " · " + sizeText(e.size) +
                    (if (esp != null) (if (onSd(e.slot)) " · ada di kartu SD" else " · belum di kartu SD") else ""),
                if (esp != null) esp.active == e.slot else act == e.id,
                { useWallpaper(e) },
                {
                    val opts = ArrayList<String>(); val acts = ArrayList<() -> Unit>()
                    opts.add("Hapus dari HP saja"); acts.add { WpLib.delete(applicationContext, e.id); refreshLib() }
                    if (onSd(e.slot)) {
                        opts.add("Hapus dari kartu SD saja"); acts.add { espDelete(e.slot, null) }
                        opts.add("Hapus dari keduanya"); acts.add { espDelete(e.slot) { WpLib.delete(applicationContext, e.id); refreshLib() } }
                    }
                    AlertDialog.Builder(this).setTitle(e.name).setItems(opts.toTypedArray()) { _, w -> acts[w]() }.setNegativeButton("Batal", null).show()
                },
                {
                    val et = EditText(this).apply { setText(e.name); setSingleLine(true) }
                    AlertDialog.Builder(this).setTitle("Ganti nama").setView(et)
                        .setPositiveButton("Simpan") { _, _ -> WpLib.rename(applicationContext, e.id, et.text.toString()); refreshLib() }
                        .setNegativeButton("Batal", null).show()
                },
                if (e.kind == "video") ({ exportEntry(e) }) else null)
        }
        if (esp != null) {                                                  // wallpaper di kartu SD yang tidak ada di koleksi HP
            for (item in esp.items.filter { i -> local.none { l -> l.slot == i.slot } }) {
                val slot = item.slot
                addRow(null, "Di kartu SD (slot $slot)", kindName(item.kind) + " · " + sizeText(item.kb * 1024), esp.active == slot,
                    { espUse(slot, null, "Wallpaper slot $slot") },
                    {
                        AlertDialog.Builder(this).setMessage("Hapus wallpaper slot $slot dari kartu SD ESP32?")
                            .setPositiveButton("Hapus") { _, _ -> espDelete(slot, null) }.setNegativeButton("Batal", null).show()
                    }, null)
            }
        }
    }

    /** Satu baris koleksi (wallpaper maupun animasi boot): thumbnail, nama, info, tombol Pakai / .mnv / Hapus. */
    private fun libRow(box: LinearLayout, thumb: Bitmap?, name: String, info: String, on: Boolean, use: () -> Unit, del: (() -> Unit)?, ren: (() -> Unit)?, exp: (() -> Unit)? = null) {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
        r.addView(ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
            setBackgroundColor(0xFF000000.toInt())
            if (thumb != null) setImageBitmap(thumb)
            setOnClickListener { use() }
        })
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(10) }
        }
        col.addView(TextView(this).apply { text = (if (on) "✓ " else "") + name; textSize = 15f; if (ren != null) setOnClickListener { ren() } })
        col.addView(TextView(this).apply { text = info + (if (on) " · sedang dipakai" else ""); textSize = 11f })
        r.addView(col)
        r.addView(Button(this).apply { text = "Pakai"; isAllCaps = false; setOnClickListener { use() } })
        if (exp != null) r.addView(Button(this).apply { text = ".mnv"; isAllCaps = false; setOnClickListener { exp() } })
        if (del != null) r.addView(Button(this).apply { text = "Hapus"; isAllCaps = false; setOnClickListener { del() } })
        box.addView(r)
    }


    // ---------- koleksi animasi boot (firmware v22.30: /boot/bNNNNN.mnv di kartu SD) ----------
    private fun buildBootLibrary(p: LinearLayout) {
        p.addView(TextView(this).apply {
            text = "Ketuk Pakai untuk memilih animasi boot. Animasi yang SUDAH ada di kartu SD ESP32 (tanda \"ada di kartu SD\") langsung ganti tanpa kirim ulang; " +
                "yang belum akan dikirim dulu. \"Tanpa animasi\" = ESP32 langsung masuk tampilan biasa. " +
                "Pilihan juga bisa diganti di layar motor: Pengaturan > ANIMASI BOOT (tahan = berikutnya)."
            textSize = 12f
        })
        p.addView(row(button("Perbarui daftar kartu SD") { refreshEspBoot() }))
        tvBootLib = TextView(this).apply { textSize = 12f; setPadding(0, dp(4), 0, dp(4)) }
        p.addView(tvBootLib)
        bootLibBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        p.addView(bootLibBox)
        refreshBootLib()
    }

    private fun refreshBootLib() {
        if (!::bootLibBox.isInitialized) return
        bootLibBox.removeAllViews()
        val act = BootLib.active(applicationContext)
        val esp = espBoot
        fun onSd(slot: Int) = esp?.items?.any { it.slot == slot } == true
        fun addRow(thumb: Bitmap?, name: String, info: String, on: Boolean, use: () -> Unit, del: (() -> Unit)?, ren: (() -> Unit)?, exp: (() -> Unit)? = null) =
            libRow(bootLibBox, thumb, name, info, on, use, del, ren, exp)
        addRow(null, "Tanpa animasi", "ESP32 langsung masuk tampilan biasa", if (esp != null) esp.active == 0 else act == null, { espBootUse(0, null, "Tanpa animasi") }, null, null)
        val local = BootLib.list(applicationContext).sortedByDescending { it.at }
        for (e in local) {
            addRow(BootLib.thumb(applicationContext, e.id), e.name,
                "Video · " + sizeText(e.size) +
                    (if (esp != null) (if (onSd(e.slot)) " · ada di kartu SD" else " · belum di kartu SD") else ""),
                if (esp != null) esp.active == e.slot else act == e.id,
                { useBoot(e) },
                {
                    val opts = ArrayList<String>(); val acts = ArrayList<() -> Unit>()
                    opts.add("Hapus dari HP saja"); acts.add { BootLib.delete(applicationContext, e.id); refreshBootLib() }
                    if (onSd(e.slot)) {
                        opts.add("Hapus dari kartu SD saja"); acts.add { espBootDelete(e.slot, null) }
                        opts.add("Hapus dari keduanya"); acts.add { espBootDelete(e.slot) { BootLib.delete(applicationContext, e.id); refreshBootLib() } }
                    }
                    AlertDialog.Builder(this).setTitle(e.name).setItems(opts.toTypedArray()) { _, w -> acts[w]() }.setNegativeButton("Batal", null).show()
                },
                {
                    val et = EditText(this).apply { setText(e.name); setSingleLine(true) }
                    AlertDialog.Builder(this).setTitle("Ganti nama").setView(et)
                        .setPositiveButton("Simpan") { _, _ -> BootLib.rename(applicationContext, e.id, et.text.toString()); refreshBootLib() }
                        .setNegativeButton("Batal", null).show()
                },
                { exportBootEntry(e) })
        }
        if (esp != null) {                                                  // animasi di kartu SD yang tidak ada di koleksi HP
            for (item in esp.items.filter { i -> local.none { l -> l.slot == i.slot } }) {
                val slot = item.slot
                addRow(null, "Di kartu SD (slot $slot)", (if (item.kind == 2) "Video" else "Berkas rusak") + " · " + sizeText(item.kb * 1024), esp.active == slot,
                    { espBootUse(slot, null, "Animasi slot $slot") },
                    {
                        AlertDialog.Builder(this).setMessage("Hapus animasi boot slot $slot dari kartu SD ESP32?")
                            .setPositiveButton("Hapus") { _, _ -> espBootDelete(slot, null) }.setNegativeButton("Batal", null).show()
                    }, null)
            }
        }
    }

    private fun bootSdSummary(l: WpList): String {
        if (!l.sd) return "ESP32 melaporkan: kartu SD tidak terbaca."
        val used = l.items.sumOf { it.kb } / 1024
        return "Kartu SD ESP32: ${l.items.size} animasi boot, terpakai ±$used MB dari ${l.totalMb} MB."
    }

    /** Minta daftar animasi boot di kartu SD ESP32 lewat Bluetooth, lalu gambar ulang koleksi. */
    private fun refreshEspBoot() {
        if (!Core.ble.ready) { tvBootLib.text = "Sambungkan ESP32 (Bluetooth) dulu untuk melihat isi kartu SD."; return }
        tvBootLib.text = "Membaca isi kartu SD ESP32..."
        wpExec.execute {
            val l = try { Core.ble.listBoot() } catch (e: Exception) { null }
            runOnUiThread {
                if (l != null) { espBoot = l; tvBootLib.text = bootSdSummary(l) }
                else tvBootLib.text = "Daftar kartu SD tidak diterima (butuh firmware v22.30, Bluetooth tersambung, dan tidak sedang upload)."
                refreshBootLib()
            }
        }
    }

    /** Pilih animasi boot yang sudah ada di kartu SD (slot 0 = tanpa animasi). localId = id koleksi HP untuk penanda "dipakai". */
    private fun espBootUse(slot: Int, localId: String?, label: String) {
        if (!Core.ble.ready) { Toast.makeText(this, "Sambungkan ESP32 dulu (tombol 1).", Toast.LENGTH_LONG).show(); return }
        if (!wpStart(false)) return
        tvBootLib.text = "Mengganti animasi boot..."
        Thread {
            try {
                val err = try { Core.ble.selectBoot(slot) } catch (e: Exception) { "Galat: ${e.message}" }
                if (err == null) BootLib.setActive(applicationContext, localId)
                val l = try { Core.ble.listBoot() } catch (e: Exception) { null }
                runOnUiThread { if (l != null) espBoot = l; refreshBootLib() }
                bootEnd(if (err == null) (if (slot == 0) "Animasi boot dimatikan: ESP32 langsung masuk tampilan biasa." else "$label dipilih. Diputar saat ESP32 dinyalakan.") else "Gagal: $err")
            } catch (ex: Throwable) {
                bootEnd("Galat: ${ex.message}")
            }
        }.also { it.isDaemon = true }.start()
    }

    /** Hapus berkas animasi boot di kartu SD ESP32. after dijalankan (di thread UI) bila berhasil. */
    private fun espBootDelete(slot: Int, after: (() -> Unit)?) {
        if (!Core.ble.ready) { Toast.makeText(this, "Sambungkan ESP32 dulu (tombol 1).", Toast.LENGTH_LONG).show(); return }
        if (!wpStart(false)) return
        tvBootLib.text = "Menghapus di kartu SD..."
        Thread {
            try {
                val err = try { Core.ble.deleteBoot(slot) } catch (e: Exception) { "Galat: ${e.message}" }
                if (err == null && after != null) runOnUiThread { after() }
                val l = try { Core.ble.listBoot() } catch (e: Exception) { null }
                runOnUiThread { if (l != null) { espBoot = l }; refreshBootLib() }
                bootEnd(if (err == null) "Dihapus dari kartu SD." else "Gagal: $err")
            } catch (ex: Throwable) {
                bootEnd("Galat: ${ex.message}")
            }
        }.also { it.isDaemon = true }.start()
    }

    /** Pakai animasi dari koleksi HP: bila sudah ada di kartu SD cukup dipilih, kalau belum dikirim dulu. */
    private fun useBoot(e: BootLib.Entry) {
        if (!wpStart()) return
        tvBootLib.text = "Menyiapkan ${e.name}..."
        Thread {
            try {
                val file = BootLib.file(applicationContext, e.id)
                if (!file.exists()) { bootEnd("File animasi hilang."); return@Thread }
                var err: String? = null
                var done = false
                val viaWifi = cbWifi.isChecked
                if (!viaWifi && Core.ble.ready) {
                    val l = try { Core.ble.listBoot() } catch (ex: Exception) { null }
                    if (l != null && l.sd && l.items.any { it.slot == e.slot }) {
                        runOnUiThread { tvBootLib.text = "${e.name} sudah ada di kartu SD, mengganti..." }
                        err = Core.ble.selectBoot(e.slot)
                        done = true
                    }
                }
                if (!done) {
                    val t0 = System.currentTimeMillis()
                    err = sendImage(file, true, e.slot) { p ->
                        val el = (System.currentTimeMillis() - t0) / 1000
                        val eta = if (p > 2) el * (100 - p) / p else -1
                        runOnUiThread { tvBootLib.text = "Mengirim ${e.name}... $p%" + (if (eta >= 0) " (sisa ±${eta} dtk)" else "") }
                    }
                }
                if (err == null) BootLib.setActive(applicationContext, e.id)
                afterBootSend(viaWifi)
                bootEnd(if (err == null) "${e.name} dipilih. Diputar saat ESP32 dinyalakan." + (if (viaWifi) " (ESP32 restart, Bluetooth tersambung ulang sebentar lagi)" else "") else "Gagal: $err")
            } catch (ex: Throwable) {
                bootEnd("Galat: ${ex.message}")
            }
        }.also { it.isDaemon = true }.start()
    }

    /** Setelah kirim: lewat WiFi ESP32 restart (isi kartu tak diketahui lagi); lewat Bluetooth ambil daftar terbaru. */
    private fun afterBootSend(viaWifi: Boolean) {
        if (viaWifi || !Core.ble.ready) { runOnUiThread { espBoot = null; refreshBootLib() }; return }
        val l = try { Core.ble.listBoot() } catch (e: Exception) { null }
        runOnUiThread { if (l != null) espBoot = l; refreshBootLib() }
    }

    private fun exportBootEntry(e: BootLib.Entry) {
        val d = BootLib.file(applicationContext, e.id)
        if (!d.exists()) Toast.makeText(this, "File animasi hilang.", Toast.LENGTH_LONG).show()
        else exportMnv(d, "b%05d.mnv".format(e.slot), false)
    }

    private fun sizeText(bytes: Long): String =
        if (bytes >= 1024L * 1024L) "%.1f MB".format(bytes / 1048576.0) else "${bytes / 1024} KB"

    private fun sdSummary(l: WpList): String {
        if (!l.sd) return "ESP32 melaporkan: kartu SD tidak terbaca."
        val used = l.items.sumOf { it.kb } / 1024
        return "Kartu SD ESP32: ${l.items.size} wallpaper, terpakai ±$used MB dari ${l.totalMb} MB."
    }

    /** Minta daftar wallpaper di kartu SD ESP32 lewat Bluetooth, lalu gambar ulang koleksi. */
    private fun refreshEsp() {
        if (!Core.ble.ready) { tvLib.text = "Sambungkan ESP32 (Bluetooth) dulu untuk melihat isi kartu SD."; return }
        tvLib.text = "Membaca isi kartu SD ESP32..."
        wpExec.execute {
            val l = try { Core.ble.listWallpapers() } catch (e: Exception) { null }
            runOnUiThread {
                if (l != null) { espList = l; tvLib.text = sdSummary(l) }
                else tvLib.text = "Daftar kartu SD tidak diterima (butuh firmware v22.29, Bluetooth tersambung, dan tidak sedang upload)."
                refreshLib()
            }
        }
    }

    /** Ganti ke wallpaper yang sudah ada di kartu SD (slot 0 = foto bawaan). localId = id koleksi HP untuk penanda "dipakai". */
    private fun espUse(slot: Int, localId: String?, label: String) {
        if (!Core.ble.ready) { Toast.makeText(this, "Sambungkan ESP32 dulu (tombol 1).", Toast.LENGTH_LONG).show(); return }
        if (!wpStart(false)) return
        tvLib.text = "Mengganti wallpaper..."
        Thread {
            try {
                val err = try { Core.ble.selectWallpaper(slot) } catch (e: Exception) { "Galat: ${e.message}" }
                if (err == null) WpLib.setActive(applicationContext, localId)
                val l = try { Core.ble.listWallpapers() } catch (e: Exception) { null }
                runOnUiThread { if (l != null) espList = l; refreshLib() }
                wpEnd(if (err == null) "$label sekarang tampil di layar standby." else "Gagal: $err")
            } catch (ex: Throwable) {
                wpEnd("Galat: ${ex.message}")
            }
        }.also { it.isDaemon = true }.start()
    }

    /** Hapus berkas wallpaper di kartu SD ESP32. after dijalankan (di thread UI) bila berhasil. */
    private fun espDelete(slot: Int, after: (() -> Unit)?) {
        if (!Core.ble.ready) { Toast.makeText(this, "Sambungkan ESP32 dulu (tombol 1).", Toast.LENGTH_LONG).show(); return }
        if (!wpStart(false)) return
        tvLib.text = "Menghapus di kartu SD..."
        Thread {
            try {
                val err = try { Core.ble.deleteWallpaper(slot) } catch (e: Exception) { "Galat: ${e.message}" }
                if (err == null && after != null) runOnUiThread { after() }
                val l = try { Core.ble.listWallpapers() } catch (e: Exception) { null }
                runOnUiThread { if (l != null) { espList = l }; refreshLib() }
                wpEnd(if (err == null) "Dihapus dari kartu SD." else "Gagal: $err")
            } catch (ex: Throwable) {
                wpEnd("Galat: ${ex.message}")
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun useWallpaper(e: WpLib.Entry) {
        if (!wpStart()) return
        tvLib.text = "Menyiapkan ${e.name}..."
        Thread {
            try {
                val file = WpLib.file(applicationContext, e.id)
                if (!file.exists()) { wpEnd("File wallpaper hilang."); return@Thread }
                var err: String? = null
                var done = false
                val viaWifi = cbWifi.isChecked
                if (!viaWifi && Core.ble.ready) {                           // sudah ada di kartu SD? kalau ya cukup pilih, tanpa kirim ulang
                    val l = try { Core.ble.listWallpapers() } catch (ex: Exception) { null }
                    if (l != null && l.sd && l.items.any { it.slot == e.slot }) {
                        runOnUiThread { tvLib.text = "${e.name} sudah ada di kartu SD, mengganti..." }
                        err = Core.ble.selectWallpaper(e.slot)
                        done = true
                    }
                }
                if (!done) {
                    val t0 = System.currentTimeMillis()
                    err = sendImage(file, false, e.slot) { p ->
                        val el = (System.currentTimeMillis() - t0) / 1000
                        val eta = if (p > 2) el * (100 - p) / p else -1
                        runOnUiThread { tvLib.text = "Mengirim ${e.name}... $p%" + (if (eta >= 0) " (sisa ±${eta} dtk)" else "") }
                    }
                }
                if (err == null) WpLib.setActive(applicationContext, e.id)
                afterSend(viaWifi)
                wpEnd(if (err == null) "${e.name} sekarang tampil di layar standby." + (if (viaWifi) " (ESP32 restart, Bluetooth tersambung ulang sebentar lagi)" else "") else "Gagal: $err")
            } catch (ex: Throwable) {
                wpEnd("Galat: ${ex.message}")
            }
        }.also { it.isDaemon = true }.start()
    }

    /** Setelah kirim: lewat WiFi ESP32 restart (isi kartu tak diketahui lagi); lewat Bluetooth ambil daftar terbaru. */
    private fun afterSend(viaWifi: Boolean) {
        if (viaWifi || !Core.ble.ready) { runOnUiThread { espList = null; refreshLib() }; return }
        val l = try { Core.ble.listWallpapers() } catch (e: Exception) { null }
        runOnUiThread { if (l != null) espList = l; refreshLib() }
    }

    private fun refreshPlayer() {
        if (!::tvNow.isInitialized) return
        Music.readNow(applicationContext)
        val has = Music.npTitle.isNotEmpty()
        tvNow.text = if (has) (if (Music.npPlaying) "▶ " else "⏸ ") + Music.npTitle + (if (Music.npArtist.isNotEmpty()) " - " + Music.npArtist else "")
                     else "Tidak ada lagu diputar (atau akses notifikasi belum diberikan)"
        btnPlay.text = if (Music.npPlaying) "Jeda" else "Putar"
        if (!seeking) {
            val pos = Music.posNow()
            sbSong.progress = if (Music.npDur > 0) (pos * 1000 / Music.npDur).toInt() else 0
            tvPos.text = fmt(pos) + (if (Music.npDur > 0) " / " + fmt(Music.npDur) else "")
        }
    }


    // ---------- wallpaper ----------
    private fun pickMedia() {
        if (wpBusy) return
        val i = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try { startActivityForResult(Intent.createChooser(i, "Pilih foto/video wallpaper"), 77) }
        catch (e: Exception) { Toast.makeText(this, "Tidak ada aplikasi galeri.", Toast.LENGTH_LONG).show() }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        if (requestCode == 78) { data?.data?.let { bootPicked(it) }; return }
        if (requestCode == 80) { data?.data?.let { fwPicked(it) }; return }
        if (requestCode == 79) {
            val u = data?.data
            val b = pendingExport
            val tmp = pendingExportTemp
            pendingExport = null
            if (u != null && b != null) wpExec.execute {
                val msg = try {
                    contentResolver.openOutputStream(u)?.use { o -> java.io.FileInputStream(b).use { it.copyTo(o, 256 * 1024) } }
                    "Tersimpan. Salin ke kartu SD: wallpaper -> folder /wp (nama w#####.mnv); animasi boot -> folder /boot (nama b#####.mnv). Berkas root wallpaper.mnv / bootanim.mnv lama juga masih dipindah otomatis."
                } catch (e: Exception) { "Gagal menyimpan: ${e.message}" }
                if (tmp) b.delete()
                runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
            } else if (tmp && b != null) b.delete()
            return
        }
        if (requestCode != 77) return
        val uri: Uri = data?.data ?: return
        tvWp.text = "Membaca file..."
        wpEnable(false)
        wpExec.execute {
            val app = applicationContext
            try {
                if (Wallpaper.isVideo(app, uri)) {
                    val v = Wallpaper.openVideo(app, uri)
                    val smp = if (v != null) Wallpaper.sampleFrames(v, 5) else emptyList()
                    runOnUiThread {
                        if (v == null || smp.isEmpty()) { tvWp.text = "Video tidak bisa dibaca."; return@runOnUiThread }
                        try { wpVideo?.r?.release() } catch (e: Exception) {}
                        wpVideo = v; wpSamples = smp; wpPhoto = null; wpImage = null
                        videoBox.visibility = View.VISIBLE
                        sbZoom.progress = 0; sbPanX.progress = 100; sbPanY.progress = 100
                        wpRender()
                    }
                } else {
                    val b = Wallpaper.loadPhoto(app, uri)
                    runOnUiThread {
                        if (b == null) { tvWp.text = "Foto tidak bisa dibaca."; return@runOnUiThread }
                        wpPhoto = b; wpVideo = null; wpSamples = emptyList()
                        videoBox.visibility = View.GONE
                        sbZoom.progress = 0; sbPanX.progress = 100; sbPanY.progress = 100
                        wpRender()
                    }
                }
            } catch (e: Throwable) {
                runOnUiThread { tvWp.text = "Gagal membaca: ${e.message}" }
            }
        }
    }

    private fun look() = Wallpaper.Look(
        sbDim.progress, 1f + sbZoom.progress / 10f, (sbPanX.progress - 100) / 100f, (sbPanY.progress - 100) / 100f
    )
    private fun vopts() = Wallpaper.VideoOpts(look(), 5 + sbFps.progress, 30 + sbQual.progress, cbAuto.isChecked)

    private fun wpRender() {
        val photo = wpPhoto
        val smp = wpSamples
        if (photo == null && smp.isEmpty()) return
        val gen = ++wpGen
        val lk = look()
        val vo = vopts()
        wpExec.execute {
            if (gen != wpGen) return@execute                       // sudah ada pengaturan yang lebih baru
            try {
                if (photo != null) {
                    val r = Wallpaper.preparePhoto(photo, lk)
                    runOnUiThread {
                        if (gen != wpGen) return@runOnUiThread
                        wpImage = r.first
                        wpThumb = r.second
                        ivWp.setImageBitmap(r.second)
                        tvWpInfo.text = ""
                        wpEnable(!wpBusy)
                        tvWp.text = "Foto siap dikirim (${photo.width}x${photo.height} -> 240x240)."
                    }
                } else {
                    val pv = Wallpaper.previewOnly(smp[0], lk)
                    val s = Wallpaper.frameSize(smp, lk, vo.quality)
                    val dur = wpVideo?.durSec ?: 0.0
                    val nFit = if (s > 0) (Wallpaper.BUDGET / (s * 1.05)).toInt() else 0
                    val info = if (vo.auto) {
                        val fps = maxOf(6, minOf(vo.fps, (nFit / dur).toInt()))
                        val sec = minOf(dur, nFit.toDouble() / fps)
                        "Perkiraan: ${"%.1f".format(sec)} dtk @ $fps fps, ±${(s / 1024).toInt()} KB/frame" +
                            (if (sec < dur - 0.05) " (video ${"%.1f".format(dur)} dtk akan dipotong)" else " (seluruh video muat)")
                    } else {
                        val sec = minOf(dur, nFit.toDouble() / vo.fps)
                        "Perkiraan: muat ${"%.1f".format(sec)} dtk dari ${"%.1f".format(dur)} dtk @ ${vo.fps} fps, ±${(s / 1024).toInt()} KB/frame" +
                            (if (sec < dur - 0.05) " (akan dipotong dari awal)" else "")
                    }
                    runOnUiThread {
                        if (gen != wpGen) return@runOnUiThread
                        wpThumb = pv
                        ivWp.setImageBitmap(pv)
                        tvWpInfo.text = info
                        wpEnable(!wpBusy)
                        tvWp.text = "Video siap diproses & dikirim (${"%.1f".format(dur)} dtk)."
                    }
                }
            } catch (e: Throwable) {
                runOnUiThread { tvWp.text = "Gagal memproses: ${e.message}" }
            }
        }
    }

    /** Kirim citra ke ESP32 lewat WiFi (bila dicentang) atau Bluetooth. null = sukses. */
    private fun sendImage(file: java.io.File, boot: Boolean, slot: Int, progress: (Int) -> Unit): String? =
        if (cbWifi.isChecked) WifiUp.upload(applicationContext, file, boot, slot, { s -> runOnUiThread { if (boot) { tvBoot.text = s; tvBootLib.text = s } else { tvWp.text = s; tvLib.text = s } } }, progress)
        else Core.ble.uploadWallpaper(file, boot, slot, progress)

    /** Simpan citra video sebagai berkas .mnv (salin ke root kartu SD: wallpaper.mnv / bootanim.mnv). */
    private fun exportMnv(data: java.io.File, fileName: String, temp: Boolean) {
        pendingExport = data
        pendingExportTemp = temp
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/octet-stream"; putExtra(Intent.EXTRA_TITLE, fileName)
        }
        try { startActivityForResult(i, 79) } catch (e: Exception) { Toast.makeText(this, "Tidak bisa membuka penyimpanan.", Toast.LENGTH_LONG).show() }
    }

    private fun exportEntry(e: WpLib.Entry) {
        val d = WpLib.file(applicationContext, e.id)
        if (!d.exists()) Toast.makeText(this, "File wallpaper hilang.", Toast.LENGTH_LONG).show()
        else exportMnv(d, "w%05d.mnv".format(e.slot), false)
    }

    private fun bootExport() {
        val vid = bootVideo
        if (vid == null) { Toast.makeText(this, "Pilih video animasi boot dulu.", Toast.LENGTH_LONG).show(); return }
        if (!wpStart(false)) return
        val smp = bootSamples
        Thread {
            try {
                val vo = Wallpaper.VideoOpts(bootLook(), bootFps, bootQuality, true, Wallpaper.BOOT_CAP)
                val res = Wallpaper.encodeVideo(vid, smp, vo, WpLib.tmpDir(applicationContext), { i, n -> runOnUiThread { tvBoot.text = "Memproses video: frame $i/$n..." } }, { wpCancel })
                if (res == null) { bootEnd(if (wpCancel) "Dibatalkan." else "Video gagal diproses."); return@Thread }
                bootEnd("Siap disimpan: ${res.frames} frame @ ${res.fps} fps, ${sizeText(res.size)}.")
                val nm = "b%05d.mnv".format(Wallpaper.slotOf(Wallpaper.crcOf(res.file)))
                runOnUiThread { exportMnv(res.file, nm, true) }
            } catch (e: Throwable) {
                bootEnd("Galat: ${e.message}")
            }
        }.also { it.isDaemon = true }.start()
    }

    // ---------- update firmware ESP32 (OTA) ----------
    @Suppress("DEPRECATION")
    private fun fwPick() {
        if (fwBusy) return
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
        try { startActivityForResult(i, 80) }
        catch (e: Exception) { Toast.makeText(this, "Tidak ada aplikasi pemilih berkas.", Toast.LENGTH_LONG).show() }
    }

    private fun fwPicked(uri: Uri) {
        if (fwBusy || wpBusy) return
        tvFw.text = "Membaca berkas..."
        btnFwSend.isEnabled = false
        wpExec.execute {
            try {
                var name = "firmware.bin"
                var sz = -1L
                contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val ni = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val si = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (ni >= 0) name = c.getString(ni) ?: name
                        if (si >= 0 && !c.isNull(si)) sz = c.getLong(si)
                    }
                }
                if (sz > 4L * 1024 * 1024) { runOnUiThread { tvFw.text = "Berkas terlalu besar (${sizeText(sz)}) untuk firmware ESP32. Pilih berkas .ino.bin." }; return@execute }
                val f = java.io.File(cacheDir, "firmware_ota.bin")
                var total = 0L
                contentResolver.openInputStream(uri)!!.use { ins ->
                    f.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val k = ins.read(buf)
                            if (k < 0) break
                            total += k
                            if (total > 4L * 1024 * 1024) throw java.io.IOException("Berkas terlalu besar untuk firmware ESP32.")
                            out.write(buf, 0, k)
                        }
                    }
                }
                val h = ByteArray(36)
                val got = f.inputStream().use { it.read(h) }
                val m = (h[32].toLong() and 255) or ((h[33].toLong() and 255) shl 8) or ((h[34].toLong() and 255) shl 16) or ((h[35].toLong() and 255) shl 24)
                if (f.length() < 65536 || got < 36 || (h[0].toInt() and 255) != 0xE9 || m != 0xABCD5432L) {
                    runOnUiThread { fwFile = null; tvFw.text = "\"$name\" bukan berkas program ESP32. Pilih hasil Export Compiled Binary yang berakhiran .ino.bin (bukan .bootloader / .partitions / .merged.bin)." }
                    return@execute
                }
                fwFile = f; fwName = name
                runOnUiThread { tvFw.text = "Berkas: $name (${sizeText(f.length())}). Siap dikirim."; pbFw.progress = 0; btnFwSend.isEnabled = true }
            } catch (e: Exception) {
                runOnUiThread { fwFile = null; tvFw.text = "Gagal membaca berkas: ${e.message}" }
            }
        }
    }

    private fun fwCheck() {
        if (fwBusy || wpBusy) return
        if (!Core.ble.ready) { Toast.makeText(this, "Sambungkan ESP32 dulu (tombol 1).", Toast.LENGTH_LONG).show(); return }
        tvFw.text = "Menanyakan versi ke ESP32..."
        wpExec.execute {
            val i = Core.ble.fwInfo()
            val msg = when {
                i == null -> "ESP32 tidak membalas. Firmware di ESP32 masih sebelum v22.31 (belum bisa OTA): flash v22.31 + partitions.csv baru lewat kabel USB sekali."
                !i.ota -> "Firmware ${i.version}, tetapi tabel partisi belum punya slot program kedua. Flash ulang lewat kabel USB dengan partitions.csv dari paket v22.31."
                else -> "Firmware ${i.version}. OTA siap; ukuran slot ${sizeText(i.maxSize)}."
            }
            runOnUiThread { tvFw.text = msg }
        }
    }

    private fun fwSend() {
        val f = fwFile
        if (f == null || !f.exists()) { Toast.makeText(this, "Pilih berkas .bin firmware dulu.", Toast.LENGTH_LONG).show(); return }
        if (fwBusy || wpBusy) return
        if (!Core.ble.ready) { Toast.makeText(this, "Sambungkan ESP32 dulu (tombol 1).", Toast.LENGTH_LONG).show(); return }
        AlertDialog.Builder(this)
            .setTitle("Update firmware ESP32")
            .setMessage("Kirim \"$fwName\" (${sizeText(f.length())}) ke ESP32?\n\nButuh beberapa menit. Jaga HP dekat ESP32, jangan tutup aplikasi dan jangan matikan daya motor. " +
                "Bila gagal di tengah jalan, program lama tetap utuh. Setelah selesai ESP32 restart sendiri.")
            .setPositiveButton("Kirim") { _, _ -> fwRun(f) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun fwRun(f: java.io.File) {
        if (fwBusy || wpBusy) return
        fwBusy = true; wpBusy = true; Core.ble.cancelUpload = false
        btnFwSend.isEnabled = false; btnFwCancel.isEnabled = true
        pbFw.progress = 0
        tvFw.text = "Memeriksa ESP32..."
        wpExec.execute {
            val info = Core.ble.fwInfo()
            val err: String? = when {
                info == null -> "ESP32 tidak membalas. Firmware di ESP32 masih sebelum v22.31 (belum bisa OTA): flash v22.31 + partitions.csv baru lewat kabel USB sekali."
                !info.ota -> "Tabel partisi ESP32 belum punya slot program kedua. Flash ulang lewat kabel USB dengan partitions.csv dari paket v22.31."
                f.length() > info.maxSize -> "Berkas ${sizeText(f.length())} lebih besar dari slot program ESP32 (${sizeText(info.maxSize)}). Kecilkan program atau pakai partitions_app_besar.csv."
                else -> Core.ble.uploadFirmware(f,
                    { p -> runOnUiThread { pbFw.progress = p; tvFw.text = "Mengirim firmware... $p%" } },
                    { st -> runOnUiThread { tvFw.text = st } })
            }
            runOnUiThread {
                fwBusy = false; wpBusy = false
                btnFwCancel.isEnabled = false
                btnFwSend.isEnabled = fwFile != null
                if (err == null) {
                    pbFw.progress = 100
                    tvFw.text = "Berhasil dikirim dan diverifikasi. ESP32 restart dengan firmware baru; Bluetooth tersambung ulang sebentar lagi." +
                        (if (info != null) " (sebelumnya ${info.version})" else "")
                } else {
                    tvFw.text = "Gagal: $err"
                }
            }
        }
    }

    private fun wpEnable(b: Boolean) { btnWpSend.isEnabled = b; btnWpSave.isEnabled = b }

    private fun wpStart(needBle: Boolean = true): Boolean {
        if (wpBusy) return false
        if (needBle && !cbWifi.isChecked && !Core.ble.ready) { Toast.makeText(this, "Sambungkan ESP32 dulu (tombol 1).", Toast.LENGTH_LONG).show(); return false }
        wpBusy = true; wpCancel = false; Core.ble.cancelUpload = false; WifiUp.cancel = false
        wpEnable(false); btnWpCancel.isEnabled = true
        return true
    }

    private fun wpEnd(msg: String) {
        runOnUiThread {
            wpBusy = false
            tvWp.text = msg
            tvLib.text = msg
            btnWpCancel.isEnabled = false
            wpEnable(wpPhoto != null || wpSamples.isNotEmpty())
        }
    }

    private fun wpSend(upload: Boolean = true) {
        if (!wpStart(upload)) return
        val thumb = wpThumb
        val photoImg = wpImage
        val vid = wpVideo
        val smp = wpSamples
        val vo = vopts()
        Thread {
            try {
                val app = applicationContext
                var f: java.io.File? = null
                var what = "Foto"
                if (vid != null) {
                    what = "Video"
                    val res = Wallpaper.encodeVideo(vid, smp, vo, WpLib.tmpDir(app), { i, n ->
                        runOnUiThread { tvWp.text = "Memproses video: frame $i/$n..." }
                    }, { wpCancel })
                    if (res == null) { wpEnd(if (wpCancel) "Dibatalkan." else "Video gagal diproses."); return@Thread }
                    f = res.file
                    runOnUiThread {
                        tvWpInfo.text = "Hasil: ${res.frames} frame @ ${res.fps} fps = ${"%.1f".format(res.seconds)} dtk, ${sizeText(res.size)}" +
                            (if (res.trimmed) " (dipotong agar muat)" else "")
                    }
                } else if (photoImg != null) {
                    f = Wallpaper.toTemp(WpLib.tmpDir(app), photoImg)
                }
                if (f == null) { wpEnd("Belum ada foto/video."); return@Thread }
                val label = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                val entry = WpLib.add(app, f, thumb, "$what $label", if (vid != null) "video" else "photo")   // otomatis masuk koleksi (berkas dipindah)
                runOnUiThread { refreshLib() }
                if (!upload) { wpEnd("Disimpan ke koleksi: ${entry.name}. Pilih di daftar untuk mengirim."); return@Thread }
                val viaWifi = cbWifi.isChecked
                val t0 = System.currentTimeMillis()
                val err = sendImage(WpLib.file(app, entry.id), false, entry.slot) { p ->
                    val el = (System.currentTimeMillis() - t0) / 1000
                    val eta = if (p > 2) el * (100 - p) / p else -1
                    runOnUiThread { tvWp.text = "Mengirim $what... $p%" + (if (eta >= 0) " (sisa ±${eta} dtk)" else "") }
                }
                if (err == null) WpLib.setActive(app, entry.id)
                afterSend(viaWifi)
                wpEnd(if (err == null) "Selesai. Wallpaper tampil di layar standby." + (if (viaWifi) " (ESP32 restart)" else "") else "Gagal: $err")
            } catch (e: Throwable) {
                wpEnd("Galat: ${e.message}")
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun wpClear() = espUse(0, null, "Foto bawaan")

    // ---------- animasi boot ----------
    private val bootFps = 30
    private val bootQuality = 60
    private fun bootLook() = Wallpaper.Look(0, 1f, 0f, 0f, true)

    private fun pickBootVideo() {
        if (wpBusy) return
        val i = Intent(Intent.ACTION_GET_CONTENT).apply { type = "video/*"; addCategory(Intent.CATEGORY_OPENABLE) }
        try { startActivityForResult(Intent.createChooser(i, "Pilih video animasi boot"), 78) }
        catch (e: Exception) { Toast.makeText(this, "Tidak ada aplikasi galeri.", Toast.LENGTH_LONG).show() }
    }

    private fun bootPicked(uri: Uri) {
        tvBoot.text = "Membaca video..."
        btnBootSend.isEnabled = false
        btnBootSave.isEnabled = false
        wpExec.execute {
            try {
                val app = applicationContext
                val v = Wallpaper.openVideo(app, uri)
                val smp = if (v != null) Wallpaper.sampleFrames(v, 5) else emptyList()
                if (v == null || smp.isEmpty()) { runOnUiThread { tvBoot.text = "Video tidak bisa dibaca." }; return@execute }
                val lk = bootLook()
                val pv = Wallpaper.previewOnly(smp[0], lk)
                val s = Wallpaper.frameSize(smp, lk, bootQuality)
                val nFit = if (s > 0) (Wallpaper.budgetFor(Wallpaper.BOOT_CAP) / (s * 1.05)).toInt() else 0
                val dur = v.durSec
                val fps = maxOf(6, minOf(bootFps, (nFit / dur).toInt()))
                val sec = minOf(dur, nFit.toDouble() / fps)
                val info = "Video ${"%.1f".format(dur)} dtk. Perkiraan animasi: ${"%.1f".format(sec)} dtk @ $fps fps" +
                    (if (sec < dur - 0.05) " (akan dipotong dari akhir, pilih video lebih pendek)" else " (seluruh video muat)")
                runOnUiThread {
                    try { bootVideo?.r?.release() } catch (e: Exception) {}
                    bootVideo = v; bootSamples = smp; bootThumb = pv
                    ivBoot.setImageBitmap(pv)
                    tvBoot.text = info
                    btnBootSend.isEnabled = !wpBusy
                    btnBootSave.isEnabled = !wpBusy
                }
            } catch (e: Throwable) {
                runOnUiThread { tvBoot.text = "Gagal membaca: ${e.message}" }
            }
        }
    }

    private fun bootEnd(msg: String) {
        runOnUiThread {
            wpBusy = false
            tvBoot.text = msg
            tvBootLib.text = msg
            btnWpCancel.isEnabled = false
            wpEnable(wpPhoto != null || wpSamples.isNotEmpty())
            btnBootSend.isEnabled = bootVideo != null
            btnBootSave.isEnabled = bootVideo != null
        }
    }

    private fun bootSend(upload: Boolean = true) {
        val vid = bootVideo ?: return
        if (!wpStart(upload)) return
        btnBootSend.isEnabled = false
        btnBootSave.isEnabled = false
        val smp = bootSamples
        val thumb = bootThumb
        Thread {
            try {
                val app = applicationContext
                val vo = Wallpaper.VideoOpts(bootLook(), bootFps, bootQuality, true, Wallpaper.BOOT_CAP)
                val res = Wallpaper.encodeVideo(vid, smp, vo, WpLib.tmpDir(app), { i, n ->
                    runOnUiThread { tvBoot.text = "Memproses video: frame $i/$n..." }
                }, { wpCancel })
                if (res == null) { bootEnd(if (wpCancel) "Dibatalkan." else "Video gagal diproses."); return@Thread }
                val label = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                val entry = BootLib.add(app, res.file, thumb, "Animasi $label", "video")   // otomatis masuk koleksi (berkas dipindah)
                runOnUiThread { refreshBootLib() }
                val info = "${res.frames} frame @ ${res.fps} fps = ${"%.1f".format(res.seconds)} dtk, ${sizeText(res.size)}"
                if (!upload) { bootEnd("Disimpan ke koleksi: ${entry.name} ($info). Ketuk Pakai di daftar untuk mengirim."); return@Thread }
                val viaWifi = cbWifi.isChecked
                val t0 = System.currentTimeMillis()
                val err = sendImage(BootLib.file(app, entry.id), true, entry.slot) { p ->
                    val el = (System.currentTimeMillis() - t0) / 1000
                    val eta = if (p > 2) el * (100 - p) / p else -1
                    runOnUiThread { tvBoot.text = "Mengirim animasi boot... $p%" + (if (eta >= 0) " (sisa ±${eta} dtk)" else "") }
                }
                if (err == null) BootLib.setActive(app, entry.id)
                afterBootSend(viaWifi)
                bootEnd(
                    if (err == null) "Selesai: $info. Diputar saat ESP32 dinyalakan." + (if (viaWifi) " (ESP32 restart)" else "")
                    else "Gagal: $err"
                )
            } catch (e: Throwable) {
                bootEnd("Galat: ${e.message}")
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
        refreshNotifState()
        val s = pendingShare ?: return
        if (missing().isEmpty()) { pendingShare = null; processShare(s) }
    }

    override fun onResume() {
        super.onResume()
        Core.listener = this
        refreshNotifState()
        val r = object : Runnable {
            override fun run() { try { refreshPlayer() } catch (e: Exception) {}; ui.postDelayed(this, 1000) }
        }
        tick = r
        ui.post(r)
        try {
            if (listenerOn()) android.service.notification.NotificationListenerService.requestRebind(
                android.content.ComponentName(this, NotifService::class.java))
        } catch (e: Exception) {}
    }

    override fun onPause() {
        tick?.let { ui.removeCallbacks(it) }
        Core.listener = null
        if (::motorBox.isInitialized) motorUnlock(false)   // menu Motor terkunci lagi saat aplikasi ditinggalkan
        super.onPause()
    }

    override fun onStatus(text: String) {
        tvStatus.text = text
        if (text.startsWith("Tersambung") && ::libBox.isInitialized && !wpBusy) ui.postDelayed({ if (!wpBusy) refreshEsp() }, 2500)   // ambil isi kartu SD ESP32 otomatis
        if (text.startsWith("Tersambung") && ::bootLibBox.isInitialized && !wpBusy) ui.postDelayed({ if (!wpBusy) refreshEspBoot() }, 2600)   // idem untuk animasi boot (wpExec satu thread: berurutan)
    }
    override fun onPreview(b: Bitmap) { ivPreview.setImageBitmap(b) }

    // ---------- notifikasi & telepon ----------
    private fun listenerOn(): Boolean {
        val f = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return f.contains(packageName)
    }

    private fun phonePerms(): List<String> =
        listOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS, Manifest.permission.ANSWER_PHONE_CALLS)

    private fun refreshNotifState() {
        val l = listenerOn()
        val ph = phonePerms().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        tvNotif.text = (if (l) "Akses notifikasi: AKTIF\n" else "Akses notifikasi: belum diberikan (tekan tombol 1)\n") +
            (if (ph) "Izin telepon: AKTIF" else "Izin telepon: belum lengkap (tekan tombol 2)") +
            "\n" + Music.vizStatus +
            (if (!l && Build.VERSION.SDK_INT >= 33) "\nCatatan Android 13+: bila pilihan Nav160 abu-abu, buka Info aplikasi Nav160 > menu titik tiga > Izinkan setelan terbatas, lalu ulangi." else "")
    }

    private fun askPhonePerms() {
        val m = phonePerms().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (m.isEmpty()) { Toast.makeText(this, "Izin telepon sudah lengkap.", Toast.LENGTH_SHORT).show(); return }
        requestPermissions(m.toTypedArray(), 2)
    }

    private fun pickNotifApps() {
        val seen = Notify.seen(applicationContext)
        if (seen.isEmpty()) {
            Toast.makeText(this, "Belum ada aplikasi yang terbaca. Kirim satu notifikasi dulu (mis. chat), lalu coba lagi.", Toast.LENGTH_LONG).show()
            return
        }
        val pk = seen.keys.sortedBy { seen[it]!!.lowercase() }
        val names = pk.map { seen[it]!! }.toTypedArray()
        val blocked = HashSet(Notify.blocked(applicationContext))
        val checked = BooleanArray(pk.size) { pk[it] !in blocked }
        AlertDialog.Builder(this)
            .setTitle("Aplikasi yang boleh tampil")
            .setMultiChoiceItems(names, checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("Simpan") { _, _ ->
                val nb = HashSet<String>()
                for (i in pk.indices) if (!checked[i]) nb.add(pk[i])
                Notify.setBlocked(applicationContext, nb)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun pickPriorityApps() {
        val seen = Notify.seen(applicationContext)
        if (seen.isEmpty()) {
            Toast.makeText(this, "Belum ada aplikasi yang terbaca. Kirim satu notifikasi dulu, lalu coba lagi.", Toast.LENGTH_LONG).show()
            return
        }
        val pk = seen.keys.sortedBy { seen[it]!!.lowercase() }
        val names = pk.map { seen[it]!! }.toTypedArray()
        val checked = BooleanArray(pk.size) { Notify.isPriority(applicationContext, pk[it]) }
        AlertDialog.Builder(this)
            .setTitle("Boleh tampil saat berkendara")
            .setMultiChoiceItems(names, checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("Simpan") { _, _ ->
                val ns = HashSet<String>()
                for (i in pk.indices) if (checked[i]) ns.add(pk[i])
                Notify.setDrivePri(applicationContext, ns)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun testNotif(call: Boolean) {
        if (Core.bleIfReady() == null) {
            Toast.makeText(this, "Sambungkan ESP32 dulu (tombol 1. Sambungkan ESP32).", Toast.LENGTH_LONG).show()
            return
        }
        if (call) Notify.testCall() else Notify.test(applicationContext)
    }

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
