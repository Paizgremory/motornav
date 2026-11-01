package com.motornav.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
    private lateinit var tvLib: TextView
    private lateinit var libBox: LinearLayout
    @Volatile private var wpGen = 0
    @Volatile private var wpCancel = false
    @Volatile private var wpBusy = false

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

        var root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(24))
        }
        val top = root
        root.addView(TextView(this).apply { text = "MotorNav"; textSize = 24f })
        root.addView(TextView(this).apply {
            text = "Navigasi ke layar motor"
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

        // ---------- tab: Navigasi | Musik | Layar | Notifikasi ----------
        fun newPage() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pageNav = newPage(); val pageMusic = newPage(); val pageScreen = newPage(); val pageNotif = newPage()
        val pages = listOf(pageNav, pageMusic, pageScreen, pageNotif)
        val tabBtns = ArrayList<Button>()
        fun showTab(i: Int) {
            pages.forEachIndexed { k, pg -> pg.visibility = if (k == i) View.VISIBLE else View.GONE }
            tabBtns.forEachIndexed { k, b -> b.alpha = if (k == i) 1f else 0.45f }
        }
        listOf("Navigasi", "Musik", "Layar", "Notifikasi").forEachIndexed { k, n ->
            tabBtns.add(button(n) { showTab(k) }.apply { textSize = 12f })
        }
        root.addView(row(*tabBtns.toTypedArray()))
        pages.forEach { root.addView(it) }
        showTab(0)
        buildMusicPage(pageMusic)
        root = pageNav

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
        root = pageNotif
        // ---------- Notifikasi & telepon ke layar motor ----------
        root.addView(TextView(this).apply { text = "Notifikasi & telepon di layar motor"; textSize = 18f; setPadding(0, dp(18), 0, dp(4)) })
        root.addView(TextView(this).apply {
            text = "Notifikasi HP tampil di layar ESP32 lengkap dengan logo aplikasi, nama pengirim, dan isi pesan yang bergulir otomatis bila panjang. " +
                "Telepon masuk tampil dengan nama kontak. Butuh firmware v22.15 dan MotorNav tersambung ke ESP32."
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
        root.addView(TextView(this).apply { text = "Tambah wallpaper baru (foto / video)"; textSize = 18f; setPadding(0, dp(18), 0, dp(4)) })
        root.addView(TextView(this).apply {
            text = "Pilih foto ATAU video, atur, lalu Kirim (otomatis masuk Koleksi di atas) atau Simpan saja. Layar fisik 240x240: gambar dibaca dari sumber asli, diperkecil halus, dipertajam; foto diberi dithering agar gradasi tidak berpita."
            textSize = 12f
        })
        ivWp = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(220), dp(220)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8) }
            setBackgroundColor(0xFF000000.toInt())
        }
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
        sbFps = seek("FPS (5-30). Makin tinggi makin halus tapi makin pendek durasi yang muat", 25, 10, videoBox)
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
        btnWpSend = button("Kirim ke ESP32") { wpSend(true) }
        btnWpSave = button("Simpan saja") { wpSend(false) }
        btnWpCancel = button("Batal") { wpCancel = true; Core.ble.cancelUpload = true }
        btnWpCancel.isEnabled = false
        root.addView(row(button("Pilih foto/video") { pickMedia() }, btnWpSend, btnWpSave, btnWpCancel))
        wpEnable(false)
        root.addView(row(button("Hapus wallpaper (kembali bawaan)") { wpClear() }))

        // ---------- Animasi boot: video yang diputar sekali tiap ESP32 dinyalakan (butuh firmware v22.25) ----------
        root.addView(TextView(this).apply { text = "Animasi boot (video saat dinyalakan)"; textSize = 18f; setPadding(0, dp(24), 0, dp(4)) })
        root.addView(TextView(this).apply {
            text = "Pilih video lalu Kirim. Video diputar sekali di layar motor setiap kali ESP32 dinyalakan, sebelum masuk tampilan biasa " +
                "(sentuh sensor = lewati). Bagian tengah video dipotong persegi. Slot sekitar 0,5 MB: video 2-4 dtk paling pas, yang lebih panjang " +
                "FPS-nya diturunkan otomatis. Butuh firmware v22.26 dan partitions.csv baru (flash ulang penuh)."
            textSize = 12f
        })
        ivBoot = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(160), dp(160)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8) }
            setBackgroundColor(0xFF000000.toInt())
        }
        root.addView(ivBoot)
        tvBoot = TextView(this).apply { text = "Belum ada video dipilih."; textSize = 13f; setPadding(0, dp(6), 0, dp(4)) }
        root.addView(tvBoot)
        btnBootSend = button("Kirim animasi boot") { bootSend() }
        btnBootSend.isEnabled = false
        root.addView(row(
            button("Pilih video") { pickBootVideo() }, btnBootSend,
            button("Batal") { wpCancel = true; Core.ble.cancelUpload = true },
            button("Hapus animasi boot") { bootClear() }
        ))

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
        p.addView(TextView(this).apply { text = t; textSize = 17f; setPadding(0, dp(14), 0, dp(4)) })

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

    private fun buildScreenPage(p: LinearLayout) {
        buildLibrary(p)
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
            text = "Ketuk Pakai untuk mengganti wallpaper di layar motor. Foto terkirim sekitar 10-30 dtk; video beberapa menit. " +
                "Wallpaper baru (di bawah) otomatis masuk sini."
            textSize = 12f
        })
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
        fun addRow(thumb: Bitmap?, name: String, info: String, on: Boolean, use: () -> Unit, del: (() -> Unit)?, ren: (() -> Unit)?) {
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
            if (del != null) r.addView(Button(this).apply { text = "Hapus"; isAllCaps = false; setOnClickListener { del() } })
            libBox.addView(r)
        }
        addRow(null, "Foto bawaan", "Kembali ke wallpaper dari firmware", act == null, { wpClear() }, null, null)
        for (e in WpLib.list(applicationContext).sortedByDescending { it.at }) {
            addRow(WpLib.thumb(applicationContext, e.id), e.name,
                (if (e.kind == "video") "Video" else "Foto") + " · " + (e.size / 1024) + " KB", act == e.id,
                { useWallpaper(e) },
                { AlertDialog.Builder(this).setMessage("Hapus \"${e.name}\" dari koleksi? (Wallpaper di ESP32 tidak berubah.)")
                    .setPositiveButton("Hapus") { _, _ -> WpLib.delete(applicationContext, e.id); refreshLib() }.setNegativeButton("Batal", null).show() },
                {
                    val et = EditText(this).apply { setText(e.name); setSingleLine(true) }
                    AlertDialog.Builder(this).setTitle("Ganti nama").setView(et)
                        .setPositiveButton("Simpan") { _, _ -> WpLib.rename(applicationContext, e.id, et.text.toString()); refreshLib() }
                        .setNegativeButton("Batal", null).show()
                })
        }
    }

    private fun useWallpaper(e: WpLib.Entry) {
        if (!wpStart()) return
        tvLib.text = "Menyiapkan ${e.name}..."
        Thread {
            try {
                val data = WpLib.read(applicationContext, e.id)
                if (data == null) { wpEnd("File wallpaper hilang."); return@Thread }
                val t0 = System.currentTimeMillis()
                val err = Core.ble.uploadWallpaper(data) { p ->
                    val el = (System.currentTimeMillis() - t0) / 1000
                    val eta = if (p > 2) el * (100 - p) / p else -1
                    runOnUiThread { tvLib.text = "Mengirim ${e.name}... $p%" + (if (eta >= 0) " (sisa ±${eta} dtk)" else "") }
                }
                if (err == null) WpLib.setActive(applicationContext, e.id)
                runOnUiThread { refreshLib() }
                wpEnd(if (err == null) "${e.name} sekarang tampil di layar standby." else "Gagal: $err")
            } catch (ex: Throwable) {
                wpEnd("Galat: ${ex.message}")
            }
        }.also { it.isDaemon = true }.start()
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

    private fun wpEnable(b: Boolean) { btnWpSend.isEnabled = b; btnWpSave.isEnabled = b }

    private fun wpStart(needBle: Boolean = true): Boolean {
        if (wpBusy) return false
        if (needBle && !Core.ble.ready) { Toast.makeText(this, "Sambungkan ESP32 dulu (tombol 1).", Toast.LENGTH_LONG).show(); return false }
        wpBusy = true; wpCancel = false; Core.ble.cancelUpload = false
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
                var img: ByteArray? = photoImg
                var what = "Foto"
                if (vid != null) {
                    what = "Video"
                    val res = Wallpaper.encodeVideo(vid, smp, vo, { i, n ->
                        runOnUiThread { tvWp.text = "Memproses video: frame $i/$n..." }
                    }, { wpCancel })
                    if (res == null) { wpEnd(if (wpCancel) "Dibatalkan." else "Video gagal diproses."); return@Thread }
                    img = res.image
                    runOnUiThread {
                        tvWpInfo.text = "Hasil: ${res.frames} frame @ ${res.fps} fps = ${"%.1f".format(res.seconds)} dtk, ${res.image.size / 1024} KB" +
                            (if (res.trimmed) " (dipotong agar muat)" else "")
                    }
                }
                val data = img
                if (data == null) { wpEnd("Belum ada foto/video."); return@Thread }
                val label = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                val entry = WpLib.add(applicationContext, data, thumb, "$what $label", if (vid != null) "video" else "photo")   // otomatis masuk koleksi
                runOnUiThread { refreshLib() }
                if (!upload) { wpEnd("Disimpan ke koleksi: ${entry.name}. Pilih di daftar untuk mengirim."); return@Thread }
                val t0 = System.currentTimeMillis()
                val err = Core.ble.uploadWallpaper(data) { p ->
                    val el = (System.currentTimeMillis() - t0) / 1000
                    val eta = if (p > 2) el * (100 - p) / p else -1
                    runOnUiThread { tvWp.text = "Mengirim $what... $p%" + (if (eta >= 0) " (sisa ±${eta} dtk)" else "") }
                }
                if (err == null) WpLib.setActive(applicationContext, entry.id)
                runOnUiThread { refreshLib() }
                wpEnd(if (err == null) "Selesai. Wallpaper tampil di layar standby." else "Gagal: $err")
            } catch (e: Throwable) {
                wpEnd("Galat: ${e.message}")
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun wpClear() {
        if (!wpStart()) return
        tvWp.text = "Mengosongkan..."
        Thread {
            val err = try { Core.ble.clearWallpaper() } catch (e: Exception) { "Galat: ${e.message}" }
            if (err == null) WpLib.setActive(applicationContext, null)
            runOnUiThread { refreshLib() }
            wpEnd(if (err == null) "Wallpaper dikosongkan: kembali ke foto bawaan." else "Gagal: $err")
        }.also { it.isDaemon = true }.start()
    }

    // ---------- animasi boot ----------
    private val bootFps = 15
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
                    bootVideo = v; bootSamples = smp
                    ivBoot.setImageBitmap(pv)
                    tvBoot.text = info
                    btnBootSend.isEnabled = !wpBusy
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
            btnWpCancel.isEnabled = false
            wpEnable(wpPhoto != null || wpSamples.isNotEmpty())
            btnBootSend.isEnabled = bootVideo != null
        }
    }

    private fun bootSend() {
        val vid = bootVideo ?: return
        if (!wpStart(true)) return
        btnBootSend.isEnabled = false
        val smp = bootSamples
        Thread {
            try {
                val vo = Wallpaper.VideoOpts(bootLook(), bootFps, bootQuality, true, Wallpaper.BOOT_CAP)
                val res = Wallpaper.encodeVideo(vid, smp, vo, { i, n ->
                    runOnUiThread { tvBoot.text = "Memproses video: frame $i/$n..." }
                }, { wpCancel })
                if (res == null) { bootEnd(if (wpCancel) "Dibatalkan." else "Video gagal diproses."); return@Thread }
                val t0 = System.currentTimeMillis()
                val err = Core.ble.uploadWallpaper(res.image, true) { p ->
                    val el = (System.currentTimeMillis() - t0) / 1000
                    val eta = if (p > 2) el * (100 - p) / p else -1
                    runOnUiThread { tvBoot.text = "Mengirim animasi boot... $p%" + (if (eta >= 0) " (sisa ±${eta} dtk)" else "") }
                }
                bootEnd(
                    if (err == null) "Selesai: ${res.frames} frame @ ${res.fps} fps = ${"%.1f".format(res.seconds)} dtk, ${res.image.size / 1024} KB. Diputar saat ESP32 dinyalakan."
                    else "Gagal: $err"
                )
            } catch (e: Throwable) {
                bootEnd("Galat: ${e.message}")
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun bootClear() {
        if (!wpStart()) return
        tvBoot.text = "Menghapus animasi boot..."
        Thread {
            val err = try { Core.ble.clearWallpaper(true) } catch (e: Exception) { "Galat: ${e.message}" }
            bootEnd(if (err == null) "Animasi boot dihapus: ESP32 langsung masuk tampilan biasa." else "Gagal: $err")
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
        super.onPause()
    }

    override fun onStatus(text: String) { tvStatus.text = text }
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
            (if (!l && Build.VERSION.SDK_INT >= 33) "\nCatatan Android 13+: bila pilihan MotorNav abu-abu, buka Info aplikasi MotorNav > menu titik tiga > Izinkan setelan terbatas, lalu ulangi." else "")
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
