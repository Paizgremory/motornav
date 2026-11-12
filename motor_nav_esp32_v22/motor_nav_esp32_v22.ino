/*
  v22.37: MENU LCD -> MODE UPLOAD WIFI LANGSUNG (juga di ESP32-C3). Di Pengaturan, item WALLPAPER dan ANIMASI BOOT: pada board tanpa microSD (C3)
  TAHAN sensor = langsung masuk mode WiFi (BLE dimatikan, ESP32 membuat WiFi "MotorNav", sandi motornav1234, alamat 192.168.4.1; layar menampilkan
  judul UPLOAD WALLPAPER / UPLOAD ANIM BOOT). Item UPLOAD WIFI kini tampil di semua board. Berkas ditulis ke partisi flash (foto 115 KB -> spiffs,
  animasi boot -> bootanim); yang tidak muat ditolak "TIDAK MUAT". Tahan sensor 1,2 dtk = batal; selesai/batal = restart otomatis.
  Board dengan microSD: perilaku lama tetap (TAHAN = ganti slot; upload lewat item UPLOAD WIFI). Aplikasi: opsi "Kirim lewat WiFi" (Nav160/MotorNav v3.21).
*/

/*
  v22.36: EFEK GETAR JALAN (GIMMICK, TANPA SENSOR FISIK). Pengaturan ke-13 (cfg[12], 0/1, default 0/mati) dari aplikasi:
  bila nyala, wajah standby (mode 0 saja, bukan foto/video) ikut bergoyang kecil mengikuti guncangan jalan, dibaca dari
  akselerometer HP (bukan MPU6050 -- sudah dicabut di v22.35) lewat paket BLE baru [0xFF][0x34][intensitas u8 0..255],
  dikirim aplikasi MotorNav ~15x/dtk saat navigasi & fitur ini dinyalakan. Goyangan meluruh otomatis (~0,6 dtk) bila
  paket berhenti datang (HP diam/terkunci/BLE putus) sehingga wajah selalu kembali tenang. Paket [0xFF][0x28] kini
  boleh 13 byte (v0..v12); ukuran lama tetap diterima, cfg[12] default 0 bila tidak dikirim.

  v22.35: MODUL MPU6050 (SUDUT MIRING) DAN VOLTASE AKI DIHAPUS SELURUHNYA. Sensor MPU6050 (I2C) dan pembagi tegangan
  aki 12V tidak lagi dipakai/dibaca; tidak ada lagi baris "AKI x.xV" di standby wajah, tidak ada lagi putaran tampilan
  mengikuti kemiringan motor (TAHAN 1,5 dtk di halaman Navigasi untuk nol-kan SUDAH DIHAPUS), dan nilai nol-kalibrasi
  "toff" di NVS tidak dipakai lagi. Kanvas penuh (TILT_UI, ~115 KB RAM) TETAP ADA karena dipakai standby mode 1
  (foto/video latar), wajah, notifikasi, dan game -- hanya bagian yang membaca MPU6050 dan memutar tampilan sesuai
  sudut yang dibuang; uiAngle sekarang selalu 0 (tampilan selalu tegak). Pin PIN_BATT, PIN_SDA, PIN_SCL serta define
  BATT_ENABLED/BATT_R1/BATT_R2/BATT_CAL/MPU_ADDR/TILT_AXIS/TILT_SIGN/TILT_ALPHA/TILT_UI_SIGN/TILT_MAX_DEG dihapus;
  <Wire.h> tidak lagi disertakan. Wiring MPU6050 dan pembagi tegangan aki dihapus dari komentar wiring di atas.
  Tidak ada paket BLE atau tampilan aplikasi Android yang berkaitan dengan dua modul ini (sudah tidak ada sejak v19).

  v22.34: TEMA VISUALIZER "API". Pengaturan ke-12 (cfg[11], 0/1) dari aplikasi: 0 = batang visualizer pelangi (lama, warna
  berputar mengikuti sudut), 1 = batang visualizer api (gradien merah tua -> oranye -> kuning, mengikuti TINGGI tiap batang,
  bukan sudut). Cincin pelangi di tepi layar standby TIDAK berubah, tetap pelangi berputar seperti biasa. Paket [0xFF][0x28]
  kini boleh 12 byte (v0..v11); ukuran lama (10/11 byte) tetap diterima, cfg[11] default 0 bila tidak dikirim.

  v22.33: KENDALI MOTOR DARI HP. Modul relay 2 channel: IN1 = kunci kontak (GPIO32; C3: GPIO1), IN2 = starter (GPIO33; C3: GPIO20). Starter ditekan 2 dtk lalu lepas otomatis
  (timer perangkat keras, batas keras 3 dtk, hanya bila kunci kontak ON, jeda 4 dtk antar starter, lepas seketika bila Bluetooth putus). Perintah dilindungi kode 6 karakter
  (RLY_PIN_CODE di bawah, 6 karakter). BLE baru: [0xFF][0x30][on][kode 6 char] kunci kontak, [0xFF][0x31][ms u16][PIN] starter, [0xFF][0x32][0] status (notify 0xE5).
  Lupa kode: [0xFF][0x33][kode baru 6 angka] mengganti kode tanpa kode lama (aplikasi mengirimnya setelah OTP email benar), hanya diterima 2 menit pertama setelah ESP32 menyala; kode tersimpan di NVS.
  Restart perangkat lunak (OTA/crash) saat kunci kontak ON: kunci kontak dinyalakan lagi otomatis. Aki dicabut/dipasang: mulai OFF.

  v22.32: PAKET ESP32-C3 SUPER MINI. Kode tetap satu untuk ESP32 biasa dan C3 (pin dipilih otomatis lewat CONFIG_IDF_TARGET_ESP32C3), tetapi build/CI/README kini untuk C3.
  Perubahan perilaku di C3 (tanpa microSD, tanpa WiFi upload): item menu LCD "UPLOAD WIFI" DISEMBUNYIKAN (sebelumnya tampil tetapi tidak berbuat apa-apa).
  Wiring C3 (layar GC9A01 7 pin tanpa BL): SCL=GPIO4, SDA=GPIO6, CS=GPIO7, DC=GPIO3, RST=GPIO2, TP223 OUT=GPIO5;
  buzzer GPIO10. Board: "ESP32C3 Dev Module" atau "Nologo ESP32C3 Super Mini", USB CDC On Boot: Enabled, flash 4 MB.
  Partisi: partitions.csv yang ikut (sama dengan v22.31, 4 MB, muat di C3). Flash pertama lewat USB di alamat 0x0 memakai berkas .merged.bin; setelah itu OTA lewat aplikasi.

  v22.31: UPDATE FIRMWARE (OTA) LEWAT BLUETOOTH dari aplikasi MotorNav (v3.27+): halaman Layar > "Update firmware (OTA Bluetooth)". Aplikasi mengirim berkas .bin
  hasil Sketch > Export Compiled Binary (yang BERAKHIRAN .ino.bin, BUKAN .bootloader / .partitions / .merged.bin) ke slot program yang tidak sedang dipakai
  (app0 <-> app1 bergantian). ESP32 memeriksa CRC-32 dan keabsahan citra (esp_ota_end), baru berpindah slot lalu restart sendiri. Bila citra rusak / terputus
  di tengah jalan, program lama tetap utuh dan jalan seperti biasa.
  BLE baru: [0xFF][0x2A][ukuran u32] mulai (status 1 bersiap -> 2 siap), [0xFF][0x2B][offset u32][data...] isi, [0xFF][0x2C][crc32 u32] selesai (status 4 sukses lalu
  restart 2,5 dtk kemudian, 5 gagal), [0xFF][0x2D] minta info (dibalas notify 0xE4: [0xE4][bendera][slot maks u32][teks versi]; bendera bit0 = OTA tersedia),
  [0xFF][0x2E] batalkan. Status dibaca dari karakteristik yang sama dengan upload wallpaper (2 siap, 3 menerima, 4 sukses, 5 gagal).
  PARTISI BERUBAH (WAJIB flash lewat KABEL USB sekali ini, tidak bisa lewat OTA): dua slot program app0 + app1 @ 1,625 MB (0x1A0000), foto 192 KB, animasi boot flash 512 KB
  (alamat tetap 0x380000). Cek "Sketch uses ... bytes": harus di bawah 1703936. Bila lebih, pakai partitions_app_besar.csv (ganti nama jadi partitions.csv): 2 x 1,875 MB,
  tanpa partisi animasi boot flash (animasi boot hanya dari SD). Setelah itu tiap update cukup dari aplikasi, tanpa kabel.

  v22.30: PERPUSTAKAAN ANIMASI BOOT (slot, seperti wallpaper). Tiap animasi boot = satu berkas /boot/bNNNNN.mnv (NNNNN = nomor slot 1..65535, video
  "MNVIDEO2/3"). Banyak animasi bisa tersimpan sekaligus; yang aktif disimpan di NVS (kunci btsel), slot 0 = TANPA ANIMASI.
  /bootanim.mnv lama otomatis dipindah ke /boot/ (slot 65001) dan menjadi animasi aktif. Dengan SD terpasang, partisi flash "bootanim" tidak dipakai lagi
  (tanpa SD tetap dipakai seperti dulu).
  MENU LCD: Pengaturan > ANIMASI BOOT (di bawah WALLPAPER): tampil nomor/jumlah + durasi; TAHAN = animasi berikutnya (urutan: TANPA ANIMASI, lalu semua berkas).
  BLE baru: [0xFF][0x1B][ukuran u32][slot u16] upload animasi boot ke slot (otomatis jadi aktif), [0xFF][0x1C] minta daftar (dibalas notify 0xE3, format sama
  dengan 0xE2), [0xFF][0x1D][slot u16] pilih (0 = tanpa animasi), [0xFF][0x1E][slot u16] hapus berkas. Upload WiFi: POST /up?tgt=1&crc=..&slot=N.
  Partisi TIDAK berubah dari v22.29.

  v22.29: (1) BATAS UPLOAD DINAIKKAN: SD_MAX_UP 256 MB -> 1800 MB (kartu 2 GB; file FAT32 maks 4 GB). Aplikasi: wallpaper 512 MB, animasi boot 128 MB.
  (2) PERPUSTAKAAN WALLPAPER DI SD: tiap wallpaper (FOTO atau VIDEO) = satu berkas /wp/wNNNNN.mnv (NNNNN = nomor slot 1..65535). Banyak wallpaper bisa
  tersimpan sekaligus; yang aktif disimpan di NVS (kunci wpsel). Foto yang dipilih disalin otomatis ke partisi flash kecil (dipakai langsung dari flash,
  tanpa RAM tambahan); video diputar langsung dari SD. /wallpaper.mnv lama otomatis dipindah ke /wp/ saat boot dan menjadi wallpaper aktif.
  (3) MENU LCD: Pengaturan > WALLPAPER: tampil nomor/jumlah + jenis; TAHAN = ganti ke wallpaper berikutnya (urutan: BAWAAN, lalu semua berkas di SD).
  (4) BLE baru: [0xFF][0x15][ukuran u32][slot u16] upload ke slot (otomatis jadi aktif bila berhasil), [0xFF][0x18] minta daftar (dibalas notify 0xE2),
  [0xFF][0x19][slot u16] pilih wallpaper (0 = bawaan), [0xFF][0x1A][slot u16] hapus berkas. Upload WiFi: POST /up?tgt=0&crc=..&slot=N.
  Partisi TIDAK berubah dari v22.28 (tak perlu flash partisi ulang bila sudah v22.28, tetap disarankan flash penuh).

  v22.28: (1) FPS sampai 60 (header & firmware menerima 1-60; kecepatan nyata dibatasi dekoder JPEG ESP32, frame yang tak terkejar dilewati otomatis).
  (2) Format video baru "MNVIDEO3" (khusus SD): tabel frame sebesar jumlah frame (sampai 65535 frame), jadi durasi tidak lagi dibatasi 900 frame.
  Tabel dibaca bertahap dari SD (jendela 128 frame), bukan dimuat penuh ke RAM. "MNVIDEO2" tetap dibaca. (3) Batas upload 256 MB.
  (4) UPLOAD WIFI: Pengaturan > UPLOAD WIFI. BLE dimatikan, ESP32 membuat WiFi sendiri (SSID MotorNav, sandi motornav1234, alamat 192.168.4.1),
  aplikasi mengirim file lewat HTTP langsung ke SD (~10x lebih cepat dari BLE). Selesai/batal (tahan sensor 1,2 dtk) = ESP32 restart.
  (5) PARTISI BERUBAH: program 1,94 MB (0x1F0000, karena library WiFi menambah ukuran), wallpaper flash 1,5 MB, animasi boot 512 KB. WAJIB flash ulang penuh.

  v22.27: MICROSD (modul SPI 6 pin, ESP32 biasa). Video wallpaper dan animasi boot disimpan di kartu microSD, bukan lagi dibatasi partisi flash
  (2,19 MB / 512 KB). File di root kartu: /wallpaper.mnv (video standby) dan /bootanim.mnv (animasi boot), format "MNVIDEO2" yang sama dengan
  yang dibuat aplikasi MotorNav. Upload dari aplikasi lewat Bluetooth otomatis ditulis ke SD (ke file sementara, baru diganti bila CRC cocok,
  jadi file lama aman bila upload gagal). FOTO tetap di partisi flash (cuma 115 KB, dan dipakai langsung dari flash tanpa RAM tambahan).
  Tanpa kartu / kartu tidak terbaca: perilaku lama (partisi flash) dipakai sebagai cadangan. Prioritas: video SD > isi partisi flash > foto bawaan.
  Kabel SD (HSPI, terpisah dari layar): SCK 14, MISO 19, MOSI 13, CS 26, VCC modul ke 5V (modul punya regulator 3V3 + level shifter), GND ke GND.
  Hanya untuk ESP32 biasa (ESP32-C3 cuma punya 1 bus SPI bebas: SD dimatikan di sana). Library SD & SPI bawaan core ESP32 (tak perlu instal).
  RAM: video SD memakai penyangga satu frame (sebesar frame terbesar, maks 64 KB) + tabel frame (maks 7 KB) selama dipakai.

  v22.26: PARTISI WALLPAPER DIPERBESAR. Program (app0) dipersempit ke 1,25 MB (0x140000), animasi boot 512 KB (0x80000), sisanya untuk wallpaper:
  2,19 MB (0x230000). Bila kompilasi gagal dengan pesan "Sketch too big / text section exceeds available space", program tidak muat: beri tahu
  ukuran "Sketch uses ... bytes" supaya pembagian partisi disesuaikan. WAJIB flash ulang penuh (alamat 0x0) dengan partitions.csv baru.

  v22.25: ANIMASI BOOT + LAYAR "KANVAS OK" DIHAPUS. Layar diagnostik 2 detik ("KANVAS OK / heap bebas / video ya") saat LCD menyala sudah dihapus
  (bila kanvas gagal dialokasi hanya muncul peringatan merah singkat; detailnya tetap di Serial). Saat sistem dinyalakan, firmware memutar VIDEO yang
  dikirim dari aplikasi MotorNav (menu Layar > Animasi boot), lalu masuk ke tampilan biasa. Disimpan di partisi data KEDUA "bootanim" (format
  sama dengan video wallpaper "MNVIDEO2"), jadi wallpaper tetap terpisah. Sentuh sensor setelah 0,4 dtk = lewati animasi. Slot kosong = tanpa animasi.
  PARTISI BERUBAH (lihat v22.26). WAJIB flash ulang penuh memakai partitions.csv baru
  (Arduino IDE: otomatis dipakai dari folder sketch). Paket BLE baru: [0xFF][0x14][ukuran u32] mulai upload animasi boot (data/akhir memakai 0x11/0x12
  yang sama), [0xFF][0x17] kosongkan animasi boot. Status upload dibaca dari karakteristik yang sama (2 siap, 4 sukses, 5 gagal).

  v22.24: SLIDER MUSIK + KECERAHAN. Paket [0xFF][0x29][pos u16 dtk][dur u16 dtk] dari HP (tiap ~3 dtk / saat geser) -> progress bar bergelombang di
  standby foto/video: bagian yang sudah diputar bergelombang putih mengalir, sisanya garis datar abu-abu, waktu berjalan & durasi di bawahnya.
  Pengaturan ke-11 (cfg[10]) = KECERAHAN LAYAR 10-100%: modul tanpa pin BL, jadi diredupkan lewat perangkat lunak tepat sebelum dikirim ke panel.

  v22.23: PENGATURAN DARI APLIKASI. Paket [0xFF][0x28][v0..v9] (10 byte, tiap nilai dibatasi min/maks), disimpan di NVS (kunci "cfg") sehingga
  tetap ada setelah mati-hidup; HP mengirimnya tiap tersambung dan tiap diubah. Urutan: 0 transparansi sampul(0-255) 1 sampul on 2 keputihan jam(40-255)
  3 visualizer on 4 tinggi batang(%) 5 keterlihatan batang(%) 6 kecepatan cincin(%) 7 kartu pemutar on 8 detik masuk standby 9 wajah menari saat lagu.

  v22.22: CHRONOS DIHAPUS TOTAL (library ChronosESP32, penerima navigasi/notifikasi/telepon/musik/jam Chronos). Server BLE kini dibuat sendiri
  (layanan 4d4e0001) dan hanya melayani aplikasi MotorNav. Info lagu dan jam hanya dari aplikasi.
  VISUALIZER ASLI: paket [0xFF][0x25][n][band0..band(n-1)] (n=16, 0..255, bass dulu) dari HP ~20x/dtk -> batang visualizer mengikuti suara lagu.
  Bila paket tak datang (>0,6 dtk) visualizer kembali ke simulasi. Wajah standby juga ikut berdenyut mengikuti bass.
  GAMBAR SAMPUL: paket [0xFF][0x26][off u16][data] (64x64 RGB565 LE, 8192 byte) lalu [0xFF][0x27][1] = tampil. Digambar semi-transparan
  di atas jam pada standby foto/video; hilang saat lagu berganti/jeda sampai sampul baru tiba.

  v22.21: MUSIK DARI APLIKASI MotorNav. Paket baru [0xFF][0x24][state][titleLen][artistLen][judul][artis] (state 1 = diputar, 0 = jeda/berhenti),
  dikirim HP tiap kali berubah + tiap ~4 dtk. Dipakai sebagai sumber info lagu (menggantikan Chronos bila segar < 15 dtk), sehingga animasi musik
  muncul di KEDUA mode standby: wajah (mulai langsung menari/not melayang, aksi musik/dance lebih sering) dan foto/video (visualizer + kartu pemutar).

  v22.20: ANTREAN NOTIFIKASI. HP menahan notifikasi berikutnya sampai ESP32 selesai menampilkan yang sekarang (kabar notify [0xE1][0x02] dikirim
  firmware tiap layar notifikasi berakhir/ditutup). Flags paket teks bit 2-4 = jumlah yang masih antre -> lencana "+N" oranye di dekat logo.

  v22.19: TOLAK TELEPON DARI SENSOR: tahan 0,7 dtk di layar telepon -> firmware mengirim notify [0xE1][0x01] ke HP (karakteristik peta kini punya NOTIFY).
  Telepon tak terjawab tampil sebagai notifikasi dari HP.

  v22.18: sentuhan dari standby hanya membuka MODE GAME (tidak ada halaman Navigasi/Jam di putaran sentuhan); halaman Navigasi muncul otomatis
  saat navigasi dimulai di aplikasi, dan begitu navigasi selesai layar langsung kembali ke wajah/wallpaper standby.

  v22.17: MODE BERKENDARA. Bit 1 pada flags paket teks notifikasi (0x20) = HP sedang navigasi: notifikasi tampil 4,5 dtk, satu halaman (4 baris), bunyi pendek.

  v22.16: wajah standby tidak lagi tidur: tak ada mengantuk/menguap/tidur, aksi acak terus berganti (saklar FACE_SLEEP).

  v22.15: NOTIFIKASI & TELEPON DARI APLIKASI MotorNav (tanpa Chronos). Paket kontrol baru pada karakteristik peta:
  [0xFF][0x20][off u16][tot u16][data] teks notifikasi ([flags][appLen][titleLen][app][judul][pesan], dipotong-potong),
  [0xFF][0x21][off u16][data] logo aplikasi 48x48 RGB565 little-endian (4608 byte; 0x0000 = transparan),
  [0xFF][0x22][labelLen][nameLen][label][nama][nomor] telepon masuk, [0xFF][0x23] telepon selesai.
  Layar notifikasi baru: logo asli aplikasi di atas, nama aplikasi (chip), pengirim (ukuran 2), isi pesan ukuran 2
  (14 huruf x 4 baris per halaman) yang berganti halaman otomatis tiap 3,4 dtk; lama tampil menyesuaikan panjang pesan
  (7-26 dtk). Tanpa logo: huruf pertama nama aplikasi. Layar telepon: nama kontak besar (2 baris), nomor di bawahnya,
  label (mis. WhatsApp); berhenti otomatis maks 60 dtk. Notifikasi/telepon dari Chronos tetap berfungsi.
*/
/*
  v22.14: UPLOAD WIFI DIHAPUS (library WiFi/WebServer tidak dipakai lagi: flash & RAM lebih lega). Foto DAN video wallpaper kini
  diunggah dari aplikasi MotorNav lewat Bluetooth. Protokol: [0xFF][0x10][ukuran u32] mulai, [0xFF][0x11][offset u32][data...],
  [0xFF][0x12][crc32 u32] selesai, [0xFF][0x13] kosongkan slot. Data = citra lengkap isi partisi dari offset 0 (foto "MNPHOTO1"
  atau video "MNVIDEO2"); sektor flash dihapus tepat sebelum ditulis. Menu pengaturan item pertama kini WALLPAPER (hanya info isi slot).

  v22.13: UPLOAD WALLPAPER (FOTO) LEWAT BLUETOOTH dari aplikasi MotorNav, tanpa mode WiFi. Paket kontrol baru pada karakteristik
  peta: [0xFF][0x10][ukuran u32] mulai (ESP32 menghapus slot), [0xFF][0x11][offset u32][data...] isi, [0xFF][0x12][crc32 u32] selesai.
  Isi = 240x240 RGB565 little-endian (115200 byte), sama dengan unggahan WiFi. Status dibaca aplikasi dari karakteristik yang sama
  (1 byte: 0 diam, 1 menghapus, 2 siap, 3 menerima, 4 sukses, 5 gagal). Foto langsung tampil tanpa restart. Jalur WiFi tetap ada.

  v22.12: APLIKASI ANDROID MotorNav menggantikan Chronos. Aplikasi memakai layanan BLE peta yang sama (UUID 4d4e0001/0002),
  menggambar peta + banner belokan + kecepatan + ETA di HP lalu mengirim JPEG 240x240 (layar penuh). Ditambah paket kontrol
  [0xFF][cmd]: jam (cmd 1) dan status navigasi/heartbeat (cmd 2). Chronos tetap bisa dipakai seperti sebelumnya.
  Perubahan: isLinked() = Chronos ATAU aplikasi tersambung, jam dari aplikasi, halaman Nav menampilkan frame aplikasi tanpa overlay.

  v22.11: PETA ASLI (hanya ESP32 biasa, bukan C3). HP menjalankan halaman peta_ble.html (Chrome Android, Web Bluetooth)
  yang menggambar peta (tile CARTO/OSM) di sekitar posisi GPS, memutarnya mengikuti arah jalan, lalu mengirimnya sebagai
  JPEG 240x240 lewat BLE ke ESP32 (layanan khusus, bersama koneksi Chronos). Navigasi belok-demi-belok tetap dari
  Chronos/Google Maps: banner, jarak, nama jalan, ETA, kecepatan ditumpuk di atas peta asli. Menu TAMPILAN NAV punya
  mode ke-3 "LIVE". Jika frame peta berhenti >4 dtk, otomatis kembali ke tampilan peta bergaya.

  v22.10: TAMPILAN NAVIGASI BERGAYA PETA (seperti Google Maps mode gelap). Latar biru tua dengan jalan, garis rute biru
  dari bawah sampai persimpangan lalu membelok kiri/kanan/lurus, titik posisi, banner atas (panah belok besar, jarak
  ke belokan, nama jalan, instruksi), chip bawah (jam tiba + sisa waktu) dan lingkaran kecepatan. Ini ilustrasi dari
  data Chronos (bukan peta jalan asli). Persimpangan mendekat sesuai jarak. Cincin progress & warna hijau/oranye/merah
  tetap. Menu pengaturan: item baru TAMPILAN NAV untuk memilih PETA atau 3D (tampilan jalan + motor yang lama).

  v22.9: PERBAIKAN BOOT BERULANG SETELAH UPLOAD VIDEO. Penyebab: library JPEGDEC butuh objek ~18 KB, sedangkan sisa RAM
  hanya ~14 KB (blok terbesar) -> alokasi gagal -> ESP32 restart terus. Kini video didecode oleh dekoder JPEG di ROM
  chip (rom/tjpgd.h, kerja ~5 KB, dialokasi di awal boot), library JPEGDEC tidak dipakai lagi. Tabel frame dibaca
  langsung dari flash (hemat 5 KB RAM), buffer upload dialokasi hanya saat mode upload (hemat 4 KB), dan penyangga
  latar jam (18 KB) dihapus. Jika video gagal didecode, layar memakai foto bawaan (tidak restart).
  Catatan: dekoder ROM lebih lambat dari JPEGDEC; fps tampil bisa lebih rendah dari fps video bila C3 tidak kuat.

  v22.8: halaman upload punya pilihan FPS (otomatis / 10 / 15 / 20 / 30). Fps tinggi = durasi yang muat lebih pendek;
  jika layar tersendat pada fps tinggi, pilih fps lebih rendah.

  Baru (v22.7): SATU SLOT FOTO/VIDEO SAJA, tapi KAPASITASNYA SELURUH PARTISI DATA (~2,3 MB, 5x lebih besar dari
  slot lama 470 KB). Menu pengaturan kini: MULAI UPLOAD, TEMA, STANDBY, KELUAR (tidak ada lagi pilihan slot).
  Halaman upload WiFi hanya punya satu kotak pilih file (foto ATAU video) dan satu tombol "Kirim ke layar".
  - Foto besar (berapa pun resolusinya) otomatis dipotong persegi dan diperkecil ke 240x240 di browser HP.
  - Video panjang: browser HP memilih sendiri fps (15 turun sampai 6) dan kualitas JPEG supaya seluruh video
    muat di 2,3 MB; maks 900 frame. Kira-kira 40 detik @15 fps, atau sampai ~2 menit @6 fps (tergantung isi video).
    Video lebih panjang dari kapasitas dipotong dari awal. Video sangat pendek diberi kualitas lebih tinggi.
  - Format lama (MNVIDEO1, header 4 KB) di awal partisi tetap terbaca; unggahan baru memakai MNVIDEO2 (header 8 KB).
  - Tidak perlu mengubah partitions.csv. Tetap flash ulang penuh bila sebelumnya belum memakai partitions.csv ini.

  Baru di v23:
  - Notifikasi kini menampilkan: nama aplikasi (chip oranye), pengirim/judul (putih besar), dan ISI PESAN
    (hingga 3 baris, dipotong "~" jika panjang). Emoji/karakter non-ASCII dibuang karena font tidak mendukung.
    Lama tampil 8 detik; sentuh untuk menutup lebih cepat.
  - Menu game: SENTUH SINGKAT di layar judul = pindah ke menu berikutnya, TAHAN 0,7 dtk = mulai main.
    Di layar game over / jeda: sentuh = ulang/lanjut, TAHAN 1 dtk = keluar ke menu berikutnya.
  - Perbaikan: animasi standby yang melewati area wajah (balon, helm, roket, hantu, sepeda, dll.) tidak lagi
    meninggalkan sisa gambar yang menumpuk. Gambar dipotong ke area aslinya (persegi wajah / lingkaran dalam cincin).
  - MENU PENGATURAN: di standby TAHAN sensor 4 dtk (bunyi 3x) lalu lepas. Di menu: KLIK = item berikutnya,
    TAHAN 0,7 dtk = pilih/ubah. Isi: MULAI UPLOAD (WiFi, ke satu slot foto/video), TEMA (gelap/terang), STANDBY (wajah/foto), KELUAR.
    Slot kosong = foto bawaan. Tema terang tersimpan di memori.
  - VIDEO LATAR standby mode 1: loop video (tanpa suara, sampai 900 frame, fps otomatis 6-15) menggantikan foto.
    Disimpan sebagai JPEG per frame di partisi data. Unggah lewat halaman WiFi yang sama (bagian "Video latar"):
    HP memotong video jadi frame lalu mengirimnya. Butuh: file partitions.csv
    di folder sketch (otomatis dipakai Arduino IDE; flash ulang file .merged.bin penuh di alamat 0x0).
  - Info lagu: saat lagu diputar di HP (Spotify dll., lewat Chronos), standby menampilkan satu baris kecil
    "Judul - Artis" di bawah jam (teks panjang bergeser pelan). Hilang saat jeda/berhenti/Bluetooth putus.
    Di standby wajah, baris ini menggantikan baris voltase aki selama lagu diputar.
  - Standby foto saat lagu diputar: judul + artis, detik berjalan, garis gelombang mengalir, dan visualizer pelangi
    tembus pandang (simulasi, tidak mengikuti suara asli). Chronos tidak mengirim durasi lagu, jadi detik dihitung
    sejak lagu terdeteksi/berganti (perkiraan) dan garis tidak berupa progress sampai akhir lagu.
  - Warna dinaikkan kecerahannya (abu-abu, hijau, oranye, merah lebih terang). Kecerahan lampu latar (BL)
    ditentukan wiring: sambungkan pin BL langsung ke 3V3 agar maksimal.

  Baru di v22: STANDBY MODE 1 (foto + jam transparan + cincin pelangi berputar)
  - Standby punya 2 mode: 0 = wajah hidup (lama), 1 = foto latar + jam putih transparan + cincin pelangi berputar.
  - Saat standby, TAHAN sentuhan 1,2 detik untuk ganti mode (bunyi 2x). Pilihan tersimpan di memori,
    jadi setelah dimatikan lalu dihidupkan lagi, mode terakhir dipakai. Sentuh singkat = bangun seperti biasa.
  - Foto ada di file standby_photo.h (satu folder dengan .ino). Ganti foto: python3 foto_ke_header.py fotoku.png
  - Tombol BOOT di ESP32 biasa (GPIO0) kini berfungsi sama seperti sensor TP223 (tidak untuk ESP32-C3).
    Standby: tap = bangun | tahan 1,2 dtk (bunyi 1x) lalu lepas = ganti mode | tahan 4 dtk (bunyi 3x) lalu lepas = mode upload foto.
    PENTING: jangan tahan BOOT saat menyalakan ESP32 (itu masuk mode flash). Pakai cara tahan 4 dtk di standby.
  - GANTI FOTO LEWAT HP (tanpa kabel): matikan lalu NYALAKAN motor/ESP32 sambil TAHAN sensor sentuh ~2 detik.
    Layar menampilkan "UPLOAD FOTO". Sambungkan HP ke WiFi "MotorNav-Foto" (sandi 12345678), buka browser
    ke http://192.168.4.1 , pilih foto dari galeri, tekan "Kirim ke layar". Selesai -> ESP32 restart sendiri.
    Foto disimpan di partisi flash "spiffs" (pilih Tools > Partition Scheme: "Default 4MB with spiffs"
    atau "Minimal SPIFFS"; JANGAN "Huge APP"). Tanpa foto upload, dipakai foto bawaan standby_photo.h.
  - Mode 1 butuh TILT_UI true (kanvas penuh). Jika tidak, otomatis memakai wajah.

  Layar Navigasi Motor v15 - tema hitam/putih/hijau/oranye/merah + standby hidup + animasi notifikasi
  ESP32 biasa (DevKit 30/38 pin) ATAU ESP32-C3 Super Mini + GC9A01 (240x240 bulat) + sensor TP223
  Kode otomatis memilih pin sesuai board yang dipilih di Arduino IDE.
  Data navigasi Google Maps dari aplikasi Chronos (fbiego) lewat BLE.

  Warna rute dan cincin menurut jarak ke belokan:
    > 250 m  hijau   |   100-250 m  oranye   |   < 100 m  merah

  Mode standby: jika tidak ada navigasi selama 20 detik, layar menampilkan
  wajah animasi (mata berkedip, melirik, sesekali tersenyum, tertidur setelah
  1 menit, merah saat Bluetooth terputus). Sentuh TP223 untuk bangun.
  Saat navigasi dimulai, layar bangun sendiri.

  Library: GFX Library for Arduino, NimBLE-Arduino 2.x
  Board ESP32 biasa : "ESP32 Dev Module"
    Pin: SCK 18, MOSI 23, CS 5, DC 2, RST 4, TP223 OUT 27, BL ke 3V3, VCC ke 3V3, GND ke GND
    (jangan pakai GPIO 6-11, itu terhubung ke flash di ESP32 biasa)
  Board ESP32-C3    : "ESP32C3 Dev Module", USB CDC On Boot: Enabled
    Pin: SCK 4, MOSI 6, CS 7, DC 3, RST 2, TP223 OUT 5, BL ke 3V3

  Standby hidup (30 fps lewat kanvas, tanpa kedip layar): semua gerakan dihaluskan
  (mata, alis, mulut, pipi), wajah bernapas dan memantul, kedip kadang dobel,
  mata melirik ke segala arah. 12 aksi acak: senang, jatuh cinta, kedip sebelah,
  kaget, penasaran, pusing, musik, kacamata hitam, bersin, VROOM, melet, dan
  melirik jam. Menguap lalu tidur (Z melayang, gelembung). Titik berekor
  mengorbit di cincin, bintang berkelip. Saat Bluetooth putus: wajah sedih
  yang mencari-cari HP.

  Notifikasi HP: lonceng berayun + cincin oranye berputar selama 4,5 detik,
  menampilkan nama aplikasi dan judul. Sentuh TP223 untuk menutup lebih cepat.
  Berlaku di semua mode (navigasi, jam, info perjalanan, standby).

  Halaman (sentuh TP223 untuk ganti): lihat daftar "Halaman sekarang" di catatan v18 di atas

  Baru di v19:
  - Halaman sekarang hanya: Navigasi, Game (3 game), dan Standby (otomatis saat tidak ada aktivitas).
    Info perjalanan, Speedometer, Voltase aki, dan Sudut miring sebagai halaman DIHAPUS.
  - Standby menampilkan jam kecil dan voltase aki (hanya jika pin aki tersambung, tegangan terbaca >= 5 V).
  - SELURUH tampilan (navigasi, standby, game, notifikasi, panggilan) berputar mengikuti sudut miring motor
    dari MPU6050. Arah terbalik? ubah TILT_UI_SIGN ke -1. Matikan dengan TILT_UI false.
    Motor tegak tapi tampilan miring? Di halaman Navigasi TAHAN sentuhan 1,5 detik untuk nol-kan (tersimpan).
    Catatan: butuh ~115 KB RAM kosong untuk kanvas penuh. Jika Serial menulis "Kanvas penuh gagal", set TILT_UI false.

  Baru di v18:
  - Halaman Jam DIHAPUS. Jam kini tampil kecil di bawah wajah saat standby (mode saat tidak ada aktivitas).
    Jam disimpan sendiri sehingga tetap berjalan walau Bluetooth terputus (setelah pernah tersambung sekali).
  - Standby kini juga aktif di halaman Game saat tidak bermain (layar judul / game over / jeda) dan
    tidak disentuh 20 detik. Halaman Sudut miring tetap tanpa standby.
  Halaman sekarang: 0 Navigasi, 1 Info perjalanan, 2 Speedometer, 3 Voltase aki, 4 Sudut miring, 5-7 Game.

  Baru di v17:
  - 3 halaman GAME yang dimainkan dengan 1 sensor sentuh TP223:
      6 MOTOR RUN : sentuh = lompat (tahan = lebih tinggi), hindari kerucut.
      7 TERBANG   : sentuh = kepak, lewati celah pipa.
      8 PAS!      : sentuh untuk menghentikan penanda di dalam zona hijau; zona makin sempit.
    Di layar judul / game over / jeda: sentuh = mulai, TAHAN 1 detik = keluar ke halaman berikutnya.
    Skor tertinggi tiap game tersimpan di memori. Buzzer: GAME_SOUND false untuk mematikan.
    Notifikasi/panggilan masuk menjeda game otomatis. Titik halaman dirapatkan agar muat 9 titik.
  Halaman sekarang: 0 Navigasi, 1 Jam, 2 Info perjalanan, 3 Speedometer, 4 Voltase aki, 5 Sudut miring, 6-8 Game.

  Baru di v15:
  - 8 aksi standby baru: kembang api, kaca pembesar, main game, hantu lewat, telinga kucing
    + MEOW, petir, lampu lalu lintas 3-2-1 GO, dan makan donat. Total 43 aksi.
  - FPS lebih tinggi: batas frame 8 ms (hingga ~120 fps jika SPI sanggup), data navigasi tidak
    lagi disalin tiap putaran loop, dan ada penghitung FPS lewat Serial (SHOW_FPS true).
  - Transisi lebih halus: iris menutup dengan easing lebih rapat, lalu halaman baru TIMBUL
    perlahan (fade-in palet warna) saat ganti halaman, bangun dari standby, dan selesai notifikasi/panggilan.
  - Antar aksi standby: fade masuk/keluar lebih panjang dengan kurva halus, jeda antar aksi lebih
    pendek, dan 30% kemungkinan aksi langsung tersambung ke aksi berikutnya lewat kedipan.

  Baru di v14:
  - Halaman Voltase Aki: baca tegangan aki 12V lewat pembagi tegangan, tampil angka besar
    + status (MENGISI / BAIK / CUKUP / LEMAH) dan cincin yang mengikuti level.
  - Buzzer: bunyi pendek saat notifikasi, dering berulang saat panggilan masuk.
  - Halaman Sudut Miring: sensor MPU6050, gauge +/-60 derajat, angka sudut, catatan maksimum
    kiri/kanan. TAHAN sentuhan 1,2 detik di halaman ini untuk nol-kan (motor tegak) dan reset
    maksimum; nilai nol tersimpan di memori.
  Halaman sekarang: 0 Navigasi, 1 Jam, 2 Info perjalanan, 3 Speedometer, 4 Voltase aki, 5 Sudut miring.

  WIRING TAMBAHAN (v22.35: modul voltase aki & MPU6050/sudut-miring sudah dihapus, baris di bawah tinggal buzzer)
  ESP32 biasa : Buzzer aktif GPIO 25 (+) / GND (-).
  ESP32-C3    : Buzzer GPIO 10.
  Semua GND harus disatukan dengan GND aki/motor.

  Baru di v13:
  - 8 aksi standby baru: mencari sinyal (WiFi), baterai habis, hujan + payung, roket meluncur,
    main pong (mata mengikuti bola), bendera finish, bensin menipis, dan balon terbang.
  - Notifikasi punya 3 gaya animasi: lonceng, bubble chat (WhatsApp/Telegram/SMS dll),
    dan amplop surat (Gmail/Mail/Outlook dll). Gaya dipilih dari nama aplikasi.
  - Panggilan masuk: layar telepon hijau dengan gagang bergetar, gelombang melebar, dan nama
    penelepon. Berhenti otomatis saat diangkat/ditolak, atau sentuh TP223 untuk menutup.
    Memakai watch.setRingerCallback() dari ChronosESP32.

  Baru di v12:
  - 8 aksi wajah baru: ngakak (HA HA), menangis, mata bintang, pakai helm, motor melintas,
    ngopi, melambai "HAI!", dan radar (menyapu dengan garis hijau).
  - 3 latar baru standby: planet mengorbit, hujan meteor, salju. Total 7 gaya latar bergantian.
  - Halaman baru: Speedometer (gauge setengah lingkaran, warna hijau/oranye/merah menurut kecepatan).
    Halaman sekarang: 0 Navigasi, 1 Jam, 2 Info perjalanan, 3 Speedometer.

  Baru di v11:
  - FPS standby dan notifikasi naik (target ~60 fps, SPI 80 MHz, loop tanpa delay besar).
  - Latar belakang standby: cahaya napas di belakang wajah + 6 gaya (kunang-kunang,
    gelombang, riak, warp bintang, gelembung saat tidur, hujan saat Bluetooth putus),
    berganti dengan crossfade halus.
  - 6 aksi baru: malu, marah (uap + tanda kesal), mata berkilau, kiss, berpikir, joget.
  - Transisi: efek aksi fade masuk/keluar, kedip otomatis di pergantian aksi, warna
    cahaya latar berubah halus mengikuti aksi, mata membuka perlahan saat standby mulai.
  - Transisi iris (menutup ke tengah) saat ganti halaman, bangun dari standby, masuk standby.
  - Notifikasi digambar lewat kanvas (tanpa kedip): lonceng muncul membal, gelombang
    melebar, teks fade, lalu memudar di akhir.
*/

#include <Arduino_GFX_Library.h>
#include <NimBLEDevice.h>
#include <math.h>
#include <Preferences.h>
#include "standby_photo.h"
#include <esp_partition.h>
#include <esp_ota_ops.h>                                      // v22.31: update firmware lewat BLE
#include <WiFi.h>                                           // v22.37: mode UPLOAD WIFI juga untuk C3 (tanpa SD: berkas ditulis ke partisi flash)
#include <esp_http_server.h>
#if !CONFIG_IDF_TARGET_ESP32C3
  #include <esp_bt.h>                                       // v22.38: lepas memori Bluetooth Classic (ESP32 biasa) agar BLE muat di RAM
#endif
struct Navigation;                                          // v22.37: deklarasi awal agar prototipe otomatis Arduino (const Navigation &) tidak error
#if !CONFIG_IDF_TARGET_ESP32C3
  #define HAVE_SD 1
  #include <SPI.h>
  #include <SD.h>
#else
  #define HAVE_SD 0
#endif
#include <esp_idf_version.h>
#if __has_include(<rom/tjpgd.h>)
  #include <rom/tjpgd.h>            // dekoder JPEG di ROM ESP32-C3/S3: tidak makan flash program, RAM kerja cuma ~5 KB
  #define HAVE_ROM_JPEG 1
#else
  #define HAVE_ROM_JPEG 0
#endif

#if CONFIG_IDF_TARGET_ESP32C3
  #define PIN_SCK   4
  #define PIN_MOSI  6
  #define PIN_CS    7
  #define PIN_DC    3
  #define PIN_RST   2
  #define PIN_TOUCH 5
  #define PIN_BOOT  -1       // v22.35: GPIO9 bebas (dulu dipakai SCL MPU6050, sekarang tidak dipakai); tombol BOOT C3 tetap tidak dipakai
  #define PIN_BUZZ  10
#else                       // ESP32 biasa (VSPI)
  #define PIN_SCK   18
  #define PIN_MOSI  23
  #define PIN_CS    5
  #define PIN_DC    2
  #define PIN_RST   4
  #define PIN_TOUCH 27
  #define PIN_BOOT  0        // tombol BOOT di board ESP32 = GPIO0 (aktif LOW), berfungsi sama seperti TP223
  #define PIN_BUZZ  25
#endif

#if HAVE_SD
  #define SD_SCK   14          // microSD di bus HSPI sendiri (layar memakai VSPI), jadi tidak saling mengganggu
  #define SD_MISO  19
  #define SD_MOSI  13
  #define SD_CS    26          // hindari GPIO 12 (strapping tegangan flash) dan 6-11 (flash)
  #define SD_HZ    20000000
  #define SD_WALL      "/wallpaper.mnv"
  #define SD_WALL_TMP  "/wallpaper.tmp"
  #define SD_BOOT      "/bootanim.mnv"
  #define SD_BOOT_TMP  "/bootanim.tmp"
  #define SD_MAX_UP    (1800UL * 1024UL * 1024UL)  // v22.29: batas ukuran satu upload (kartu 2 GB; file FAT32 maks 4 GB)
  #define SD_DIR       "/wp"                       // v22.29: perpustakaan wallpaper: /wp/wNNNNN.mnv
  #define WP_LIST_MAX  64                          // jumlah wallpaper maksimum yang ditampilkan di menu / daftar aplikasi
  #define WP_MIG_BASE  65001                       // slot untuk /wallpaper.mnv lama yang dipindahkan otomatis
  #define SD_BDIR      "/boot"                     // v22.30: perpustakaan animasi boot: /boot/bNNNNN.mnv
  #define BT_LIST_MAX  64                          // jumlah animasi boot maksimum yang ditampilkan di menu / daftar aplikasi
  #define BT_MIG_BASE  65001                       // slot untuk /bootanim.mnv lama yang dipindahkan otomatis
#endif

#if HAVE_ROM_JPEG
  #define MAP_BLE 1          // v22.22: BLE ke aplikasi MotorNav = satu-satunya jalur data (butuh RAM ~40 KB)
#else
  #error "Butuh dekoder JPEG ROM (ESP32 / ESP32-S3 / C3 dengan Arduino-ESP32 core 3.x)"
#endif

#define ICON_MSB_FIRST   true   // ubah ke false jika arah dari ikon salah baca
#define RING_START_ANGLE 270    // sesuaikan jika cincin mulai dari sisi yang salah
#define RING_FULL_M      500.0f
#define MID_M            250.0f
#define NEAR_M           100.0f

#define STANDBY_MS       20000UL   // masuk standby setelah tanpa navigasi selama ini
#define FACE_SLEEP       false     // false: wajah standby TIDAK PERNAH tidur (animasi terus berganti); true: tidur seperti dulu
#if FACE_SLEEP
  #define SLEEP_MS       60000UL   // wajah tertidur setelah standby selama ini
#else
  #define SLEEP_MS       0x7FFFFFFFUL   // ~24 hari = praktis tidak pernah mengantuk/menguap/tidur
#endif

#define NOTIF_MS         8000UL    // lama animasi notifikasi (cukup untuk membaca isi pesan)
#define FRAME_MS         8         // jeda minimal antar frame animasi (8 = batas ~120 fps)
#define SHOW_FPS         false     // true: cetak FPS standby ke Serial Monitor tiap 2 detik
#define SPI_HZ           80000000UL // turunkan ke 40000000UL jika layar muncul noise

// --- Kanvas penuh (dipakai standby foto/video, wajah, notifikasi, game; v22.35: sudut miring MPU6050 dihapus, tampilan selalu tegak) ---
#define TILT_UI       true     // true: alokasikan kanvas penuh 240x240 (butuh ~115 KB RAM; dibutuhkan standby mode 1 foto/video)
                               // false: matikan kanvas penuh (kembali ke gambar langsung ke layar, hemat memori)
// --- Buzzer (aktif: bunyi saat pin HIGH) ---
#define BUZZER_ON true

#define HOR 84    // garis cakrawala
#define BOT 215   // tepi bawah jalan

// Kanvas penuh 240x240 dibagi dua blok 240x120 (2 x 57,6 KB). ESP32 biasa tidak punya satu blok RAM
// berurutan sebesar 115 KB (maks ~113 KB), sehingga kanvas tunggal gagal dialokasi: wajah, notifikasi,
// panggilan, dan game tidak tampil. Dua blok kecil selalu muat.
class SplitCanvas : public Arduino_GFX {
 public:
  static const int SH = 30;          // tinggi tiap blok (baris)
  static const int SN = 8;           // jumlah blok: 8 x 30 baris = 240 baris
  uint16_t *s[SN] = {nullptr};
  // Batas gambar per baris [lo..hi]. Mode 0 = bebas, 1 = persegi area animasi (x 36..203, y 56..191 = CVX/CVY/CVW/CVH),
  // 2 = lingkaran di dalam cincin (r 112). Dengan batas ini animasi yang keluar area tidak meninggalkan sisa gambar.
  uint8_t clipMode = 0;
  const int16_t *cLo = nullptr, *cHi = nullptr;
  int16_t clipLoT[3][240], clipHiT[3][240];
  SplitCanvas() : Arduino_GFX(240, 240) {
    for (int y = 0; y < 240; y++) {
      clipLoT[0][y] = 0; clipHiT[0][y] = 239;
      bool in = (y >= 56 && y < 56 + 136);
      clipLoT[1][y] = in ? 36 : 1; clipHiT[1][y] = in ? (36 + 168 - 1) : 0;
      float dy = y - 119.5f, r2 = 112.0f * 112.0f - dy * dy;
      if (r2 < 0) { clipLoT[2][y] = 1; clipHiT[2][y] = 0; }
      else { float q = sqrtf(r2); clipLoT[2][y] = (int16_t)ceilf(119.5f - q); clipHiT[2][y] = (int16_t)floorf(119.5f + q); }
    }
    setClip(0);
  }
  void setClip(uint8_t m) { clipMode = m; cLo = clipLoT[m]; cHi = clipHiT[m]; }
  bool begin(int32_t speed = GFX_NOT_DEFINED) override {
    (void)speed;
    for (int i = 0; i < SN; i++) {
      if (s[i]) continue;
      s[i] = (uint16_t *)malloc(240 * SH * 2);
      if (!s[i]) {
        for (int j = 0; j < SN; j++) { free(s[j]); s[j] = nullptr; }
        return false;
      }
    }
    return true;
  }
  inline uint16_t *row(int y) { return s[y / SH] + (y % SH) * 240; }
  void writePixelPreclipped(int16_t x, int16_t y, uint16_t c) override { if (x >= cLo[y] && x <= cHi[y]) row(y)[x] = c; }
  void writeFillRectPreclipped(int16_t x, int16_t y, int16_t w, int16_t h, uint16_t c) override {
    for (int j = 0; j < h; j++) {
      int yy = y + j;
      int x0 = (x > cLo[yy]) ? x : cLo[yy];
      int x1 = (x + w - 1 < cHi[yy]) ? (x + w - 1) : cHi[yy];
      if (x1 < x0) continue;
      uint16_t *p = row(yy);
      for (int i = x0; i <= x1; i++) p[i] = c;
    }
  }
  void writeFastVLine(int16_t x, int16_t y, int16_t h, uint16_t c) override {
    if (x < 0 || x >= 240 || h <= 0) return;
    if (y < 0) { h += y; y = 0; }
    if (y + h > 240) h = 240 - y;
    for (int j = 0; j < h; j++) { int yy = y + j; if (x >= cLo[yy] && x <= cHi[yy]) row(yy)[x] = c; }
  }
  void writeFastHLine(int16_t x, int16_t y, int16_t w, uint16_t c) override {
    if (y < 0 || y >= 240 || w <= 0) return;
    if (x < 0) { w += x; x = 0; }
    if (x + w > 240) w = 240 - x;
    int x0 = (x > cLo[y]) ? x : cLo[y];
    int x1 = (x + w - 1 < cHi[y]) ? (x + w - 1) : cHi[y];
    if (x1 < x0) return;
    uint16_t *p = row(y);
    for (int i = x0; i <= x1; i++) p[i] = c;
  }
};
#if TILT_UI
typedef SplitCanvas CanvasT;
#else
typedef Arduino_Canvas CanvasT;
#endif

Arduino_DataBus *bus = new Arduino_ESP32SPI(PIN_DC, PIN_CS, PIN_SCK, PIN_MOSI, GFX_NOT_DEFINED);
Arduino_GFX *panel = new Arduino_GC9A01(bus, PIN_RST, 0, true);   // layar fisik
Arduino_GFX *gfx = panel;          // target gambar: kanvas penuh (TILT_UI) atau layar langsung
CanvasT *fb = nullptr;      // kanvas penuh 240x240 (hanya jika TILT_UI)
void present();                    // dorong kanvas ke layar (dengan putaran jika TILT_UI)
void syncFB();                     // TILT_UI: tampilkan kanvas sekarang; selain itu kosong
int clockMinutes(bool connected);   // dideklarasikan di sini karena dipakai renderFace sebelum definisinya
#if TILT_UI
inline void useClip(uint8_t m) { if (fb) fb->setClip(m); }   // 0 bebas | 1 persegi area wajah/game | 2 lingkaran dalam cincin
#else
inline void useClip(uint8_t) {}
#endif
void asciiClean(const String &in, char *out, size_t cap);   // didefinisikan di bagian notifikasi

// v22.22: pengganti tipe Navigation milik library Chronos. active diisi dari aplikasi MotorNav (appNavOn); sisanya selalu kosong
// karena banner/peta digambar HP.
struct Navigation {
  bool active = false, hasIcon = false;
  String title, directions, distance, speed, eta, duration;
  uint8_t icon[288];
  uint32_t iconCRC = 0;
  Navigation() { memset(icon, 0, sizeof(icon)); }
};

// v22.22: audio & sampul dari aplikasi
#define ART_W      64
#define ART_BYTES  (ART_W * ART_W * 2)
static uint16_t *artBuf = nullptr;
static float artAmpG = 0;                                          // v22.41: 0..1, seberapa tampil sampul (dipakai menggeser progress bar)
static volatile uint16_t artGot = 0;
static volatile bool artOn = false;
#define CFG_N 13
static uint8_t cfg[CFG_N]           = {140, 1,  200, 1, 100, 100, 100, 1,  20,  1, 100, 0, 0};
static const uint8_t CFG_MIN[CFG_N] = {0,   0,  40,  0, 20,  20,  0,   0,  5,   0, 10,  0, 0};
static const uint8_t CFG_MAX[CFG_N] = {255, 1,  255, 1, 200, 200, 200, 1,  120, 1, 100, 1, 1};
// cfg[12] (v22.36): efek getar jalan di wajah standby, dari akselerometer HP lewat BLE (gimmick, MPU6050 fisik sudah dicabut sejak v22.35)
// cfg[11] = tema visualizer: 0 = cincin pelangi (lama), 1 = api (gradien merah-oranye-kuning berdasar tinggi batang)
static uint16_t dimK = 256;                                        // kecerahan layar 0..256 (256 = penuh, tanpa pengolahan)
static volatile uint32_t cfgDirtyAt = 0;                           // millis() perubahan terakhir (0 = tak ada yang perlu disimpan)
static void cfgClamp() { for (int i = 0; i < CFG_N; i++) cfg[i] = constrain(cfg[i], CFG_MIN[i], CFG_MAX[i]); dimK = (uint16_t)(cfg[10] * 256 / 100); }
static uint8_t  audBands[16];
static volatile uint16_t musPosS = 0, musDurS = 0;                 // posisi & durasi lagu (detik) dari HP
static volatile uint32_t musPosAt = 0;                             // millis() saat posisi diterima (0 = belum ada)
static volatile uint32_t audStamp = 0;                             // millis() paket spektrum terakhir (0 = belum pernah)
static inline bool audFresh() { return audStamp != 0 && millis() - audStamp < 600; }
static float audBass() {                                           // 0..1 dari 3 band terendah, atau -1 bila tak ada data
  if (!audFresh()) return -1.0f;
  return (audBands[0] + audBands[1] + audBands[2]) / (3.0f * 255.0f);
}

// v22.12: HP memakai aplikasi MotorNav (bukan Chronos). Paket kontrol dari aplikasi: [0xFF][cmd][...]
//   cmd 1 = jam  [jam][menit][detik]   cmd 2 = status [navAktif 0/1] (dikirim tiap ~2 dtk = tanda aplikasi tersambung)
volatile bool     appNav = false;
volatile uint32_t appStamp = 0;                                    // millis() paket status terakhir (0 = belum pernah)
volatile bool     appClockNew = false;
volatile uint8_t  appH = 0, appM = 0, appS = 0;
volatile uint32_t appClockMs = 0;
static inline bool appLinked() { return appStamp != 0 && millis() - appStamp < 8000; }
static inline bool appNavOn()  { return appLinked() && appNav; }

// v22.36: efek "getar jalan" di wajah standby -- MPU6050 fisik sudah dicabut (lihat catatan versi di atas), jadi sebagai
// gimmick opsional (cfg[12]) wajah bisa ikut bergoyang kecil memakai data akselerometer HP yang dikirim lewat BLE
// ([0xFF][0x34][intensitas 0..255], lihat MapCharCb). Tidak menggerakkan UI/rotasi apa pun selain wajah standby mode 0.
float    accVibTarget = 0, accVibLevel = 0;
uint32_t accVibStamp = 0;
float    vibJx = 0, vibJy = 0, vibJxT = 0, vibJyT = 0;
uint32_t vibRetargetAt = 0;
static inline bool isLinked()  { return appLinked(); }

enum Page { PAGE_NAV, PAGE_GAME, PAGE_GAME2, PAGE_GAME3, PAGE_COUNT };
enum Mood { MOOD_HAPPY, MOOD_SLEEP, MOOD_OFF };

uint8_t  page = PAGE_NAV;
bool     lastActive = false;
bool     lastTouch = false;
uint32_t lastTap = 0;
String   lastSig = "";

// Standby
const uint16_t *photoPtr = STANDBY_PHOTO;   // foto aktif: bawaan (flash program) atau hasil upload (partisi spiffs)
bool     standby = false;
bool     settingsActive = false, setDirty = false, lightTheme = false;   // menu pengaturan, tema terang
uint8_t  setIdx = 0;                                                     // item menu pengaturan
uint32_t setLastMs = 0;
uint8_t  standbyMode = 0;      // 0 = wajah hidup, 1 = foto + jam + cincin pelangi (tersimpan di memori)
uint32_t lastActivity = 0, standbyStart = 0;
uint32_t nextBlink = 0, blinkStart = 0, nextGaze = 0, nextHappy = 0, happyUntil = 0, lastFrame = 0;
bool     blinking = false;
float    gaze = 0, gazeTarget = 0;
int64_t  lastFaceKey = -1;
bool     faceConnected = true;

// Notifikasi
volatile bool notifPending = false;
char     notifApp[32] = "", notifTitle[48] = "", notifMsg[232] = "";
bool     notifActive = false;
uint32_t notifStart = 0, lastNotifFrame = 0;
long     lastNotifRing = -1;
uint8_t  notifStyle = 0, notifCount = 0;                // 0 lonceng, 1 chat, 2 amplop
// Panggilan masuk
volatile bool callStartPending = false, callEndPending = false;
char     callName[40] = "", callNum[24] = "", callLabel[24] = "";
// v22.15: notifikasi/telepon dari aplikasi MotorNav lewat BLE (logo asli aplikasi + teks panjang bergulir)
#define ICON_W        48                                           // logo aplikasi 48x48 RGB565 (4608 byte), 0x0000 = transparan
#define ICON_BYTES    (ICON_W * ICON_W * 2)
#define NT_W          14                                           // huruf per baris (ukuran 2 = 12 px/huruf, 168 px)
#define NT_ROWS       4                                            // baris per halaman
#define NT_LINES      20                                           // maks baris tersimpan (5 halaman)
#define NT_PAGE_MS    3400UL                                       // lama tiap halaman pesan
static uint16_t *iconBuf = nullptr;
static volatile uint16_t iconGot = 0;                              // byte logo yang sudah diterima
static char stApp[32] = "", stTitle[48] = "", stMsg[232] = "";     // penampung dari BLE (diambil saat startNotif)
static volatile bool stValid = false, stIcon = false, stDrive = false;
static volatile uint8_t stMore = 0;                                // jumlah notifikasi yang masih menunggu di antrean HP
static uint8_t notifMore = 0;
static bool notifHasIcon = false, notifDrive = false;   // notifDrive: mode berkendara (singkat, 1 halaman)
static char ntLines[NT_LINES][16];
static int  ntNL = 0, ntPages = 1;
static uint32_t notifDur = 8000;
bool     callActive = false;
uint32_t callStart = 0, lastCallFrame = 0;
long     lastCallRing = -1;

uint16_t COL_BG, COL_MAPBG, COL_ROAD, COL_EDGE, COL_HORIZON, COL_RING_BG;
uint16_t COL_BLD, COL_WIN1, COL_WIN2, COL_CHIP;
uint16_t COL_TEXT, COL_DIM;
uint16_t COL_GREEN, COL_ORANGE, COL_RED, COL_CHEEK;
uint16_t COL_GLOW_GREEN, COL_GLOW_ORANGE, COL_GLOW_RED;
uint16_t COL_TIRE, COL_JACKET, COL_HELMET, COL_VISOR, COL_TAIL;

// ---------- Helper ----------
String clip(const String &s, uint8_t maxLen) {
  if (s.length() <= maxLen) return s;
  return s.substring(0, maxLen - 1) + "~";
}

void drawCentered(const String &s, int y, uint8_t size, uint16_t color) {
  int16_t x1, y1;
  uint16_t w, h;
  gfx->setTextSize(size);
  gfx->getTextBounds(s, 0, 0, &x1, &y1, &w, &h);
  gfx->setTextColor(color);
  gfx->setCursor(120 - (int)w / 2 - x1, y);
  gfx->print(s);
}

void drawChipText(const String &s, int y, uint8_t size, uint16_t color, uint16_t bg) {
  int16_t x1, y1;
  uint16_t w, h;
  gfx->setTextSize(size);
  gfx->getTextBounds(s, 0, 0, &x1, &y1, &w, &h);
  gfx->fillRoundRect(120 - (int)w / 2 - 8, y - 3, w + 16, h + 6, 9, bg);
  gfx->setTextColor(color);
  gfx->setCursor(120 - (int)w / 2 - x1, y);
  gfx->print(s);
}

float parseMeters(String s) {
  s.replace(',', '.');
  float v = s.toFloat();
  String l = s;
  l.toLowerCase();
  if (l.indexOf("km") >= 0) v *= 1000.0f;
  return v;
}

// -1 = kiri, 0 = lurus, 1 = kanan
int turnDirection(const Navigation &nav) {
  String d = nav.directions;
  if (d.length() == 0) d = nav.title;
  d.toLowerCase();
  if (d.indexOf("left") >= 0 || d.indexOf("kiri") >= 0)   return -1;
  if (d.indexOf("right") >= 0 || d.indexOf("kanan") >= 0) return 1;

  // Teks tidak menyebut belokan (mis. "Ambil arah barat laut"): ikon hanya dipakai jika jarak sudah diketahui
  if (nav.hasIcon && nav.distance.length() > 0) {
    int minX = 48, maxX = -1;
    for (int y = 0; y < 48; y++) {
      for (int x = 0; x < 48; x++) {
        int idx = y * 48 + x;
        uint8_t b = nav.icon[idx / 8];
        bool on = ICON_MSB_FIRST ? (b & (0x80 >> (idx % 8))) : (b & (1 << (idx % 8)));
        if (on) {
          if (x < minX) minX = x;
          if (x > maxX) maxX = x;
        }
      }
    }
    if (maxX >= 0) {
      int leftExt = 24 - minX;
      int rightExt = maxX - 24;
      if (leftExt - rightExt > 12) return -1;
      if (rightExt - leftExt > 12) return 1;
    }
  }
  return 0;
}

void drawPageDots() {
  for (int i = 0; i < PAGE_COUNT; i++) {
    int x = 120 + (int)((i - (PAGE_COUNT - 1) / 2.0f) * (PAGE_COUNT > 7 ? 10 : 14));
    if (i == page) gfx->fillCircle(x, 229, 3, COL_TEXT);
    else           gfx->drawCircle(x, 229, 3, COL_DIM);
  }
}

void drawRing(float progress, uint16_t color) {
  gfx->fillArc(120, 120, 119, 113, 0, 360, COL_RING_BG);
  if (progress <= 0.01f) return;
  float start = RING_START_ANGLE;
  float end = start + 360.0f * progress;
  if (end <= 360.0f) {
    gfx->fillArc(120, 120, 119, 113, start, end, color);
  } else {
    gfx->fillArc(120, 120, 119, 113, start, 360, color);
    gfx->fillArc(120, 120, 119, 113, 0, end - 360.0f, color);
  }
}

// Transisi: iris menutup ke tengah dengan halus, lalu tampilan baru muncul
void irisClose() {                                                  // menutup dengan easing (smoothstep)
  int prev = 119;
  for (int i = 1; i <= 14; i++) {
    float t = i / 14.0f;
    int r = (int)(119.0f * (1.0f - t * t * (3.0f - 2.0f * t)));
    gfx->fillArc(120, 120, prev + 1, r, 0, 360, COL_BG);
    prev = r;
    syncFB();
  }
  gfx->fillCircle(120, 120, prev + 1, COL_BG);
  syncFB();
}

// ---------- Adegan jalan perspektif ----------
int hw(int y) { return 12 + (y - HOR) * 63 / (BOT - HOR); }  // setengah lebar jalan
int rw(int y) { return 3 + (y - HOR) * 16 / (BOT - HOR); }   // setengah lebar rute

void quad(int x0, int y0, int x1, int y1, int x2, int y2, int x3, int y3, uint16_t c) {
  gfx->fillTriangle(x0, y0, x1, y1, x2, y2, c);
  gfx->fillTriangle(x0, y0, x2, y2, x3, y3, c);
}

void drawBlock(int x, int y, int w, int h) {
  gfx->fillRoundRect(x, y, w, h, 4, COL_BLD);
  for (int wy = y + 5; wy < y + h - 5; wy += 9) {
    for (int wx = x + 5; wx < x + w - 5; wx += 8) {
      bool alt = (((wx - x) / 8 + (wy - y) / 9) % 2) != 0;
      gfx->fillRect(wx, wy, 3, 4, alt ? COL_WIN2 : COL_WIN1);
    }
  }
}

void drawRoute(int dir, int jy, int extra, uint16_t color) {
  int w = rw(jy) + extra;
  if (dir == 0) {
    quad(120 - 3 - extra, HOR + 4, 120 + 3 + extra, HOR + 4, 120 + w, jy, 120 - w, jy, color);
  }
  quad(120 - w, jy, 120 + w, jy, 120 + rw(BOT) + extra, BOT, 120 - rw(BOT) - extra, BOT, color);
  if (dir < 0)      gfx->fillRect(40, jy - w, 120 + w - 40, 2 * w, color);
  else if (dir > 0) gfx->fillRect(120 - w, jy - w, 200 - (120 - w), 2 * w, color);
}

void drawRider(int cx, int cy, uint16_t accent, uint16_t glow) {
  gfx->fillCircle(cx, cy + 12, 26, glow);                          // cahaya di bawah motor
  gfx->fillRoundRect(cx - 6, cy - 4, 12, 34, 5, COL_TIRE);         // ban belakang
  gfx->fillRoundRect(cx - 9, cy - 18, 18, 20, 7, accent);          // bodi
  gfx->fillRoundRect(cx - 5, cy - 14, 10, 4, 2, COL_TAIL);         // lampu belakang
  gfx->fillRoundRect(cx - 14, cy - 34, 28, 18, 8, COL_JACKET);     // jaket
  gfx->fillRect(cx - 22, cy - 26, 44, 3, COL_DIM);                 // setang
  gfx->fillCircle(cx, cy - 42, 8, COL_HELMET);                     // helm
  gfx->fillRect(cx - 6, cy - 45, 12, 3, COL_VISOR);                // visor
}

void drawScene(int dir, float meters, uint16_t accent, uint16_t glow) {
  float frac = constrain(meters, 0.0f, RING_FULL_M) / RING_FULL_M;
  int jy = 160 - (int)(frac * 48.0f);          // 160 = dekat, 112 = jauh
  int hs = 5 + (jy - HOR) * 18 / 100;          // setengah lebar jalan samping

  gfx->fillRect(0, HOR, 240, 240 - HOR, COL_MAPBG);
  gfx->drawFastHLine(0, HOR, 240, COL_HORIZON);

  drawBlock(14, 112, 34, 30);
  drawBlock(14, 156, 30, 34);
  drawBlock(192, 112, 34, 30);
  drawBlock(196, 156, 30, 34);

  quad(120 - hw(HOR), HOR, 120 + hw(HOR), HOR, 120 + hw(BOT), BOT, 120 - hw(BOT), BOT, COL_ROAD);
  gfx->drawLine(120 - hw(HOR), HOR, 120 - hw(BOT), BOT, COL_EDGE);
  gfx->drawLine(120 - hw(HOR) + 1, HOR, 120 - hw(BOT) + 1, BOT, COL_EDGE);
  gfx->drawLine(120 + hw(HOR), HOR, 120 + hw(BOT), BOT, COL_EDGE);
  gfx->drawLine(120 + hw(HOR) - 1, HOR, 120 + hw(BOT) - 1, BOT, COL_EDGE);
  gfx->fillRect(0, jy - hs, 120 - hw(jy), 2 * hs, COL_ROAD);
  gfx->fillRect(120 + hw(jy), jy - hs, 240 - (120 + hw(jy)), 2 * hs, COL_ROAD);
  gfx->fillRect(120 - hw(jy) - 1, jy - hs + 1, 4, 2 * hs - 2, COL_ROAD);
  gfx->fillRect(120 + hw(jy) - 2, jy - hs + 1, 4, 2 * hs - 2, COL_ROAD);

  drawRoute(dir, jy, 4, glow);
  drawRoute(dir, jy, 0, accent);
  int hh = rw(jy) + 9;
  if (dir < 0)      gfx->fillTriangle(16, jy, 42, jy - hh, 42, jy + hh, accent);
  else if (dir > 0) gfx->fillTriangle(224, jy, 198, jy - hh, 198, jy + hh, accent);
  else              gfx->fillTriangle(120, HOR, 106, HOR + 24, 134, HOR + 24, accent);

  drawRider(120, 186, accent, glow);
}

// ---------- Tampilan navigasi bergaya peta ----------
uint8_t navStyle = 0;                                              // 0 = peta (gaya Google Maps), 1 = jalan 3D + motor (lama)

bool mapDecode(const uint8_t *p, uint32_t len);                   // didefinisikan di bagian video (dekoder JPEG ROM)

#if MAP_BLE
void bleNotifText(uint16_t off, uint16_t tot, const uint8_t *d, size_t n);   // v22.15
void bleIconData(uint16_t off, const uint8_t *d, size_t n);
void bleCallStart(const uint8_t *d, size_t n);
void bleCallEnd();
void bleWpBegin(uint32_t size, uint8_t tgt = 0, uint16_t slot = 0);                  // v22.13: upload wallpaper lewat BLE (didefinisikan dekat wallpaperLoad)
void bleWpData(uint32_t off, const uint8_t *d, size_t n);
void bleWpEnd(uint32_t crc);
void bleWpClear(uint8_t tgt = 0);
void bleWpCmd(uint8_t t, uint16_t slot, uint8_t tgt = 0);                         // v22.29: 1 pilih, 2 hapus, 3 daftar; v22.30: tgt 1 = animasi boot
void bleWpTick();
bool bleWpBusy();
void bleOtaBegin(uint32_t size);                                   // v22.31: update firmware (OTA) lewat BLE
void bleOtaData(uint32_t off, const uint8_t *d, size_t n);
void bleOtaEnd(uint32_t crc);
void bleOtaInfo();
void bleOtaAbort();
static NimBLECharacteristic *mapChr = nullptr;

// ================= v22.33: KENDALI MOTOR LEWAT HP (modul relay 2 channel) =================
// Relay 1 = KUNCI KONTAK (tahan ON/OFF), relay 2 = STARTER (ditekan 2 detik lalu otomatis lepas).
// Perintah dari aplikasi: [0xFF][0x30][on/off][kode 6 char]            kunci kontak
//                         [0xFF][0x31][ms u16 LE][kode 6 char]         starter (ms dibatasi 300..3000)
//                         [0xFF][0x32][0]                             minta status
// Balasan notify: [0xE5][kunci kontak 0/1][starter 0/1][galat]  galat: 0 tidak ada, 1 kode salah, 2 kunci kontak mati, 3 terlalu cepat, 4 ganti kode ditolak (lewat 2 menit), 5 kode berhasil diganti, 6 kode baru tidak valid
#if CONFIG_IDF_TARGET_ESP32C3
  #define PIN_RLY_IGN    1
  #define PIN_RLY_START  20
#else
  #define PIN_RLY_IGN    32      // IN1 modul relay -> kunci kontak
  #define PIN_RLY_START  33      // IN2 modul relay -> starter
#endif
#define RLY_ACTIVE_LOW    1      // 1 = modul relay aktif LOW (paling umum); 0 = aktif HIGH
#define RLY_PIN_CODE      "258525" // kode keamanan perintah motor, tepat 6 karakter; harus sama dengan kode di aplikasi (tab Motor)
#define RLY_RESET_WINDOW_MS 120000UL // ganti kode (lupa) hanya diterima sejak ESP32 menyala selama ini
#define RLY_START_MIN_MS  300
#define RLY_START_MAX_MS  3000   // batas keras: starter tidak pernah ditahan lebih lama dari ini
#define RLY_SETTLE_MS     800    // starter baru boleh setelah kunci kontak menyala selama ini
#define RLY_COOLDOWN_MS   4000   // jeda minimum antar starter (lindungi motor starter)
#include <esp_timer.h>
#include <esp_system.h>
static volatile bool rlyIgn = false, rlyStart = false, rlyNotify = false, rlySave = false;
static volatile uint8_t rlyErr = 0;
static uint32_t rlyIgnAt = 0, rlyStartAt = 0, rlyLastStart = 0;
static bool rlyEverStarted = false;
static esp_timer_handle_t rlyTimer = nullptr;
static char rlyCode[8] = RLY_PIN_CODE;                             // kode aktif (bawaan = RLY_PIN_CODE, bisa diganti dari aplikasi, tersimpan di NVS)
static volatile bool rlyCodeSave = false;

static inline void rlyWrite(int pin, bool on) { digitalWrite(pin, (on ^ (RLY_ACTIVE_LOW ? 1 : 0)) ? HIGH : LOW); }
static void rlyStarterOff() { rlyWrite(PIN_RLY_START, false); rlyStart = false; rlyNotify = true; }
static void rlyTimerCb(void *) { rlyStarterOff(); }               // dijalankan timer perangkat keras (esp_timer): tetap lepas walau loop() sedang sibuk

static void rlyInit() {
  Preferences p; p.begin("nav", true); bool was = p.getUChar("ign", 0) != 0; String mc = p.getString("mcode", ""); p.end();
  if (mc.length() == 6) memcpy(rlyCode, mc.c_str(), 7);
  esp_reset_reason_t r = esp_reset_reason();
  bool soft = (r == ESP_RST_SW || r == ESP_RST_PANIC || r == ESP_RST_INT_WDT || r == ESP_RST_TASK_WDT || r == ESP_RST_WDT);
  // restart perangkat lunak (OTA, crash) saat kunci kontak ON -> nyalakan lagi, supaya mesin tidak mati di jalan. Cabut/pasang aki = mulai dari OFF.
  rlyIgn = soft && was;
  rlyWrite(PIN_RLY_IGN, rlyIgn); rlyWrite(PIN_RLY_START, false);   // tulis level dulu, baru jadikan output (hindari kedipan relay)
  pinMode(PIN_RLY_IGN, OUTPUT); pinMode(PIN_RLY_START, OUTPUT);
  rlyIgnAt = millis();
  if (!rlyIgn && was) { p.begin("nav", false); p.putUChar("ign", 0); p.end(); }
  esp_timer_create_args_t a = {}; a.callback = rlyTimerCb; a.name = "rlystart";
  esp_timer_create(&a, &rlyTimer);
}

static void rlySetIgn(bool on) {
  if (!on && rlyStart) { esp_timer_stop(rlyTimer); rlyStarterOff(); }   // kunci kontak mati => starter pasti lepas dulu
  rlyIgn = on; rlyWrite(PIN_RLY_IGN, on);
  if (on) rlyIgnAt = millis();
  rlySave = true; rlyNotify = true;
}

static void rlyStartMotor(uint16_t ms) {
  if (!rlyIgn) { rlyErr = 2; rlyNotify = true; return; }
  if (rlyStart || millis() - rlyIgnAt < RLY_SETTLE_MS || (rlyEverStarted && millis() - rlyLastStart < RLY_COOLDOWN_MS)) { rlyErr = 3; rlyNotify = true; return; }
  if (ms < RLY_START_MIN_MS) ms = RLY_START_MIN_MS;
  if (ms > RLY_START_MAX_MS) ms = RLY_START_MAX_MS;
  rlyEverStarted = true; rlyLastStart = rlyStartAt = millis();
  rlyStart = true; rlyWrite(PIN_RLY_START, true);
  esp_timer_start_once(rlyTimer, (uint64_t)ms * 1000ULL);
  rlyNotify = true;
}

static void rlyCmd(const uint8_t *d, size_t n) {                   // d[1] = 0x30..0x33
  if (d[1] == 0x33) {                                              // [FF][33][kode baru 6 angka]: ganti kode TANPA kode lama (lupa kode); hanya 2 menit pertama setelah ESP32 menyala
    if (millis() > RLY_RESET_WINDOW_MS) { rlyErr = 4; rlyNotify = true; return; }
    bool ok = n >= 8;
    for (int i = 0; ok && i < 6; i++) ok = d[2 + i] >= '0' && d[2 + i] <= '9';
    if (!ok) { rlyErr = 6; rlyNotify = true; return; }
    memcpy(rlyCode, d + 2, 6); rlyCode[6] = 0; rlyCodeSave = true; rlyErr = 5; rlyNotify = true;
    return;
  }
  if (d[1] == 0x32) { rlyNotify = true; return; }
  size_t at = (d[1] == 0x30) ? 3 : 4;
  if (n < at + 6 || memcmp(d + at, rlyCode, 6) != 0) { rlyErr = 1; rlyNotify = true; return; }
  if (d[1] == 0x30) rlySetIgn(d[2] != 0);
  else rlyStartMotor(d[2] | (d[3] << 8));
}

static void rlyOnDisconnect() {                                    // HP putus saat starter ditekan => lepas seketika. Kunci kontak dibiarkan (mesin tetap hidup)
  if (rlyStart) { esp_timer_stop(rlyTimer); rlyStarterOff(); }
}

static void rlyTick() {
  if (rlyStart && millis() - rlyStartAt > RLY_START_MAX_MS + 300) rlyStarterOff();   // jaring pengaman kedua
  if (rlyCodeSave) { rlyCodeSave = false; Preferences p; p.begin("nav", false); p.putString("mcode", rlyCode); p.end(); }
  if (rlySave) { rlySave = false; Preferences p; p.begin("nav", false); p.putUChar("ign", rlyIgn ? 1 : 0); p.end(); }
  if (rlyNotify) {
    rlyNotify = false;
    if (mapChr) { uint8_t b[4] = {0xE5, (uint8_t)rlyIgn, (uint8_t)rlyStart, rlyErr}; rlyErr = 0; mapChr->notify(b, 4); }
  }
}
// v22.21: info lagu dari aplikasi (diisi di callback BLE, dibaca pollMusic)
static char appMusTitle[64] = "", appMusArtist[48] = "";
static volatile uint8_t  appMusState = 0;
static volatile uint32_t appMusStamp = 0;                          // millis() paket musik terakhir (0 = belum pernah)
static volatile bool     appMusNew = false;
void bleSendEvent(uint8_t code);                                   // v22.19: kabar ke HP lewat notify: [0xE1][kode], kode 1 = tolak telepon
// Frame peta dari HP: paket = [id u8][offset u16 LE][total u16 LE][data...]; frame lengkap -> JPEG 240x240.
#define MAP_SVC_UUID "4d4e0001-7a1e-4c1b-9d52-6f2d4e1a0001"
#define MAP_CHR_UUID "4d4e0002-7a1e-4c1b-9d52-6f2d4e1a0001"
#define MAP_BUF_SZ   12000
static uint8_t *mapBuf[2] = {nullptr, nullptr};
static volatile uint8_t  mapAct = 0;                               // buffer berisi frame lengkap terakhir
static volatile uint16_t mapLen[2] = {0, 0};
static volatile uint32_t mapStamp = 0;                             // millis() frame lengkap terakhir (0 = belum ada)
static uint8_t  mapFid = 255, mapFill = 1;
static uint16_t mapGot = 0, mapTot = 0;
static volatile int mapConn = -1;                                  // handle koneksi HP-peta (dikenali dari tulisan pertama)
static int mapTotalConn = 0;

class MapCharCb : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic *c, NimBLEConnInfo &ci) override {
    mapConn = ci.getConnHandle();
    NimBLEAttValue v = c->getValue();
    const uint8_t *d = v.data();
    size_t n = v.size();
    if (n >= 3 && d[0] == 0xFF) {                                  // paket kontrol dari aplikasi MotorNav
      if (d[1] == 1 && n >= 5) { appH = d[2]; appM = d[3]; appS = d[4]; appClockMs = millis(); appClockNew = true; }
      else if (d[1] == 2) { appNav = d[2] != 0; appStamp = millis(); }
      else if (d[1] == 0x10 && n >= 6) bleWpBegin(d[2] | (d[3] << 8) | (d[4] << 16) | ((uint32_t)d[5] << 24));
      else if (d[1] == 0x11 && n >= 7) bleWpData(d[2] | (d[3] << 8) | (d[4] << 16) | ((uint32_t)d[5] << 24), d + 6, n - 6);
      else if (d[1] == 0x13) bleWpClear(0);
      else if (d[1] == 0x14 && n >= 6) bleWpBegin(d[2] | (d[3] << 8) | (d[4] << 16) | ((uint32_t)d[5] << 24), 1);   // v22.25: animasi boot
      else if (d[1] == 0x17) bleWpClear(1);
      else if (d[1] == 0x15 && n >= 8) bleWpBegin(d[2] | (d[3] << 8) | (d[4] << 16) | ((uint32_t)d[5] << 24), 0, d[6] | (d[7] << 8));   // v22.29: upload ke slot perpustakaan
      else if (d[1] == 0x18) bleWpCmd(3, 0);                                                              // minta daftar wallpaper di SD
      else if (d[1] == 0x19 && n >= 4) bleWpCmd(1, d[2] | (d[3] << 8));                                   // pilih wallpaper (0 = bawaan)
      else if (d[1] == 0x1B && n >= 8) bleWpBegin(d[2] | (d[3] << 8) | (d[4] << 16) | ((uint32_t)d[5] << 24), 1, d[6] | (d[7] << 8));   // v22.30: upload animasi boot ke slot
      else if (d[1] == 0x1C) bleWpCmd(3, 0, 1);                                                           // minta daftar animasi boot di SD
      else if (d[1] == 0x1D && n >= 4) bleWpCmd(1, d[2] | (d[3] << 8), 1);                                // pilih animasi boot (0 = tanpa animasi)
      else if (d[1] == 0x1E && n >= 4) bleWpCmd(2, d[2] | (d[3] << 8), 1);                                // hapus animasi boot di SD
      else if (d[1] == 0x1A && n >= 4) bleWpCmd(2, d[2] | (d[3] << 8));                                   // hapus wallpaper di SD
      else if (d[1] == 0x20 && n >= 7) bleNotifText(d[2] | (d[3] << 8), d[4] | (d[5] << 8), d + 6, n - 6);   // teks notifikasi (berpotong-potong)
      else if (d[1] == 0x21 && n >= 5) bleIconData(d[2] | (d[3] << 8), d + 4, n - 4);                        // logo aplikasi
      else if (d[1] == 0x22 && n >= 4) bleCallStart(d + 2, n - 2);                                          // telepon masuk
      else if (d[1] == 0x23) bleCallEnd();
      else if (d[1] == 0x24 && n >= 5) {                                                                    // v22.21: info lagu dari HP
        size_t tl = d[3], al = d[4];
        if (tl > 63) tl = 63;
        if (al > 47) al = 47;
        if (5 + tl > n) tl = n - 5;
        if (5 + tl + al > n) al = n - 5 - tl;
        if (strlen(appMusTitle) != tl || memcmp(appMusTitle, d + 5, tl) != 0) { artOn = false; musPosS = 0; musDurS = 0; musPosAt = millis(); }   // lagu berganti: sampul lama dibuang
        memcpy(appMusTitle, d + 5, tl); appMusTitle[tl] = 0;
        memcpy(appMusArtist, d + 5 + tl, al); appMusArtist[al] = 0;
        appMusState = d[2];
        appMusStamp = millis();
        appMusNew = true;
      }
      else if (d[1] == 0x29 && n >= 6) { musPosS = d[2] | (d[3] << 8); musDurS = d[4] | (d[5] << 8); musPosAt = millis(); }   // posisi lagu
      else if (d[1] == 0x25 && n >= 3) {                                                                    // v22.22: spektrum audio asli
        size_t k = d[2]; if (k > 16) k = 16; if (k > n - 3) k = n - 3;
        if (k >= 4) { for (size_t i = 0; i < 16; i++) audBands[i] = d[3 + (i * k) / 16]; audStamp = millis(); }
      }
      else if (d[1] == 0x28 && n >= 3) {                                                                    // v22.23: pengaturan dari aplikasi
        size_t k = n - 2; if (k > CFG_N) k = CFG_N;
        memcpy(cfg, d + 2, k); cfgClamp(); cfgDirtyAt = millis();
      }
      else if (d[1] == 0x34 && n >= 3) { accVibTarget = d[2] / 255.0f; accVibStamp = millis(); }             // v22.36: getar jalan dari akselerometer HP
      else if (d[1] == 0x26 && n >= 5 && artBuf) {                                                          // sampul album (berurutan)
        uint16_t off = d[2] | (d[3] << 8); size_t k = n - 4;
        if (off == 0) { artGot = 0; artOn = false; }
        if (off == artGot && (size_t)off + k <= ART_BYTES) { memcpy((uint8_t *)artBuf + off, d + 4, k); artGot = off + k; }
      }
      else if (d[1] == 0x27 && n >= 3) artOn = (d[2] != 0) && artBuf && artGot >= ART_BYTES;                                                                  // telepon selesai
      else if (d[1] == 0x2A && n >= 6) bleOtaBegin(d[2] | (d[3] << 8) | (d[4] << 16) | ((uint32_t)d[5] << 24));          // v22.31: OTA mulai
      else if (d[1] == 0x2B && n >= 7) bleOtaData(d[2] | (d[3] << 8) | (d[4] << 16) | ((uint32_t)d[5] << 24), d + 6, n - 6);   // OTA isi
      else if (d[1] == 0x2C && n >= 6) bleOtaEnd(d[2] | (d[3] << 8) | (d[4] << 16) | ((uint32_t)d[5] << 24));            // OTA selesai (CRC-32)
      else if (d[1] == 0x2D) bleOtaInfo();                                                                                // OTA info (balasan notify 0xE4)
      else if (d[1] >= 0x30 && d[1] <= 0x33) rlyCmd(d, n);                                                                // v22.33: kunci kontak / starter lewat relay
      else if (d[1] == 0x2E) bleOtaAbort();                                                                               // OTA batal
      else if (d[1] == 0x12 && n >= 6) bleWpEnd(d[2] | (d[3] << 8) | (d[4] << 16) | ((uint32_t)d[5] << 24));
      return;
    }
    if (bleWpBusy()) return;                                       // sedang upload wallpaper: frame peta diabaikan
    if (n < 6 || !mapBuf[0] || !mapBuf[1]) return;
    uint8_t fid = d[0];
    uint16_t off = d[1] | (d[2] << 8), tot = d[3] | (d[4] << 8);
    if (tot == 0 || tot > MAP_BUF_SZ) return;
    if (fid != mapFid || tot != mapTot) { mapFid = fid; mapTot = tot; mapGot = 0; mapFill = mapAct ^ 1; }
    size_t pl = n - 5;
    if ((size_t)off + pl > tot) return;
    memcpy(mapBuf[mapFill] + off, d + 5, pl);
    mapGot += pl;
    if (mapGot >= mapTot) {                                        // frame lengkap
      mapLen[mapFill] = mapTot; mapAct = mapFill; mapStamp = millis();
      mapFid = 255; mapGot = 0; mapTot = 0;
    }
  }
};
static MapCharCb mapCharCb;

class MapSrvCb : public NimBLEServerCallbacks {
  void onConnect(NimBLEServer *s, NimBLEConnInfo &ci) override { mapTotalConn++; }
  void onDisconnect(NimBLEServer *s, NimBLEConnInfo &ci, int reason) override {
    if (mapTotalConn > 0) mapTotalConn--;
    rlyOnDisconnect();
    if ((int)ci.getConnHandle() == mapConn) { mapConn = -1; mapStamp = 0; }
    appStamp = 0; appMusStamp = 0; audStamp = 0; artOn = false; accVibStamp = 0;   // HP putus: semua status dari HP dianggap hilang
    NimBLEDevice::startAdvertising();
  }
};
static MapSrvCb mapSrvCb;

static bool mapFresh() { return mapStamp != 0 && millis() - mapStamp < 4000; }
#endif

static void mapArrow(int x, int y, int dir, uint16_t c) {          // panah belok putih dalam kotak 36x36
  if (dir < 0) {
    gfx->fillRect(x + 17, y + 20, 6, 16, c); gfx->fillRect(x + 8, y + 17, 15, 6, c);
    gfx->fillTriangle(x + 1, y + 20, x + 13, y + 7, x + 13, y + 33, c);
  } else if (dir > 0) {
    gfx->fillRect(x + 13, y + 20, 6, 16, c); gfx->fillRect(x + 13, y + 17, 15, 6, c);
    gfx->fillTriangle(x + 35, y + 20, x + 23, y + 7, x + 23, y + 33, c);
  } else {
    gfx->fillRect(x + 15, y + 14, 6, 22, c);
    gfx->fillTriangle(x + 18, y + 1, x + 5, y + 16, x + 31, y + 16, c);
  }
}

static void mapText(const char *t, int x, int y, uint8_t sz, uint16_t c) {
  gfx->setTextSize(sz); gfx->setTextColor(c); gfx->setCursor(x, y); gfx->print(t);
}

static void drawNavMap(const Navigation &nav, int dir, bool noDist, float meters, float progress, uint16_t accent, bool real) {
  const uint16_t MBG = gfx->color565(12, 28, 64), MROAD = gfx->color565(30, 62, 120), MROAD2 = gfx->color565(21, 44, 90);
  const uint16_t RT_O = gfx->color565(20, 90, 200), RT = gfx->color565(100, 185, 255);
  const uint16_t BAN = gfx->color565(22, 104, 158), CH = gfx->color565(10, 20, 46), CWHT = 0xFFFF;
  float frac = constrain(noDist ? RING_FULL_M : meters, 0.0f, RING_FULL_M) / RING_FULL_M;
  int jy = 150 - (int)(frac * 50.0f);                              // persimpangan: dekat (150) ... jauh (100)

  bool drew = false;
#if MAP_BLE
  if (real) drew = mapDecode(mapBuf[mapAct], mapLen[mapAct]);        // peta asli dari HP (posisi/arah sudah digambar HP)
#endif
  if (!drew) {
  gfx->fillScreen(MBG);
  for (int k = -2; k <= 2; k++) if (k) gfx->fillRect(0, jy + k * 64 - 4, 240, 8, MROAD2);   // jalan lain (bergeser saat mendekat)
  gfx->fillRect(28, 0, 8, 240, MROAD2);
  gfx->fillRect(204, 0, 8, 240, MROAD2);
  gfx->fillRect(0, jy - 11, 240, 22, MROAD);                       // jalan yang disilang
  gfx->fillRect(107, 0, 26, 240, MROAD);                           // jalan utama

  for (int pass = 0; pass < 2; pass++) {                           // rute: garis tepi dulu, lalu isi
    int w = pass ? 10 : 16; uint16_t c = pass ? RT : RT_O;
    gfx->fillRect(120 - w / 2, jy - w / 2, w, 240 - jy + w / 2, c);
    if (dir < 0)      gfx->fillRect(44, jy - w / 2, 120 - 44 + w / 2, w, c);
    else if (dir > 0) gfx->fillRect(120 - w / 2, jy - w / 2, 196 - 120 + w / 2, w, c);
    else              gfx->fillRect(120 - w / 2, 100, w, jy - 100, c);
    gfx->fillCircle(120, jy, w / 2, c);
    int e = pass ? 0 : 3;                                          // kepala panah di ujung rute
    if (dir < 0)      gfx->fillTriangle(30 - e, jy, 50 + e, jy - 11 - e, 50 + e, jy + 11 + e, c);
    else if (dir > 0) gfx->fillTriangle(210 + e, jy, 190 - e, jy - 11 - e, 190 - e, jy + 11 + e, c);
    else              gfx->fillTriangle(120, 92 - e, 108 - e, 110 + e, 132 + e, 110 + e, c);
  }

  gfx->fillTriangle(120, 150, 105, 173, 135, 173, gfx->color565(50, 105, 215));   // posisi saya: kerucut arah + titik
  gfx->fillCircle(120, 178, 11, CWHT);
  gfx->fillCircle(120, 178, 8, gfx->color565(66, 133, 244));
  }

  char t1[40], t2[40];
  asciiClean(nav.title, t1, sizeof(t1));
  asciiClean(nav.directions, t2, sizeof(t2));
  String street = t1[0] ? String(t1) : String(t2);
  String instr  = (t1[0] && strcmp(t1, t2) != 0) ? String(t2) : String("");
  String distTxt = nav.distance;
  if (noDist) distTxt = (dir < 0) ? "Kiri" : (dir > 0 ? "Kanan" : "Lurus");

  gfx->fillRoundRect(44, 40, 152, 56, 12, BAN);                    // banner atas
  mapArrow(50, 50, dir, CWHT);
  char b[24];
  strlcpy(b, clip(distTxt, 8).c_str(), sizeof(b));  mapText(b, 94, 46, 2, accent);
  strlcpy(b, clip(street, 16).c_str(), sizeof(b));  mapText(b, 94, 66, 1, CWHT);
  if (instr.length()) { strlcpy(b, clip(instr, 16).c_str(), sizeof(b)); mapText(b, 94, 78, 1, gfx->color565(190, 225, 245)); }

  String etaT = nav.eta; int cm = etaT.indexOf(','); if (cm > 0) etaT = etaT.substring(0, cm);
  String bt = etaT.length() ? String("ETA ") + etaT : String("");
  if (nav.duration.length()) bt += (bt.length() ? "  " : "") + nav.duration;
  if (bt.length()) drawChipText(clip(bt, 24), 206, 1, COL_TEXT, CH);

  String num = "";                                                 // lingkaran kecepatan
  for (unsigned i = 0; i < nav.speed.length(); i++) {
    char ch = nav.speed[i];
    if (ch >= '0' && ch <= '9') num += ch; else if (num.length()) break;
  }
  if (num.length()) {
    gfx->fillCircle(190, 162, 21, CH); gfx->drawCircle(190, 162, 21, CWHT);
    int nw = num.length() * 12;
    mapText(num.c_str(), 190 - nw / 2, 152, 2, CWHT);
    mapText("km/j", 190 - 12, 171, 1, gfx->color565(150, 190, 230));
  }
  drawRing(progress, accent);
  drawPageDots();
}

void drawNavPage(const Navigation &nav, bool connected) {
#if MAP_BLE
  if (appNavOn()) {                                                // v22.12: aplikasi MotorNav menggambar seluruh layar (peta + banner + kecepatan)
    if (mapFresh() && mapDecode(mapBuf[mapAct], mapLen[mapAct])) return;
    // v22.41: peta belum fresh -> biarkan tampilan sebelumnya saja (tidak lagi tampil "Menunggu peta...").
    // nav.active hanya aktif setelah frame peta pertama datang, jadi kondisi ini seharusnya jarang/singkat.
    return;
  }
#endif
  gfx->fillScreen(COL_BG);

  if (!connected) {
    drawRing(1.0f, COL_RED);
    drawCentered("Hubungkan", 85, 3, COL_RED);
    drawCentered("aplikasi", 118, 3, COL_TEXT);
    drawPageDots();
    return;
  }
  if (!nav.active) {
    drawRing(1.0f, COL_GREEN);
    if (appLinked()) {
      drawCentered("Pilih", 85, 3, COL_TEXT);
      drawCentered("tujuan di aplikasi", 118, 2, COL_DIM);
    } else {
      drawCentered("Hubungkan", 85, 3, COL_TEXT);
      drawCentered("aplikasi", 118, 3, COL_DIM);
    }
    drawPageDots();
    return;
  }

  float meters = parseMeters(nav.distance);
  bool noDist = (nav.distance.length() == 0 || meters <= 0.0f);     // mis. awal rute "Ambil arah ...": belum ada jarak belokan
  float progress = noDist ? 0.0f : 1.0f - constrain(meters / RING_FULL_M, 0.0f, 1.0f);
  uint16_t accent = COL_GREEN, glow = COL_GLOW_GREEN;
  if (!noDist && meters < NEAR_M)      { accent = COL_RED;    glow = COL_GLOW_RED; }
  else if (!noDist && meters < MID_M)  { accent = COL_ORANGE; glow = COL_GLOW_ORANGE; }
  int dir = turnDirection(nav);

  if (navStyle != 1) {
    bool real = false;
#if MAP_BLE
    real = (navStyle == 2) && mapFresh();
#endif
    drawNavMap(nav, dir, noDist, meters, progress, accent, real);
    return;
  }
  drawScene(dir, noDist ? RING_FULL_M : meters, accent, glow);   // tanpa jarak: persimpangan digambar jauh di depan
  drawRing(progress, accent);

  String spd = nav.speed.length() ? nav.speed : String("--");
  drawCentered(clip(spd, 4), 8, 3, COL_TEXT);
  String distTxt = nav.distance;
  if (noDist) distTxt = (dir < 0) ? "Kiri" : (dir > 0 ? "Kanan" : "Lurus");
  drawCentered(clip(distTxt, 7), 31, 3, accent);

  String ttl = nav.title.length() ? nav.title : nav.directions;
  if (ttl.length() <= 11) {
    drawChipText(ttl, 57, 2, COL_TEXT, COL_CHIP);
  } else {                                                           // teks panjang: ukuran kecil, maksimal 2 baris
    String l1 = ttl, l2 = "";
    if (ttl.length() > 22) {
      int cut = ttl.lastIndexOf(' ', 22);
      if (cut < 6) cut = 22;
      l1 = ttl.substring(0, cut);
      l2 = ttl.substring(cut);
      l2.trim();
      l2 = clip(l2, 22);
    }
    int16_t x1, y1; uint16_t w1, h1, w2 = 0;
    gfx->setTextSize(1);
    gfx->getTextBounds(l1, 0, 0, &x1, &y1, &w1, &h1);
    if (l2.length()) { gfx->getTextBounds(l2, 0, 0, &x1, &y1, &w2, &h1); }
    uint16_t wm = max(w1, w2);
    int ch = l2.length() ? 26 : 16;
    gfx->fillRoundRect(120 - (int)wm / 2 - 8, 55, wm + 16, ch, 8, COL_CHIP);
    gfx->setTextColor(COL_TEXT);
    gfx->getTextBounds(l1, 0, 0, &x1, &y1, &w1, &h1);
    gfx->setCursor(120 - (int)w1 / 2 - x1, 59);
    gfx->print(l1);
    if (l2.length()) {
      gfx->getTextBounds(l2, 0, 0, &x1, &y1, &w2, &h1);
      gfx->setCursor(120 - (int)w2 / 2 - x1, 69);
      gfx->print(l2);
    }
  }
  drawPageDots();
}

// ---------- (v22.35: modul voltase aki dihapus) ----------

// ---------- Mode standby: wajah hidup (kanvas, 30 fps) ----------
#define CVX 36
#define CVY 56
#define CVW 168
#define CVH 136
#if TILT_UI
  #define OX 0                // kanvas penuh: koordinat layar = koordinat kanvas
  #define OY 0
#else
  #define OX CVX              // kanvas kecil di posisi (CVX, CVY)
  #define OY CVY
#endif

#if defined(GFX_SKIP_OUTPUT_BEGIN)
  #define CANVAS_BEGIN_ARG GFX_SKIP_OUTPUT_BEGIN
#elif defined(GFX_SKIP_DISPLAY_BEGIN)
  #define CANVAS_BEGIN_ARG GFX_SKIP_DISPLAY_BEGIN
#else
  #define CANVAS_BEGIN_ARG GFX_NOT_DEFINED
#endif

CanvasT *cv = nullptr;
bool canvasOK = false;
inline void cvClear() { cv->fillRect(CVX - OX, CVY - OY, CVW, CVH, COL_BG); }   // hapus area animasi saja
inline void cvClearAll() { cv->fillRect(0, 0, 240, 240, COL_BG); }                 // hapus seluruh kanvas (dibatasi mode clip)
uint16_t COL_MOUTH, COL_TONGUE, COL_TEAR;

enum FaceEv { EV_NONE, EV_HAPPY, EV_LOVE, EV_WINK, EV_SURPRISE, EV_CURIOUS, EV_DIZZY,
              EV_MUSIC, EV_COOL, EV_SNEEZE, EV_VROOM, EV_TONGUE, EV_PEEK,
              EV_SHY, EV_ANGRY, EV_SPARKLE, EV_KISS, EV_THINK, EV_DANCE,
              EV_LAUGH, EV_CRY, EV_STAREYES, EV_HELMET, EV_BIKE, EV_COFFEE, EV_HELLO, EV_RADAR,
              EV_SIGNAL, EV_BATT, EV_RAIN, EV_ROCKET, EV_PONG, EV_FLAG, EV_FUEL, EV_BALLOON,
              EV_FIREWORK, EV_MAGNIFY, EV_GAME, EV_GHOST, EV_CAT, EV_THUNDER, EV_TRAFFIC, EV_EAT,
              EV_YAWN, EV_COUNT };
const uint16_t EV_DUR[EV_COUNT] = {0, 1500, 2800, 1000, 1500, 3400, 2400, 3800, 3400, 1900, 2400, 1700, 2200, 2600, 2800, 2600, 2400, 3400, 3800,
                                  2400, 3200, 2800, 3000, 3600, 3800, 2600, 3400,
                                  3200, 3400, 3400, 3200, 3800, 3000, 3200, 3400,
                                  3600, 3600, 3800, 3000, 3400, 3000, 4000, 3600, 3200};

struct FaceP {
  float openL, openR, happy, lookX, lookY, browY, browR, browTilt, browA,
        smile, mouthOpen, mouthW, tongue, blush, love, spiral, jump, shake;
};
FaceP Fc = {1, 1, 0, 0, 0, 0, 0, 0, 0, 0.5f, 0, 28, 0, 0.3f, 0, 0, 0, 0};
FaceP Ft = Fc;

int      curEv = EV_NONE, lastEv = EV_NONE;
uint32_t evStartMs = 0, evEndMs = 0, nextEvMs = 0;
bool     yawned = false, dblBlink = false;
float    gzTx = 0, gzTy = 0;
int      fxEv = EV_NONE;          // aksi yang efek tambahannya sedang digambar (bisa fade keluar)
float    fxFade = 0, frameDt = 20;
int      bgCur = 0, bgPrev = 0;   // gaya latar belakang sekarang / sebelumnya
float    bgMix = 1;
uint32_t bgNext = 0;
float    glR = 10, glG = 60, glB = 28;   // warna cahaya latar (dihaluskan)
float    bellScale = 1;

const uint8_t STAR_X[5] = {70, 170, 120, 88, 152};
const uint8_t STAR_Y[5] = {38, 42, 26, 206, 204};
int8_t   starSz[5] = {-1, -1, -1, -1, -1};
int      orbPX[4], orbPY[4];
bool     orbOn = false;

void resetStandbyFx() {
  orbOn = false;
  for (int i = 0; i < 5; i++) starSz[i] = -1;
}

uint16_t mix565(uint16_t a, uint16_t b, float t) {
  t = constrain(t, 0.0f, 1.0f);
  int ar = (a >> 11) & 31, ag = (a >> 5) & 63, ab = a & 31;
  int br = (b >> 11) & 31, bgc = (b >> 5) & 63, bb = b & 31;
  return (uint16_t)((((int)(ar + (br - ar) * t)) << 11) | (((int)(ag + (bgc - ag) * t)) << 5) | (int)(ab + (bb - ab) * t));
}

float ap(float cur, float tgt, float tau, float dt) {
  return cur + (tgt - cur) * (1.0f - expf(-dt / tau));
}

inline uint16_t fx(uint16_t c) { float f = fxFade; return mix565(COL_BG, c, f * f * (3.0f - 2.0f * f)); }   // fade efek aksi

// Pembungkus gambar: koordinat layar penuh, otomatis digeser ke kanvas
inline void cCircle(int x, int y, int r, uint16_t c) { cv->fillCircle(x - OX, y - OY, r, c); }
inline void cRRect(int x, int y, int w, int h, int r, uint16_t c) { cv->fillRoundRect(x - OX, y - OY, w, h, r, c); }
inline void cRect(int x, int y, int w, int h, uint16_t c) { cv->fillRect(x - OX, y - OY, w, h, c); }
inline void cLine(int x0, int y0, int x1, int y1, uint16_t c) { cv->drawLine(x0 - OX, y0 - OY, x1 - OX, y1 - OY, c); }
inline void cArc(int x, int y, int r1, int r2, float a0, float a1, uint16_t c) { cv->fillArc(x - OX, y - OY, r1, r2, a0, a1, c); }

void cText(const char *s, int x, int y, uint8_t size, uint16_t col) {
  cv->setTextSize(size);
  cv->setTextColor(col);
  cv->setCursor(x - OX, y - OY);
  cv->print(s);
}

void cTextCenter(const char *s, int y, uint8_t size, uint16_t col) {
  int16_t x1, y1;
  uint16_t w, h;
  cv->setTextSize(size);
  cv->getTextBounds(s, 0, 0, &x1, &y1, &w, &h);
  cv->setTextColor(col);
  cv->setCursor(120 - OX - (int)w / 2 - x1, y - OY);
  cv->print(s);
}

void cHeart(int cx, int cy, int s, uint16_t c) {
  cCircle(cx - s / 2, cy - s / 3, s / 2 + 1, c);
  cCircle(cx + s / 2, cy - s / 3, s / 2 + 1, c);
  cv->fillTriangle(cx - s - 1 - OX, cy - s / 4 - OY, cx + s + 1 - OX, cy - s / 4 - OY, cx - OX, cy + s - OY, c);
}

void drawNote(int x, int y, uint16_t c) {
  cCircle(x, y, 3, c);
  cLine(x + 3, y, x + 3, y - 12, c);
  cLine(x + 4, y, x + 4, y - 12, c);
  cLine(x + 3, y - 12, x + 9, y - 8, c);
  cLine(x + 4, y - 12, x + 10, y - 8, c);
}

void drawSpiral(int cx, int cy, float a0, float scale, uint16_t col) {
  int px = cx, py = cy;
  for (int k = 1; k <= 26; k++) {
    float ang = a0 + k * 0.55f;
    float r = k * 0.65f * scale;
    int x = cx + (int)(cosf(ang) * r), y = cy + (int)(sinf(ang) * r);
    cLine(px, py, x, y, col);
    cLine(px + 1, py, x + 1, y, col);
    px = x; py = y;
  }
}

// Bintang lima titik (dipakai mata bintang)
void cStar(int cx, int cy, int R, int r, float rot, uint16_t c) {
  int px[10], py[10];
  for (int i = 0; i < 10; i++) {
    float a = rot + i * 0.62832f - 1.5708f;
    int rr = (i & 1) ? r : R;
    px[i] = cx + (int)(cosf(a) * rr);
    py[i] = cy + (int)(sinf(a) * rr);
  }
  for (int i = 0; i < 10; i++) {
    int j = (i + 1) % 10;
    cv->fillTriangle(cx - OX, cy - OY, px[i] - OX, py[i] - OY, px[j] - OX, py[j] - OY, c);
  }
}

// ---------- Latar belakang standby ----------
// Dihitung dari waktu (tanpa array status); warna dicampur ke hitam agar bisa fade/crossfade
inline uint16_t bgc(uint16_t c, float a) { return mix565(COL_BG, c, constrain(a, 0.0f, 1.0f)); }

void drawBgLayer(int style, float a, uint32_t t, uint16_t c1, uint16_t c2) {
  if (a < 0.02f) return;
  float ft = (float)t;
  switch (style) {
    case 0:                                                        // kunang-kunang naik
      for (int i = 0; i < 12; i++) {
        float u = fmodf(ft * 0.00005f * (0.6f + 0.12f * (i % 5)) + i * 0.173f, 1.0f);
        int y = CVY + CVH - (int)(u * CVH);
        int x = CVX + 6 + (int)fmodf(i * 53.7f, CVW - 12) + (int)(sinf(ft * 0.0012f + i * 1.7f) * 8.0f);
        float tw = 0.5f + 0.5f * sinf(ft * 0.004f + i);
        cCircle(x, y, (i % 3 == 0) ? 2 : 1, bgc((i & 1) ? c2 : c1, a * sinf(u * 3.1416f) * (0.4f + 0.6f * tw)));
      }
      break;
    case 1: {                                                      // gelombang mengalir
      static const int base[3] = {64, 186, 178};
      for (int w = 0; w < 3; w++) {
        uint16_t col = bgc(w == 2 ? c2 : c1, a * (w == 2 ? 0.3f : 0.45f));
        int px = 0, py = 0;
        for (int x = CVX; x <= CVX + CVW; x += 8) {
          int y = base[w] + (int)(sinf(x * 0.045f + ft * 0.0018f * (w + 1) + w * 2.0f) * 6.0f);
          if (x > CVX) cLine(px, py, x, y, col);
          px = x; py = y;
        }
      }
      break;
    }
    case 2:                                                        // riak melebar dari tengah
      for (int k = 0; k < 3; k++) {
        float ph = fmodf(ft / 2600.0f + k / 3.0f, 1.0f);
        cv->drawCircle(120 - OX, 124 - OY, 14 + (int)(ph * 54.0f), bgc(c1, a * (1.0f - ph) * 0.8f));
      }
      break;
    case 3:                                                        // warp bintang keluar dari tengah
      for (int i = 0; i < 16; i++) {
        float ph = fmodf(ft / 3000.0f + i * 0.0625f, 1.0f);
        float ang = i * 2.39996f;
        float r = 6.0f + ph * ph * 84.0f;
        int x = 120 + (int)(cosf(ang) * r * 1.25f), y = 124 + (int)(sinf(ang) * r * 0.85f);
        if (x < CVX || x >= CVX + CVW || y < CVY || y >= CVY + CVH) continue;
        cCircle(x, y, ph > 0.65f ? 2 : 1, bgc(c1, a * ((ph < 0.2f) ? ph * 5.0f : 1.0f)));
      }
      break;
    case 4:                                                        // gelembung (tidur)
      for (int i = 0; i < 8; i++) {
        float u = fmodf(ft * 0.00003f * (0.7f + 0.1f * (i % 4)) + i * 0.125f, 1.0f);
        int y = CVY + CVH - (int)(u * CVH);
        int x = CVX + 10 + (int)fmodf(i * 37.3f, CVW - 20) + (int)(sinf(ft * 0.001f + i) * 6.0f);
        cv->drawCircle(x - OX, y - OY, 2 + (i % 3) * 2, bgc(c1, a * sinf(u * 3.1416f) * 0.8f));
      }
      break;
    case 6:                                                        // planet mengorbit
      for (int i = 0; i < 6; i++) {
        float ang = ft * 0.0009f * ((i & 1) ? -1.0f : 1.0f) + i * 1.0472f;
        int x = 120 + (int)(cosf(ang) * (70 + (i % 3) * 6)), y = 124 + (int)(sinf(ang) * (44 + (i % 3) * 5));
        cCircle(x, y, (i & 1) ? 2 : 3, bgc((i & 1) ? c2 : c1, a * 0.8f));
      }
      break;
    case 7:                                                        // hujan meteor
      for (int i = 0; i < 4; i++) {
        float u = fmodf(ft / 2200.0f + i * 0.25f, 1.0f);
        int x = CVX + CVW - 10 - (int)(u * 150.0f) - i * 23;
        int y = CVY + 4 + (i * 29) % 50 + (int)(u * 90.0f);
        uint16_t mc = bgc(c2, a * sinf(u * 3.1416f));
        cLine(x, y, x + 12, y - 7, mc);
        cCircle(x, y, 1, bgc(COL_TEXT, a * sinf(u * 3.1416f)));
      }
      break;
    case 8:                                                        // salju turun pelan
      for (int i = 0; i < 14; i++) {
        float u = fmodf(ft * 0.00006f * (0.6f + 0.1f * (i % 5)) + i * 0.0714f, 1.0f);
        int y = CVY + (int)(u * CVH);
        int x = CVX + 4 + (int)fmodf(i * 47.9f, CVW - 8) + (int)(sinf(ft * 0.0015f + i) * 7.0f);
        cCircle(x, y, (i % 4 == 0) ? 2 : 1, bgc(COL_TEXT, a * 0.6f));
      }
      break;
    default:                                                       // hujan (Bluetooth putus)
      for (int i = 0; i < 14; i++) {
        float u = fmodf(ft * 0.0007f * (0.8f + 0.1f * (i % 4)) + i * 0.071f, 1.0f);
        int y = CVY + (int)(u * CVH);
        int x = CVX + 4 + (int)fmodf(i * 41.3f, CVW - 8);
        cLine(x, y, x - 1, y + 6, bgc(c1, a * 0.7f));
      }
      break;
  }
}

void drawBg(uint32_t now, int mood, float dt) {
  int want = (mood == MOOD_SLEEP) ? 4 : (mood == MOOD_OFF) ? 5 : bgCur;
  static const uint8_t BGL[7] = {0, 1, 2, 3, 6, 7, 8};             // gaya latar untuk suasana senang
  bool happyStyle = (bgCur < 4 || bgCur >= 6);
  if (mood == MOOD_HAPPY && !happyStyle) want = 0;
  else if (mood == MOOD_HAPPY && now >= bgNext) {
    int k = 0;
    while (k < 7 && BGL[k] != bgCur) k++;
    want = BGL[(k + 1) % 7];
  }
  if (want != bgCur) {
    bgPrev = bgCur; bgCur = want; bgMix = 0;
    bgNext = now + random(11000, 17000);
  }
  bgMix = min(1.0f, bgMix + dt / 1200.0f);
  float s = bgMix * bgMix * (3.0f - 2.0f * bgMix);                // smoothstep
  uint16_t c1 = (mood == MOOD_OFF) ? COL_RED : (mood == MOOD_SLEEP) ? COL_DIM : COL_GREEN;
  uint16_t c2 = (mood == MOOD_HAPPY) ? COL_ORANGE : c1;
  if (bgMix < 1.0f) drawBgLayer(bgPrev, 1.0f - s, now, c1, c2);
  drawBgLayer(bgCur, s, now, c1, c2);
}

// Cahaya napas di belakang wajah; warnanya berubah halus mengikuti suasana/aksi
void drawGlow(uint32_t now, int mood, float dt) {
  float tr = 10, tg = 60, tb = 28;
  if (mood == MOOD_SLEEP)      { tr = 6;  tg = 14; tb = 44; }
  else if (mood == MOOD_OFF)   { tr = 70; tg = 14; tb = 12; }
  else switch (fxEv) {
    case EV_LOVE: case EV_KISS: case EV_SHY:             tr = 80; tg = 18; tb = 34; break;
    case EV_ANGRY:                                       tr = 96; tg = 12; tb = 8;  break;
    case EV_MUSIC: case EV_DANCE: case EV_VROOM:
    case EV_BIKE: case EV_HELMET: case EV_COFFEE:
    case EV_FUEL: case EV_ROCKET: case EV_FLAG:          tr = 84; tg = 50; tb = 10; break;
    case EV_HAPPY: case EV_SPARKLE: case EV_TONGUE:
    case EV_LAUGH: case EV_STAREYES: case EV_HELLO:
    case EV_BALLOON: case EV_PONG: case EV_FIREWORK: case EV_THUNDER: tr = 70; tg = 62; tb = 14; break;
    case EV_GAME: case EV_GHOST:                         tr = 14; tg = 30; tb = 80; break;
    case EV_TRAFFIC: case EV_CAT: case EV_EAT:           tr = 84; tg = 50; tb = 10; break;
    case EV_COOL: case EV_THINK: case EV_CRY: case EV_RAIN: case EV_SIGNAL: tr = 14; tg = 40; tb = 80; break;
    default: break;
  }
  glR = ap(glR, tr, 260, dt); glG = ap(glG, tg, 260, dt); glB = ap(glB, tb, 260, dt);
  float br = (0.8f + 0.2f * sinf(now / 1100.0f)) * ((mood == MOOD_SLEEP) ? 0.6f : 1.0f);
  static const uint8_t RAD[3] = {64, 50, 36};
  static const float K[3] = {0.35f, 0.65f, 1.0f};
  for (int i = 0; i < 3; i++) {
    uint16_t gcol;
    if (lightTheme) {                                              // tema terang: cahaya berupa semburat pastel tipis
      gcol = mix565(COL_BG, gfx->color565((uint8_t)min(255.0f, glR * 3.5f), (uint8_t)min(255.0f, glG * 3.5f), (uint8_t)min(255.0f, glB * 3.5f)),
                    0.10f + 0.16f * br * K[i]);
    } else {
      gcol = gfx->color565((uint8_t)(glR * br * K[i]), (uint8_t)(glG * br * K[i]), (uint8_t)(glB * br * K[i]));
    }
    cCircle(120, 124, RAD[i], gcol);
  }
}

// Efek tambahan untuk 6 aksi baru (fade ikut fxFade)
void drawFxNew(float p, int dx, int dy, int cxL, int cxR, int ey) {
  switch (fxEv) {
    case EV_SPARKLE: {
      int s = (int)(7.0f * (0.7f + 0.3f * sinf(p * 0.02f)));
      uint16_t w = fx(COL_TEXT);
      int sx[2] = {cxL - 4, cxR - 4};
      for (int k = 0; k < 2; k++) {                                // kilau di mata
        cLine(sx[k] - s, ey - 8, sx[k] + s, ey - 8, w);
        cLine(sx[k], ey - 8 - s, sx[k], ey - 8 + s, w);
        cCircle(sx[k] + 9, ey + 6, 2, w);
      }
      static const int px[4] = {48, 192, 62, 180};
      static const int py[4] = {88, 92, 160, 164};
      for (int i = 0; i < 4; i++) {                                // bintang kecil berkelip
        int r = (int)(sinf(fmodf(p + i * 350.0f, 1400.0f) / 1400.0f * 3.1416f) * 6.0f);
        if (r < 1) continue;
        uint16_t c = fx((i & 1) ? COL_ORANGE : COL_TEXT);
        cLine(px[i] - r, py[i] + dy, px[i] + r, py[i] + dy, c);
        cLine(px[i], py[i] + dy - r, px[i], py[i] + dy + r, c);
      }
      break;
    }
    case EV_ANGRY: {
      for (int side = 0; side < 2; side++)                         // uap naik dari kepala
        for (int i = 0; i < 3; i++) {
          float q = fmodf(p + i * 300.0f + side * 150.0f, 900.0f) / 900.0f;
          cCircle((side ? 192 : 48) + (side ? 1 : -1) * (int)(q * 8.0f), 108 + dy - (int)(q * 30.0f),
                  2 + (int)(q * 5.0f), mix565(COL_BG, COL_DIM, (1.0f - q) * fxFade));
        }
      uint16_t rc = mix565(COL_BG, COL_RED, (0.6f + 0.4f * sinf(p * 0.03f)) * fxFade);
      for (int k = 0; k < 4; k++) {                                // tanda kesal di dahi
        int sx = (k & 1) ? 1 : -1, sy = (k & 2) ? 1 : -1;
        int ax = 178 + sx * 3, ay = 70 + dy + sy * 3;
        cLine(ax, ay, ax + sx * 6, ay, rc);
        cLine(ax, ay + 1, ax + sx * 6, ay + 1, rc);
        cLine(ax, ay, ax, ay + sy * 6, rc);
        cLine(ax + 1, ay, ax + 1, ay + sy * 6, rc);
      }
      break;
    }
    case EV_KISS:
      if (p > 600 && p < 1900) {                                   // hati terbang dari mulut
        float q = (p - 600.0f) / 1300.0f;
        int hx = 128 + (int)(q * 52.0f), hy = 140 + dy - (int)(q * 62.0f) - (int)(sinf(q * 9.0f) * 4.0f);
        cHeart(hx, hy, 4 + (int)(q * 5.0f), fx(q > 0.75f ? mix565(COL_RED, COL_BG, (q - 0.75f) * 4.0f) : COL_RED));
      }
      break;
    case EV_THINK: {
      float g = constrain(p / 300.0f, 0.0f, 1.0f);
      cCircle(179, 82, 2, fx(COL_DIM));
      cCircle(182, 77, 3, fx(COL_DIM));
      int r = (int)(13.0f * g);
      if (r > 2) cCircle(188, 70, r, mix565(COL_BG, COL_DIM, 0.4f * fxFade));
      int n = ((int)(p / 450.0f)) % 4;                             // titik-titik muncul bergantian
      for (int i = 0; i < n && g > 0.9f; i++) cCircle(180 + i * 8, 70, 2, fx(COL_TEXT));
      break;
    }
    case EV_DANCE:
      for (int i = 0; i < 12; i++) {                               // konfeti jatuh
        int x = CVX + 6 + (int)fmodf(i * 61.7f, CVW - 12) + (int)(sinf(p * 0.006f + i) * 4.0f);
        uint16_t cc = (i % 4 == 0) ? COL_ORANGE : (i % 4 == 1) ? COL_GREEN : (i % 4 == 2) ? COL_TEXT : COL_RED;
        cRect(x, CVY + (int)fmodf(p * 0.05f + i * 37.0f, (float)CVH), 3, 3, fx(cc));
      }
      break;
    case EV_SHY: {
      uint16_t rc = fx(COL_RED);
      for (int k = 0; k < 3; k++) {                                // garis pipi merona
        cLine(42 + k * 5, 134 + dy, 46 + k * 5, 142 + dy, rc);
        cLine(182 + k * 5, 134 + dy, 186 + k * 5, 142 + dy, rc);
      }
      break;
    }
    default: break;
  }
}

// Efek di belakang wajah (digambar sebelum mata)
void drawFxBack(float p) {
  if (fxEv != EV_RADAR) return;
  int cx = 120, cy = 124;
  uint16_t gc = mix565(COL_BG, COL_GREEN, 0.5f * fxFade);
  cv->drawCircle(cx - OX, cy - OY, 62, gc);
  cv->drawCircle(cx - OX, cy - OY, 40, mix565(COL_BG, COL_GREEN, 0.25f * fxFade));
  float ang = p * 0.006f;
  for (int k = 6; k >= 0; k--) {                                   // garis sapuan dengan ekor memudar
    float a = ang - k * 0.15f;
    cLine(cx, cy, cx + (int)(cosf(a) * 62.0f), cy + (int)(sinf(a) * 62.0f),
          mix565(COL_BG, COL_GREEN, (1.0f - k / 7.0f) * 0.8f * fxFade));
  }
  static const float BA[3] = {0.9f, 2.6f, 4.4f};                   // titik target menyala saat tersapu
  static const int BRAD[3] = {50, 34, 56};
  for (int i = 0; i < 3; i++) {
    float d = fmodf(fmodf(ang - BA[i], 6.2832f) + 6.2832f, 6.2832f);
    float al = 1.0f - d / 6.2832f;
    cCircle(cx + (int)(cosf(BA[i]) * BRAD[i]), cy + (int)(sinf(BA[i]) * BRAD[i]), 3,
            mix565(COL_BG, COL_ORANGE, al * al * fxFade));
  }
}

// Efek depan untuk 8 aksi v12
void drawFxNew2(float p, int dx, int dy, int cxL, int cxR, int ey) {
  float dur = (fxEv >= 0 && fxEv < EV_COUNT) ? (float)EV_DUR[fxEv] : 1.0f;
  switch (fxEv) {
    case EV_LAUGH: {
      cTextCenter("HA HA", 62 + (int)(sinf(p * 0.03f) * 3.0f), 2, fx(COL_ORANGE));
      for (int side = 0; side < 2; side++)                         // air mata tawa menyembur
        for (int i = 0; i < 2; i++) {
          float q = fmodf(p + i * 350.0f + side * 120.0f, 700.0f) / 700.0f;
          int x = (side ? cxR + 24 : cxL - 24) + (side ? 1 : -1) * (int)(q * 16.0f);
          int y = ey + 4 - (int)(q * 12.0f) + (int)(q * q * 34.0f);
          cCircle(x, y + dy, 2, mix565(COL_BG, COL_TEAR, (1.0f - q * 0.5f) * fxFade));
        }
      break;
    }
    case EV_CRY: {
      for (int side = 0; side < 2; side++) {
        int ex = side ? cxR : cxL;
        cRect(ex - 3 + (side ? 6 : -6), ey + 20 + dy, 2, 10, mix565(COL_BG, COL_TEAR, 0.6f * fxFade));
        for (int i = 0; i < 2; i++) {                              // tetes air mata jatuh
          float q = fmodf(p * 1.0f + i * 420.0f + side * 200.0f, 840.0f) / 840.0f;
          int y = ey + 22 + dy + (int)(q * 56.0f);
          cCircle(ex + (side ? 6 : -6), y, 3 - (int)(q * 1.5f), mix565(COL_BG, COL_TEAR, (1.0f - q * 0.6f) * fxFade));
        }
      }
      break;
    }
    case EV_STAREYES: {
      for (int k = 0; k < 4; k++) {                                // bintang kecil mengorbit
        float a = p * 0.003f + k * 1.5708f;
        cStar(120 + (int)(cosf(a) * 74.0f), 118 + dy + (int)(sinf(a) * 40.0f), 5, 2, a, fx((k & 1) ? COL_ORANGE : COL_TEXT));
      }
      break;
    }
    case EV_HELMET: {
      float off;
      if (p < 380.0f)               { float t = 1.0f - p / 380.0f; off = -90.0f * t * t; }
      else if (p < 560.0f)          off = -sinf((p - 380.0f) / 180.0f * 3.1416f) * 5.0f;
      else if (p > dur - 400.0f)    off = -((p - (dur - 400.0f)) / 400.0f) * 100.0f;
      else                          off = 0;
      int oy = dy + (int)off;
      uint16_t w = COL_TEXT;
      cRRect(48 + dx, 62 + oy, 144, 16, 8, w);                     // ubun-ubun helm
      cRRect(44 + dx, 70 + oy, 12, 84, 6, w);                      // pelindung pipi
      cRRect(184 + dx, 70 + oy, 12, 84, 6, w);
      cRRect(70 + dx, 154 + oy, 100, 6, 3, w);                     // tali dagu
      cRect(70 + dx, 68 + oy, 100, 3, COL_ORANGE);                 // garis oranye
      break;
    }
    case EV_BIKE: {
      float q = constrain(p / dur, 0.0f, 1.0f);
      int bx = CVX - 34 + (int)(q * (CVW + 70)), by = 70;
      for (int i = 0; i < 4; i++)                                  // asap knalpot
        cCircle(bx - 6 - i * 7, by + 10 - i, 1 + i / 2, mix565(COL_BG, COL_DIM, (1.0f - i * 0.22f) * fxFade));
      for (int w = 0; w < 2; w++) {
        int wx = bx + w * 26;
        cCircle(wx, by + 8, 7, COL_TEXT);
        cCircle(wx, by + 8, 4, COL_BG);
        float a = p * 0.03f;
        cLine(wx, by + 8, wx + (int)(cosf(a) * 4.0f), by + 8 + (int)(sinf(a) * 4.0f), COL_DIM);
      }
      cLine(bx, by + 8, bx + 12, by, COL_ORANGE);                  // rangka
      cLine(bx + 12, by, bx + 26, by + 8, COL_ORANGE);
      cLine(bx + 12, by, bx + 22, by - 3, COL_ORANGE);
      cRRect(bx + 6, by - 4, 12, 5, 2, COL_TEXT);                  // jok
      cCircle(bx + 14, by - 12, 4, COL_TEXT);                      // pengendara
      cLine(bx + 12, by - 8, bx + 10, by - 1, COL_TEXT);
      cLine(bx + 14, by - 8, bx + 22, by - 3, COL_TEXT);
      break;
    }
    case EV_COFFEE: {
      float t = constrain(p / 350.0f, 0.0f, 1.0f);
      float u = (p > dur - 350.0f) ? (p - (dur - 350.0f)) / 350.0f : 0.0f;
      int x = 156 + (int)((1.0f - t + u) * 60.0f), y = 148 + dy;
      cRRect(x, y, 26, 22, 5, COL_TEXT);
      cRRect(x + 3, y + 3, 20, 5, 2, mix565(COL_BG, COL_ORANGE, 0.55f));
      cCircle(x + 29, y + 11, 6, COL_TEXT);
      cCircle(x + 29, y + 11, 3, COL_BG);
      cRRect(x - 3, y + 21, 32, 4, 2, COL_DIM);                    // tatakan
      for (int i = 0; i < 3; i++) {                                // uap naik bergelombang
        float q = fmodf(p + i * 400.0f, 1200.0f) / 1200.0f;
        cCircle(x + 6 + i * 7 + (int)(sinf(q * 8.0f + i) * 3.0f), y - 4 - (int)(q * 26.0f), 2,
                mix565(COL_BG, COL_DIM, (1.0f - q) * fxFade));
      }
      break;
    }
    case EV_HELLO: {
      cTextCenter("HAI!", 60 + (int)(sinf(p * 0.012f) * 3.0f), 3, fx(COL_ORANGE));
      float a = sinf(p * 0.014f) * 0.55f;
      int wx = 196, wy = 176 + dy;
      int hx = wx + (int)(sinf(a) * 16.0f), hy = wy - (int)(cosf(a) * 16.0f);
      cLine(wx, wy, hx, hy, fx(COL_TEXT));
      cLine(wx + 1, wy, hx + 1, hy, fx(COL_TEXT));
      cCircle(hx, hy, 7, fx(COL_TEXT));
      for (int k = 0; k < 4; k++) {                                // jari-jari terbuka
        float fa = a + (k - 1.5f) * 0.3f;
        int fx2 = hx + (int)(sinf(fa) * 16.0f), fy2 = hy - (int)(cosf(fa) * 16.0f);
        cLine(hx, hy, fx2, fy2, fx(COL_TEXT));
        cLine(hx + 1, hy, fx2 + 1, fy2, fx(COL_TEXT));
      }
      break;
    }
    default: break;
  }
}

inline void cTri(int x0, int y0, int x1, int y1, int x2, int y2, uint16_t c) {
  cv->fillTriangle(x0 - OX, y0 - OY, x1 - OX, y1 - OY, x2 - OX, y2 - OY, c);
}

// Efek depan untuk 8 aksi v13
void drawFxNew3(float p, int dx, int dy, int cxL, int cxR, int ey) {
  float dur = (fxEv >= 0 && fxEv < EV_COUNT) ? (float)EV_DUR[fxEv] : 1.0f;
  float q = constrain(p / dur, 0.0f, 1.0f);
  switch (fxEv) {
    case EV_SIGNAL: {
      int lit = ((int)(p / 260.0f)) % 4;                           // busur menyala bergiliran
      for (int k = 0; k < 3; k++)
        cArc(120, 90, 10 + k * 9 + 4, 10 + k * 9, 225, 315, lit > k ? fx(COL_ORANGE) : fx(COL_RING_BG));
      cCircle(120, 90, 3, fx(COL_ORANGE));
      break;
    }
    case EV_BATT: {
      float lvl = 0.6f - 0.55f * q;
      cRRect(96, 60, 48, 20, 4, fx(COL_TEXT));
      cRRect(98, 62, 44, 16, 3, COL_BG);
      cRect(144, 66, 4, 8, fx(COL_TEXT));
      if (!(lvl < 0.2f && ((int)(p / 200.0f)) % 2)) {
        int w = max(2, (int)(40.0f * lvl));
        cRRect(100, 64, w, 12, 2, fx(lvl < 0.3f ? COL_RED : COL_ORANGE));
      }
      break;
    }
    case EV_RAIN: {
      cArc(120, 84, 30, 24, 180, 360, fx(COL_ORANGE));             // payung
      cLine(120, 84, 120, 98, fx(COL_DIM));
      for (int i = 0; i < 12; i++) {
        int x = 40 + i * 13 + (i % 3) * 3;
        int y = 58 + (int)fmodf(p * 0.28f + i * 41.0f, 134.0f);
        cLine(x, y, x - 2, y + 6, mix565(COL_BG, COL_TEAR, 0.7f * fxFade));
      }
      break;
    }
    case EV_ROCKET: {
      int x = 196 + (int)(sinf(p * 0.02f) * 2.0f), y = 206 - (int)(q * 160.0f);
      for (int i = 0; i < 5; i++)                                  // jejak asap
        cCircle(x + (int)(sinf(p * 0.01f + i) * 2.0f), y + 16 + i * 9, 2 + i / 2, mix565(COL_BG, COL_DIM, (1.0f - i * 0.18f) * fxFade));
      int fl = 8 + (int)(sinf(p * 0.06f) * 3.0f);
      cTri(x - 4, y + 12, x + 4, y + 12, x, y + 12 + fl, COL_ORANGE);
      cRRect(x - 6, y - 18, 12, 30, 5, COL_TEXT);
      cTri(x - 6, y - 16, x + 6, y - 16, x, y - 30, COL_RED);
      cTri(x - 6, y + 4, x - 12, y + 14, x - 6, y + 12, COL_RED);
      cTri(x + 6, y + 4, x + 12, y + 14, x + 6, y + 12, COL_RED);
      cCircle(x, y - 6, 3, COL_TEAR);
      break;
    }
    case EV_PONG: {
      float tri = fabsf(fmodf(p / 800.0f, 2.0f) - 1.0f);
      int bx = 62 + (int)(tri * 116.0f), by = 76 + (int)(sinf(p * 0.011f) * 8.0f);
      cRRect(52, by - 10, 4, 20, 2, fx(COL_TEXT));
      cRRect(184, 66 + (int)(sinf(p * 0.009f) * 8.0f), 4, 20, 2, fx(COL_TEXT));
      cCircle(bx, by, 4, fx(COL_ORANGE));
      break;
    }
    case EV_FLAG: {
      cLine(150, 62, 150, 102, fx(COL_DIM));
      cLine(151, 62, 151, 102, fx(COL_DIM));
      for (int i = 0; i < 6; i++)
        for (int j = 0; j < 3; j++) {
          int off = (int)(sinf(p * 0.012f + i * 0.8f) * 3.0f);
          cRect(152 + i * 8, 62 + j * 7 + off, 8, 7, fx(((i + j) & 1) ? COL_TEXT : COL_RING_BG));
        }
      break;
    }
    case EV_FUEL: {
      float lvl = 0.5f - 0.45f * q;
      cArc(120, 92, 30, 24, 180, 360, fx(COL_RING_BG));
      cArc(120, 92, 30, 24, 180, 180 + 180.0f * lvl, fx(lvl < 0.25f ? COL_RED : COL_GREEN));
      float a = 3.1416f + 3.1416f * lvl;
      cLine(120, 92, 120 + (int)(cosf(a) * 22.0f), 92 + (int)(sinf(a) * 22.0f), fx(COL_TEXT));
      cCircle(120, 92, 3, fx(COL_TEXT));
      if (lvl < 0.25f && ((int)(p / 250.0f)) % 2) cCircle(172, 66, 4, fx(COL_RED));  // lampu peringatan
      break;
    }
    case EV_BALLOON: {
      for (int b = 0; b < 2; b++) {
        float u = constrain((p - b * 500.0f) / 2600.0f, 0.0f, 1.0f);
        if (u <= 0.0f) continue;
        int x = (b ? 176 : 64) + (int)(sinf(p * 0.004f + b * 2.0f) * 6.0f), y = 196 - (int)(u * 150.0f);
        cLine(x, y + 12, x + (int)(sinf(p * 0.005f + b) * 3.0f), y + 38, fx(COL_DIM));
        cCircle(x, y, 12, fx(b ? COL_ORANGE : COL_RED));
        cTri(x - 3, y + 17, x + 3, y + 17, x, y + 11, fx(b ? COL_ORANGE : COL_RED));
        cCircle(x - 4, y - 5, 3, mix565(COL_BG, COL_TEXT, 0.5f * fxFade));
      }
      break;
    }
    default: break;
  }
}

// Efek depan untuk 8 aksi v15
void drawFxNew4(float p, int dx, int dy, int cxL, int cxR, int ey) {
  float dur = (fxEv >= 0 && fxEv < EV_COUNT) ? (float)EV_DUR[fxEv] : 1.0f;
  float q = constrain(p / dur, 0.0f, 1.0f);
  switch (fxEv) {
    case EV_FIREWORK: {
      static const int BX[3] = {80, 160, 120}, BY[3] = {78, 72, 64};
      for (int b = 0; b < 3; b++) {
        float u = (p - b * 1000.0f) / 1200.0f;
        if (u <= 0.0f || u >= 1.0f) continue;
        if (u < 0.22f) {                                           // roket naik
          int y = 190 - (int)((u / 0.22f) * (190 - BY[b]));
          cCircle(BX[b], y, 2, fx(COL_TEXT));
          cLine(BX[b], y + 3, BX[b], y + 9, fx(COL_DIM));
        } else {                                                   // ledakan
          float e = (u - 0.22f) / 0.78f, r = e * (30 + b * 3);
          for (int k = 0; k < 12; k++) {
            float a = k * 0.5236f;
            uint16_t c = (k % 3 == 0) ? COL_ORANGE : (k % 3 == 1) ? COL_RED : COL_TEXT;
            cCircle(BX[b] + (int)(cosf(a) * r), BY[b] + (int)(sinf(a) * r + e * e * 14.0f), e < 0.6f ? 2 : 1,
                    mix565(COL_BG, c, (1.0f - e) * fxFade));
          }
        }
      }
      break;
    }
    case EV_MAGNIFY: {
      int lx = 120 + (int)(sinf(p * 0.0035f) * 52.0f), ly = 112 + dy;
      uint16_t w = fx(COL_TEXT), o = fx(COL_ORANGE);
      cArc(lx, ly, 26, 21, 0, 360, w);
      for (int k = 0; k < 3; k++) cLine(lx + 18 + (k == 1), ly + 18 + (k == 2), lx + 36 + (k == 1), ly + 36 + (k == 2), o);
      cLine(lx - 12, ly - 10, lx - 6, ly - 16, w);                 // kilau kaca
      break;
    }
    case EV_GAME: {
      int x = 44, y = 150 + dy;
      uint16_t w = fx(COL_TEXT);
      cRRect(x, y, 52, 24, 10, w);
      cRect(x + 8, y + 10, 12, 4, COL_BG);
      cRect(x + 12, y + 6, 4, 12, COL_BG);                         // tombol arah
      bool a = ((int)(p / 180.0f)) & 1;
      cCircle(x + 36, y + 14, 4, a ? fx(COL_RED) : fx(COL_DIM));
      cCircle(x + 44, y + 9, 4, a ? fx(COL_DIM) : fx(COL_GREEN));
      break;
    }
    case EV_GHOST: {
      int gx = 210 - (int)(q * 230.0f), gy = 76 + (int)(sinf(p * 0.01f) * 4.0f) + dy;
      uint16_t gc = mix565(COL_BG, COL_TEXT, 0.9f * fxFade);
      cCircle(gx, gy, 13, gc);
      cRect(gx - 13, gy, 26, 16, gc);
      for (int i = 0; i < 3; i++) cCircle(gx - 9 + i * 9, gy + 16, 4, gc);   // ujung bergelombang
      cCircle(gx - 5, gy - 2, 2, COL_BG); cCircle(gx + 5, gy - 2, 2, COL_BG); cCircle(gx, gy + 6, 2, COL_BG);
      break;
    }
    case EV_CAT: {
      uint16_t w = fx(COL_TEXT), pk = fx(COL_TONGUE);
      cTri(58, 86, 86, 86, 64, 58, w);   cTri(62, 84, 80, 84, 66, 66, pk);        // telinga
      cTri(154, 86, 182, 86, 176, 58, w); cTri(160, 84, 178, 84, 174, 66, pk);
      for (int s2 = -1; s2 <= 1; s2 += 2)                                          // kumis
        for (int k = 0; k < 3; k++)
          cLine(120 + s2 * 66, 138 + k * 6 + dy, 120 + s2 * 80, 132 + k * 12 + dy, w);
      cTri(116, 128 + dy, 124, 128 + dy, 120, 134 + dy, pk);                       // hidung
      cTextCenter("MEOW", 62 + (int)(sinf(p * 0.012f) * 2.0f), 2, fx(COL_ORANGE));
      break;
    }
    case EV_THUNDER: {
      uint16_t cc = fx(COL_DIM);
      cCircle(92, 76, 13, cc); cCircle(114, 68, 16, cc); cCircle(138, 72, 14, cc); cCircle(152, 78, 10, cc);
      cRRect(84, 76, 76, 14, 7, cc);                               // awan
      if (p > 700 && fmodf(p, 1100.0f) < 280.0f) {                 // sambaran petir
        static const int PX[4] = {124, 110, 122, 106}, PY[4] = {90, 114, 114, 146};
        for (int i = 0; i < 3; i++)
          for (int o = -1; o <= 1; o++) cLine(PX[i] + o, PY[i], PX[i + 1] + o, PY[i + 1], COL_ORANGE);
      }
      break;
    }
    case EV_TRAFFIC: {
      cRRect(176, 74, 22, 58, 6, fx(COL_CHIP));
      int ph = p < 900 ? 0 : p < 1800 ? 1 : p < 2700 ? 2 : 3;
      uint16_t lamp[3] = {COL_RED, COL_ORANGE, COL_GREEN};
      for (int i = 0; i < 3; i++) {
        bool on = (ph == i) || (ph == 3 && i == 2);
        cCircle(187, 88 + i * 16, 6, on ? fx(lamp[i]) : mix565(COL_BG, lamp[i], 0.18f * fxFade));
      }
      if (ph >= 2) cTextCenter("GO!", 62 + (int)(sinf(p * 0.02f) * 2.0f), 3, fx(COL_GREEN));
      break;
    }
    case EV_EAT: {
      if (p < 1100) {
        float e = constrain(p / 900.0f, 0.0f, 1.0f);
        e = e * e * (3.0f - 2.0f * e);
        int x = 180 - (int)(e * 50.0f), y = 164 - (int)(e * 22.0f) + dy;
        int r = p < 900 ? 12 : (int)(12.0f * (1.0f - (p - 900.0f) / 200.0f));
        if (r > 1) {                                               // donat
          cCircle(x, y, r, fx(COL_ORANGE));
          cCircle(x, y, r / 2, COL_BG);
          cCircle(x - r / 2, y - r / 2, 1, fx(COL_TEXT));
          cCircle(x + r / 2, y - r / 3, 1, fx(COL_RED));
          cCircle(x + r / 3, y + r / 2, 1, fx(COL_GREEN));
        }
      } else {
        for (int i = 0; i < 5; i++) {                              // remah jatuh
          float q2 = fmodf(p + i * 190.0f, 760.0f) / 760.0f;
          cCircle(114 + i * 3, 150 + dy + (int)(q2 * 22.0f), 1, mix565(COL_BG, COL_ORANGE, (1.0f - q2) * fxFade));
        }
      }
      break;
    }
    default: break;
  }
}

void drawEye(int ex, int ey, float open, float happy, uint16_t col, float lx, float ly) {
  int h = max(4, (int)(52.0f * open));
  int w = constrain(36 + (int)((1.0f - open) * 8.0f), 32, 44);   // melar saat menutup
  cRRect(ex - w / 2, ey - h / 2, w, h, min(16, h / 2), col);
  if (h > 26 && happy < 0.5f) {
    cCircle(ex - 6 + (int)(lx * 4.0f), ey - h / 2 + 11 + (int)(ly * 3.0f), 5, COL_TEXT);
    cCircle(ex + 8 + (int)(lx * 3.0f), ey + h / 4, 2, COL_TEXT);
  }
  if (happy > 0.02f) {                                              // kelopak bawah naik = mata ^ ^
    int cy0 = ey + h / 2 + 28, cy1 = ey - h / 2 + 34;
    cCircle(ex, cy0 + (int)((cy1 - cy0) * happy), 27, COL_BG);
  }
}

void drawBrow(int bx, int by, float tilt, bool isLeft, uint16_t col) {
  int inner = isLeft ? 14 : -14;
  for (int o = -1; o <= 1; o++) cLine(bx - inner, by - (int)tilt + o, bx + inner, by + (int)tilt + o, col);
}

void drawMouth(int cx, int my, float smile, float open, float w, float tongue) {
  if (open > 0.08f) {
    int mw = (int)(w * (0.55f + 0.45f * open));
    int mh = (int)(6.0f + open * 24.0f);
    cRRect(cx - mw / 2, my - 2, mw, mh, min(mw, mh) / 2, COL_MOUTH);
    if (tongue > 0.05f) {
      int th = max(2, (int)(8.0f * tongue));
      cRRect(cx - mw / 4, my - 2 + mh - th, mw / 2, th, min(th / 2, mw / 4), COL_TONGUE);
    }
  } else {
    if (tongue > 0.05f) cRRect(cx + 2, my + 2, 12, max(2, (int)(14.0f * tongue)), 5, COL_TONGUE);
    int px = 0, py = 0;
    for (int i = 0; i <= 8; i++) {
      float u = i / 8.0f;
      int x = cx - (int)(w / 2) + (int)(w * u);
      float s2 = 1.0f - (2.0f * u - 1.0f) * (2.0f * u - 1.0f);
      int y = my + (int)(smile * 7.0f * s2);
      if (i) {
        cLine(px, py, x, y, COL_TEXT);
        cLine(px, py + 1, x, y + 1, COL_TEXT);
      }
      px = x; py = y;
    }
  }
}

// Titik putih berekor mengorbit di cincin (4,5 detik per putaran)
void updateOrbit(bool connected) {
  uint16_t rc = connected ? COL_GREEN : COL_RED;
  float head = (millis() % 4500) / 4500.0f * 6.2832f - 1.5708f;
  int nx[4], ny[4];
  for (int i = 0; i < 4; i++) {
    float a = head - i * 0.09f;
    nx[i] = 120 + (int)lroundf(116.0f * cosf(a));
    ny[i] = 120 + (int)lroundf(116.0f * sinf(a));
  }
  if (orbOn && nx[0] == orbPX[0] && ny[0] == orbPY[0]) return;
  if (orbOn) for (int i = 3; i >= 0; i--) gfx->fillCircle(orbPX[i], orbPY[i], 3, rc);
  uint16_t cols[4] = {COL_TEXT, mix565(COL_TEXT, rc, 0.45f), mix565(COL_TEXT, rc, 0.7f), mix565(COL_TEXT, rc, 0.85f)};
  for (int i = 3; i >= 0; i--) {
    gfx->fillCircle(nx[i], ny[i], i == 0 ? 3 : 2, cols[i]);
    orbPX[i] = nx[i]; orbPY[i] = ny[i];
  }
  orbOn = true;
}

// Bintang berkelip di area kosong atas dan bawah wajah
void updateStars(int mood) {
  static const uint8_t wave[8] = {0, 1, 2, 3, 2, 1, 0, 0};
  uint32_t step = millis() / 220;
  for (int i = 0; i < 5; i++) {
    int sz = (mood == MOOD_HAPPY) ? wave[(step + i * 3) % 8] : 0;
    if (sz == starSz[i]) continue;
    starSz[i] = sz;
    int x = STAR_X[i], y = STAR_Y[i];
    gfx->fillRect(x - 5, y - 5, 11, 11, COL_BG);
    if (sz == 0) continue;
    uint16_t c = (i % 2) ? COL_ORANGE : COL_TEXT;
    gfx->drawFastHLine(x - sz - 1, y, 2 * sz + 3, c);
    gfx->drawFastVLine(x, y - sz - 1, 2 * sz + 3, c);
  }
}

void enterStandby(bool connected) {
  irisClose();
  uint32_t now = millis();
  standby = true;
  standbyStart = now;
  blinking = false;
  nextBlink = now + 1500;
  nextGaze = now + 1000;
  gzTx = 0; gzTy = 0;
  faceConnected = connected;
  lastFaceKey = -1;
  curEv = EV_NONE;
  lastEv = EV_NONE;
  yawned = false;
  nextEvMs = now + 3500;
  fxEv = EV_NONE; fxFade = 0;
  Fc.openL = Fc.openR = 0.05f;                 // mata membuka perlahan
  bgCur = bgPrev = 0; bgMix = 1; bgNext = now + 12000;
  resetStandbyFx();
  gfx->fillScreen(COL_BG);
  drawRing(1.0f, connected ? COL_GREEN : COL_RED);
}

void forceBlink(uint32_t now) {
  if (blinking) return;
  blinking = true; blinkStart = now; dblBlink = false;
}

void startEv(int ev, uint32_t now) {
  forceBlink(now);                             // kedip menyamarkan pergantian aksi
  fxEv = ev;
  curEv = ev;
  lastEv = ev;
  evStartMs = now;
  evEndMs = now + EV_DUR[ev];
  nextEvMs = evEndMs + random(1500, 3800);
}

// 0 = mata terbuka, 1 = tertutup. Kadang kedip dobel.
float blinkAmount(uint32_t now) {
  if (!blinking) {
    if (now < nextBlink) return 0.0f;
    blinking = true;
    blinkStart = now;
    dblBlink = (random(0, 100) < 25);
  }
  int32_t t1 = (int32_t)(now - blinkStart);
  float a = 0.0f;
  if (t1 < 170) a = sinf(3.14159f * t1 / 170.0f);
  if (dblBlink) {
    int32_t t2 = t1 - 260;
    if (t2 >= 0 && t2 < 170) a = max(a, sinf(3.14159f * t2 / 170.0f));
  }
  if (t1 >= (dblBlink ? 430 : 170)) {
    blinking = false;
    nextBlink = now + random(1800, 5200);
    a = 0.0f;
  }
  return a;
}

// Menentukan pose target; pose sebenarnya mengejar target dengan halus
void faceTargets(uint32_t now, int mood, uint32_t st) {
  float p = (curEv != EV_NONE) ? (float)(now - evStartMs) : 0.0f;
  float d = (curEv != EV_NONE) ? (float)EV_DUR[curEv] : 1.0f;
  float q = constrain(p / d, 0.0f, 1.0f);

  Ft.openL = 1; Ft.openR = 1; Ft.happy = 0; Ft.lookX = gzTx; Ft.lookY = gzTy;
  Ft.browY = 0; Ft.browR = 0; Ft.browTilt = 0; Ft.browA = 0;
  Ft.smile = 0.55f; Ft.mouthOpen = 0; Ft.mouthW = 28; Ft.tongue = 0; Ft.blush = 0.3f;
  Ft.love = 0; Ft.spiral = 0; Ft.jump = 0; Ft.shake = 0;

  if (mood == MOOD_SLEEP) {
    Ft.openL = 0.12f; Ft.openR = 0.12f; Ft.lookX = 0; Ft.lookY = 0;
    Ft.smile = 0; Ft.blush = 0.15f;
    return;
  }
  if (mood == MOOD_OFF) {                       // sedih, mencari-cari HP
    Ft.openL = 0.75f; Ft.openR = 0.75f;
    Ft.lookX = sinf(now / 1700.0f); Ft.lookY = 0.2f;
    Ft.smile = -0.7f; Ft.browA = 1; Ft.browY = 2; Ft.browTilt = -5; Ft.blush = 0;
    return;
  }

  float sl = constrain(((float)st - (SLEEP_MS - 9000)) / 5800.0f, 0.0f, 1.0f);   // mulai mengantuk
  if (sl > 0.0f) {
    float lid = 0.5f + 0.5f * sinf(now / 450.0f);
    Ft.openL = Ft.openR = 1.0f - 0.45f * sl * lid;
    Ft.smile = 0.3f;
  }

  switch (curEv) {
    case EV_HAPPY:
      Ft.happy = 1; Ft.smile = 1; Ft.blush = 1;
      Ft.jump = -fabsf(sinf(p * 0.011f)) * 7.0f * (1.0f - q);
      break;
    case EV_LOVE:
      Ft.love = 1; Ft.openL = 0; Ft.openR = 0; Ft.smile = 1; Ft.blush = 1;
      Ft.lookX = 0; Ft.lookY = 0;
      Ft.jump = -fabsf(sinf(p * 0.008f)) * 3.0f;
      break;
    case EV_WINK:
      Ft.smile = 1; Ft.blush = 0.7f; Ft.lookX = 0; Ft.lookY = 0; Ft.happy = 0.35f;
      Ft.browA = 1; Ft.browY = -2;
      if (p > 150 && p < 700) Ft.openR = 0.04f;
      break;
    case EV_SURPRISE:
      Ft.openL = 1.3f; Ft.openR = 1.3f; Ft.browA = 1; Ft.browY = -9;
      Ft.smile = 0; Ft.mouthOpen = 0.6f; Ft.mouthW = 14; Ft.blush = 0;
      Ft.lookX = 0; Ft.lookY = 0;
      Ft.jump = -fabsf(sinf(p * 0.012f)) * 10.0f * expf(-p / 500.0f);
      break;
    case EV_CURIOUS:
      Ft.smile = 0.2f; Ft.mouthW = 20; Ft.browA = 1; Ft.browR = -7; Ft.browY = -2;
      if (p < 800)       { Ft.lookX = -1; Ft.lookY = -0.6f; }
      else if (p < 1700) { Ft.lookX = 1;  Ft.lookY = -0.6f; }
      else if (p < 2500) { Ft.lookX = 0;  Ft.lookY = -1; }
      else               { Ft.lookX = 0;  Ft.lookY = 0; }
      break;
    case EV_DIZZY:
      Ft.spiral = 1; Ft.smile = sinf(p * 0.012f) * 0.8f; Ft.blush = 0.6f;
      Ft.shake = sinf(p * 0.02f) * 5.0f; Ft.jump = sinf(p * 0.02f) * 2.0f;
      Ft.lookX = 0; Ft.lookY = 0;
      break;
    case EV_MUSIC:
      Ft.happy = 1; Ft.smile = 1; Ft.blush = 0.8f; Ft.lookX = 0; Ft.lookY = 0;
      { float bl = audBass(); float k = (bl >= 0.0f) ? (0.25f + 1.5f * bl) : 1.0f;   // v22.22: lompat mengikuti bass lagu
        Ft.shake = sinf(p * 0.008f) * 6.0f * k;
        Ft.jump = -fabsf(sinf(p * 0.008f)) * 4.0f * k; }
      break;
    case EV_COOL:
      Ft.smile = 0.9f; Ft.blush = 0.2f; Ft.lookX = 0; Ft.lookY = 0;
      Ft.jump = sinf(p * 0.004f) * 1.5f;
      break;
    case EV_SNEEZE:
      if (p < 750) {
        float u = p / 750.0f;
        Ft.lookY = -0.8f * u; Ft.openL = Ft.openR = 1.0f - 0.55f * u;
        Ft.mouthOpen = 0.7f * u; Ft.mouthW = 18; Ft.smile = 0;
        Ft.jump = -6.0f * u; Ft.browA = 1; Ft.browY = -4.0f * u;
      } else if (p < 1100) {
        Ft.openL = Ft.openR = 0.04f; Ft.mouthOpen = 0.9f; Ft.mouthW = 24; Ft.smile = 0;
        Ft.jump = 9; Ft.shake = (((int)(p / 35.0f)) % 2) ? 5.0f : -5.0f;
        Ft.lookY = 0.5f; Ft.browA = 1; Ft.browTilt = 4;
      } else {
        Ft.smile = 0.5f; Ft.blush = 0.6f;
      }
      break;
    case EV_VROOM:
      Ft.openL = Ft.openR = 0.55f; Ft.browA = 1; Ft.browTilt = 6; Ft.browY = 2;
      Ft.smile = 0.5f; Ft.mouthW = 30; Ft.lookX = 0; Ft.lookY = 0.1f; Ft.blush = 0.1f;
      Ft.shake = sinf(p * 0.09f) * 1.6f; Ft.jump = sinf(p * 0.13f) * 1.2f;
      break;
    case EV_TONGUE:
      Ft.happy = 0.5f; Ft.smile = 1; Ft.tongue = 1; Ft.mouthOpen = 0.25f; Ft.mouthW = 30; Ft.blush = 1;
      Ft.jump = -fabsf(sinf(p * 0.01f)) * 3.0f;
      if (p > 250 && p < 1400) Ft.openR = 0.05f;
      break;
    case EV_PEEK:                                  // melirik jam di bawah
      Ft.lookX = 0; Ft.lookY = 1; Ft.smile = 0.4f; Ft.browA = 1; Ft.browY = -2;
      break;
    case EV_SHY:
      Ft.lookX = (p < 1300) ? -1.0f : 0.7f; Ft.lookY = 0.6f;
      Ft.openL = Ft.openR = 0.8f; Ft.smile = 0.5f; Ft.mouthW = 18; Ft.blush = 1;
      Ft.browA = 1; Ft.browY = -1; Ft.browTilt = -3;
      Ft.shake = sinf(p * 0.012f) * 1.5f; Ft.jump = 2;
      break;
    case EV_ANGRY:
      Ft.openL = Ft.openR = 0.7f; Ft.browA = 1; Ft.browTilt = 8; Ft.browY = 5;
      Ft.smile = -0.8f; Ft.mouthW = 22; Ft.blush = 0; Ft.lookX = 0; Ft.lookY = 0.1f;
      Ft.shake = sinf(p * 0.05f) * 2.0f;
      break;
    case EV_SPARKLE:
      Ft.openL = Ft.openR = 1.15f; Ft.lookX = 0; Ft.lookY = -0.3f; Ft.smile = 0.9f;
      Ft.mouthW = 26; Ft.blush = 0.8f; Ft.browA = 1; Ft.browY = -4;
      Ft.jump = -fabsf(sinf(p * 0.006f)) * 3.0f;
      break;
    case EV_KISS:
      Ft.smile = 0; Ft.mouthOpen = 0.2f; Ft.mouthW = 9; Ft.blush = 1; Ft.lookX = 0; Ft.lookY = 0;
      Ft.happy = 0.3f;
      if (p > 350 && p < 900) Ft.openR = 0.04f;
      break;
    case EV_THINK:
      Ft.lookX = 0.9f; Ft.lookY = -0.9f; Ft.smile = 0.05f; Ft.mouthW = 14; Ft.blush = 0.1f;
      Ft.browA = 1; Ft.browR = -6; Ft.browY = -3;
      break;
    case EV_DANCE:
      Ft.happy = 1; Ft.smile = 1; Ft.blush = 0.9f; Ft.lookY = 0;
      Ft.shake = sinf(p * 0.009f) * 12.0f; Ft.lookX = sinf(p * 0.009f) * 0.6f;
      Ft.jump = -fabsf(sinf(p * 0.009f)) * 6.0f;
      break;
    case EV_LAUGH:
      Ft.happy = 1; Ft.smile = 1; Ft.blush = 1; Ft.lookX = 0; Ft.lookY = 0;
      Ft.mouthOpen = 0.55f + 0.4f * fabsf(sinf(p * 0.012f)); Ft.mouthW = 34;
      Ft.shake = sinf(p * 0.03f) * 3.0f; Ft.jump = -fabsf(sinf(p * 0.015f)) * 5.0f;
      break;
    case EV_CRY:
      Ft.openL = Ft.openR = 0.7f; Ft.browA = 1; Ft.browTilt = -6; Ft.browY = 3;
      Ft.smile = -1; Ft.mouthOpen = 0.25f + 0.1f * sinf(p * 0.02f); Ft.mouthW = 18;
      Ft.blush = 0; Ft.lookX = 0; Ft.lookY = 0.3f; Ft.shake = sinf(p * 0.025f) * 1.2f;
      break;
    case EV_STAREYES:
      Ft.smile = 1; Ft.mouthOpen = 0.35f; Ft.mouthW = 24; Ft.blush = 1; Ft.lookX = 0; Ft.lookY = 0;
      Ft.jump = -fabsf(sinf(p * 0.007f)) * 3.0f;
      break;
    case EV_HELMET:
      Ft.smile = 0.6f; Ft.mouthW = 26; Ft.lookX = 0; Ft.lookY = 0.1f; Ft.blush = 0.4f;
      if (p > 380 && p < 600) Ft.jump = 3;
      break;
    case EV_BIKE:
      Ft.lookX = -1.0f + 2.0f * q; Ft.lookY = -0.8f; Ft.smile = 0.4f; Ft.mouthW = 22;
      Ft.browA = 1; Ft.browY = -3;
      break;
    case EV_COFFEE:
      Ft.happy = 0.9f; Ft.smile = 1; Ft.blush = 0.8f; Ft.lookX = 0; Ft.lookY = 0;
      Ft.jump = sinf(p * 0.005f) * 1.5f;
      break;
    case EV_HELLO:
      Ft.happy = 0.6f; Ft.smile = 1; Ft.blush = 0.8f; Ft.browA = 1; Ft.browY = -3;
      Ft.lookX = 0.6f; Ft.lookY = 0.2f; Ft.jump = -fabsf(sinf(p * 0.01f)) * 2.0f;
      break;
    case EV_RADAR:
      Ft.openL = Ft.openR = 0.6f; Ft.browA = 1; Ft.browTilt = 3; Ft.browY = 2;
      Ft.smile = 0.1f; Ft.mouthW = 18; Ft.blush = 0; Ft.lookX = sinf(p * 0.006f); Ft.lookY = -0.1f;
      break;
    case EV_SIGNAL:
      Ft.lookX = 0; Ft.lookY = -1; Ft.browA = 1; Ft.browY = -3; Ft.smile = 0.1f; Ft.mouthW = 20; Ft.blush = 0;
      break;
    case EV_BATT:
      Ft.openL = Ft.openR = 0.5f; Ft.smile = -0.6f; Ft.mouthW = 20; Ft.blush = 0; Ft.lookX = sinf(p * 0.004f) * 0.5f;
      Ft.lookY = 0.3f; Ft.shake = (q > 0.7f) ? sinf(p * 0.05f) * 1.5f : 0;
      break;
    case EV_RAIN:
      Ft.lookX = 0; Ft.lookY = -0.6f; Ft.smile = 0.2f; Ft.mouthW = 22; Ft.blush = 0.2f;
      break;
    case EV_ROCKET:
      Ft.lookX = 0.8f; Ft.lookY = -1.0f + 1.6f * q; Ft.smile = 0.5f; Ft.mouthOpen = 0.3f; Ft.mouthW = 20; Ft.browA = 1; Ft.browY = -3;
      break;
    case EV_PONG: {
      float tri = fabsf(fmodf(p / 800.0f, 2.0f) - 1.0f);
      Ft.lookX = constrain((tri - 0.5f) * 2.0f, -1.0f, 1.0f); Ft.lookY = -0.8f; Ft.smile = 0.3f; Ft.mouthW = 22;
      break;
    }
    case EV_FLAG:
      Ft.happy = 0.8f; Ft.smile = 1; Ft.mouthOpen = 0.4f; Ft.mouthW = 28; Ft.blush = 1; Ft.lookX = 0.7f; Ft.lookY = -0.5f;
      Ft.jump = -fabsf(sinf(p * 0.01f)) * 3.0f;
      break;
    case EV_FUEL:
      Ft.lookX = 0; Ft.lookY = -1; Ft.smile = -0.5f; Ft.mouthW = 18; Ft.browA = 1; Ft.browY = -4; Ft.blush = 0;
      Ft.shake = (q > 0.6f) ? sinf(p * 0.04f) * 1.5f : 0;
      break;
    case EV_BALLOON:
      Ft.happy = 0.5f; Ft.smile = 1; Ft.blush = 0.8f; Ft.lookX = sinf(p * 0.004f); Ft.lookY = -1;
      Ft.jump = sinf(p * 0.006f) * 2.0f;
      break;
    case EV_FIREWORK: {
      static const float LX[3] = {-0.7f, 0.7f, 0.0f};
      int bi = min(2, (int)(p / 1000.0f));
      Ft.lookX = LX[bi]; Ft.lookY = -0.9f; Ft.happy = 0.3f; Ft.smile = 1; Ft.mouthOpen = 0.4f; Ft.mouthW = 22; Ft.blush = 0.6f;
      break;
    }
    case EV_MAGNIFY:
      Ft.lookX = sinf(p * 0.0035f); Ft.lookY = 0; Ft.smile = 0.2f; Ft.browA = 1; Ft.browY = -3; Ft.mouthW = 20;
      break;
    case EV_GAME:
      Ft.openL = Ft.openR = 0.75f; Ft.browA = 1; Ft.browTilt = 4; Ft.lookX = -0.5f; Ft.lookY = 0.7f;
      Ft.smile = 0.1f; Ft.mouthW = 16; Ft.shake = sinf(p * 0.03f) * 0.8f;
      break;
    case EV_GHOST: {
      float gx = 210.0f - q * 230.0f;
      Ft.lookX = constrain((gx - 120.0f) / 70.0f, -1.0f, 1.0f); Ft.lookY = -0.7f;
      Ft.browA = 1; Ft.browTilt = -5; Ft.browY = -4; Ft.smile = -0.5f; Ft.mouthOpen = 0.7f; Ft.mouthW = 16;
      Ft.shake = sinf(p * 0.06f) * 2.0f; Ft.blush = 0;
      break;
    }
    case EV_CAT:
      Ft.happy = 0.4f; Ft.smile = 1; Ft.blush = 1; Ft.mouthW = 24; Ft.lookX = 0; Ft.lookY = 0;
      Ft.jump = sinf(p * 0.008f) * 2.0f;
      break;
    case EV_THUNDER: {
      bool fl = p > 700 && fmodf(p, 1100.0f) < 280.0f;
      Ft.openL = Ft.openR = 1; Ft.smile = -0.3f; Ft.mouthOpen = 0.6f; Ft.mouthW = 16; Ft.lookX = 0; Ft.lookY = -0.6f;
      Ft.shake = fl ? sinf(p * 0.25f) * 3.0f : 0; Ft.blush = 0;
      break;
    }
    case EV_TRAFFIC:
      Ft.lookX = 0.8f; Ft.lookY = -0.2f;
      if (p < 1800) { Ft.smile = -0.1f; Ft.browA = 1; Ft.browTilt = 3; Ft.mouthW = 18; }
      else { Ft.smile = 1; Ft.mouthOpen = 0.4f; Ft.mouthW = 28; Ft.blush = 0.8f; Ft.happy = p > 2700 ? 0.5f : 0.0f;
             Ft.jump = -fabsf(sinf(p * 0.012f)) * 3.0f; }
      break;
    case EV_EAT:
      if (p < 1000) { Ft.lookX = 0.6f; Ft.lookY = 0.5f; Ft.smile = 0.3f; Ft.mouthW = 22; Ft.mouthOpen = p > 600 ? 0.5f : 0.0f; }
      else { Ft.happy = 0.8f; Ft.smile = 1; Ft.blush = 1; Ft.mouthW = 24; Ft.lookX = 0; Ft.lookY = 0;
             Ft.mouthOpen = fabsf(sinf(p * 0.014f)) * 0.6f; Ft.jump = -fabsf(sinf(p * 0.014f)) * 2.0f; }
      break;
    case EV_YAWN: {
      float s = sinf(3.14159f * q);
      Ft.openL = Ft.openR = 0.12f + 0.4f * (1.0f - q);
      Ft.happy = 0.7f * s; Ft.mouthOpen = s; Ft.mouthW = 20; Ft.smile = 0;
      Ft.browA = 1; Ft.browY = -6.0f * s; Ft.lookY = -0.3f; Ft.jump = -5.0f * s; Ft.blush = 0.2f;
      break;
    }
    default: break;
  }
}

// ---------- Info lagu (dari aplikasi MotorNav) ----------
char musicTxt[100] = "", musicTitle[64] = "", musicArtist[48] = "";
bool musicOn = false;
uint32_t musicElapsed = 0, musicTickAt = 0;                       // detik lagu berjalan (perkiraan, dihitung sejak lagu terdeteksi)

void pollMusic(bool connected) {                                  // dipanggil tiap loop; data dibaca tiap 500 ms
  static uint32_t last = 0;
  uint32_t now = millis();
  if (musicOn && musicTickAt) musicElapsed += now - musicTickAt;  // hitung waktu hanya saat diputar
  musicTickAt = now;
  if (now - last < 500) return;
  last = now;
  if (!connected) { musicOn = false; return; }
  char nt[64], na[48];
  bool playing = false;
  if (appMusStamp != 0 && now - appMusStamp < 15000UL) {          // sumber satu-satunya: aplikasi MotorNav
    appMusNew = false;
    asciiClean(appMusTitle, nt, sizeof(nt));
    asciiClean(appMusArtist, na, sizeof(na));
    playing = (appMusState != 0);
  } else {
    nt[0] = 0; na[0] = 0;
  }
  if (strcmp(nt, musicTitle) || strcmp(na, musicArtist)) {        // lagu berganti: waktu mulai dari 0
    strcpy(musicTitle, nt); strcpy(musicArtist, na);
    musicElapsed = 0;
  }
  snprintf(musicTxt, sizeof(musicTxt), "%s%s%s", nt, (nt[0] && na[0]) ? " - " : "", na);
  musicOn = (playing && musicTxt[0] != 0);                        // jeda/berhenti = mati
}

// Jendela teks 21 huruf; teks yang lebih panjang bergeser satu huruf tiap 280 ms
int textWindow(const char *src, char *win, uint32_t now) {
  const int W = 21;
  int len = strlen(src);
  if (len <= W) { strcpy(win, src); return len; }
  int cyc = len + 3, st = (int)((now / 280) % (uint32_t)cyc);
  for (int i = 0; i < W; i++) { int k = (st + i) % cyc; win[i] = (k < len) ? src[k] : ' '; }
  win[W] = 0;
  return W;
}
int musicWindow(char *win, uint32_t now) { return textWindow(musicTxt, win, now); }

void drawMusicLine(uint32_t now) {                                // standby wajah: ikon nada hijau + teks kecil
  char win[24];
  int n = musicWindow(win, now);
  int x0 = 120 - (12 + n * 6) / 2;
  cCircle(x0 + 2, 189, 2, COL_GREEN);
  cLine(x0 + 4, 185, x0 + 4, 189, COL_GREEN);
  cLine(x0 + 4, 185, x0 + 7, 187, COL_GREEN);
  cText(win, x0 + 12, 185, 1, COL_DIM);
}

void renderFace(uint32_t now, int mood, bool connected, float blink) {
  useClip(1);                                                     // animasi tidak boleh keluar dari area wajah
  cvClear();
  drawGlow(now, mood, frameDt);
  drawBg(now, mood, frameDt);
  float p = (fxEv != EV_NONE) ? (float)(now - evStartMs) : 0.0f;
  float bob = (mood == MOOD_SLEEP) ? sinf(now / 1500.0f) * 3.0f : sinf(now / 1000.0f) * 2.0f;   // bernapas
  int dx = (int)lroundf(Fc.shake);
  int dy = (int)lroundf(Fc.jump + bob);
  uint16_t ec = (mood == MOOD_OFF) ? COL_RED : COL_GREEN;
  int ey = 112 + dy + (int)lroundf(Fc.lookY * 5.0f);
  int exo = (int)lroundf(Fc.lookX * 9.0f);
  int cxL = 80 + dx + exo, cxR = 160 + dx + exo;

  drawFxBack(p);
  bool starEyes = (curEv == EV_STAREYES && mood == MOOD_HAPPY);

  if (fxEv == EV_VROOM) {                                         // garis kecepatan
    for (int i = 0; i < 5; i++) {
      int x = 204 - (int)fmodf(p * 0.55f + i * 53.0f, 190.0f);
      if (x < CVX + 2) continue;
      cLine(x, 78 + i * 22, x + 14 + (i % 3) * 8, 78 + i * 22, fx(COL_DIM));
    }
  }

  float oL = Fc.openL * (1.0f - blink * 0.96f);
  float oR = Fc.openR * (1.0f - blink * 0.96f);
  if (mood == MOOD_SLEEP) {
    cArc(cxL, ey - 4, 17, 12, 20, 160, ec);
    cArc(cxR, ey - 4, 17, 12, 20, 160, ec);
  } else {
    if (Fc.love < 0.6f && Fc.spiral < 0.6f && !starEyes) {
      drawEye(cxL, ey, oL, Fc.happy, ec, Fc.lookX, Fc.lookY);
      drawEye(cxR, ey, oR, Fc.happy, ec, Fc.lookX, Fc.lookY);
    }
    if (starEyes) {                                                // mata bintang berdenyut
      float sc = constrain(p / 260.0f, 0.0f, 1.0f);
      int R = (int)(27.0f * sc * (1.0f + 0.1f * sinf(p * 0.012f)));
      if (R > 2) {
        cStar(cxL, ey, R, R * 45 / 100, p * 0.0015f, COL_ORANGE);
        cStar(cxR, ey, R, R * 45 / 100, p * 0.0015f, COL_ORANGE);
        cStar(cxL, ey, R / 2, R / 5, p * 0.0015f, COL_TEXT);
        cStar(cxR, ey, R / 2, R / 5, p * 0.0015f, COL_TEXT);
      }
    }
    if (Fc.love > 0.05f) {                                         // mata hati berdetak
      int s = (int)(14.0f * Fc.love * (1.0f + 0.15f * sinf(p * 0.018f)));
      if (s > 1) { cHeart(cxL, ey, s, COL_RED); cHeart(cxR, ey, s, COL_RED); }
    }
    if (Fc.spiral > 0.05f) {                                       // mata pusing berputar
      drawSpiral(cxL, ey, p * 0.012f, Fc.spiral, ec);
      drawSpiral(cxR, ey, -p * 0.012f, Fc.spiral, ec);
    }
  }

  if (fxEv == EV_COOL && mood == MOOD_HAPPY) {                    // kacamata jatuh lalu naik
    float dur = (float)EV_DUR[EV_COOL];
    float off = (p < dur - 450.0f) ? -50.0f * expf(-p / 110.0f) * cosf(p / 55.0f)
                                    : -((p - (dur - 450.0f)) / 450.0f) * 60.0f;
    if (off > -58.0f) {
      int gy = 112 + dy + (int)off;
      cRRect(cxL - 26, gy - 16, 52, 32, 9, COL_DIM);
      cRRect(cxR - 26, gy - 16, 52, 32, 9, COL_DIM);
      cRRect(cxL - 24, gy - 14, 48, 28, 8, COL_TIRE);
      cRRect(cxR - 24, gy - 14, 48, 28, 8, COL_TIRE);
      cRect(cxL + 26, gy - 6, (cxR - 26) - (cxL + 26), 5, COL_DIM);
      cLine(cxL - 16, gy - 8, cxL - 8, gy - 8, COL_TEXT);
      cLine(cxL - 16, gy - 7, cxL - 8, gy - 7, COL_TEXT);
      cLine(cxR - 16, gy - 8, cxR - 8, gy - 8, COL_TEXT);
      cLine(cxR - 16, gy - 7, cxR - 8, gy - 7, COL_TEXT);
    }
  }

  if (Fc.browA > 0.04f && mood != MOOD_SLEEP && Fc.love < 0.5f && fxEv != EV_COOL && fxEv != EV_HELMET && fxEv != EV_BATT && fxEv != EV_SIGNAL && fxEv != EV_FUEL && fxEv != EV_RAIN && fxEv != EV_THUNDER && fxEv != EV_CAT) {
    uint16_t bc = mix565(COL_BG, COL_TEXT, Fc.browA);
    int byL = ey - (int)(26.0f * constrain(Fc.openL, 0.5f, 1.3f)) - 13 + (int)Fc.browY;
    int byR = ey - (int)(26.0f * constrain(Fc.openR, 0.5f, 1.3f)) - 13 + (int)(Fc.browY + Fc.browR);
    drawBrow(cxL, byL, Fc.browTilt, true, bc);
    drawBrow(cxR, byR, Fc.browTilt, false, bc);
  }

  uint16_t cc = mix565(COL_BG, COL_CHEEK, Fc.blush);
  cCircle(52 + dx / 2, 142 + dy, 8, cc);
  cCircle(188 + dx / 2, 142 + dy, 8, cc);

  if (mood == MOOD_SLEEP) {
    int r = 3 + (int)(1.5f * (0.5f + 0.5f * sinf(now / 1500.0f)));
    cv->drawCircle(120 + dx - OX, 148 + dy - OY, r, COL_DIM);
    int br = 3 + (int)(8.0f * (0.5f + 0.5f * sinf(now / 700.0f)));   // gelembung ingus
    cv->drawCircle(131 - OX, 146 + dy - OY, br, COL_DIM);
    for (int i = 0; i < 3; i++) {                                    // Z melayang naik
      float q = ((now + i * 900) % 2700) / 2700.0f;
      uint16_t zc = (q > 0.72f) ? mix565(COL_ORANGE, COL_BG, (q - 0.72f) * 3.5f) : COL_ORANGE;
      cText("Z", 164 + (int)(q * 20.0f), 104 - (int)(q * 44.0f), 1 + (uint8_t)(q * 2.4f), zc);
    }
  } else {
    drawMouth(120 + dx, 142 + dy, Fc.smile, Fc.mouthOpen, Fc.mouthW, Fc.tongue);
  }

  if (fxEv == EV_LOVE) {                                          // hati melayang naik
    static const int hxs[5] = {46, 194, 120, 58, 182};
    for (int i = 0; i < 5; i++) {
      float q = fmodf(p + i * 520.0f, 2600.0f) / 2600.0f;
      int hx = hxs[i] + (int)(sinf(q * 6.28f + i) * 5.0f);
      uint16_t hc = fx((q > 0.8f) ? mix565(COL_RED, COL_BG, (q - 0.8f) * 5.0f) : COL_RED);
      cHeart(hx, 140 + dy - (int)(q * 76.0f), 3 + (i % 3), hc);
    }
  }
  if (fxEv == EV_MUSIC) {                                         // not balok melayang
    for (int i = 0; i < 3; i++) {
      float q = fmodf(p + i * 1100.0f, 3300.0f) / 3300.0f;
      int nx = ((i % 2) ? 188 : 48) + (int)(sinf(q * 9.4f) * 6.0f);
      uint16_t base = fx((i % 2) ? COL_ORANGE : COL_TEXT);
      drawNote(nx, 150 - (int)(q * 88.0f), (q > 0.75f) ? mix565(base, COL_BG, (q - 0.75f) * 4.0f) : base);
    }
  }
  if (fxEv == EV_SNEEZE && p > 1000 && p < 1750) {
    cTextCenter("Hatchii!", 62 + max(0, 10 - (int)((p - 1000.0f) / 20.0f)), 2, fx(COL_ORANGE));
  }
  if (fxEv == EV_VROOM && p > 200 && p < EV_DUR[EV_VROOM] - 300) {
    cTextCenter("VROOM", 64 + (int)(sinf(p * 0.05f) * 2.0f), 2, fx(COL_ORANGE));
  }

  drawFxNew(p, dx, dy, cxL, cxR, ey);
  drawFxNew2(p, dx, dy, cxL, cxR, ey);
  drawFxNew3(p, dx, dy, cxL, cxR, ey);
  drawFxNew4(p, dx, dy, cxL, cxR, ey);

  int cm = clockMinutes(connected);                               // jam kecil di bawah wajah
  if (cm >= 0) {
    char buf[8];
    snprintf(buf, sizeof(buf), "%02d:%02d", cm / 60, cm % 60);
    uint16_t tc = (fxEv == EV_PEEK) ? mix565(COL_DIM, COL_ORANGE, 0.5f + 0.5f * sinf(p * 0.012f)) : COL_DIM;
    cTextCenter(buf, 168, 2, tc);
  } else {
    cTextCenter("Menunggu HP", 168, 2, COL_DIM);
  }
  pollMusic(connected);
  if (musicOn) {
    drawMusicLine(now);                                           // lagu diputar: baris info lagu di bawah jam
  }
  useClip(0);
}

int randomEv() {
  if (musicOn && cfg[9] && random(0, 100) < 75) return (random(0, 100) < 60) ? EV_MUSIC : EV_DANCE;   // v22.21: lagu diputar -> wajah sering menari
  int ev = random(EV_HAPPY, EV_YAWN);
  if (ev == lastEv && !musicOn) ev = (ev == EV_EAT) ? EV_HAPPY : ev + 1;
  return ev;
}

// ---------- Standby mode 1: foto + jam transparan + cincin pelangi ----------
int clockMinutes(bool connected);

// ---------- Video latar (JPEG per frame di partisi spiffs, mulai offset VID_OFF) ----------
// Satu slot foto/video = SELURUH partisi data (lihat partitions.csv, 0x250000 = 2,3 MB).
#define VID_HDR1  4096             // format lama "MNVIDEO1": header 4 KB, maks 240 frame
#define VID_HDR2  8192             // format baru "MNVIDEO2": header 8 KB, maks VID_MAXF frame
#define VID_MAXF  900
#define VID_MAXFPS 60              // v22.28 (dulu 30)
#define JD_POOL 5120               // memori kerja dekoder JPEG ROM (min. ~3100 byte)
uint8_t *jdPool = nullptr;                 // dialokasi di awal boot (wallpaperLoad), saat RAM masih lega
const uint8_t *vidBase = nullptr;        // frame dipetakan langsung dari flash
uint16_t vidN = 0, vidFps = 15;
const uint32_t *vidTab = nullptr;        // tabel (offset, panjang) tiap frame, dibaca langsung dari header di flash
bool     vidOK = false;

#if HAVE_SD
// ---------- v22.27/28: microSD ----------
SPIClass sdSpi(HSPI);
bool sdOK = false;                         // kartu terpasang & terbaca
#define SDV_WIN 128                        // jendela tabel frame di RAM (128 frame = 1 KB)

void sdBegin() {
  sdSpi.begin(SD_SCK, SD_MISO, SD_MOSI, SD_CS);
  static const uint32_t hzTry[] = {SD_HZ, 10000000, 4000000, 1000000};   // v22.40: kabel jumper sering gagal di 20 MHz -> coba makin pelan
  sdOK = false;
  for (uint8_t i = 0; i < 4 && !sdOK; i++) {
    if (i) SD.end();
    sdOK = SD.begin(SD_CS, sdSpi, hzTry[i]);
    if (sdOK) Serial.printf("SD: terbaca @ %u Hz | tipe %u | %u MB\n", (unsigned)hzTry[i], (unsigned)SD.cardType(), (unsigned)(SD.cardSize() / (1024ULL * 1024ULL)));
  }
  if (!sdOK) Serial.println("SD: tidak ada / gagal dibaca (pakai partisi flash) -- cek kabel, VCC 5V, format FAT32");
}

// Video di SD: "MNVIDEO2" (header 8 KB, maks 900 frame) atau "MNVIDEO3" (header = 16 + n*8 dibulatkan 4 KB, sampai 65535 frame).
// Tabel (offset,panjang) tiap frame dibaca per jendela; hanya satu penyangga frame di RAM.
struct SdVid {
  File f;
  uint16_t n = 0, fps = 0;
  uint32_t hdr = 0, total = 0, maxLen = 0;
  uint32_t win[SDV_WIN * 2];
  int winBase = -1;
  uint8_t *buf = nullptr;

  void close() { if (f) f.close(); if (buf) { free(buf); buf = nullptr; } n = 0; winBase = -1; }

  bool loadWin(int base) {
    int cnt = n - base; if (cnt > SDV_WIN) cnt = SDV_WIN;
    if (!f.seek(16 + (uint32_t)base * 8)) return false;
    if (f.read((uint8_t *)win, (size_t)cnt * 8) != (int)((size_t)cnt * 8)) return false;
    winBase = base;
    return true;
  }

  bool open(const char *path) {
    close();
    f = SD.open(path, FILE_READ);
    if (!f) return false;
    uint8_t h[16];
    if (f.read(h, 16) != 16) { close(); return false; }
    bool v2 = memcmp(h, "MNVIDEO2", 8) == 0, v3 = memcmp(h, "MNVIDEO3", 8) == 0;
    if (!v2 && !v3) { close(); return false; }
    n = h[8] | (h[9] << 8); fps = h[10] | (h[11] << 8);
    total = h[12] | (h[13] << 8) | (h[14] << 16) | ((uint32_t)h[15] << 24);
    hdr = v2 ? VID_HDR2 : ((16 + (uint32_t)n * 8 + 4095) / 4096) * 4096;
    if (n < 1 || (v2 && n > VID_MAXF) || fps < 1 || fps > VID_MAXFPS || total == 0 || (uint64_t)hdr + total > f.size()) { close(); return false; }
    maxLen = 0;
    for (int base = 0; base < n; base += SDV_WIN) {                // cek seluruh tabel sekali, sekalian cari frame terbesar
      if (!loadWin(base)) { close(); return false; }
      int cnt = n - base; if (cnt > SDV_WIN) cnt = SDV_WIN;
      for (int k = 0; k < cnt; k++) {
        uint32_t off = win[k * 2], len = win[k * 2 + 1];
        if (len == 0 || len > 65535 || (uint64_t)off + len > total) { Serial.println("SD: tabel frame rusak"); close(); return false; }
        if (len > maxLen) maxLen = len;
      }
    }
    buf = (uint8_t *)malloc(maxLen);
    if (!buf) { Serial.println("SD: RAM penyangga frame gagal"); close(); return false; }
    winBase = -1;
    return true;
  }

  bool frame(int i, const uint8_t *&p, uint32_t &len) {            // baca frame i ke penyangga
    if (!f || !buf || i < 0 || i >= n) return false;
    if (winBase < 0 || i < winBase || i >= winBase + SDV_WIN) { if (!loadWin((i / SDV_WIN) * SDV_WIN)) return false; }
    uint32_t off = win[(i - winBase) * 2];
    len = win[(i - winBase) * 2 + 1];
    if (!f.seek(hdr + off) || f.read(buf, len) != (int)len) return false;
    p = buf;
    return true;
  }
};

static SdVid sdw;                          // video wallpaper yang sedang diputar
static bool sdVid = false;                 // wallpaper video sedang diputar dari SD

static void sdWallClose() {                // lepas file wallpaper + RAM-nya (sebelum upload / hapus / muat ulang)
  vidOK = false; sdVid = false;
  sdw.close();
}
#else
bool sdOK = false;
static void sdWallClose() {}
#endif

#if TILT_UI
uint16_t rbwCol[256];
float    rbwC[1024], rbwS[1024];
float    rbwRot = 0;
bool     rbwInit = false;

void rbwSetup() {
  for (int i = 0; i < 256; i++) {
    float h = i / 42.6667f; int sx = (int)h; float f = h - sx;
    uint8_t q = (uint8_t)(255 * (1 - f)), t = (uint8_t)(255 * f), r = 0, g = 0, b = 0;
    switch (sx % 6) {
      case 0: r = 255; g = t; b = 0; break;
      case 1: r = q; g = 255; b = 0; break;
      case 2: r = 0; g = 255; b = t; break;
      case 3: r = 0; g = q; b = 255; break;
      case 4: r = t; g = 0; b = 255; break;
      default: r = 255; g = 0; b = q; break;
    }
    rbwCol[i] = fb->color565(r, g, b);
  }
  for (int i = 0; i < 1024; i++) { float a = i * 6.2831853f / 1024.0f; rbwC[i] = cosf(a); rbwS[i] = sinf(a); }
  rbwInit = true;
}

#if HAVE_ROM_JPEG
static const uint8_t *jdSrc = nullptr;
static uint32_t jdLen = 0, jdPos = 0;

static UINT jdIn(JDEC *jd, BYTE *buf, UINT n) {                    // baca data frame dari flash (buf null = lewati)
  (void)jd;
  if (jdPos + n > jdLen) n = jdLen - jdPos;
  if (buf) memcpy(buf, jdSrc + jdPos, n);
  jdPos += n;
  return n;
}

static UINT jdOut(JDEC *jd, void *bmp, JRECT *r) {                 // blok RGB888 hasil decode -> kanvas RGB565
  (void)jd;
  const uint8_t *p = (const uint8_t *)bmp;
  for (int y = r->top; y <= r->bottom; y++) {
    if (y >= 240) break;
    uint16_t *row = fb->row(y);
    for (int x = r->left; x <= r->right; x++, p += 3)
      if (x < 240) row[x] = ((p[0] & 0xF8) << 8) | ((p[1] & 0xFC) << 3) | (p[2] >> 3);
  }
  return 1;
}

bool vidDecode(int i) {
#if HAVE_SD
  if (sdVid) {                                                     // v22.27: frame dibaca dari SD ke penyangga RAM
    if (!jdPool) return false;
    const uint8_t *fp; uint32_t len;
    if (!sdw.frame(i, fp, len)) return false;
    jdSrc = fp; jdLen = len; jdPos = 0;
  } else
#endif
  {
    if (!jdPool || !vidTab) return false;
    jdSrc = vidBase + vidTab[i * 2]; jdLen = vidTab[i * 2 + 1]; jdPos = 0;
  }
  JDEC jd;
  JRESULT rc = jd_prepare(&jd, jdIn, jdPool, JD_POOL, nullptr);
  if (rc == JDR_OK && (jd.width != 240 || jd.height != 240)) rc = JDR_PAR;
  if (rc == JDR_OK) rc = jd_decomp(&jd, jdOut, 0);
  static bool warned = false;
  if (rc != JDR_OK && !warned) { warned = true; Serial.printf("Video: decode frame %d gagal (kode %d)\n", i, (int)rc); }
  return rc == JDR_OK;
}

bool mapDecode(const uint8_t *p, uint32_t len) {                   // JPEG 240x240 dari RAM -> kanvas
  if (!jdPool || !fb || !p || len < 100) return false;
  jdSrc = p; jdLen = len; jdPos = 0;
  JDEC jd;
  JRESULT rc = jd_prepare(&jd, jdIn, jdPool, JD_POOL, nullptr);
  if (rc == JDR_OK && (jd.width != 240 || jd.height != 240)) rc = JDR_PAR;
  if (rc == JDR_OK) rc = jd_decomp(&jd, jdOut, 0);
  return rc == JDR_OK;
}
#else
bool vidDecode(int) { return false; }
bool mapDecode(const uint8_t *, uint32_t) { return false; }
#endif

static inline uint16_t blend565(uint16_t bg, uint16_t fg, int a) {   // a: 0..256
  int rb = bg >> 11, gb = (bg >> 5) & 63, bb = bg & 31;
  int rf = fg >> 11, gf = (fg >> 5) & 63, bf = fg & 31;
  rb += ((rf - rb) * a) >> 8; gb += ((gf - gb) * a) >> 8; bb += ((bf - bb) * a) >> 8;
  return (uint16_t)((rb << 11) | (gb << 5) | bb);
}

// Tema "api": warna batang berdasar tinggi level (0..1), bukan posisi sudut -> gradien merah tua (pelan) sampai
// kuning terang (keras), beda total dari cincin pelangi (yang warnanya berputar mengikuti sudut, bukan level).
static inline uint16_t fireColor(float lv) {
  if (lv < 0.0f) lv = 0.0f; if (lv > 1.0f) lv = 1.0f;
  int r, g, b;
  if (lv < 0.5f) {
    float t = lv / 0.5f;
    r = 150 + (int)(105 * t); g = (int)(45 * t); b = 0;
  } else {
    float t = (lv - 0.5f) / 0.5f;
    r = 255; g = 45 + (int)(175 * t); b = (int)(70 * t);
  }
  return fb->color565(r, g, b);
}

// Visualizer simulasi (bukan dari suara asli): 64 batang pelangi menjorok ke dalam dari cincin, tembus pandang
// agar foto tetap terlihat. Simetris kiri-kanan, bass di bawah, denyut tiap ~0,5 dtk, memudar saat lagu jeda.
float vizLv[33];
float vizAmp = 0;

void drawVisualizer(uint32_t now, float dt) {
  vizAmp = ap(vizAmp, musicOn ? 1.0f : 0.0f, 350.0f, dt);
  if (vizAmp < 0.03f) { for (int f = 0; f < 33; f++) vizLv[f] = 0; return; }
  float t = now * 0.001f;
  bool real = audFresh();
  float kick = expf(-fmodf(t * 1.9f, 1.0f) * 6.0f);
  int rot = (int)rbwRot;
  for (int f = 0; f < 33; f++) {
    float n = (0.5f + 0.5f * sinf(t * (2.3f + 0.37f * f) + f * 1.7f)) * (0.5f + 0.5f * sinf(t * (1.1f + 0.21f * f) + f * 0.6f));
    float tgt = 0.12f + 0.6f * n * (1.0f - f / 55.0f) + kick * (f < 10 ? 0.5f : 0.12f);
    if (real) {                                                   // v22.22: spektrum asli dari HP (band 0 = bass), diinterpolasi ke 33 batang
      float bp = f * 15.0f / 32.0f; int b0 = (int)bp; int b1 = (b0 < 15) ? b0 + 1 : 15; float fr = bp - b0;
      float lv = (audBands[b0] * (1.0f - fr) + audBands[b1] * fr) / 255.0f;
      tgt = 0.04f + 0.96f * lv;
    }
    if (tgt > 1.0f) tgt = 1.0f;
    if (tgt > vizLv[f]) vizLv[f] = ap(vizLv[f], tgt, real ? 25.0f : 45.0f, dt);
    else vizLv[f] = max(real ? tgt : 0.0f, vizLv[f] - dt * (real ? 0.0028f : 0.0017f));
  }
  bool fireTheme = cfg[11] == 1;
  for (int i = 0; i < 64; i++) {
    int j = (i <= 32) ? i : 64 - i;
    float lvRaw = vizLv[32 - j];
    int len = min(40, (int)((3.0f + lvRaw * 20.0f) * vizAmp * cfg[4] / 100.0f));
    if (len < 2) continue;
    float a = i * 0.0981748f - 1.5708f;
    float ux = cosf(a), uy = sinf(a);
    uint16_t col = fireTheme ? fireColor(lvRaw) : rbwCol[((i << 2) + rot) & 255];
    for (int rr = 107; rr > 107 - len; rr--) {
      float fade = 1.0f - (float)(107 - rr) / (len + 1);          // pangkal pekat, ujung memudar
      int aCore = min(256, ((int)(125 * fade) + 25) * cfg[5] / 100), aHalo = min(256, ((int)(55 * fade) + 8) * cfg[5] / 100);
      for (int tt = -3; tt <= 3; tt++) {
        int x = (int)(119.5f + ux * rr - uy * tt + 0.5f), y = (int)(119.5f + uy * rr + ux * tt + 0.5f);
        if ((unsigned)x >= 240 || (unsigned)y >= 240) continue;
        uint16_t *px = fb->row(y) + x;
        *px = blend565(*px, col, (abs(tt) <= 1) ? aCore : aHalo);
      }
    }
  }
}

// Kartu pemutar kecil di bawah jam: judul, artis, detik berjalan + garis gelombang mengalir (bayangan hitam agar terbaca)
void drawPhotoPlayer(uint32_t now) {
  if (!musicOn) return;
  char win[24];
  fb->setTextSize(1);
  int n = textWindow(musicTitle, win, now), tx = 120 - n * 3;
  fb->setTextColor(0x0000); fb->setCursor(tx + 1, 152); fb->print(win);
  fb->setTextColor(0xFFFF); fb->setCursor(tx, 151); fb->print(win);
  if (musicArtist[0]) {
    n = textWindow(musicArtist, win, now); tx = 120 - n * 3;
    fb->setTextColor(0x0000); fb->setCursor(tx + 1, 163); fb->print(win);
    fb->setTextColor(0xCE79); fb->setCursor(tx, 162); fb->print(win);
  }
  // Progress bar bergelombang: sudah diputar = gelombang putih mengalir, sisa = garis datar abu-abu
  uint32_t posS = musPosAt ? (uint32_t)musPosS + (now - musPosAt) / 1000UL : musicElapsed / 1000UL;
  uint32_t durS = musDurS;
  if (durS && posS > durS) posS = durS;
  const int X0 = 50 + (int)(44 * artAmpG), X1 = 190 + (int)(6 * artAmpG), Y = 178 + (int)(4 * artAmpG);   // v22.41: sampul di kiri bar -> bar bergeser kanan
  float frac = durS ? (float)posS / (float)durS : 1.0f;
  int xe = X0 + (int)((X1 - X0) * frac);
  const uint16_t GREY = 0x8410;
  if (xe < X1) {
    fb->drawLine(xe, Y + 2, X1, Y + 2, 0x0000);
    fb->drawLine(xe, Y, X1, Y, GREY);
    fb->drawLine(xe, Y + 1, X1, Y + 1, GREY);
  }
  float ph = now * 0.006f;
  int px = X0, py = Y;
  for (int x = X0; x <= xe; x += 2) {
    float amp = 2.8f * fminf(1.0f, (x - X0) / 6.0f) * fminf(1.0f, (xe - x) / 14.0f);   // pangkal & ujung menghalus ke garis lurus
    int y = Y + (int)lroundf(sinf((x - X0) * 0.28f - ph) * amp);
    if (x > X0) {
      fb->drawLine(px, py + 2, x, y + 2, 0x0000);
      fb->drawLine(px, py, x, y, 0xFFFF);
      fb->drawLine(px, py + 1, x, y + 1, 0xFFFF);
    }
    px = x; py = y;
  }
  char tb[8];
  snprintf(tb, sizeof(tb), "%u:%02u", (unsigned)(posS / 60), (unsigned)(posS % 60));
  fb->setTextColor(0x0000); fb->setCursor(X0 + 1, Y + 9); fb->print(tb);
  fb->setTextColor(0xFFFF); fb->setCursor(X0, Y + 8); fb->print(tb);
  if (durS) {
    snprintf(tb, sizeof(tb), "%u:%02u", (unsigned)(durS / 60), (unsigned)(durS % 60));
    int tx = X1 - (int)strlen(tb) * 6;
    fb->setTextColor(0x0000); fb->setCursor(tx + 1, Y + 9); fb->print(tb);
    fb->setTextColor(0xCE79); fb->setCursor(tx, Y + 8); fb->print(tb);
  }
}

// Gambar sampul album 64x64, agak transparan (cfg[0]/255) supaya wallpaper tetap terlihat; sudut membulat, muncul/hilang halus
void drawArt(float dt) {
  static float amp = 0;
  amp = ap(amp, (musicOn && artOn && artBuf && cfg[1]) ? 1.0f : 0.0f, 300.0f, dt);
  artAmpG = amp;
  if (amp < 0.03f) return;
  // v22.41: sampul diperkecil jadi 36x36 dan ditaruh di KIRI progress bar / detik lagu (sebelumnya 64x64 di atas jam)
  const int S = 36, X0 = 54, Y0 = 170, R = 8;
  int a = (int)(cfg[0] * amp);
  for (int y = 0; y < S; y++) {
    int cy = (y < R) ? R - y : (y >= S - R ? y - (S - 1 - R) : 0);
    uint16_t *row = fb->row(Y0 + y) + X0;
    const uint16_t *src = artBuf + (y * ART_W / S) * ART_W;
    for (int x = 0; x < S; x++) {
      int cx = (x < R) ? R - x : (x >= S - R ? x - (S - 1 - R) : 0);
      if (cx * cx + cy * cy > R * R) continue;
      row[x] = blend565(row[x], src[x * ART_W / S], a);
    }
  }
}

void updatePhotoStandby(bool connected) {
  uint32_t now = millis();
  static uint32_t last = 0;
  static int lastVidIdx = -1;
  int vi = -1;
  if (vidOK) {                                                     // video: ganti frame sesuai fps, di antaranya tidak menggambar ulang
    vi = (int)(((uint64_t)now * vidFps / 1000) % vidN);
    if (vi == lastVidIdx) return;
  }
  if (now - last < FRAME_MS) return;
  float dt = (float)(now - last); if (dt > 100.0f) dt = 100.0f;
  last = now;
  lastVidIdx = vi;
  if (!rbwInit) rbwSetup();

  const uint16_t *csrc = nullptr;                            // piksel asli di area jam (untuk jam transparan)
  int cstride = 240;
  bool vidShown = (vi >= 0) && vidDecode(vi);                // video sebagai latar; gagal decode -> foto
  if (vidShown) {
    // video: latar di bawah huruf jam diambil dari piksel video terdekat di kirinya (tanpa penyangga RAM tambahan)
  } else {
    for (int i = 0; i < SplitCanvas::SN; i++)                // foto sebagai latar
      memcpy_P(fb->s[i], photoPtr + i * 240 * SplitCanvas::SH, 240 * SplitCanvas::SH * 2);
    csrc = photoPtr + 96 * 240 + 26;
  }

  // Jam: digambar dulu dengan warna kunci, lalu piksel kunci diganti putih transparan (campur dengan foto)
  int cm = clockMinutes(connected);
  char buf[8];
  if (cm >= 0) snprintf(buf, sizeof(buf), "%02d:%02d", cm / 60, cm % 60); else strcpy(buf, "--:--");
  const uint16_t KEY = 0x0001;
  fb->setTextSize(6);
  fb->setTextColor(KEY);
  fb->setCursor(30, 99); fb->print(buf);
  fb->setCursor(31, 99); fb->print(buf);                           // tebalkan huruf
  const int ALPHA = cfg[2];                                        // 0-255: makin besar makin putih
  for (int y = 96; y < 146; y++) {
    uint16_t *row = fb->row(y);
    uint16_t nb = row[25];                                         // piksel asli di kiri area jam
    for (int x = 26; x < 214; x++) {
      if (row[x] != KEY) { nb = row[x]; continue; }
      uint16_t pc = csrc ? csrc[(y - 96) * cstride + (x - 26)] : nb;
      int r = pc >> 11, g = (pc >> 5) & 63, b = pc & 31;
      r += ((31 - r) * ALPHA) >> 8; g += ((63 - g) * ALPHA) >> 8; b += ((31 - b) * ALPHA) >> 8;
      row[x] = (r << 11) | (g << 5) | b;
    }
  }

  pollMusic(connected);
  drawArt(dt);                                                     // sampul album semi-transparan
  if (cfg[3]) drawVisualizer(now, dt);                             // visualizer pelangi tembus pandang
  if (cfg[7]) drawPhotoPlayer(now);                                           // judul, artis, detik, garis gelombang

  // Cincin pelangi di tepi, warna bergeser sehingga tampak berputar
  rbwRot += dt * 0.07f * cfg[6] / 100.0f;
  if (rbwRot >= 256.0f) rbwRot -= 256.0f;
  int rot = (int)rbwRot;
  for (int i = 0; i < 1024; i++) {
    uint16_t c = rbwCol[((i >> 2) + rot) & 255];
    for (int r = 110; r <= 119; r++) {
      int x = (int)(119.5f + rbwC[i] * r + 0.5f), y = (int)(119.5f + rbwS[i] * r + 0.5f);
      fb->row(y)[x] = c;
    }
  }
  present();
}
#else
void updatePhotoStandby(bool connected) { (void)connected; }
bool mapDecode(const uint8_t *, uint32_t) { return false; }
#endif

void updateFace(bool connected) {
  uint32_t now = millis();
  if (now - lastFrame < FRAME_MS) return;
  float dt = (float)(now - lastFrame);
  if (dt > 100.0f) dt = 100.0f;
  lastFrame = now;
  frameDt = dt;

  if (connected != faceConnected) {
    faceConnected = connected;
    drawRing(1.0f, connected ? COL_GREEN : COL_RED);
    resetStandbyFx();
    curEv = EV_NONE;
  }

  uint32_t st = now - standbyStart;
  int mood = MOOD_HAPPY;
  if (!connected) mood = MOOD_OFF;
  else if (st > SLEEP_MS) mood = MOOD_SLEEP;

  updateOrbit(connected);
  updateStars(mood);
  if (!canvasOK) return;

  // Arah pandang acak (kadang kembali ke tengah)
  if (now >= nextGaze) {
    gzTx = random(-100, 101) / 100.0f;
    gzTy = random(-60, 61) / 100.0f;
    if (random(0, 3) == 0) { gzTx = 0; gzTy = 0; }
    nextGaze = now + random(1200, 3500);
  }

  // Penjadwal aksi acak
  if (mood != MOOD_HAPPY) {
    curEv = EV_NONE;
  } else {
    if (curEv != EV_NONE && now >= evEndMs) {
      if (!yawned && st < SLEEP_MS - 4500 && random(0, 100) < (FACE_SLEEP ? 30 : 45)) startEv(randomEv(), now);   // langsung sambung lewat kedipan
      else { curEv = EV_NONE; forceBlink(now); }
    }
    if (!yawned && st > SLEEP_MS - 3200) {
      yawned = true;
      startEv(EV_YAWN, now);
    } else if (curEv == EV_NONE && !yawned && now >= nextEvMs) {
      startEv(randomEv(), now);
    }
  }

  {                                                // v22.21: lagu baru mulai -> langsung menari (tidak menunggu jadwal acak)
    static bool prevMusic = false;
    pollMusic(connected);
    if (!cfg[9]) prevMusic = true;
    else if (musicOn && !prevMusic && mood == MOOD_HAPPY && curEv != EV_MUSIC && curEv != EV_DANCE) startEv(EV_MUSIC, now);
    prevMusic = musicOn;
    if (cfg[9] && musicOn && curEv == EV_NONE && nextEvMs > now + 1200) nextEvMs = now + 1200;   // jeda antar aksi dipersingkat saat lagu diputar
  }

  float fT = (curEv != EV_NONE) ? 1.0f : 0.0f;   // fade efek aksi masuk/keluar
  fxFade = ap(fxFade, fT, fT > fxFade ? 180.0f : 280.0f, dt);
  if (curEv == EV_NONE && fxFade < 0.03f) fxEv = EV_NONE;

  float blink = (mood == MOOD_SLEEP) ? 0.0f : blinkAmount(now);
  faceTargets(now, mood, st);

  Fc.openL = ap(Fc.openL, Ft.openL, 55, dt);       Fc.openR = ap(Fc.openR, Ft.openR, 55, dt);
  Fc.happy = ap(Fc.happy, Ft.happy, 110, dt);      Fc.lookX = ap(Fc.lookX, Ft.lookX, 70, dt);
  Fc.lookY = ap(Fc.lookY, Ft.lookY, 70, dt);       Fc.browY = ap(Fc.browY, Ft.browY, 80, dt);
  Fc.browR = ap(Fc.browR, Ft.browR, 80, dt);       Fc.browTilt = ap(Fc.browTilt, Ft.browTilt, 90, dt);
  Fc.browA = ap(Fc.browA, Ft.browA, 120, dt);      Fc.smile = ap(Fc.smile, Ft.smile, 100, dt);
  Fc.mouthOpen = ap(Fc.mouthOpen, Ft.mouthOpen, 70, dt);
  Fc.mouthW = ap(Fc.mouthW, Ft.mouthW, 90, dt);    Fc.tongue = ap(Fc.tongue, Ft.tongue, 90, dt);
  Fc.blush = ap(Fc.blush, Ft.blush, 250, dt);      Fc.love = ap(Fc.love, Ft.love, 90, dt);
  Fc.spiral = ap(Fc.spiral, Ft.spiral, 110, dt);   Fc.jump = ap(Fc.jump, Ft.jump, 55, dt);
  Fc.shake = ap(Fc.shake, Ft.shake, 35, dt);

  // v22.36: tambahkan goyangan jalan (gimmick, dari akselerometer HP lewat BLE) di atas animasi wajah biasa,
  // hanya saat diaktifkan (cfg[12]), tersambung, dan standby mode wajah (bukan foto/video).
  if (cfg[12] && connected && standbyMode == 0 && now - accVibStamp < 600) accVibLevel = ap(accVibLevel, accVibTarget, 60, dt);
  else accVibLevel = ap(accVibLevel, 0.0f, 150, dt);
  if (accVibLevel > 0.01f) {
    if (now >= vibRetargetAt) {
      vibJxT = random(-100, 101) / 100.0f; vibJyT = random(-100, 101) / 100.0f;
      vibRetargetAt = now + random(35, 85);
    }
    vibJx = ap(vibJx, vibJxT, 25, dt); vibJy = ap(vibJy, vibJyT, 25, dt);
    Fc.shake += vibJx * accVibLevel * 3.0f;
    Fc.jump  += vibJy * accVibLevel * 1.6f;
  }

  renderFace(now, mood, connected, blink);
  present();
  if (SHOW_FPS) {
    static uint32_t fc = 0, ft = 0;
    fc++;
    if (now - ft >= 2000) { Serial.printf("FPS standby: %.1f\n", fc * 1000.0f / (now - ft)); fc = 0; ft = now; }
  }
}

// Palet warna dibangun lewat fungsi agar bisa diredupkan (fade-in halaman)
void buildPalette(float k) {
#define PC(r, g, b) (lightTheme ? gfx->color565((uint8_t)((r) * k + 255 * (1 - k)), (uint8_t)((g) * k + 255 * (1 - k)), (uint8_t)((b) * k + 255 * (1 - k))) \
                                : gfx->color565((uint8_t)((r) * k), (uint8_t)((g) * k), (uint8_t)((b) * k)))
  COL_BG          = PC(0, 0, 0);
  COL_MAPBG       = PC(6, 6, 8);
  COL_ROAD        = PC(44, 44, 48);
  COL_EDGE        = PC(255, 255, 255);
  COL_HORIZON     = PC(110, 110, 118);
  COL_RING_BG     = PC(62, 62, 68);
  COL_BLD         = PC(36, 36, 40);
  COL_WIN1        = PC(92, 92, 100);
  COL_WIN2        = PC(145, 145, 155);
  COL_CHIP        = PC(58, 58, 64);
  COL_TEXT        = PC(255, 255, 255);
  COL_DIM         = PC(205, 205, 212);
  COL_GREEN       = PC(40, 240, 100);
  COL_ORANGE      = PC(255, 170, 40);
  COL_RED         = PC(255, 60, 52);
  COL_CHEEK       = PC(170, 92, 26);
  COL_GLOW_GREEN  = PC(14, 98, 46);
  COL_GLOW_ORANGE = PC(118, 70, 14);
  COL_GLOW_RED    = PC(112, 26, 22);
  COL_TIRE        = PC(14, 14, 16);
  COL_JACKET      = PC(105, 108, 118);
  COL_HELMET      = PC(255, 255, 255);
  COL_VISOR       = PC(12, 12, 14);
  COL_TAIL        = PC(255, 60, 50);
  COL_MOUTH       = PC(100, 24, 30);
  COL_TONGUE      = PC(230, 100, 120);
  COL_TEAR        = PC(80, 170, 255);
  if (lightTheme) {                                                // tema terang: latar putih keabuan, teks gelap
    COL_BG = PC(244, 244, 247);   COL_MAPBG = PC(226, 228, 232);   COL_ROAD = PC(188, 190, 197);
    COL_EDGE = PC(255, 255, 255); COL_HORIZON = PC(150, 152, 160); COL_RING_BG = PC(208, 210, 216);
    COL_BLD = PC(204, 206, 212);  COL_WIN1 = PC(168, 170, 178);    COL_WIN2 = PC(128, 130, 138);
    COL_CHIP = PC(214, 216, 222); COL_TEXT = PC(20, 22, 28);       COL_DIM = PC(92, 96, 106);
    COL_GREEN = PC(18, 150, 64);  COL_ORANGE = PC(225, 110, 0);    COL_RED = PC(205, 30, 30);
    COL_CHEEK = PC(255, 170, 150);
    COL_GLOW_GREEN = PC(140, 210, 160); COL_GLOW_ORANGE = PC(245, 190, 130); COL_GLOW_RED = PC(240, 150, 150);
    COL_TIRE = PC(40, 40, 46);    COL_JACKET = PC(90, 95, 110);    COL_HELMET = PC(35, 35, 42);
    COL_VISOR = PC(220, 225, 235); COL_TAIL = PC(235, 50, 40);
    COL_MOUTH = PC(150, 40, 50);  COL_TONGUE = PC(235, 110, 130);  COL_TEAR = PC(60, 140, 235);
  }
#undef PC
}

bool pendingFade = false;

// ---------- Buzzer ----------
uint8_t  buzzLeft = 0;
uint16_t buzzOnMs = 80, buzzOffMs = 80;
uint32_t buzzNext = 0;
bool     buzzState = false;
long     lastCallBeep = -1;

void buzz(uint8_t n, uint16_t on = 80, uint16_t off = 80) {
  if (!BUZZER_ON) return;
  buzzLeft = n; buzzOnMs = on; buzzOffMs = off; buzzState = false; buzzNext = millis();
}

void buzzStop() {
  buzzLeft = 0; buzzState = false;
  digitalWrite(PIN_BUZZ, LOW);
}

void updateBuzzer() {
  if (!BUZZER_ON) return;
  uint32_t now = millis();
  if (buzzState) {
    if (now >= buzzNext) { digitalWrite(PIN_BUZZ, LOW); buzzState = false; buzzNext = now + buzzOffMs; }
  } else if (buzzLeft && now >= buzzNext) {
    digitalWrite(PIN_BUZZ, HIGH); buzzState = true; buzzLeft--; buzzNext = now + buzzOnMs;
  }
}

// ---------- (v22.35: modul MPU6050/sudut-miring dan voltase aki dihapus seluruhnya) ----------
Preferences prefs;
static void saveCfg() { prefs.begin("nav", false); prefs.putBytes("cfg", cfg, CFG_N); prefs.end(); }

// ---------- Tampilan miring (seluruh layar berputar mengikuti sudut motor) ----------
float    uiAngle = 0;                 // sudut putar tampilan (derajat, searah jarum jam positif), dihaluskan
float    presentedAngle = 999;
bool     fbDirty = true;

#if TILT_UI
uint16_t stripBuf[240 * 8];

static inline uint16_t dimPx(uint16_t c) {                          // kecerahan perangkat lunak (RGB565 diskalakan dimK/256)
  uint32_t r = ((uint32_t)(c >> 11) * dimK) >> 8, g = ((uint32_t)((c >> 5) & 63) * dimK) >> 8, b = ((uint32_t)(c & 31) * dimK) >> 8;
  return (uint16_t)((r << 11) | (g << 5) | b);
}

void present() {
  if (!canvasOK) return;
  presentedAngle = uiAngle;
  fbDirty = false;
  if (fabsf(uiAngle) < 0.4f && dimK < 256) {                        // tegak + diredupkan: salin per 8 baris lewat penyangga
    for (int y0 = 0; y0 < 240; y0 += 8) {
      for (int r = 0; r < 8; r++) { const uint16_t *src = fb->row(y0 + r); uint16_t *o = stripBuf + r * 240; for (int x = 0; x < 240; x++) o[x] = dimPx(src[x]); }
      panel->draw16bitRGBBitmap(0, y0, stripBuf, 240, 8);
    }
    return;
  }
  if (fabsf(uiAngle) < 0.4f) {                                      // tegak: salin langsung
    for (int i = 0; i < SplitCanvas::SN; i++)
      panel->draw16bitRGBBitmap(0, i * SplitCanvas::SH, fb->s[i], 240, SplitCanvas::SH);
    return;
  }
  float a = uiAngle * 0.0174533f;
  float cf = cosf(a) * 65536.0f, sf = sinf(a) * 65536.0f;
  int32_t cI = (int32_t)cf, sI = (int32_t)sf;
  for (int y0 = 0; y0 < 240; y0 += 8) {                              // putar per 8 baris lalu kirim ke layar
    for (int r = 0; r < 8; r++) {
      float dy = (y0 + r) - 119.5f;
      int32_t sx = (int32_t)(7864320.0f + (-119.5f * cf + dy * sf));  // 7864320 = 120 * 65536
      int32_t sy = (int32_t)(7864320.0f + (119.5f * sf + dy * cf));
      uint16_t *o = stripBuf + r * 240;
      for (int xd = 0; xd < 240; xd++) {
        uint32_t xi = (uint32_t)(sx >> 16), yi = (uint32_t)(sy >> 16);
        o[xd] = (xi < 240 && yi < 240) ? fb->row(yi)[xi] : 0;
        sx += cI; sy -= sI;
      }
    }
    if (dimK < 256) for (int i = 0; i < 240 * 8; i++) stripBuf[i] = dimPx(stripBuf[i]);
    panel->draw16bitRGBBitmap(0, y0, stripBuf, 240, 8);
  }
}

void syncFB() { present(); }

void tiltRefresh() {                                                // halaman statis: gambar ulang jika sudut berubah
  if (canvasOK && (fbDirty || fabsf(uiAngle - presentedAngle) > 0.35f)) present();
}

void tiltTick() {}                                                  // v22.35: sudut miring (MPU6050) dihapus; uiAngle tetap 0 (tegak)
#else
void present() { if (canvasOK) cv->flush(); }
void syncFB() {}
void tiltRefresh() {}
void tiltTick() {}
#endif

// ---------- Game (cukup 1 sensor sentuh): 3 game, masing-masing satu halaman ----------
// 6 MOTOR RUN (lompati kerucut) | 7 TERBANG (lewati pipa) | 8 PAS! (hentikan penanda di zona hijau)
// Sentuh = aksi permainan. Di layar judul / game over / jeda: sentuh = mulai, TAHAN 1 detik = keluar ke halaman berikutnya.
#define GAME_SOUND true         // buzzer: aksi, tabrak, skor
#define G_N        3
#define G_GY       160          // tanah MOTOR RUN
#define G_PX       78           // posisi x motor / burung
#define G_MAXOB    4
#define G_CEIL     72           // TERBANG: langit-langit
#define G_FLOOR    172          // TERBANG: lantai
#define G_GAP      52           // TERBANG: lebar celah pipa
#define G_TX       56           // PAS!: x awal lintasan
#define G_TRACK    128          // PAS!: panjang lintasan

enum GameState { GS_READY, GS_PLAY, GS_OVER, GS_PAUSE };
struct Obst { float x; uint8_t w, h; bool on, pass; };

// gH/gV/gSpeed/gDist/gNextSpawn dipakai bergantian menurut game:
//  MOTOR RUN: gH tinggi lompat, gV kecepatan naik, gDist jarak tempuh, gNextSpawn jarak ke kerucut berikut
//  TERBANG  : gH y burung, gV kecepatan (positif = turun), gNextSpawn jarak ke pipa berikut
//  PAS!     : gH posisi penanda, gV arah (+1/-1), gDist pusat zona, gNextSpawn setengah lebar zona
uint8_t  gState = GS_READY;
Obst     gOb[G_MAXOB];
float    gH = 0, gV = 0, gSpeed = 0.15f, gScore = 0, gNextSpawn = 0, gDist = 0, gPlayMs = 0;
bool     gAir = false, gNewBest = false, gBestLoaded = false;
uint16_t gBest[G_N] = {0, 0, 0};
uint32_t gLastUs = 0, gLastFrame = 0, gOverAt = 0;
int      gRingFor = -1, gLastHundred = 0, gActive = -1;

inline int gid() { return page - PAGE_GAME; }
const char *G_TITLE[G_N] = {"MOTOR RUN", "TERBANG", "PAS!"};
const char *G_TIP[G_N]   = {"SENTUH = LOMPAT", "SENTUH = KEPAK", "SENTUH DI ZONA HIJAU"};
const char *G_OVER[G_N]  = {"TABRAK!", "JATUH!", "MELESET!"};

void gameLoadBest() {
  prefs.begin("nav", true);
  for (int i = 0; i < G_N; i++) { char k[8]; snprintf(k, sizeof(k), "gbest%d", i); gBest[i] = prefs.getUShort(k, 0); }
  prefs.end();
}

void gameSaveBest() {
  char k[8]; snprintf(k, sizeof(k), "gbest%d", gid());
  prefs.begin("nav", false);
  prefs.putUShort(k, gBest[gid()]);
  prefs.end();
}

void gameNewZone() { gDist = random((long)gNextSpawn + 4, G_TRACK - (long)gNextSpawn - 3); }

void gameReset() {
  for (int i = 0; i < G_MAXOB; i++) gOb[i].on = false;
  gH = 0; gV = 0; gAir = false;
  gScore = 0; gDist = 0; gPlayMs = 0; gNewBest = false; gLastHundred = 0;
  int id = gid();
  if (id == 0)      { gSpeed = 0.15f; gNextSpawn = 130; }
  else if (id == 1) { gSpeed = 0.12f; gH = 122; gV = -0.15f; gNextSpawn = 60; }
  else              { gSpeed = 0.12f; gH = 0; gV = 1; gNextSpawn = 22; gameNewZone(); }
}

void gameCrash() {
  gState = GS_OVER;
  gOverAt = millis();
  int sc = (int)gScore, id = gid();
  gNewBest = (sc > (int)gBest[id]);
  if (gNewBest) { gBest[id] = sc; gameSaveBest(); }
  if (GAME_SOUND) buzz(3, 90, 60);
}

void gamePress() {                                                  // sentuhan mulai saat bermain
  if (gState != GS_PLAY) return;
  int id = gid();
  if (id == 0) {
    if (!gAir) { gV = 0.345f; gAir = true; if (GAME_SOUND) buzz(1, 20, 0); }
  } else if (id == 1) {
    gV = -0.30f;
    if (GAME_SOUND) buzz(1, 15, 0);
  } else {
    float d = fabsf(gH - gDist);
    if (d > gNextSpawn) { gameCrash(); return; }
    gScore += (d < 3.0f) ? 2 : 1;                                   // tepat di tengah = 2 poin
    if (GAME_SOUND) buzz(d < 3.0f ? 2 : 1, 30, 30);
    gNextSpawn = max(6.0f, gNextSpawn - 2.0f);
    gSpeed = min(0.40f, gSpeed + 0.012f);
    gameNewZone();
  }
}

void gameRelease() {                                                // MOTOR RUN: lepas cepat = lompat lebih pendek
  if (gid() == 0 && gAir && gV > 0.14f) gV = 0.14f;
}

void gameTapStart() {                                               // sentuhan singkat di layar judul / game over / jeda
  if (gState == GS_OVER && millis() - gOverAt < 600) return;        // cegah restart tak sengaja
  if (gState == GS_PAUSE) { gState = GS_PLAY; return; }
  gameReset();
  gState = GS_PLAY;
}

void gameSpawnRun() {
  for (int i = 0; i < G_MAXOB; i++) {
    if (gOb[i].on) continue;
    int r = random(100);
    gOb[i].on = true;
    gOb[i].x = CVX + CVW + 4;
    if (r < 35)            { gOb[i].w = 12; gOb[i].h = 14; }        // kerucut kecil
    else if (r < 65)       { gOb[i].w = 14; gOb[i].h = 22; }        // sedang
    else if (r < 85)       { gOb[i].w = 14; gOb[i].h = 30; }        // tinggi (perlu tahan)
    else if (gScore >= 30) { gOb[i].w = 28; gOb[i].h = 18; }        // dua kerucut berdempetan
    else                   { gOb[i].w = 12; gOb[i].h = 14; }
    return;
  }
}

void gameUpdateRun(float dt) {
  gPlayMs += dt;
  gSpeed = 0.15f + min(0.17f, gPlayMs * 0.000008f);                 // makin lama makin cepat
  float dx = gSpeed * dt;
  gDist += dx;
  gScore += dx / 12.0f;
  int hundred = (int)gScore / 100;
  if (hundred > gLastHundred) { gLastHundred = hundred; if (GAME_SOUND) buzz(2, 35, 35); }
  if (gAir) {
    gH += gV * dt;
    gV -= 0.0013f * dt;
    if (gH <= 0) { gH = 0; gV = 0; gAir = false; }
  }
  for (int i = 0; i < G_MAXOB; i++) {
    if (!gOb[i].on) continue;
    gOb[i].x -= dx;
    if (gOb[i].x + gOb[i].w < CVX - 2) gOb[i].on = false;
  }
  gNextSpawn -= dx;
  if (gNextSpawn <= 0) {
    gameSpawnRun();
    gNextSpawn = gSpeed * 600.0f + 50.0f + random(0, 120);
  }
  float px0 = G_PX - 9, px1 = G_PX + 9;                             // tabrakan: kotak motor vs kerucut
  for (int i = 0; i < G_MAXOB; i++) {
    if (!gOb[i].on) continue;
    float ox0 = gOb[i].x + 3, ox1 = gOb[i].x + gOb[i].w - 3;
    if (px1 > ox0 && px0 < ox1 && gH < gOb[i].h - 3) { gameCrash(); return; }
  }
}

void gameUpdateFlap(float dt) {
  gPlayMs += dt;
  gSpeed = 0.12f + min(0.08f, gPlayMs * 0.000004f);
  float dx = gSpeed * dt;
  gV = min(0.5f, gV + 0.0011f * dt);
  gH += gV * dt;
  gNextSpawn -= dx;
  if (gNextSpawn <= 0) {
    for (int i = 0; i < G_MAXOB; i++) {
      if (gOb[i].on) continue;
      gOb[i].on = true; gOb[i].pass = false; gOb[i].w = 22;
      gOb[i].x = CVX + CVW + 4;
      gOb[i].h = random(G_CEIL + 10, G_FLOOR - G_GAP - 6);         // y tepi atas celah
      break;
    }
    gNextSpawn = 118 + random(0, 30);
  }
  if (gH - 6 < G_CEIL || gH + 6 > G_FLOOR) { gameCrash(); return; }
  for (int i = 0; i < G_MAXOB; i++) {
    if (!gOb[i].on) continue;
    gOb[i].x -= dx;
    if (gOb[i].x + gOb[i].w < CVX - 4) { gOb[i].on = false; continue; }
    if (!gOb[i].pass && gOb[i].x + gOb[i].w < G_PX - 6) {
      gOb[i].pass = true; gScore += 1;
      if (GAME_SOUND) buzz(1, 25, 0);
    }
    if (G_PX + 5 > gOb[i].x + 1 && G_PX - 5 < gOb[i].x + gOb[i].w - 1 &&
        (gH - 5 < gOb[i].h || gH + 5 > gOb[i].h + G_GAP)) { gameCrash(); return; }
  }
}

void gameUpdateTiming(float dt) {
  gH += gV * gSpeed * dt;
  if (gH >= G_TRACK) { gH = G_TRACK; gV = -1; }
  if (gH <= 0)       { gH = 0;       gV = 1; }
}

void gHLine(int x0, int x1, int y, uint16_t c) {                    // garis datar yang dipotong ke area kanvas
  if (y < CVY || y >= CVY + CVH) return;
  if (x0 < CVX) x0 = CVX;
  if (x1 > CVX + CVW - 1) x1 = CVX + CVW - 1;
  if (x1 < x0) return;
  cv->drawFastHLine(x0 - OX, y - OY, x1 - x0 + 1, c);
}

void gRect(int x, int y, int w, int h, uint16_t c) {                // kotak yang dipotong ke area kanvas
  int x1 = x + w - 1, y1 = y + h - 1;
  if (x < CVX) x = CVX;
  if (y < CVY) y = CVY;
  if (x1 > CVX + CVW - 1) x1 = CVX + CVW - 1;
  if (y1 > CVY + CVH - 1) y1 = CVY + CVH - 1;
  if (x1 < x || y1 < y) return;
  cv->fillRect(x - OX, y - OY, x1 - x + 1, y1 - y + 1, c);
}

void gCone(float fx, int w, int h) {
  int x = (int)fx;
  for (int r = 0; r < h; r++) {
    int span = max(2, w * (r + 1) / h);
    int xs = x + (w - span) / 2;
    gHLine(xs, xs + span - 1, G_GY - h + r, ((r / 5) % 2) ? COL_TEXT : COL_ORANGE);
  }
}

void drawRunScene() {
  static const uint8_t SY[5] = {96, 108, 100, 118, 92};             // bintang kecil bergeser pelan
  for (int i = 0; i < 5; i++) {
    int sx = CVX + CVW - (int)fmodf(gDist * 0.15f + i * 47.0f, (float)CVW);
    cRect(sx, SY[i], 2, 2, COL_DIM);
  }
  gHLine(CVX, CVX + CVW - 1, G_GY, COL_TEXT);                       // tanah
  for (float x = -fmodf(gDist, 24.0f); x < CVW; x += 24.0f) {
    int x0 = CVX + (int)x;
    gHLine(x0, x0 + 9, G_GY + 7, COL_DIM);
  }
  for (int i = 0; i < G_MAXOB; i++) {
    if (!gOb[i].on) continue;
    if (gOb[i].w > 20) { gCone(gOb[i].x, 14, gOb[i].h); gCone(gOb[i].x + 14, 14, gOb[i].h); }
    else               gCone(gOb[i].x, gOb[i].w, gOb[i].h);
  }
  int wy = G_GY - (int)gH - 7;                                      // pusat roda
  int xr = G_PX - 12, xf = G_PX + 12;
  float a = gDist * 0.12f;
  cv->drawCircle(xr - OX, wy - OY, 7, COL_TEXT);
  cv->drawCircle(xf - OX, wy - OY, 7, COL_TEXT);
  cLine(xr, wy, xr + (int)(cosf(a) * 6), wy + (int)(sinf(a) * 6), COL_DIM);
  cLine(xf, wy, xf + (int)(cosf(a) * 6), wy + (int)(sinf(a) * 6), COL_DIM);
  cRRect(G_PX - 12, wy - 8, 26, 5, 2, COL_TEXT);                    // rangka
  cRRect(G_PX - 8, wy - 14, 12, 6, 2, COL_RED);                     // tangki
  cLine(G_PX + 10, wy - 6, G_PX + 12, wy - 17, COL_TEXT);           // stang
  cRRect(G_PX - 7, wy - 24, 10, 14, 3, COL_ORANGE);                 // badan pengendara
  cCircle(G_PX - 1, wy - 30, 6, COL_HELMET);                        // helm
  cRect(G_PX + 1, wy - 32, 6, 3, COL_VISOR);                        // kaca helm
}

void drawFlapScene() {
  uint16_t cap = mix565(COL_GREEN, COL_TEXT, 0.4f);
  gHLine(CVX, CVX + CVW - 1, G_FLOOR, COL_TEXT);
  if (gState == GS_PLAY) gHLine(CVX, CVX + CVW - 1, G_CEIL, COL_RING_BG);
  for (int i = 0; i < G_MAXOB; i++) {
    if (!gOb[i].on) continue;
    int x = (int)gOb[i].x, w = gOb[i].w, top = gOb[i].h, bot = top + G_GAP;
    gRect(x, G_CEIL + 1, w, top - G_CEIL - 1, COL_GREEN);
    gRect(x - 2, top - 6, w + 4, 6, cap);
    gRect(x, bot, w, G_FLOOR - bot, COL_GREEN);
    gRect(x - 2, bot, w + 4, 6, cap);
  }
  int by = (int)constrain(gH, (float)G_CEIL, (float)G_FLOOR);
  cCircle(G_PX, by, 7, COL_ORANGE);
  cRRect(G_PX - 7, by + (gV < 0 ? -2 : 2), 8, 4, 2, COL_RED);       // sayap naik/turun
  cCircle(G_PX + 3, by - 2, 3, COL_TEXT);
  cCircle(G_PX + 4, by - 2, 1, COL_VISOR);
  cTri(G_PX + 6, by, G_PX + 13, by + 2, G_PX + 6, by + 4, COL_RED); // paruh
}

void drawTimingScene() {
  cRRect(G_TX, 120, G_TRACK, 14, 4, COL_RING_BG);
  cRect(G_TX + (int)(gDist - gNextSpawn), 120, (int)(gNextSpawn * 2), 14, COL_GREEN);
  cRect(G_TX + (int)gDist - 1, 122, 2, 10, COL_BG);                 // tanda tengah zona (tepat = 2 poin)
  if (gState != GS_READY) {
    int mx = G_TX + (int)gH;
    cRect(mx - 1, 112, 3, 30, COL_TEXT);                            // penanda
    cTri(mx - 4, 106, mx + 4, 106, mx, 111, COL_ORANGE);
  }
}

void drawGame() {
  useClip(1);
  cvClear();
  int id = gid();
  if (id == 0) drawRunScene(); else if (id == 1) drawFlapScene(); else drawTimingScene();

  char buf[28];
  bool blink = (millis() / 400) & 1;
  if (gState == GS_READY) {
    cTextCenter(G_TITLE[id], 64, 2, COL_ORANGE);
    cTextCenter(G_TIP[id], 84, 1, COL_TEXT);
    cTextCenter("TAHAN = MULAI MAIN", 96, 1, COL_TEXT);
    cTextCenter("KLIK SEKALI = MENU LAIN", 106, 1, COL_DIM);
    snprintf(buf, sizeof(buf), "TERBAIK %d", (int)gBest[id]);
    cTextCenter(buf, 180, 1, COL_DIM);
  } else if (gState == GS_PLAY) {
    snprintf(buf, sizeof(buf), "%d", (int)gScore);
    if (id == 2) cTextCenter(buf, 66, 4, COL_TEXT); else cTextCenter(buf, 58, 2, COL_TEXT);
    snprintf(buf, sizeof(buf), "TERBAIK %d", (int)gBest[id]);
    cTextCenter(buf, 180, 1, COL_DIM);
  } else if (gState == GS_OVER) {
    cTextCenter(G_OVER[id], 62, 2, COL_RED);
    snprintf(buf, sizeof(buf), "SKOR %d", (int)gScore);
    cTextCenter(buf, 84, 2, COL_TEXT);
    if (gNewBest) { if (blink) cTextCenter("REKOR BARU!", 102, 1, COL_ORANGE); }
    else { snprintf(buf, sizeof(buf), "TERBAIK %d", (int)gBest[id]); cTextCenter(buf, 102, 1, COL_DIM); }
    cTextCenter("SENTUH:ULANG TAHAN:KELUAR", 180, 1, COL_DIM);
  } else {                                                          // GS_PAUSE
    cTextCenter("JEDA", 70, 3, COL_ORANGE);
    cTextCenter("SENTUH UNTUK LANJUT", 98, 1, COL_TEXT);
    cTextCenter("TAHAN 1 DTK = KELUAR", 110, 1, COL_DIM);
  }
  useClip(0);
  present();
}

void updateGamePage(bool connected) {
  (void)connected;
  uint32_t nowUs = micros();
  String gs = String("game") + page;
  if (lastSig != gs) {                                              // masuk halaman game / gambar ulang setelah notifikasi
    lastSig = gs;
    if (!gBestLoaded) { gBestLoaded = true; gameLoadBest(); }
    bool resume = (gState == GS_PLAY || gState == GS_PAUSE) && gActive == gid();
    gState = resume ? GS_PAUSE : GS_READY;
    gActive = gid();
    if (!resume) gameReset();
    gfx->fillScreen(COL_BG);
    gRingFor = -1;
    gLastUs = nowUs;
  }
  if (millis() - gLastFrame < FRAME_MS || !canvasOK) return;
  gLastFrame = millis();

  float dt = (nowUs - gLastUs) / 1000.0f;
  gLastUs = nowUs;
  if (dt > 400.0f) { if (gState == GS_PLAY) gState = GS_PAUSE; dt = 0; }   // celah panjang (mis. notifikasi): jeda
  dt = min(dt, 40.0f);
  if (gState == GS_PLAY) {
    int id = gid();
    if (id == 0) gameUpdateRun(dt); else if (id == 1) gameUpdateFlap(dt); else gameUpdateTiming(dt);
  }

  int want = (gState == GS_OVER) ? 2 : (gState == GS_PLAY ? 0 : 1);
  if (want != gRingFor) {                                           // cincin: hijau main, oranye siap/jeda, merah game over
    gRingFor = want;
    drawRing(1.0f, want == 2 ? COL_RED : (want == 0 ? COL_GREEN : COL_ORANGE));
    drawPageDots();
  }
  drawGame();
}

// ---------- Animasi notifikasi ----------
// Salin teks sebagai ASCII murni: emoji/karakter non-ASCII dibuang, baris baru jadi spasi, spasi ganda dirapatkan
void asciiClean(const String &in, char *out, size_t cap) {
  size_t n = 0;
  bool sp = true;
  for (size_t i = 0; i < in.length() && n + 1 < cap; i++) {
    uint8_t c = (uint8_t)in[i];
    if (c >= 0x80) continue;
    if (c < 32) c = ' ';
    if (c == ' ') { if (sp) continue; sp = true; } else sp = false;
    out[n++] = (char)c;
  }
  while (n && out[n - 1] == ' ') n--;
  out[n] = 0;
}

// Pecah teks jadi maks 3 baris (21 huruf per baris) pada spasi; sisa yang tidak muat diakhiri "~"
int wrapMsg(const char *src, char lines[3][24]) {
  const int W = 21;
  int len = strlen(src), pos = 0, nl = 0;
  while (pos < len && nl < 3) {
    while (src[pos] == ' ') pos++;
    int rem = len - pos;
    if (rem <= 0) break;
    int take = rem;
    if (rem > W) {
      int k = W;
      while (k >= 8 && src[pos + k] != ' ') k--;
      take = (k >= 8) ? k : W;
    }
    bool last = (nl == 2), more = (pos + take < len);
    int cp = (last && more) ? ((take < W - 1) ? take : W - 1) : take;
    memcpy(lines[nl], src + pos, cp);
    lines[nl][cp] = 0;
    if (last && more) { lines[nl][cp] = '~'; lines[nl][cp + 1] = 0; }
    pos += take;
    nl++;
  }
  return nl;
}

// ---- v22.15: notifikasi dari aplikasi MotorNav ----
// Paket kontrol: 0x20 [off u16][tot u16][data] = teks ([flags][appLen][titleLen][app][title][pesan], dipotong-potong),
// 0x21 [off u16][data] = logo 48x48 RGB565 little-endian, 0x22 telepon masuk, 0x23 telepon selesai.
static void blobToStr(const uint8_t *p, int len, char *out, size_t cap) {
  String s;
  s.reserve(len);
  for (int i = 0; i < len; i++) s += (char)p[i];
  asciiClean(s, out, cap);
}

void bleIconData(uint16_t off, const uint8_t *d, size_t n) {
  if (!iconBuf) iconBuf = (uint16_t *)malloc(ICON_BYTES);
  if (!iconBuf) return;
  if (off == 0) iconGot = 0;
  if (off != iconGot || (size_t)off + n > ICON_BYTES) return;     // harus berurutan
  memcpy((uint8_t *)iconBuf + off, d, n);
  iconGot = off + n;
}

void bleNotifText(uint16_t off, uint16_t tot, const uint8_t *d, size_t n) {
  static uint8_t buf[320];
  static uint16_t got = 0, want = 0;
  if (tot < 3 || tot > sizeof(buf)) return;
  if (off == 0) { got = 0; want = tot; }
  if (tot != want || off != got || (size_t)off + n > tot) return;
  memcpy(buf + off, d, n);
  got += n;
  if (got < want) return;
  got = 0; want = 0;
  int al = buf[1], tl = buf[2];
  if (3 + al + tl > (int)tot) return;
  blobToStr(buf + 3, al, stApp, sizeof(stApp));
  blobToStr(buf + 3 + al, tl, stTitle, sizeof(stTitle));
  blobToStr(buf + 3 + al + tl, tot - 3 - al - tl, stMsg, sizeof(stMsg));
  stIcon = (buf[0] & 1) != 0;
  stMore = (buf[0] >> 2) & 7;
  stDrive = (buf[0] & 2) != 0;                                     // HP sedang navigasi: tampil singkat
  stValid = true;
  notifPending = true;
}

void bleCallStart(const uint8_t *d, size_t n) {                    // [labelLen][nameLen][label][name][number]
  int ll = d[0], nl = d[1];
  if (n < 2 || 2 + ll + nl > (int)n) return;
  blobToStr(d + 2, ll, callLabel, sizeof(callLabel));
  blobToStr(d + 2 + ll, nl, callName, sizeof(callName));
  blobToStr(d + 2 + ll + nl, n - 2 - ll - nl, callNum, sizeof(callNum));
  callStartPending = true;
}

void bleCallEnd() { callEndPending = true; }

void bleSendEvent(uint8_t code) {
#if MAP_BLE
  if (mapChr) { uint8_t b[2] = {0xE1, code}; mapChr->notify(b, 2); }
#else
  (void)code;
#endif
}

// Pecah teks jadi baris selebar W huruf pada spasi (kata terlalu panjang dipotong). Mengembalikan jumlah baris.
// Bila teks tidak muat di maxLines, baris terakhir diakhiri "~".
int wrapText(const char *src, char lines[][16], int maxLines, int W) {
  int len = strlen(src), pos = 0, nl = 0;
  while (pos < len && nl < maxLines) {
    while (src[pos] == ' ') pos++;
    int rem = len - pos;
    if (rem <= 0) break;
    int take = rem;
    if (rem > W) {
      int k = W;
      while (k >= 5 && src[pos + k] != ' ') k--;
      take = (k >= 5) ? k : W;
    }
    memcpy(lines[nl], src + pos, take);
    int e = take;
    while (e > 0 && lines[nl][e - 1] == ' ') e--;
    lines[nl][e] = 0;
    pos += take;
    nl++;
  }
  while (pos < len && src[pos] == ' ') pos++;
  if (pos < len && nl > 0) {
    int e = strlen(lines[nl - 1]);
    if (e >= W) e = W - 1;
    lines[nl - 1][e] = '~';
    lines[nl - 1][e + 1] = 0;
  }
  return nl;
}

// Lonceng digambar di kanvas (tanpa kedip), titik pivot (120,70), bellScale untuk animasi muncul
int bsc(int n) { return max(1, (int)lroundf(n * bellScale)); }

void bellPt(float a, float x, float y, int &ox, int &oy) {
  x *= bellScale; y *= bellScale;
  ox = 120 + (int)lroundf(x * cosf(a) - y * sinf(a));
  oy = 70  + (int)lroundf(x * sinf(a) + y * cosf(a));
}

void cQuad(int x0, int y0, int x1, int y1, int x2, int y2, int x3, int y3, uint16_t c) {
  cv->fillTriangle(x0 - OX, y0 - OY, x1 - OX, y1 - OY, x2 - OX, y2 - OY, c);
  cv->fillTriangle(x0 - OX, y0 - OY, x2 - OX, y2 - OY, x3 - OX, y3 - OY, c);
}

void drawBell(float a, uint16_t col, uint16_t clap) {
  int x0, y0, x1, y1, x2, y2, x3, y3;
  bellPt(a, 0, 0, x0, y0);   cCircle(x0, y0, bsc(4), col);                  // gagang
  bellPt(a, 0, 24, x0, y0);  cCircle(x0, y0, bsc(18), col);                 // kubah
  bellPt(a, -18, 24, x0, y0); bellPt(a, 18, 24, x1, y1);
  bellPt(a, 30, 50, x2, y2);  bellPt(a, -30, 50, x3, y3);
  cQuad(x0, y0, x1, y1, x2, y2, x3, y3, col);                               // badan melebar
  bellPt(a, -33, 50, x0, y0); bellPt(a, 33, 50, x1, y1);
  bellPt(a, 33, 56, x2, y2);  bellPt(a, -33, 56, x3, y3);
  cQuad(x0, y0, x1, y1, x2, y2, x3, y3, col);                               // bibir
  bellPt(a, -a * 14.0f, 63, x0, y0);
  cCircle(x0, y0, bsc(6), clap);                                            // pemukul
}

void cChipText(const String &s, int y, uint8_t size, uint16_t color, uint16_t bg) {
  int16_t x1, y1;
  uint16_t w, h;
  cv->setTextSize(size);
  cv->getTextBounds(s, 0, 0, &x1, &y1, &w, &h);
  cRRect(120 - (int)w / 2 - 8, y - 3, w + 16, h + 6, 9, bg);
  cv->setTextColor(color);
  cv->setCursor(120 - OX - (int)w / 2 - x1, y - OY);
  cv->print(s);
}

uint8_t pickNotifStyle() {
  String l = String(notifApp);
  l.toLowerCase();
  if (l.indexOf("mail") >= 0 || l.indexOf("outlook") >= 0 || l.indexOf("yahoo") >= 0) return 2;
  if (l.indexOf("whats") >= 0 || l.indexOf("telegram") >= 0 || l.indexOf("messeng") >= 0 || l.indexOf("line") >= 0 ||
      l.indexOf("sms") >= 0 || l.indexOf("message") >= 0 || l.indexOf("pesan") >= 0 || l.indexOf("signal") >= 0 ||
      l.indexOf("discord") >= 0 || l.indexOf("chat") >= 0 || l.indexOf("instagram") >= 0) return 1;
  return (notifCount++) % 3;                                       // aplikasi lain: bergantian
}

void drawChat(uint32_t t, float in, float out) {
  float sc = bellScale;
  int w = (int)(88 * sc), h = (int)(52 * sc);
  uint16_t bc = mix565(COL_BG, COL_GREEN, out);
  cRRect(120 - w / 2, 92 - h / 2, w, h, 14, bc);
  cTri(120 - w / 2 + 10, 92 + h / 2 - 4, 120 - w / 2 + 28, 92 + h / 2 - 4, 120 - w / 2 + 6, 92 + h / 2 + 14, bc);
  if (in > 0.8f)
    for (int i = 0; i < 3; i++)                                    // titik mengetik memantul
      cCircle(104 + i * 16, 92 - (int)(fmaxf(0, sinf(t * 0.012f - i * 0.8f)) * 6.0f), 4, mix565(COL_BG, COL_TEXT, out));
}

void drawEnvelope(uint32_t t, float in, float out) {
  float sc = bellScale;
  int cx = 120, cy = 96, w = (int)(80 * sc), h = (int)(54 * sc);
  float open = constrain((t - 450.0f) / 350.0f, 0.0f, 1.0f);
  uint16_t body = mix565(COL_BG, COL_TEXT, out), dim = mix565(COL_BG, COL_DIM, out), oc = mix565(COL_BG, COL_ORANGE, out);
  int x0 = cx - w / 2, y0 = cy - h / 2;
  if (open > 0.0f) {                                               // tutup terbuka + surat naik
    cTri(x0 + 2, y0 + 2, x0 + w - 2, y0 + 2, cx, y0 - (int)(h * 0.45f * open), dim);
    int rise = (int)(open * 26.0f);
    cRRect(x0 + 8, y0 - rise + 4, w - 16, h - 8, 3, oc);
    cRect(x0 + 16, y0 - rise + 14, w - 32, 3, mix565(COL_BG, COL_BG, 1));
    cRect(x0 + 16, y0 - rise + 22, w - 44, 3, mix565(COL_BG, COL_BG, 1));
  }
  cRRect(x0, y0, w, h, 6, body);
  cLine(x0 + 2, y0 + 2, cx, cy + 4, dim);
  cLine(x0 + w - 2, y0 + 2, cx, cy + 4, dim);
  if (open <= 0.0f) cTri(x0 + 2, y0 + 2, x0 + w - 2, y0 + 2, cx, cy + 6, dim);
}

void startNotif() {
  if (stValid) {                                                   // dari aplikasi MotorNav
    strlcpy(notifApp, stApp, sizeof(notifApp));
    strlcpy(notifTitle, stTitle, sizeof(notifTitle));
    strlcpy(notifMsg, stMsg, sizeof(notifMsg));
    if (!strcmp(notifTitle, notifMsg)) notifMsg[0] = 0;
    notifHasIcon = stIcon && iconBuf && iconGot >= ICON_BYTES;
    notifDrive = stDrive;
    notifMore = stMore;
    stValid = false;
  } else { notifHasIcon = false; notifDrive = false; notifMore = 0; }
  if (notifDrive) {                                                // mode berkendara: 1 halaman saja, 4,5 dtk, bunyi pendek
    ntNL = wrapText(notifMsg, ntLines, NT_ROWS, NT_W);
    ntPages = 1;
    notifDur = 4500;
  } else {
    ntNL = wrapText(notifMsg, ntLines, NT_LINES, NT_W);
    ntPages = max(1, (ntNL + NT_ROWS - 1) / NT_ROWS);
    notifDur = constrain(1800UL + ntPages * NT_PAGE_MS, 7000UL, 26000UL);   // makin panjang pesan makin lama tampil
  }
  notifStyle = pickNotifStyle();
  if (notifDrive) buzz(1, 60, 0); else buzz(2, 70, 80);
  irisClose();
  notifActive = true;
  notifStart = millis();
  lastNotifRing = -1;
  lastNotifFrame = 0;
  gfx->fillScreen(COL_BG);
}

void endNotif() {
  bleSendEvent(2);                                                 // v22.20: kabar ke HP -> kirim notifikasi berikutnya dari antrean
  setDirty = true;
  notifActive = false;
  pendingFade = true;
  lastSig = "";              // paksa gambar ulang halaman
  lastFaceKey = -1;
  gfx->fillScreen(COL_BG);
  if (standby) { drawRing(1.0f, faceConnected ? COL_GREEN : COL_RED); resetStandbyFx(); }
}

static void cTextCenterAt(const char *s, int cx, int y, uint8_t size, uint16_t col) {   // teks rata tengah di x tertentu
  int16_t x1, y1;
  uint16_t w, h;
  cv->setTextSize(size);
  cv->getTextBounds(s, 0, 0, &x1, &y1, &w, &h);
  cv->setTextColor(col);
  cv->setCursor(cx - OX - (int)w / 2 - x1, y - OY);
  cv->print(s);
}

// Logo aplikasi 48x48 (piksel 0x0000 = transparan), memudar bersama layar
static void drawNotifIcon(int x0, int y0, float fade) {
  for (int j = 0; j < ICON_W; j++)
    for (int i = 0; i < ICON_W; i++) {
      uint16_t c = iconBuf[j * ICON_W + i];
      if (!c) continue;
      if (fade < 1.0f) c = mix565(COL_BG, c, fade);
      cv->drawPixel(x0 + i - OX, y0 + j - OY, c);
    }
}

void updateNotif() {
  uint32_t now = millis();
  uint32_t t = now - notifStart;
  if (t >= notifDur) { endNotif(); return; }
  if (now - lastNotifFrame < FRAME_MS) return;
  lastNotifFrame = now;

  long rk = t / 100;                                                 // cincin oranye berputar (~10x/detik)
  if (rk != lastNotifRing) {
    lastNotifRing = rk;
    drawRing((t % 1000) / 1000.0f, COL_ORANGE);
  }
  if (!canvasOK) return;

  float in = constrain(t / 380.0f, 0.0f, 1.0f);
  float out = constrain((notifDur - t) / 350.0f, 0.0f, 1.0f);
  float x = in - 1.0f;
  float sc = 1.0f + 2.70158f * x * x * x + 1.70158f * x * x;         // muncul membal (easeOutBack)
  float tf = min(constrain((t - 120.0f) / 280.0f, 0.0f, 1.0f), out);  // teks fade masuk/keluar
  int pg = min(ntPages - 1, (int)(t / NT_PAGE_MS));                  // halaman pesan yang tampil
  uint32_t pt = t - (uint32_t)pg * NT_PAGE_MS;
  float pf = 1.0f;                                                   // fade antar halaman
  if (pg > 0) pf = constrain(pt / 220.0f, 0.0f, 1.0f);
  if (pg < ntPages - 1) pf = min(pf, constrain((NT_PAGE_MS - pt) / 220.0f, 0.0f, 1.0f));

  useClip(2);
  cvClearAll();
  for (int k = 0; k < 3; k++) {                                      // gelombang melebar dari logo
    float ph = fmodf(t / 1500.0f + k / 3.0f, 1.0f);
    cv->drawCircle(120 - OX, 42 - OY, 27 + (int)(ph * 24.0f), mix565(COL_BG, COL_ORANGE, (1.0f - ph) * 0.5f * out * in));
  }
  cCircle(120, 42, (int)(28 * sc), mix565(COL_BG, COL_CHIP, out));  // alas bulat logo
  if (notifHasIcon) {
    if (in > 0.5f) drawNotifIcon(120 - ICON_W / 2, 42 - ICON_W / 2, out);
  } else if (in > 0.5f) {                                            // tanpa logo: huruf pertama nama aplikasi
    char lt[2] = {(char)toupper(notifApp[0] ? notifApp[0] : '!'), 0};
    cTextCenter(lt, 30, 3, mix565(COL_BG, COL_ORANGE, out));
  }

  if (notifMore > 0 && in > 0.5f) {                                  // lencana "+N": masih ada notifikasi menunggu
    char bdg[4] = {'+', (char)('0' + notifMore), 0, 0};
    cCircle(148, 24, 10, mix565(COL_BG, COL_ORANGE, tf));
    cTextCenterAt(bdg, 148, 20, 1, mix565(COL_BG, COL_BG, 1.0f));
  }
  String app = clip(String(notifApp[0] ? notifApp : "Notifikasi"), 24);       // nama aplikasi
  cChipText(app, 73, 1, mix565(COL_BG, COL_ORANGE, tf), mix565(COL_BG, COL_CHIP, tf));
  uint16_t ct = mix565(COL_BG, COL_TEXT, tf);
  if (strlen(notifTitle) > 16) cTextCenter(clip(String(notifTitle), 32).c_str(), 94, 1, ct);   // judul panjang: huruf kecil
  else if (notifTitle[0]) cTextCenter(notifTitle, 90, 2, ct);                                   // pengirim / judul (besar)
  uint16_t cb = mix565(COL_BG, COL_TEXT, min(tf, pf));
  for (int i = 0; i < NT_ROWS; i++) {                                // isi pesan: ukuran 2, 4 baris per halaman
    int li = pg * NT_ROWS + i;
    if (li >= ntNL) break;
    cTextCenter(ntLines[li], 113 + i * 20, 2, cb);
  }
  if (ntPages > 1) {                                                 // titik penanda halaman
    int x0 = 120 - (ntPages - 1) * 6;
    for (int i = 0; i < ntPages; i++)
      cCircle(x0 + i * 12, 198, (i == pg) ? 3 : 2, mix565(COL_BG, (i == pg) ? COL_ORANGE : COL_DIM, tf));
  }
  useClip(0);
  present();
}

// ---------- Panggilan masuk (dari aplikasi MotorNav) ----------
void startCall() {
  irisClose();
  notifActive = false;
  callActive = true;
  lastCallBeep = -1;
  standby = false;
  lastActivity = millis();
  callStart = millis();
  lastCallRing = -1;
  lastCallFrame = 0;
  gfx->fillScreen(COL_BG);
}

void endCall() {
  setDirty = true;
  callActive = false;
  pendingFade = true;
  buzzStop();
  lastSig = "";
  lastFaceKey = -1;
  gfx->fillScreen(COL_BG);
}

void updateCall() {
  uint32_t now = millis();
  uint32_t t = now - callStart;
  if (now - lastCallFrame < FRAME_MS) return;
  lastCallFrame = now;

  long cb = t / 1500;
  if (cb != lastCallBeep) { lastCallBeep = cb; buzz(3, 120, 100); }   // dering berulang
  long rk = t / 100;
  if (rk != lastCallRing) {
    lastCallRing = rk;
    drawRing((t % 1200) / 1200.0f, COL_GREEN);
  }
  if (!canvasOK) return;

  if (t > 60000UL) { endCall(); return; }                          // pengaman: tak ada kabar "selesai" dari HP
  float in = constrain(t / 350.0f, 0.0f, 1.0f);
  float x = in - 1.0f;
  float sc = 1.0f + 2.70158f * x * x * x + 1.70158f * x * x;
  const int CY = 64;                                               // pusat ikon telepon (naik agar nama muat besar)
  useClip(2);
  cvClearAll();
  for (int k = 0; k < 3; k++) {                                    // gelombang hijau melebar
    float ph = fmodf(t / 1500.0f + k / 3.0f, 1.0f);
    cv->drawCircle(120 - OX, CY - OY, 34 + (int)(ph * 20.0f), mix565(COL_BG, COL_GREEN, (1.0f - ph) * 0.6f));
  }
  cCircle(120, CY, (int)(32 * sc), mix565(COL_BG, COL_GREEN, 0.35f));
  float sh = ((t % 1500) < 800) ? sinf(t * 0.05f) * 14.0f : 0.0f;  // gagang bergetar lalu jeda
  float a0 = 25.0f + sh, a1 = 155.0f + sh;
  cArc(120, CY - 8, 27, 20, a0, a1, COL_TEXT);                     // gagang telepon
  cCircle(120 + (int)(cosf(a0 * 0.017453f) * 23.0f), CY - 8 + (int)(sinf(a0 * 0.017453f) * 23.0f) - 2, (int)(8 * sc), COL_TEXT);
  cCircle(120 + (int)(cosf(a1 * 0.017453f) * 23.0f), CY - 8 + (int)(sinf(a1 * 0.017453f) * 23.0f) - 2, (int)(8 * sc), COL_TEXT);

  float tf = constrain((t - 200.0f) / 300.0f, 0.0f, 1.0f);
  uint16_t cTx = mix565(COL_BG, COL_TEXT, tf), cDm = mix565(COL_BG, COL_DIM, tf);
  cTextCenter(callLabel[0] ? callLabel : "Panggilan masuk", 112, 1, mix565(COL_BG, COL_GREEN, tf));
  const char *nm = callName[0] ? callName : (callNum[0] ? callNum : "Tak dikenal");
  char cl[2][16];
  int n = wrapText(nm, cl, 2, NT_W);                               // nama besar, maks 2 baris
  for (int i = 0; i < n; i++) cTextCenter(cl[i], 128 + i * 20, 2, cTx);
  if (callNum[0] && strcmp(callNum, nm)) {                         // nomor di bawah nama
    int ny = 128 + n * 20 + 6;
    if (strlen(callNum) <= (size_t)NT_W) cTextCenter(callNum, ny, 2, cDm);
    else cTextCenter(callNum, ny + 4, 1, cDm);
  }
  cTextCenter("sentuh=tutup  tahan=tolak", 202, 1, mix565(COL_BG, COL_DIM, tf * 0.7f));
  useClip(0);
  present();
}

// ---------- Foto standby: simpan/baca di partisi flash + mode upload lewat WiFi ----------
#define PHOTO_BYTES   (16 + 240 * 240 * 2)
#define PHOTO_ERASE   (((PHOTO_BYTES + 4095) / 4096) * 4096)
static const char PHOTO_MAGIC[8] = {'M', 'N', 'P', 'H', 'O', 'T', 'O', '1'};
#if ESP_IDF_VERSION_MAJOR >= 5
typedef esp_partition_mmap_handle_t PhotoMapH;
#else
typedef spi_flash_mmap_handle_t PhotoMapH;
#endif

const esp_partition_t *photoPart() {
  const esp_partition_t *p = esp_partition_find_first(ESP_PARTITION_TYPE_DATA, ESP_PARTITION_SUBTYPE_DATA_SPIFFS, NULL);
  return (p && p->size >= PHOTO_ERASE) ? p : nullptr;
}

#if ESP_IDF_VERSION_MAJOR >= 5
  #define WP_UNMAP(h) esp_partition_munmap(h)
#else
  #define WP_UNMAP(h) spi_flash_munmap(h)
#endif
static PhotoMapH wpH;
static bool wpMapped = false;

uint32_t wpCapKB() {                                               // kapasitas slot (seluruh partisi data)
  const esp_partition_t *p = photoPart();
  return p ? p->size / 1024 : 0;
}


#if HAVE_SD
// ---------- v22.29: perpustakaan wallpaper di SD (/wp/wNNNNN.mnv) ----------
static uint16_t wpSel = 0;                                         // slot aktif (0 = bawaan)
static uint16_t wpSlots[WP_LIST_MAX];                              // daftar slot di SD (urut naik)
static uint8_t  wpCount = 0;

static void wpPath(uint16_t s, char *o, size_t n, bool tmp = false) { snprintf(o, n, "%s/w%05u.%s", SD_DIR, (unsigned)s, tmp ? "tmp" : "mnv"); }

static void wpSaveSel() {
  prefs.begin("nav", false);
  prefs.putUShort("wpsel", wpSel);
  prefs.end();
}

static void wpScan() {                                             // baca isi folder /wp
  wpCount = 0;
  if (!sdOK) return;
  File d = SD.open(SD_DIR);
  if (!d) return;
  if (!d.isDirectory()) { d.close(); return; }
  while (true) {
    File e = d.openNextFile();
    if (!e) break;
    if (!e.isDirectory()) {
      const char *nm = e.name();
      const char *b = strrchr(nm, '/'); b = b ? b + 1 : nm;
      unsigned s = 0; char ext[8] = "";
      if (sscanf(b, "w%5u.%3s", &s, ext) == 2 && strcasecmp(ext, "mnv") == 0 && s >= 1 && s <= 65535 && wpCount < WP_LIST_MAX) wpSlots[wpCount++] = (uint16_t)s;
    }
    e.close();
  }
  d.close();
  for (int i = 1; i < wpCount; i++) {                              // urutkan naik
    uint16_t v = wpSlots[i]; int j = i - 1;
    while (j >= 0 && wpSlots[j] > v) { wpSlots[j + 1] = wpSlots[j]; j--; }
    wpSlots[j + 1] = v;
  }
}

// jenis: 0 tidak sah, 1 foto, 2 video. kb = ukuran berkas, secs = durasi video.
static void btPath(uint16_t s, char *o, size_t n, bool tmp = false) { snprintf(o, n, "%s/b%05u.%s", SD_BDIR, (unsigned)s, tmp ? "tmp" : "mnv"); }
static uint8_t wpMeta(uint16_t s, uint32_t *kb, uint32_t *secs, bool boot = false) {
  char p[28]; if (boot) btPath(s, p, sizeof(p)); else wpPath(s, p, sizeof(p));
  File f = SD.open(p, FILE_READ);
  if (!f) return 0;
  uint8_t h[16], k = 0;
  if (kb) *kb = (uint32_t)(f.size() / 1024);
  if (secs) *secs = 0;
  if (f.read(h, 16) == 16) {
    if (memcmp(h, PHOTO_MAGIC, 8) == 0) k = 1;
    else if (memcmp(h, "MNVIDEO2", 8) == 0 || memcmp(h, "MNVIDEO3", 8) == 0) {
      k = 2;
      uint32_t n = h[8] | (h[9] << 8), fps = h[10] | (h[11] << 8);
      if (secs && fps) *secs = (n + fps / 2) / fps;
    }
  }
  f.close();
  return k;
}

static int wpIdx() {                                               // 0 = bawaan, 1.. = urutan di wpSlots
  if (!wpSel) return 0;
  for (int i = 0; i < wpCount; i++) if (wpSlots[i] == wpSel) return i + 1;
  return 0;
}

// Foto di SD dipakai lewat partisi flash (dipetakan langsung, tanpa RAM tambahan). Disalin hanya bila isinya beda.
static bool wpPhotoToFlash(const char *path) {
  const esp_partition_t *p = photoPart();
  if (!p) return false;
  File f = SD.open(path, FILE_READ);
  if (!f) return false;
  if (f.size() != PHOTO_BYTES) { f.close(); return false; }
  uint8_t *a = (uint8_t *)malloc(4096), *b = (uint8_t *)malloc(4096);
  if (!a || !b) { if (a) free(a); if (b) free(b); f.close(); return false; }
  bool same = true;
  for (uint32_t off = 0; off < PHOTO_BYTES && same; off += 4096) {
    size_t n = PHOTO_BYTES - off; if (n > 4096) n = 4096;
    if (f.read(a, n) != (int)n || esp_partition_read(p, off, b, n) != ESP_OK || memcmp(a, b, n) != 0) same = false;
  }
  bool ok = true;
  if (!same) {
    f.seek(0);
    uint8_t magic[8] = {0};
    for (uint32_t off = 0; off < PHOTO_BYTES && ok; off += 4096) {
      size_t n = PHOTO_BYTES - off; if (n > 4096) n = 4096;
      if (f.read(a, n) != (int)n) { ok = false; break; }
      if (off == 0) { memcpy(magic, a, 8); memset(a, 0xFF, 8); }   // tanda sah ditulis paling akhir
      if (esp_partition_erase_range(p, off, 4096) != ESP_OK) { ok = false; break; }
      if (esp_partition_write(p, off, a, n) != ESP_OK) ok = false;
    }
    if (ok) ok = esp_partition_write(p, 0, magic, 8) == ESP_OK;
  }
  free(a); free(b); f.close();
  return ok;
}

static void wpMigrate() {                                          // /wallpaper.mnv (cara lama / salinan manual) -> perpustakaan
  if (!SD.exists(SD_WALL)) return;
  char p[28]; uint32_t s = WP_MIG_BASE;
  for (; s <= 65535; s++) { wpPath((uint16_t)s, p, sizeof(p)); if (!SD.exists(p)) break; }
  if (s > 65535) return;
  if (SD.rename(SD_WALL, p)) { wpSel = (uint16_t)s; wpSaveSel(); Serial.printf("SD: /wallpaper.mnv dipindah ke slot %u\n", (unsigned)s); }
}

void wpInit() {                                                    // dipanggil di setup() setelah sdBegin()
  if (!sdOK) return;
  prefs.begin("nav", true);
  wpSel = prefs.getUShort("wpsel", 0);
  prefs.end();
  if (!SD.exists(SD_DIR)) SD.mkdir(SD_DIR);
  wpMigrate();
  if (wpSel) { char p[28]; wpPath(wpSel, p, sizeof(p)); if (!SD.exists(p)) { wpSel = 0; wpSaveSel(); } }
  wpScan();
  Serial.printf("SD: %u wallpaper di perpustakaan, aktif slot %u\n", (unsigned)wpCount, (unsigned)wpSel);
}
#endif

// Petakan isi slot dari flash. Slot kosong / rusak -> foto bawaan (standby_photo.h).
// Isi slot: "MNPHOTO1" (foto mentah RGB565) atau "MNVIDEO1/2" (header + frame JPEG).
void wallpaperLoad() {
  if (wpMapped) { WP_UNMAP(wpH); wpMapped = false; }
  sdWallClose();
  photoPtr = STANDBY_PHOTO;
  vidOK = false;
#if HAVE_SD && HAVE_ROM_JPEG
  if (sdOK && wpSel) {                                             // v22.29: wallpaper terpilih dari perpustakaan SD
    char path[28]; wpPath(wpSel, path, sizeof(path));
    uint8_t mg[8] = {0};
    File mf = SD.open(path, FILE_READ);
    if (mf) { if (mf.read(mg, 8) != 8) memset(mg, 0, 8); mf.close(); }
    if (memcmp(mg, PHOTO_MAGIC, 8) == 0) {
      if (!wpPhotoToFlash(path)) { Serial.println("Wallpaper SD: foto gagal disalin, pakai bawaan"); return; }
      // lanjut ke bagian flash di bawah: foto dipetakan dari partisi
    } else if (sdw.open(path)) {
      if (!jdPool) jdPool = (uint8_t *)malloc(JD_POOL);
      if (jdPool) {
        sdVid = true; vidN = sdw.n; vidFps = sdw.fps; vidOK = true;
        Serial.printf("Wallpaper: video SD slot %u, %u frame @ %u fps, frame maks %u B\n", (unsigned)wpSel, sdw.n, sdw.fps, (unsigned)sdw.maxLen);
        return;
      }
      Serial.println("Wallpaper SD: RAM tidak cukup, pakai bawaan");
      sdWallClose();
      return;
    } else {
      Serial.println("Wallpaper SD: berkas rusak, pakai bawaan");
      return;
    }
  } else
  if (sdOK && sdw.open(SD_WALL)) {                                 // v22.27: video di SD didahulukan
    if (!jdPool) jdPool = (uint8_t *)malloc(JD_POOL);
    if (jdPool) {
      sdVid = true; vidN = sdw.n; vidFps = sdw.fps; vidOK = true;
      Serial.printf("Wallpaper: video SD %u frame @ %u fps, frame maks %u B\n", sdw.n, sdw.fps, (unsigned)sdw.maxLen);
      return;
    }
    Serial.println("Wallpaper SD: RAM tidak cukup, pakai cadangan");
    sdWallClose();
  }
#endif
  const esp_partition_t *p = photoPart();
  if (!p) return;
  uint8_t h[16];
  if (esp_partition_read(p, 0, h, 16) != ESP_OK) return;
  const void *ptr = nullptr;
  if (memcmp(h, PHOTO_MAGIC, 8) == 0) {
    if (esp_partition_mmap(p, 0, PHOTO_BYTES, ESP_PARTITION_MMAP_DATA, &ptr, &wpH) != ESP_OK) return;
    wpMapped = true;
    photoPtr = (const uint16_t *)((const uint8_t *)ptr + 16);
    Serial.println("Wallpaper: foto");
    return;
  }
  bool v2 = memcmp(h, "MNVIDEO2", 8) == 0;
  if (!v2 && memcmp(h, "MNVIDEO1", 8) != 0) return;
  uint32_t hdr = v2 ? VID_HDR2 : VID_HDR1;
  uint16_t n = h[8] | (h[9] << 8), fps = h[10] | (h[11] << 8);
  uint32_t total = h[12] | (h[13] << 8) | (h[14] << 16) | ((uint32_t)h[15] << 24);
  if (n < 1 || n > (v2 ? VID_MAXF : 240) || fps < 1 || fps > VID_MAXFPS || total == 0 || hdr + total > p->size) return;
#if HAVE_ROM_JPEG
  if (!jdPool) jdPool = (uint8_t *)malloc(JD_POOL);               // dialokasi sekarang, selagi RAM lega
  if (!jdPool) { Serial.println("Video: RAM kerja dekoder gagal"); return; }
  if (esp_partition_mmap(p, 0, hdr + total, ESP_PARTITION_MMAP_DATA, &ptr, &wpH) != ESP_OK) { Serial.println("Video: mmap gagal"); return; }
  wpMapped = true;
  const uint32_t *tab = (const uint32_t *)((const uint8_t *)ptr + 16);   // header & tabel frame dibaca langsung dari flash
  for (int i = 0; i < n; i++) {
    uint32_t off = tab[i * 2], len = tab[i * 2 + 1];
    if (len == 0 || len > 65535 || off + len > total) { WP_UNMAP(wpH); wpMapped = false; Serial.println("Video: tabel frame rusak"); return; }
  }
  vidTab = tab; vidBase = (const uint8_t *)ptr + hdr; vidN = n; vidFps = fps; vidOK = true;
  Serial.printf("Wallpaper: video %u frame @ %u fps, %u KB\n", n, fps, (unsigned)(total / 1024));
#else
  Serial.println("Video tidak didukung: rom/tjpgd.h tidak ada di core ini");
#endif
}

void wpInfo(char *out, size_t cap) {                               // keterangan isi slot untuk menu
#if HAVE_SD
  if (sdVid) { snprintf(out, cap, "VIDEO SD %u DTK", vidFps ? (vidN + vidFps / 2) / vidFps : 0); return; }
  if (sdOK && wpSel) { strlcpy(out, "FOTO SD", cap); return; }
#endif
  const esp_partition_t *p = photoPart();
  uint8_t h[16];
  if (!p || esp_partition_read(p, 0, h, 16) != ESP_OK) { strlcpy(out, "TIDAK ADA PARTISI", cap); return; }
  if (memcmp(h, PHOTO_MAGIC, 8) == 0) strlcpy(out, "ISI: FOTO", cap);
  else if (memcmp(h, "MNVIDEO1", 8) == 0 || memcmp(h, "MNVIDEO2", 8) == 0) {
    unsigned n = h[8] | (h[9] << 8), fps = h[10] | (h[11] << 8);
    snprintf(out, cap, "ISI: VIDEO %u DTK", fps ? (n + fps / 2) / fps : 0);
  }
  else strlcpy(out, "ISI: KOSONG (FOTO BAWAAN)", cap);
}


#if HAVE_SD
// ---------- v22.29: pilih / hapus / ganti wallpaper (dipanggil dari loop(), bukan dari tugas BLE) ----------
bool wpSelect(uint16_t s) {                                        // 0 = bawaan
  if (s) { char p[28]; wpPath(s, p, sizeof(p)); if (!SD.exists(p)) return false; }
  if (wpMapped) { WP_UNMAP(wpH); wpMapped = false; }
  sdWallClose();
  photoPtr = STANDBY_PHOTO; vidOK = false;
  wpSel = s; wpSaveSel();
  if (!s) { const esp_partition_t *k = photoPart(); if (k) esp_partition_erase_range(k, 0, 4096); }   // kosongkan salinan foto: bawaan tampil
  wallpaperLoad();
  return true;
}

bool wpDelete(uint16_t s) {
  if (!s || !sdOK) return false;
  char p[28]; wpPath(s, p, sizeof(p));
  if (!SD.exists(p)) return false;
  if (s == wpSel) { sdWallClose(); photoPtr = STANDBY_PHOTO; vidOK = false; }
  bool ok = SD.remove(p);
  if (ok && s == wpSel) wpSelect(0);
  wpScan();
  return ok;
}

void wpCycle() {                                                   // menu LCD: wallpaper berikutnya (BAWAAN, lalu semua berkas)
  wpScan();
  int next = (wpIdx() + 1) % (wpCount + 1);
  wpSelect(next == 0 ? 0 : wpSlots[next - 1]);
}

void wpMenuText(char *v, size_t vn, char *info, size_t in) {      // teks item WALLPAPER di menu
  if (!sdOK) { strlcpy(v, "TANPA SD", vn); wpInfo(info, in); return; }
  snprintf(v, vn, "%d/%d", wpIdx() + 1, (int)wpCount + 1);
  if (!wpSel) { strlcpy(info, "BAWAAN", in); return; }
  uint32_t kb = 0, secs = 0;
  uint8_t k = wpMeta(wpSel, &kb, &secs);
  char sz[16];
  if (kb >= 1024) snprintf(sz, sizeof(sz), "%u MB", (unsigned)(kb / 1024)); else snprintf(sz, sizeof(sz), "%u KB", (unsigned)kb);
  if (k == 1) snprintf(info, in, "FOTO %s", sz);
  else if (k == 2) snprintf(info, in, "VIDEO %u DTK %s", (unsigned)secs, sz);
  else strlcpy(info, "BERKAS RUSAK", in);
}
#endif

// ---------- v22.25: animasi boot (video "MNVIDEO2" di partisi data kedua "bootanim") ----------
const esp_partition_t *bootPart() {
  const esp_partition_t *p = esp_partition_find_first(ESP_PARTITION_TYPE_DATA, ESP_PARTITION_SUBTYPE_ANY, "bootanim");
  return (p && p->size >= 65536) ? p : nullptr;
}

#if HAVE_SD
// ---------- v22.30: perpustakaan animasi boot di SD (/boot/bNNNNN.mnv) ----------
static uint16_t btSel = 0;                                         // slot aktif (0 = tanpa animasi)
static uint16_t btSlots[BT_LIST_MAX];                              // daftar slot di SD (urut naik)
static uint8_t  btCount = 0;

static void btSaveSel() {
  prefs.begin("nav", false);
  prefs.putUShort("btsel", btSel);
  prefs.end();
}

static void btScan() {                                             // baca isi folder /boot
  btCount = 0;
  if (!sdOK) return;
  File d = SD.open(SD_BDIR);
  if (!d) return;
  if (!d.isDirectory()) { d.close(); return; }
  while (true) {
    File e = d.openNextFile();
    if (!e) break;
    if (!e.isDirectory()) {
      const char *nm = e.name();
      const char *b = strrchr(nm, '/'); b = b ? b + 1 : nm;
      unsigned s = 0; char ext[8] = "";
      if (sscanf(b, "b%5u.%3s", &s, ext) == 2 && strcasecmp(ext, "mnv") == 0 && s >= 1 && s <= 65535 && btCount < BT_LIST_MAX) btSlots[btCount++] = (uint16_t)s;
    }
    e.close();
  }
  d.close();
  for (int i = 1; i < btCount; i++) {                              // urutkan naik
    uint16_t v = btSlots[i]; int j = i - 1;
    while (j >= 0 && btSlots[j] > v) { btSlots[j + 1] = btSlots[j]; j--; }
    btSlots[j + 1] = v;
  }
}

static int btIdx() {                                               // 0 = tanpa animasi, 1.. = urutan di btSlots
  if (!btSel) return 0;
  for (int i = 0; i < btCount; i++) if (btSlots[i] == btSel) return i + 1;
  return 0;
}

static void btMigrate() {                                          // /bootanim.mnv (cara lama / salinan manual) -> perpustakaan
  if (!SD.exists(SD_BOOT)) return;
  char p[28]; uint32_t s = BT_MIG_BASE;
  for (; s <= 65535; s++) { btPath((uint16_t)s, p, sizeof(p)); if (!SD.exists(p)) break; }
  if (s > 65535) return;
  if (SD.rename(SD_BOOT, p)) { btSel = (uint16_t)s; btSaveSel(); Serial.printf("SD: /bootanim.mnv dipindah ke slot boot %u\n", (unsigned)s); }
}

void btInit() {                                                    // dipanggil di setup() setelah sdBegin(), sebelum bootAnimPlay()
  if (!sdOK) return;
  prefs.begin("nav", true);
  btSel = prefs.getUShort("btsel", 0);
  prefs.end();
  if (!SD.exists(SD_BDIR)) SD.mkdir(SD_BDIR);
  btMigrate();
  if (btSel) { char p[28]; btPath(btSel, p, sizeof(p)); if (!SD.exists(p)) { btSel = 0; btSaveSel(); } }
  btScan();
  Serial.printf("SD: %u animasi boot di perpustakaan, aktif slot %u\n", (unsigned)btCount, (unsigned)btSel);
}

bool btSelect(uint16_t s) {                                        // 0 = tanpa animasi (dipanggil dari loop() / menu)
  if (s) { if (!sdOK) return false; char p[28]; btPath(s, p, sizeof(p)); if (!SD.exists(p)) return false; }
  btSel = s; btSaveSel();
  return true;
}

bool btDelete(uint16_t s) {
  if (!s || !sdOK) return false;
  char p[28]; btPath(s, p, sizeof(p));
  if (!SD.exists(p)) return false;
  bool ok = SD.remove(p);
  if (ok && s == btSel) btSelect(0);
  btScan();
  return ok;
}

void btCycle() {                                                   // menu LCD: animasi berikutnya (TANPA ANIMASI, lalu semua berkas)
  btScan();
  int next = (btIdx() + 1) % (btCount + 1);
  btSelect(next == 0 ? 0 : btSlots[next - 1]);
}

void btMenuText(char *v, size_t vn, char *info, size_t in) {      // teks item ANIMASI BOOT di menu
  if (!sdOK) { strlcpy(v, "TANPA SD", vn); strlcpy(info, "BUTUH MICROSD", in); return; }
  snprintf(v, vn, "%d/%d", btIdx() + 1, (int)btCount + 1);
  if (!btSel) { strlcpy(info, "TANPA ANIMASI", in); return; }
  uint32_t kb = 0, secs = 0;
  uint8_t k = wpMeta(btSel, &kb, &secs, true);
  char sz[16];
  if (kb >= 1024) snprintf(sz, sizeof(sz), "%u MB", (unsigned)(kb / 1024)); else snprintf(sz, sizeof(sz), "%u KB", (unsigned)kb);
  if (k == 2) snprintf(info, in, "VIDEO %u DTK %s", (unsigned)secs, sz);
  else strlcpy(info, "BERKAS RUSAK", in);
}
#endif

#if TILT_UI && HAVE_ROM_JPEG
void bootAnimPlay() {                                              // dipanggil sekali di setup(); slot kosong/rusak = langsung kembali
  if (!canvasOK || !fb) return;
  if (!jdPool) jdPool = (uint8_t *)malloc(JD_POOL);                // v22.27: dulu animasi boot tak jalan bila wallpaper bukan video
  if (!jdPool) return;
  uint16_t n = 0, fps = 0;
  const uint32_t *tab = nullptr;
  const uint8_t *base = nullptr;                                   // sumber flash (dipetakan)
  PhotoMapH mh;
  bool mapped = false;
#if HAVE_SD
  SdVid *sv = nullptr;                                             // sumber SD (didahulukan)
  if (sdOK) {                                                      // v22.30: dengan SD, animasi = slot terpilih (0 = tanpa animasi); partisi flash tak dipakai
    if (!btSel) return;
    char bp[28]; btPath(btSel, bp, sizeof(bp));
    sv = new SdVid();
    if (sv && sv->open(bp)) { n = sv->n; fps = sv->fps; }
    else { delete sv; sv = nullptr; Serial.println("Boot: berkas animasi di SD rusak"); return; }
  }
  if (!sv)
#endif
  {
    const esp_partition_t *p = bootPart();
    if (!p) return;
    uint8_t h[16];
    if (esp_partition_read(p, 0, h, 16) != ESP_OK || memcmp(h, "MNVIDEO2", 8) != 0) return;
    n = h[8] | (h[9] << 8); fps = h[10] | (h[11] << 8);
    uint32_t total = h[12] | (h[13] << 8) | (h[14] << 16) | ((uint32_t)h[15] << 24);
    if (n < 1 || n > VID_MAXF || fps < 1 || fps > VID_MAXFPS || total == 0 || VID_HDR2 + total > p->size) return;
    const void *ptr = nullptr;
    if (esp_partition_mmap(p, 0, VID_HDR2 + total, ESP_PARTITION_MMAP_DATA, &ptr, &mh) != ESP_OK) { Serial.println("Boot: mmap gagal"); return; }
    mapped = true;
    tab = (const uint32_t *)((const uint8_t *)ptr + 16);
    base = (const uint8_t *)ptr + VID_HDR2;
    for (int i = 0; i < n; i++) {
      uint32_t off = tab[i * 2], len = tab[i * 2 + 1];
      if (len == 0 || len > 65535 || off + len > total) { WP_UNMAP(mh); Serial.println("Boot: tabel frame rusak"); return; }
    }
  }
  uiAngle = 0;                                                     // animasi selalu tegak
  const uint32_t durMs = (uint32_t)n * 1000UL / fps;
  const uint32_t t0 = millis();
  int last = -1;
  while (true) {
    uint32_t el = millis() - t0;
    if (el >= durMs) break;
    int i = (int)((uint64_t)el * fps / 1000);                      // pacu waktu: bila decode lambat, frame dilewati (durasi tetap)
    if (i >= n) i = n - 1;
    if (i != last) {
      last = i;
#if HAVE_SD
      if (sv) {
        const uint8_t *fp; uint32_t len;
        if (sv->frame(i, fp, len) && mapDecode(fp, len)) present();
      } else
#endif
      if (mapDecode(base + tab[i * 2], tab[i * 2 + 1])) present();
    } else delay(1);
    if (el > 400 && digitalRead(PIN_TOUCH)) break;                 // sentuh = lewati
  }
  if (mapped) WP_UNMAP(mh);
#if HAVE_SD
  if (sv) { sv->close(); delete sv; }
#endif
  Serial.printf("Boot: animasi %u frame @ %u fps selesai\n", n, fps);
}
#else
void bootAnimPlay() {}
#endif

// ---------- Utama ----------
bool bootPressed() {                                               // tombol BOOT (ESP32 biasa) = sentuhan tambahan
#if PIN_BOOT >= 0
  return digitalRead(PIN_BOOT) == LOW;
#else
  return false;
#endif
}

// ---------- Menu pengaturan (tahan sensor 4 dtk di standby) ----------
void wifiUploadMode(uint8_t hint = 0);                             // v22.37: hint 0 umum, 1 wallpaper, 2 animasi boot. v22.28: mode upload WiFi (tidak kembali; ESP32 restart)
#define SET_N 7                                                    // v22.37: UPLOAD WIFI ada di semua board (C3 juga)
const char *SET_NAME[SET_N] = {"WALLPAPER", "ANIMASI BOOT", "TAMPILAN NAV", "TEMA", "STANDBY", "UPLOAD WIFI", "KELUAR"};

void saveU8(const char *k, uint8_t v) {
  prefs.begin("nav", false);
  prefs.putUChar(k, v);
  prefs.end();
}

void drawSettings() {
  gfx->fillScreen(COL_BG);
  drawRing(1.0f, COL_ORANGE);
  drawCentered("PENGATURAN", 40, 1, COL_DIM);
  drawCentered(SET_NAME[setIdx], 76, 2, COL_ORANGE);
  char v[24] = "", info[32] = "";
  switch (setIdx) {
    case 0:
#if HAVE_SD
      wpMenuText(v, sizeof(v), info, sizeof(info));
#else
      strlcpy(v, "TAHAN", sizeof(v)); strlcpy(info, "UPLOAD FOTO/VIDEO LEWAT WIFI", sizeof(info));   // v22.37: tanpa SD -> langsung mode WiFi
#endif
      break;
    case 1:                                                        // v22.30: animasi boot (slot di SD)
#if HAVE_SD
      btMenuText(v, sizeof(v), info, sizeof(info));
#else
      strlcpy(v, "TAHAN", sizeof(v)); strlcpy(info, "UPLOAD ANIMASI LEWAT WIFI", sizeof(info));      // v22.37
#endif
      break;
    case 2:
      strlcpy(v, navStyle == 1 ? "3D" : (navStyle == 2 ? "LIVE" : "PETA"), sizeof(v));
      strlcpy(info, navStyle == 2 ? "PETA ASLI DARI HP (BLE)" : "TAHAN: UBAH TAMPILAN", sizeof(info));
      break;
    case 3: strlcpy(v, lightTheme ? "TERANG" : "GELAP", sizeof(v)); strlcpy(info, "TAHAN: UBAH TEMA", sizeof(info)); break;
    case 4: strlcpy(v, standbyMode ? "FOTO/VIDEO" : "WAJAH", sizeof(v)); strlcpy(info, "TAHAN: UBAH MODE", sizeof(info)); break;
    case 5: strlcpy(v, "TAHAN", sizeof(v)); strlcpy(info, "MULAI UPLOAD LEWAT WIFI", sizeof(info)); break;
    default: strlcpy(v, "TAHAN", sizeof(v)); strlcpy(info, "KEMBALI KE STANDBY", sizeof(info)); break;
  }
  drawCentered(v, 106, 3, COL_TEXT);
  drawCentered(info, 148, 1, COL_DIM);
  for (int i = 0; i < SET_N; i++) {
    int x = 120 + (int)((i - (SET_N - 1) / 2.0f) * 14);
    if (i == setIdx) gfx->fillCircle(x, 170, 3, COL_ORANGE);
    else             gfx->drawCircle(x, 170, 3, COL_DIM);
  }
  drawCentered("KLIK=LANJUT  TAHAN=PILIH", 190, 1, COL_DIM);
}

void settingsEnter() {
#if HAVE_SD
  wpScan();
  btScan();
#endif
  irisClose();
  settingsActive = true;
  standby = false;
  setIdx = 0;
  setDirty = true;
  setLastMs = millis();
}

void settingsLeave() {
  settingsActive = false;
  lastActivity = millis();
  enterStandby(isLinked());
}

void settingsAction() {                                            // dipanggil saat item menu ditahan
  switch (setIdx) {
    case 0:                                                        // v22.29: TAHAN = wallpaper berikutnya (BAWAAN, lalu semua berkas di SD)
#if HAVE_SD
      if (sdOK) { if (!bleWpBusy()) wpCycle(); break; }
#endif
      wifiUploadMode(1);                                           // v22.37: tanpa SD (C3) = TAHAN langsung masuk mode WiFi
      return;
    case 1:                                                        // v22.30: TAHAN = animasi boot berikutnya (TANPA ANIMASI, lalu semua berkas di SD)
#if HAVE_SD
      if (sdOK) { if (!bleWpBusy()) btCycle(); break; }
#endif
      wifiUploadMode(2);                                           // v22.37
      return;
    case 2:
      navStyle = (navStyle + 1) % (MAP_BLE ? 3 : 2);
      saveU8("navst", navStyle);
      break;
    case 3:
      lightTheme = !lightTheme;
      saveU8("light", lightTheme ? 1 : 0);
      buildPalette(1.0f);
      break;
    case 4:
      standbyMode = standbyMode ? 0 : 1;
      saveU8("sbmode", standbyMode);
      break;
    case 5:
      wifiUploadMode();                                            // blokir sampai selesai/batal, lalu restart
      return;
    default:
      settingsLeave();
      return;
  }
  setDirty = true;
}

#if MAP_BLE
// ---------- v22.14: upload wallpaper (FOTO atau VIDEO) lewat BLE ----------
// Yang dikirim = citra lengkap isi partisi, mulai offset 0 (foto: header 16 byte "MNPHOTO1" + 115200 byte RGB565;
// video: header "MNVIDEO2" + tabel + frame JPEG). Delapan byte pertama (tanda sah) ditulis paling akhir, setelah CRC cocok.
// Status (dibaca aplikasi): 0 diam, 1 bersiap, 2 siap, 3 menerima, 4 sukses, 5 gagal.
static volatile uint8_t wpUp = 0;
static volatile uint8_t wpUpTgt = 0;                               // v22.25: 0 = wallpaper, 1 = animasi boot
const esp_partition_t *photoPart();
const esp_partition_t *bootPart();
static const esp_partition_t *upPart() { return wpUpTgt ? bootPart() : photoPart(); }
static volatile bool wpUpReq = false, wpUpReload = false, wpUpClear = false;
static uint32_t wpUpSize = 0, wpUpExp = 0, wpUpFlushed = 0, wpUpCrc = 0;
#if HAVE_SD
static bool wpUpSd = false;                                        // v22.27: upload ini ditulis ke SD (video wallpaper & animasi boot)
static File wpUpFile;
static volatile bool wpUpKill = false;                             // setelah sukses ke SD: hapus tanda sah slot flash yang lama
// Penulisan SD TIDAK dikerjakan di tugas BLE (tumpukannya kecil): data diserahkan lewat penyangga kedua, loop() yang menulis.
static uint8_t *wpUpBufB = nullptr;
static volatile uint16_t wpUpPendLen = 0;
static volatile bool wpUpPend = false, wpUpFin = false, wpUpAbort = false, wpUpRmSd = false;
static uint16_t wpUpSlot = 0;                                      // v22.29: slot perpustakaan (0 = cara lama /wallpaper.mnv)
static char wpUpTmpP[28], wpUpDstP[28];
static volatile uint8_t wpCmdT = 0;                                // perintah tertunda dari BLE: 1 pilih, 2 hapus, 3 daftar
static volatile uint16_t wpCmdS = 0;
static volatile uint8_t wpCmdG = 0;                                // v22.30: sasaran perintah: 0 wallpaper, 1 animasi boot
static const char *wpUpTmp() { return wpUpSlot ? wpUpTmpP : (wpUpTgt ? SD_BOOT_TMP : SD_WALL_TMP); }
static const char *wpUpDst() { return wpUpSlot ? wpUpDstP : (wpUpTgt ? SD_BOOT : SD_WALL); }
#else
static const bool wpUpSd = false;
static const uint8_t wpCmdT = 0;
#endif
static uint16_t wpUpFill = 0;
static uint8_t wpUpMagic[8];
static volatile uint32_t wpUpLast = 0;                              // aktivitas terakhir (batas waktu 10 dtk)

static void wpUpSet(uint8_t st) {
#if HAVE_SD
  if (st == 5 && wpUpSd) { wpUpAbort = true; wpUpReload = true; }  // gagal: loop() membuang file sementara (file lama utuh) lalu memutar lagi
#endif
  wpUp = st;
  if (mapChr) mapChr->setValue(&st, 1);
}

static uint32_t wpCrc(uint32_t c, const uint8_t *p, size_t n) {    // CRC-32 standar (sama dengan java.util.zip.CRC32)
  while (n--) { c ^= *p++; for (int k = 0; k < 8; k++) c = (c >> 1) ^ (0xEDB88320u & (0u - (c & 1u))); }
  return c;
}

bool bleWpBusy() { return wpUp >= 1 && wpUp <= 3; }

void bleWpBegin(uint32_t size, uint8_t tgt, uint16_t slot) {       // dipanggil dari tugas BLE
  wpUpTgt = tgt ? 1 : 0;
#if HAVE_SD
  wpUpSlot = sdOK ? slot : 0;                                      // v22.30: animasi boot juga punya slot (/boot/bNNNNN.mnv)
  if (wpUpSlot) {
    if (tgt) { btPath(wpUpSlot, wpUpDstP, sizeof(wpUpDstP)); btPath(wpUpSlot, wpUpTmpP, sizeof(wpUpTmpP), true); }
    else     { wpPath(wpUpSlot, wpUpDstP, sizeof(wpUpDstP)); wpPath(wpUpSlot, wpUpTmpP, sizeof(wpUpTmpP), true); }
  }
  wpUpSd = sdOK && (tgt || wpUpSlot || size != PHOTO_BYTES);       // slot perpustakaan & video & animasi boot ke SD; foto tanpa slot ke flash
  if (wpUpSd) {
    if (!wpUpBufB) wpUpBufB = (uint8_t *)malloc(4096);
    if (!mapBuf[0] || !wpUpBufB || size < 64 || (size & 3) || size > SD_MAX_UP) { wpUpSd = false; wpUpSet(5); return; }
    wpUpPend = false; wpUpFin = false; wpUpAbort = false;
  } else
#endif
  {
    const esp_partition_t *p = upPart();
    if (!p || !mapBuf[0] || size < 64 || (size & 3) || size > p->size) { wpUpSet(5); return; }
  }
  wpUpSize = size; wpUpExp = 0; wpUpFlushed = 0; wpUpFill = 0; wpUpCrc = 0xFFFFFFFFu;
  mapFid = 255; mapGot = 0; mapTot = 0;
  wpUpLast = millis();
  wpUpSet(1);
  wpUpReq = true;                                                  // pelepasan peta & hapus sektor awal dikerjakan di loop()
}

void bleWpCmd(uint8_t t, uint16_t slot, uint8_t tgt) {            // v22.29: dipanggil dari tugas BLE; dikerjakan di bleWpTick(). v22.30: tgt 1 = animasi boot
#if HAVE_SD
  if (bleWpBusy() || wpCmdT) return;
  wpCmdG = tgt ? 1 : 0;
  if (t == 3) { wpCmdT = 3; return; }                              // daftar: tanpa status
  wpUpTgt = 0; wpUpSd = false; wpUpSlot = 0;
  wpCmdS = slot; wpCmdT = t;
  wpUpLast = millis();
  wpUpSet(1);
#endif
}

void bleWpClear(uint8_t tgt) {                                     // kosongkan slot (wallpaper -> foto bawaan, boot -> tanpa animasi)
  if (bleWpBusy()) return;
  wpUpTgt = tgt ? 1 : 0;
#if HAVE_SD
  wpUpSd = false; wpUpSlot = 0;
#endif
  wpUpLast = millis();
  wpUpSet(1);
  wpUpClear = true;
}

static bool wpUpFlush() {                                          // tulis penyangga (mapBuf[0]) ke flash; sektor dihapus tepat sebelum ditulis
  if (!wpUpFill) return true;
#if HAVE_SD
  if (wpUpSd) {                                                    // serahkan ke loop(): tunggu penyangga B kosong, salin, tandai
    uint32_t t0 = millis();
    while (wpUpPend && wpUp != 5 && millis() - t0 < 3000) delay(2);
    if (wpUpPend) return false;
    memcpy(wpUpBufB, mapBuf[0], wpUpFill);
    wpUpPendLen = wpUpFill;
    __sync_synchronize();
    wpUpPend = true;
    wpUpFlushed += wpUpFill; wpUpFill = 0;
    return true;
  }
#endif
  const esp_partition_t *p = upPart();
  if (wpUpFlushed == 0) { memcpy(wpUpMagic, mapBuf[0], 8); memset(mapBuf[0], 0xFF, 8); }   // sektor 0 sudah dihapus saat mulai
  else if (esp_partition_erase_range(p, wpUpFlushed, 4096) != ESP_OK) return false;
  if (esp_partition_write(p, wpUpFlushed, mapBuf[0], wpUpFill) != ESP_OK) return false;
  wpUpFlushed += wpUpFill; wpUpFill = 0;
  return true;
}

void bleWpData(uint32_t off, const uint8_t *d, size_t n) {
  if (wpUp != 2 && wpUp != 3) return;
  wpUpLast = millis();
  if (off < wpUpExp) return;                                       // paket ganda (kirim ulang): abaikan
  if (off != wpUpExp || off + n > wpUpSize) { wpUpSet(5); return; }
  wpUpCrc = wpCrc(wpUpCrc, d, n);
  size_t i = 0;
  while (i < n) {
    size_t k = n - i; if (k > 4096u - wpUpFill) k = 4096u - wpUpFill;
    memcpy(mapBuf[0] + wpUpFill, d + i, k);
    wpUpFill += k; i += k;
    if (wpUpFill == 4096 && !wpUpFlush()) { wpUpSet(5); return; }
  }
  wpUpExp += n;
  if (wpUp == 2) wpUpSet(3);
}

void bleWpEnd(uint32_t crc) {
#if HAVE_SD
  if (wpUpSd) {                                                    // SD: sisa data diserahkan, penutupan file & ganti nama dikerjakan loop()
    if (wpUp != 3 || wpUpExp != wpUpSize || (~wpUpCrc) != crc || !wpUpFlush()) { wpUpSet(5); return; }
    wpUpLast = millis();
    wpUpFin = true;
    return;
  }
#endif
  if (wpUp != 3 || wpUpExp != wpUpSize || !wpUpFlush() || (~wpUpCrc) != crc) { wpUpSet(5); return; }
  if (esp_partition_write(upPart(), 0, wpUpMagic, 8) != ESP_OK) { wpUpSet(5); return; }   // tanda sah paling akhir
#if HAVE_SD
  if (sdOK && wpUpTgt == 0) wpUpRmSd = true;                       // foto baru (flash) menggantikan video SD lama (dihapus di loop())
#endif
  wpUpReload = (wpUpTgt == 0);                                               // petakan ulang di loop()
  wpUpSet(4);
}

// ---------- v22.31: UPDATE FIRMWARE (OTA) lewat BLE ----------
// Citra baru ditulis ke slot program yang TIDAK sedang berjalan (app0 <-> app1). Slot lama tidak disentuh, jadi upload yang gagal/terputus tidak merusak
// program yang jalan. Pindah slot (esp_ota_set_boot_partition) hanya setelah panjang, CRC-32 dan esp_ota_end (keabsahan citra) semuanya cocok.
// Status memakai wpUp yang sama dengan upload wallpaper: 1 bersiap, 2 siap, 3 menerima, 4 sukses (lalu restart), 5 gagal.
#define FW_VER "v22.41"
#ifdef OTA_WITH_SEQUENTIAL_WRITES
  #define OTA_BEGIN_SZ OTA_WITH_SEQUENTIAL_WRITES                  // hapus flash sedikit-sedikit saat menulis: mulai cepat
#else
  #define OTA_BEGIN_SZ OTA_SIZE_UNKNOWN                            // core lama: seluruh slot dihapus di depan (beberapa detik)
#endif
static esp_ota_handle_t otaH = 0;
static const esp_partition_t *otaPart = nullptr;
static bool otaMode = false;                                       // ada sesi OTA berjalan (status wpUp milik OTA)
static bool otaActive = false;                                     // esp_ota_begin sudah sukses dan belum ditutup
static volatile bool otaReq = false, otaEndReq = false, otaInfoReq = false, otaAbortReq = false;
static volatile uint32_t otaRebootAt = 0;
static uint32_t otaSize = 0, otaExp = 0, otaCrc = 0;
static uint16_t otaFill = 0;
static bool otaChecked = false;

static const esp_partition_t *otaTarget() {                        // slot tujuan; nullptr bila tabel partisi tak punya slot kedua
  const esp_partition_t *run = esp_ota_get_running_partition();
  const esp_partition_t *nx = esp_ota_get_next_update_partition(nullptr);
  if (!run || !nx || nx->address == run->address) return nullptr;
  return nx;
}

static void otaAbortNow() {
  if (otaActive) { esp_ota_abort(otaH); otaActive = false; }
  otaMode = false; otaFill = 0; otaReq = false; otaEndReq = false;
}

static void otaFail() { wpUpSet(5); }                              // slot dibuang di otaTick() (loop)

void bleOtaBegin(uint32_t size) {                                  // tugas BLE: hanya memeriksa, esp_ota_begin dikerjakan di loop()
  if (bleWpBusy() && !otaMode) return;                             // upload wallpaper sedang jalan: abaikan
#if HAVE_SD
  wpUpSd = false;                                                  // sisa upload wallpaper ke SD tidak boleh ikut campur
#endif
  if (otaActive || otaReq) otaAbortNow();                          // aplikasi mengulang: buang sesi lama
  otaPart = otaTarget();
  if (!otaPart || !mapBuf[0] || size < 65536 || size > otaPart->size) { otaMode = true; otaFail(); return; }
  otaSize = size; otaExp = 0; otaCrc = 0xFFFFFFFFu; otaFill = 0; otaChecked = false;
  otaMode = true;
  wpUpLast = millis();
  wpUpSet(1);
  otaReq = true;
}

static bool otaFlush() {                                           // tulis penyangga (mapBuf[0]) ke slot
  if (!otaFill) return true;
  if (esp_ota_write(otaH, mapBuf[0], otaFill) != ESP_OK) return false;
  otaFill = 0;
  return true;
}

void bleOtaData(uint32_t off, const uint8_t *d, size_t n) {
  if (!otaMode || !otaActive || (wpUp != 2 && wpUp != 3)) return;
  wpUpLast = millis();
  if (off < otaExp) return;                                        // paket ganda: abaikan
  if (off != otaExp || off + n > otaSize) { otaFail(); return; }
  otaCrc = wpCrc(otaCrc, d, n);
  size_t i = 0;
  while (i < n) {
    size_t k = n - i; if (k > 4096u - otaFill) k = 4096u - otaFill;
    memcpy(mapBuf[0] + otaFill, d + i, k);
    otaFill += k; i += k;
    if (!otaChecked && otaExp + i >= 36) {                         // 36 byte pertama masih di penyangga (flush baru di 4096): periksa kepala citra aplikasi ESP32
      const uint8_t *b = mapBuf[0];
      uint32_t magic = b[32] | (b[33] << 8) | (b[34] << 16) | ((uint32_t)b[35] << 24);
      if (b[0] != 0xE9 || magic != 0xABCD5432u) { otaFail(); return; }   // bukan berkas program (mis. bootloader / merged.bin)
      otaChecked = true;
    }
    if (otaFill == 4096 && !otaFlush()) { otaFail(); return; }
  }
  otaExp += n;
  if (wpUp == 2) wpUpSet(3);
}

void bleOtaEnd(uint32_t crc) {                                     // tugas BLE: cek panjang + CRC; esp_ota_end (verifikasi citra, tumpukan besar) dikerjakan di loop()
  if (!otaMode || !otaActive || wpUp != 3 || otaExp != otaSize || (~otaCrc) != crc || !otaChecked || !otaFlush()) { otaFail(); return; }
  wpUpLast = millis();
  otaEndReq = true;
}

void bleOtaInfo() { otaInfoReq = true; }                           // balasan dikirim dari loop()
void bleOtaAbort() { otaAbortReq = true; }

static void otaTick() {                                            // dipanggil dari bleWpTick() (loop)
  if (otaAbortReq) { otaAbortReq = false; if (otaMode) { otaAbortNow(); wpUpSet(0); } }
  if (otaReq) {
    otaReq = false;
    if (otaPart && esp_ota_begin(otaPart, OTA_BEGIN_SZ, &otaH) == ESP_OK) {
      otaActive = true;
      wpUpLast = millis();
      wpUpSet(2);
    } else otaFail();
  }
  if (otaEndReq) {
    otaEndReq = false;
    otaActive = false;                                             // esp_ota_end menutup handle apa pun hasilnya
    if (esp_ota_end(otaH) != ESP_OK) otaFail();                    // citra tidak sah / bukan untuk chip ini
    else if (esp_ota_set_boot_partition(otaPart) != ESP_OK) otaFail();
    else {
      otaMode = false;
      wpUpSet(4);
      otaRebootAt = millis() + 2500; if (!otaRebootAt) otaRebootAt = 1;   // beri waktu aplikasi membaca status 4
    }
    wpUpLast = millis();
  }
  if (otaMode && wpUp == 5) otaAbortNow();                         // gagal / waktu habis: lepas slot, program lama tetap utuh
  if (otaInfoReq) {
    otaInfoReq = false;
    if (mapChr) {
      const esp_partition_t *t = otaTarget();
      uint32_t mx = t ? t->size : 0;
      uint8_t b[40] = {0xE4, (uint8_t)(t ? 1 : 0), (uint8_t)mx, (uint8_t)(mx >> 8), (uint8_t)(mx >> 16), (uint8_t)(mx >> 24)};
      size_t l = strlen(FW_VER); memcpy(b + 6, FW_VER, l);
      mapChr->notify(b, 6 + l);
    }
  }
  if (otaRebootAt && (int32_t)(millis() - otaRebootAt) >= 0) { otaRebootAt = 0; ESP.restart(); }
}

#if HAVE_SD
static void wpSendList(uint8_t tgt) {                              // v22.29: kirim daftar wallpaper di SD lewat notify 0xE2; v22.30: tgt 1 = animasi boot (0xE3)
  if (!mapChr) return;
  if (tgt) btScan(); else wpScan();
  const uint8_t code = tgt ? 0xE3 : 0xE2;
  const uint16_t *sl = tgt ? btSlots : wpSlots;
  const uint8_t cnt = tgt ? btCount : wpCount;
  const uint16_t sel = tgt ? btSel : wpSel;
  uint32_t mb = sdOK ? (uint32_t)(SD.cardSize() / (1024ULL * 1024ULL)) : 0;
  if (mb > 65535) mb = 65535;
  uint8_t h[8] = {code, 0, (uint8_t)(sdOK ? 1 : 0), cnt, (uint8_t)(sel & 255), (uint8_t)(sel >> 8), (uint8_t)(mb & 255), (uint8_t)(mb >> 8)};
  mapChr->notify(h, 8);
  delay(15);
  for (int i = 0; i < cnt; i++) {
    uint32_t kb = 0, secs = 0;
    uint8_t k = wpMeta(sl[i], &kb, &secs, tgt != 0);
    uint8_t e[11] = {code, 1, (uint8_t)i, (uint8_t)(sl[i] & 255), (uint8_t)(sl[i] >> 8), k,
                     (uint8_t)(kb & 255), (uint8_t)((kb >> 8) & 255), (uint8_t)((kb >> 16) & 255), (uint8_t)((kb >> 24) & 255)};
    mapChr->notify(e, 10);
    delay(15);
  }
}
#endif

void bleWpTick() {                                                 // dipanggil dari loop()
  otaTick();                                                       // v22.31
#if HAVE_SD
  if (wpCmdT) {                                                    // v22.29: perintah pilih / hapus / daftar dari aplikasi
    uint8_t t = wpCmdT; uint16_t s = wpCmdS; uint8_t g = wpCmdG; wpCmdT = 0;
    if (t == 3) wpSendList(g);
    else {
      bool ok;
      if (g) { ok = (t == 1) ? btSelect(s) : btDelete(s); btScan(); }   // v22.30: animasi boot
      else   { ok = (t == 1) ? wpSelect(s) : wpDelete(s); wpScan(); }
      wpUpSet(ok ? 4 : 5);
    }
  }
#endif
  if (bleWpBusy() && !wpUpReq && !wpUpClear && !wpCmdT && !otaReq && !otaEndReq && millis() - wpUpLast > 10000) wpUpSet(5);   // HP hilang di tengah upload: lepas kunci frame peta
  if (wpUpReq || wpUpClear) {
    bool clr = wpUpClear;
    wpUpReq = false; wpUpClear = false;
    if (wpUpTgt == 0 || wpUpSd) {
      if (wpMapped) { WP_UNMAP(wpH); wpMapped = false; }          // lepas pemetaan flash sebelum dihapus
      sdWallClose();                                              // lepas file SD yang sedang diputar (bus SD dipakai upload)
      photoPtr = STANDBY_PHOTO; vidOK = false;
    }
#if HAVE_SD
    if (wpUpSd) {                                                  // upload ke SD: buka file sementara
      SD.remove(wpUpTmp());
      wpUpFile = SD.open(wpUpTmp(), FILE_WRITE);
      if (!wpUpFile) { wpUpSet(5); return; }
      wpUpLast = millis();
      wpUpSet(2);
      return;
    }
    if (clr && sdOK) SD.remove(wpUpTgt ? SD_BOOT : SD_WALL);       // kosongkan juga file di SD
    if (clr && sdOK && wpUpTgt == 0) { wpSel = 0; wpSaveSel(); }   // v22.29: kembali ke bawaan
    if (clr && sdOK && wpUpTgt == 1) { btSel = 0; btSaveSel(); }   // v22.30: animasi boot = tanpa animasi
#endif
    const esp_partition_t *up = upPart();
    if (up && esp_partition_erase_range(up, 0, 4096) != ESP_OK) { wpUpSet(5); return; }   // tanda sah lama hilang
    if (!up && !(clr && sdOK)) { wpUpSet(5); return; }
    wpUpLast = millis();
    if (clr) { if (wpUpTgt == 0) wallpaperLoad(); wpUpSet(4); }
    else wpUpSet(2);
  }
#if HAVE_SD
  if (wpUpSd) {                                                    // layani upload SD: tulis, selesaikan, atau batalkan
    if (wpUpAbort) {
      wpUpAbort = false; wpUpPend = false; wpUpFin = false;
      if (wpUpFile) wpUpFile.close();
      SD.remove(wpUpTmp());
      if (wpUpBufB) { free(wpUpBufB); wpUpBufB = nullptr; }
    } else {
      if (wpUpPend) {
        bool ok = wpUpFile && wpUpFile.write(wpUpBufB, wpUpPendLen) == wpUpPendLen;
        wpUpPend = false;
        wpUpLast = millis();
        if (!ok) wpUpSet(5);
      }
      if (wpUpFin && !wpUpPend && wpUp == 3) {
        wpUpFin = false;
        wpUpFile.close();
        File chk = SD.open(wpUpTmp(), FILE_READ);
        bool ok = chk && chk.size() == wpUpSize;
        if (chk) chk.close();
        if (ok) { SD.remove(wpUpDst()); ok = SD.rename(wpUpTmp(), wpUpDst()); }
        if (wpUpBufB) { free(wpUpBufB); wpUpBufB = nullptr; }
        if (ok) {
          if (wpUpSlot && !wpUpTgt) { wpSel = wpUpSlot; wpSaveSel(); wpScan(); }   // v22.29: upload ke slot = langsung jadi wallpaper aktif
          else if (wpUpSlot && wpUpTgt) { btSel = wpUpSlot; btSaveSel(); btScan(); }   // v22.30: upload animasi boot ke slot = langsung jadi aktif
          wpUpKill = (wpUpSlot == 0); wpUpReload = true; wpUpSet(4);
        }
        else wpUpSet(5);
      }
    }
  }
  if (wpUpRmSd && wpUpReload) { wpUpRmSd = false; SD.remove(SD_WALL); }
  if (wpUpKill && wpUpReload) {                                    // video baru di SD: kosongkan slot flash yang sama
    wpUpKill = false;
    if (wpMapped) { WP_UNMAP(wpH); wpMapped = false; }
    const esp_partition_t *k = wpUpTgt ? bootPart() : photoPart();
    if (k) esp_partition_erase_range(k, 0, 4096);
  }
#endif
  if (wpUpReload) { wpUpReload = false; wallpaperLoad(); Serial.println("Wallpaper dari BLE tersimpan"); }
}
#endif

// ---------- v22.28: mode UPLOAD WIFI ----------
// BLE dimatikan, ESP32 membuka WiFi sendiri (SSID/sandi di bawah), aplikasi MotorNav mengirim berkas lewat HTTP: POST /up?tgt=0|1&crc=<hex>
// dengan badan = citra lengkap (sama dengan isi upload BLE). Video & animasi boot -> SD (file sementara, diganti bila CRC cocok);
// foto 115216 B -> partisi flash. Tanpa SD, video ditulis ke partisi flash bila muat. Selesai atau batal = restart.
#if MAP_BLE && TILT_UI
#define WU_SSID "MotorNav"
#define WU_PASS "motornav1234"
static volatile uint32_t wuGot = 0, wuTotal = 0;
static volatile int8_t wuState = 0;                                // 0 menunggu, 1 menerima, 2 sukses, -1 gagal
static char wuMsg[40] = "";
static uint8_t *wuBuf = nullptr;

static esp_err_t wuFail(httpd_req_t *req, const char *msg) {
  strlcpy(wuMsg, msg, sizeof(wuMsg));
  wuState = -1;
  httpd_resp_set_status(req, "400 Bad Request");
  httpd_resp_sendstr(req, msg);
  return ESP_OK;
}

static esp_err_t wuPing(httpd_req_t *req) { httpd_resp_sendstr(req, "MotorNav v22.30"); return ESP_OK; }

#if HAVE_SD
  #define WU_MAX SD_MAX_UP
#else
  #define WU_MAX (1024UL * 1024UL)                                 // tanpa SD: batas = ukuran partisi (dicek lagi di bawah)
#endif

static esp_err_t wuUp(httpd_req_t *req) {
  char q[64], v[16];
  uint8_t tgt = 0; uint32_t crc = 0; bool haveCrc = false; uint16_t slot = 0;
  if (httpd_req_get_url_query_str(req, q, sizeof(q)) == ESP_OK) {
    if (httpd_query_key_value(q, "tgt", v, sizeof(v)) == ESP_OK) tgt = atoi(v) ? 1 : 0;
    if (httpd_query_key_value(q, "crc", v, sizeof(v)) == ESP_OK) { crc = strtoul(v, nullptr, 16); haveCrc = true; }
    if (httpd_query_key_value(q, "slot", v, sizeof(v)) == ESP_OK) { long sv = atol(v); if (sv >= 1 && sv <= 65535) slot = (uint16_t)sv; }
  }
  uint32_t size = req->content_len;
  wuTotal = size; wuGot = 0;
  if (!haveCrc || size < 64 || (size & 3) || size > WU_MAX || !wuBuf) return wuFail(req, "PARAMETER SALAH");
#if HAVE_SD
  if (!sdOK) slot = 0;                                             // v22.30: animasi boot (tgt=1) juga boleh memakai slot
  bool toSd = sdOK && (tgt || slot || size != PHOTO_BYTES);
  File f;
  char tmpB[28], dstB[28];
  if (slot) {
    if (tgt) { btPath(slot, dstB, sizeof(dstB)); btPath(slot, tmpB, sizeof(tmpB), true); }
    else     { wpPath(slot, dstB, sizeof(dstB)); wpPath(slot, tmpB, sizeof(tmpB), true); }
  }
  const char *tmp = slot ? tmpB : (tgt ? SD_BOOT_TMP : SD_WALL_TMP), *dst = slot ? dstB : (tgt ? SD_BOOT : SD_WALL);
#else
  const bool toSd = false;                                         // v22.37: C3 tanpa SD -> selalu ke partisi flash
#endif
  const esp_partition_t *part = nullptr;
#if HAVE_SD
  if (toSd) {
    SD.remove(tmp);
    f = SD.open(tmp, FILE_WRITE);
    if (!f) return wuFail(req, "SD GAGAL DIBUKA");
  } else
#endif
  {
    part = tgt ? bootPart() : photoPart();
    if (!part || size > part->size) return wuFail(req, sdOK ? "TIDAK MUAT" : "SD TIDAK TERBACA");
    if (esp_partition_erase_range(part, 0, 4096) != ESP_OK) return wuFail(req, "FLASH GAGAL");
  }
  wuState = 1;
  uint32_t off = 0, c = 0xFFFFFFFFu;
  uint8_t magic[8] = {0};
  bool ok = true;
  int idle = 0;
  while (off < size && ok) {
    uint32_t want = size - off; if (want > 4096) want = 4096;
    uint32_t got = 0;
    while (got < want) {
      int r = httpd_req_recv(req, (char *)wuBuf + got, want - got);
      if (r == HTTPD_SOCK_ERR_TIMEOUT) { if (++idle > 5) { ok = false; break; } continue; }
      if (r <= 0) { ok = false; break; }
      idle = 0; got += r;
    }
    if (!ok) break;
    c = wpCrc(c, wuBuf, got);
#if HAVE_SD
    if (toSd) {
      if (f.write(wuBuf, got) != got) ok = false;
    } else
#endif
    {
      if (off == 0) { memcpy(magic, wuBuf, 8); memset(wuBuf, 0xFF, 8); }
      else if (esp_partition_erase_range(part, off, 4096) != ESP_OK) ok = false;
      if (ok && esp_partition_write(part, off, wuBuf, got) != ESP_OK) ok = false;
    }
    off += got; wuGot = off;
  }
#if HAVE_SD
  if (toSd) f.close();
#endif
  if (!ok || (~c) != crc) {
#if HAVE_SD
    if (toSd) SD.remove(tmp);
#endif
    return wuFail(req, ok ? "CRC TIDAK COCOK" : "KONEKSI PUTUS");
  }
#if HAVE_SD
  if (toSd) {
    File chk = SD.open(tmp, FILE_READ);
    bool szOk = chk && chk.size() == size;
    if (chk) chk.close();
    if (!szOk) { SD.remove(tmp); return wuFail(req, "UKURAN FILE SALAH"); }
    SD.remove(dst);
    if (!SD.rename(tmp, dst)) return wuFail(req, "GANTI NAMA GAGAL");
    if (slot) { if (tgt) { btSel = slot; btSaveSel(); } else { wpSel = slot; wpSaveSel(); } }   // v22.29/30: jadi wallpaper / animasi boot aktif (dimuat saat ESP32 restart)
    else {
      const esp_partition_t *k = tgt ? bootPart() : photoPart();   // kosongkan slot flash yang sama agar tidak muncul lagi nanti
      if (k) esp_partition_erase_range(k, 0, 4096);
    }
  } else
#endif
  {
    if (esp_partition_write(part, 0, magic, 8) != ESP_OK) return wuFail(req, "FLASH GAGAL");   // tanda sah paling akhir
#if HAVE_SD
    if (sdOK && tgt == 0) SD.remove(SD_WALL);                      // foto baru menggantikan video SD lama
#endif
  }
  wuState = 2;
  httpd_resp_sendstr(req, "OK");
  return ESP_OK;
}

static uint8_t wuHint = 0;                                         // v22.37: 1 = dari menu WALLPAPER, 2 = dari menu ANIMASI BOOT
static void wuDraw(const char *l1, const char *l2, uint16_t col) {
  gfx->fillScreen(COL_BG);
  drawRing(1.0f, COL_ORANGE);
  drawCentered(wuHint == 1 ? "UPLOAD WALLPAPER" : (wuHint == 2 ? "UPLOAD ANIM BOOT" : "UPLOAD WIFI"), 34, 2, COL_ORANGE);
  drawCentered(String("WIFI: ") + WU_SSID, 68, 1, COL_TEXT);
  drawCentered(String("SANDI: ") + WU_PASS, 82, 1, COL_TEXT);
  drawCentered("ALAMAT: 192.168.4.1", 96, 1, COL_DIM);
  drawCentered(l1, 126, 3, col);
  drawCentered(l2, 166, 1, COL_DIM);
  drawCentered("TAHAN SENSOR = BATAL", 196, 1, COL_DIM);
  syncFB();
}

void wifiUploadMode(uint8_t hint) {
  wuHint = hint;
  uiAngle = 0;
  wuDraw("MEMULAI", "MATIKAN BLE, NYALAKAN WIFI", COL_TEXT);
  if (wpMapped) { WP_UNMAP(wpH); wpMapped = false; }
  sdWallClose();
  NimBLEDevice::deinit(true);                                      // bebaskan RAM controller BLE
  artOn = false;
  if (artBuf) { free(artBuf); artBuf = nullptr; }
  if (mapBuf[0]) { free(mapBuf[0]); mapBuf[0] = nullptr; }
  if (mapBuf[1]) { free(mapBuf[1]); mapBuf[1] = nullptr; }
  if (canvasOK && fb) {                                            // v22.39: WiFi butuh ~70 KB RAM; lepas kanvas 115 KB, gambar langsung ke layar (restart sesudah selesai)
    for (int i = 0; i < SplitCanvas::SN; i++) { free(fb->s[i]); fb->s[i] = nullptr; }
    canvasOK = false;
  }
  gfx = panel;
  Serial.printf("WiFi upload: heap sebelum WiFi %u | blok terbesar %u\n", (unsigned)ESP.getFreeHeap(), (unsigned)ESP.getMaxAllocHeap());
  wuDraw("MEMULAI", "MENYALAKAN WIFI", COL_TEXT);
  wuBuf = (uint8_t *)malloc(4096);
  WiFi.mode(WIFI_AP);
  bool ap = WiFi.softAP(WU_SSID, WU_PASS, 1, 0, 1);
  WiFi.setSleep(false);
  httpd_handle_t srv = nullptr;
  httpd_config_t cfg = HTTPD_DEFAULT_CONFIG();
  cfg.stack_size = 8192;
  cfg.recv_wait_timeout = 10;
  cfg.send_wait_timeout = 10;
  cfg.max_open_sockets = 3;
  cfg.lru_purge_enable = true;
  bool hs = ap && wuBuf && httpd_start(&srv, &cfg) == ESP_OK;
  Serial.printf("WiFi upload: ap=%d buf=%d http=%d heap=%u\n", ap, wuBuf != nullptr, hs, (unsigned)ESP.getFreeHeap());
  if (!hs) { wuDraw("GAGAL", "RAM/WIFI TIDAK CUKUP", COL_RED); delay(2500); ESP.restart(); }
  httpd_uri_t uUp = {"/up", HTTP_POST, wuUp, nullptr};
  httpd_uri_t uPing = {"/ping", HTTP_GET, wuPing, nullptr};
  httpd_register_uri_handler(srv, &uUp);
  httpd_register_uri_handler(srv, &uPing);
  buzz(2, 60, 60);
  uint32_t t0 = millis(), lastDraw = 0, press = 0, failAt = 0;
  int shown = -2;
  while (true) {
    uint32_t now = millis();
    bool t = digitalRead(PIN_TOUCH) || bootPressed();
    if (t) { if (!press) press = now; else if (now - press > 1200) { wuDraw("BATAL", "RESTART...", COL_ORANGE); delay(600); ESP.restart(); } }
    else press = 0;
    if (wuState == 2) { buzz(3, 60, 60); wuDraw("SELESAI", "RESTART...", COL_GREEN); delay(1500); ESP.restart(); }
    if (wuState == -1) {
      if (!failAt) failAt = now;
      if (now - lastDraw > 400) { lastDraw = now; wuDraw("GAGAL", wuMsg, COL_RED); }
      if (now - failAt > 4000) { failAt = 0; wuState = 0; shown = -2; }
    } else if (wuState == 1) {
      uint32_t tot = wuTotal; int pct = tot ? (int)((uint64_t)wuGot * 100 / tot) : 0;
      t0 = now;
      if (pct != shown && now - lastDraw > 250) {
        shown = pct; lastDraw = now;
        char b[12]; snprintf(b, sizeof(b), "%d%%", pct);
        char c2[32]; snprintf(c2, sizeof(c2), "%u / %u KB", (unsigned)(wuGot / 1024), (unsigned)(tot / 1024));
        wuDraw(b, c2, COL_GREEN);
      }
    } else if (shown != -1) {
      shown = -1;
      wuDraw("SIAP", "MENUNGGU APLIKASI...", COL_GREEN);
    }
    if (now - t0 > 300000UL) ESP.restart();                        // 5 menit tanpa kiriman
    delay(20);
  }
}
#else
void wifiUploadMode(uint8_t) {}
#endif

void setup() {
#if !CONFIG_IDF_TARGET_ESP32C3
  esp_bt_controller_mem_release(ESP_BT_MODE_CLASSIC_BT);          // v22.38: ESP32 biasa hanya pakai BLE; membebaskan puluhan KB heap (sebelumnya BLE gagal menyala: RAM habis)
#endif
  rlyInit();                                                       // v22.33: relay motor paling awal (default OFF)
  Serial.begin(115200);
  pinMode(PIN_TOUCH, INPUT);
#if PIN_BOOT >= 0
  pinMode(PIN_BOOT, INPUT_PULLUP);
#endif
  prefs.begin("nav", true);
  lightTheme = prefs.getUChar("light", 0) != 0;
  navStyle = prefs.getUChar("navst", 0);
  if (navStyle >= (MAP_BLE ? 3 : 2)) navStyle = 0;
  prefs.end();
#if HAVE_SD
  sdBegin();                                                       // v22.27: microSD dulu, supaya wallpaperLoad() bisa memakai video di SD
  wpInit();                                                        // v22.29: perpustakaan wallpaper (/wp), pindahkan /wallpaper.mnv lama
  btInit();                                                        // v22.30: perpustakaan animasi boot (/boot), pindahkan /bootanim.mnv lama
#endif
  wallpaperLoad();
  pinMode(PIN_BUZZ, OUTPUT);
  digitalWrite(PIN_BUZZ, LOW);

  panel->begin(SPI_HZ);
#if TILT_UI
  fb = new SplitCanvas();       // kanvas penuh agar seluruh tampilan bisa dimiringkan
  canvasOK = fb->begin(CANVAS_BEGIN_ARG);
  if (canvasOK) { cv = fb; gfx = fb; }
  else Serial.println("Kanvas penuh gagal dialokasi (memori kurang): tampilan miring nonaktif. Set TILT_UI false.");
#ifdef ARDUINO_ARCH_ESP32
  Serial.printf("Kanvas %s | heap bebas %u | blok terbesar %u\n", canvasOK ? "OK" : "GAGAL", (unsigned)ESP.getFreeHeap(), (unsigned)ESP.getMaxAllocHeap());
#endif
  if (!canvasOK) {                                                 // v22.25: layar "KANVAS OK" dihapus; hanya peringatan bila gagal
    panel->fillScreen(0x0000);
    panel->setTextSize(2);
    panel->setTextColor(0xF800);
    panel->setCursor(40, 110); panel->print("KANVAS GAGAL");
    delay(1500);
  }
#else
  cv = new Arduino_Canvas(CVW, CVH, (Arduino_G *)panel, CVX, CVY);
  canvasOK = cv->begin(CANVAS_BEGIN_ARG);
  if (!canvasOK) Serial.println("Kanvas wajah gagal dialokasi");
#endif
  buildPalette(1.0f);
  gfx->fillScreen(COL_BG);
  syncFB();

  lastActivity = millis();
  prefs.begin("nav", true);
  standbyMode = prefs.getUChar("sbmode", 0) ? 1 : 0;               // mode standby terakhir
  { uint8_t t[CFG_N]; size_t n = prefs.getBytesLength("cfg"); if (n == CFG_N && prefs.getBytes("cfg", t, CFG_N) == CFG_N) { memcpy(cfg, t, CFG_N); cfgClamp(); } }
  prefs.end();
  Serial.printf("[boot] sebelum animasi boot | heap %u\n", (unsigned)ESP.getFreeHeap());   // v22.37: jejak diagnostik
  bootAnimPlay();                                                  // v22.25: animasi boot dari aplikasi (kecerahan sudah terbaca dari cfg)
  Serial.println("[boot] animasi boot selesai");
  gfx->fillScreen(COL_BG);
  syncFB();
#if MAP_BLE
  NimBLEDevice::init("Motor Nav");                                 // v22.38: BLE dulu, sebelum buffer peta (BLE butuh blok RAM besar)
  Serial.printf("[boot] BLE init OK | heap %u | blok terbesar %u\n", (unsigned)ESP.getFreeHeap(), (unsigned)ESP.getMaxAllocHeap());
  NimBLEServer *msrv = NimBLEDevice::createServer();
  msrv->setCallbacks(&mapSrvCb, false);
  NimBLEService *msvc = msrv->createService(MAP_SVC_UUID);
  NimBLECharacteristic *mch = msvc->createCharacteristic(MAP_CHR_UUID, NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR | NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
  mch->setCallbacks(&mapCharCb);
  mapChr = mch;
  { uint8_t z = 0; mch->setValue(&z, 1); }                         // status upload wallpaper: 0 = diam
  msvc->start();
  {                                                                // v22.22: iklan sendiri (UUID di paket iklan, nama di scan response)
    NimBLEAdvertising *adv = NimBLEDevice::getAdvertising();
    NimBLEAdvertisementData ad, sr;
    ad.setFlags(0x06);
    ad.addServiceUUID(NimBLEUUID(MAP_SVC_UUID));
    sr.setName("Motor Nav");
    adv->setAdvertisementData(ad);
    adv->setScanResponseData(sr);
    adv->enableScanResponse(true);
    adv->start();
  }
  mapBuf[0] = (uint8_t *)malloc(MAP_BUF_SZ);                       // v22.38: buffer peta/sampul dialokasi setelah BLE menyala
  mapBuf[1] = (uint8_t *)malloc(MAP_BUF_SZ);
  if (!jdPool) jdPool = (uint8_t *)malloc(JD_POOL);
  artBuf = (uint16_t *)malloc(ART_BYTES);
  Serial.printf("[boot] setup selesai | buffer peta %p %p %p art %p | heap %u\n", mapBuf[0], mapBuf[1], jdPool, artBuf, (unsigned)ESP.getFreeHeap());
#endif
}


// v22.18: halaman yang bisa dipilih lewat sentuhan hanya game; Navigasi muncul otomatis saat navigasi dimulai dari aplikasi
static uint8_t nextGamePage() { return page >= PAGE_GAME3 ? PAGE_GAME : page + 1; }

void handleTouch() {
  bool t = digitalRead(PIN_TOUCH) || bootPressed();
  if (callActive) {                                                // v22.19: sentuh = tutup layar, TAHAN 0,7 dtk = tolak telepon
    static bool ch = false, cd = false;
    static uint32_t cp = 0;
    if (t && !lastTouch) { ch = true; cd = false; cp = millis(); }
    if (ch && t && !cd && millis() - cp > 700) {
      cd = true;
      bleSendEvent(1);                                             // HP menolak panggilan
      buzz(2, 60, 60);
      lastTap = millis();
      endCall();
    }
    if (ch && !t) {
      ch = false;
      if (!cd && millis() - lastTap > 300) { lastTap = millis(); endCall(); }
    }
    lastTouch = t;
    return;
  }
  if (t && !lastTouch && notifActive && millis() - lastTap > 300) {
    lastTap = millis();       // sentuhan menutup animasi notifikasi
    endNotif();
    lastTouch = t;
    return;
  }
  if (settingsActive) {                                            // menu pengaturan: klik = berikutnya, tahan 0,7 dtk = pilih
    static bool stHold = false, stDone = false;
    static uint32_t stPress = 0;
    if (t && !lastTouch) { stHold = true; stDone = false; stPress = millis(); }
    if (stHold && t && !stDone && millis() - stPress > 700) { stDone = true; buzz(1, 60, 0); setLastMs = millis(); settingsAction(); }
    if (stHold && !t) {
      stHold = false;
      if (!stDone) { setIdx = (setIdx + 1) % SET_N; setDirty = true; setLastMs = millis(); }
    }
    lastTouch = t;
    return;
  }
  static bool gHold = false, gExit = false;
  static uint8_t gPressState = GS_READY;
  static uint32_t gPressMs = 0;
  if (!standby && page >= PAGE_GAME) {                             // di halaman game: sentuh = main/lompat, tahan = keluar
    if (t && !lastTouch) { lastActivity = millis(); gHold = true; gExit = false; gPressMs = millis(); gPressState = gState; if (gState == GS_PLAY) gamePress(); }
    // Layar judul: sentuh singkat = menu berikutnya, TAHAN 0,7 dtk = mulai main.
    // Game over / jeda: sentuh = ulang/lanjut, TAHAN 1 dtk = keluar ke menu berikutnya.
    if (gHold && t && !gExit && gPressState != GS_PLAY && millis() - gPressMs > (gPressState == GS_READY ? 700UL : 1000UL)) {
      gExit = true; gHold = false;
      lastTap = millis(); lastActivity = lastTap;
      buzz(1, 60, 0);
      if (gPressState == GS_READY) gameTapStart();
      else { irisClose(); page = nextGamePage(); lastSig = ""; pendingFade = true; }
    }
    if (gHold && !t) {
      gHold = false;
      if (gPressState == GS_PLAY) gameRelease();
      else if (gPressState == GS_READY) { lastTap = millis(); lastActivity = lastTap; irisClose(); page = nextGamePage(); lastSig = ""; pendingFade = true; }
      else if (!gExit) gameTapStart();
    }
    lastTouch = t;
    return;
  }
  static bool navHold = false;
  if (!standby && page == PAGE_NAV) {                              // v22.18: saat navigasi, sentuh singkat tidak pindah halaman
    if (t && !lastTouch && millis() - lastTap > 300) navHold = true;
    if (navHold && !t) { navHold = false; lastTap = millis(); lastActivity = lastTap; }
    lastTouch = t;
    return;
  }
  if (standby) {      // standby: tap = bangun | tahan 1,2 dtk lalu lepas = ganti mode | tahan 4 dtk lalu lepas = menu pengaturan
    static bool sbHold = false;
    static uint8_t sbStage = 0;
    static uint32_t sbPress = 0;
    if (t && !lastTouch && millis() - lastTap > 300) { sbHold = true; sbStage = 0; sbPress = millis(); }
    if (sbHold && t) {
      uint32_t d = millis() - sbPress;
      if (sbStage == 0 && d > 1200) { sbStage = 1; buzz(1, 60, 0); }       // bunyi 1x: lepas sekarang = ganti mode
      if (sbStage == 1 && d > 4000) { sbStage = 2; buzz(3, 60, 60); }      // bunyi 3x: lepas sekarang = menu pengaturan
    }
    if (sbHold && !t) {
      sbHold = false;
      lastTap = millis(); lastActivity = lastTap;
      if (sbStage == 2) {
        settingsEnter();                                            // tahan 4 dtk = menu pengaturan
      } else if (sbStage == 1) {
        standbyMode = standbyMode ? 0 : 1;
        prefs.begin("nav", false); prefs.putUChar("sbmode", standbyMode); prefs.end();
        Serial.printf("Mode standby: %u\n", standbyMode);
        buzz(2, 60, 60);
        enterStandby(isLinked());
      } else {
        irisClose();
        standby = false;
        page = PAGE_GAME;   // v22.18: dari standby sentuhan hanya membuka mode game
        lastSig = "";
        pendingFade = true;
      }
    }
    lastTouch = t;
    return;
  }
  if (t && !lastTouch && millis() - lastTap > 300) {
    lastTap = millis();
    lastActivity = lastTap;
    if (standby) {
      irisClose();
      standby = false;      // sentuhan pertama hanya membangunkan
      page = PAGE_GAME;   // v22.18: dari standby sentuhan hanya membuka mode game
    } else {
      irisClose();
      page = nextGamePage();
    }
    lastSig = "";
    pendingFade = true;
  }
  lastTouch = t;
}

void drawCurrentPage(const Navigation &nav, bool connected) {
  switch (page) {
    case PAGE_NAV:   drawNavPage(nav, connected);   break;
    default: break;
  }
}

// ---------- Jam (disimpan sendiri agar tetap jalan saat Bluetooth putus) ----------
bool     clockSet = false;
int      clockBaseMin = 0;          // menit dalam sehari saat disinkron
uint32_t clockBaseMs = 0;
uint32_t lastClockPoll = 0;

void updateClock(bool connected) {
  if (millis() - lastClockPoll < 1000) return;
  if (appClockMs != 0 && millis() - appClockMs < 90000UL) {        // jam dari aplikasi MotorNav
    lastClockPoll = millis();
    if (appH > 23 || appM > 59 || appS > 59) return;
    uint32_t base = (uint32_t)appH * 3600UL + appM * 60UL + appS + (millis() - appClockMs) / 1000UL;
    clockBaseMin = (int)((base / 60UL) % 1440UL);
    clockBaseMs = millis() - (base % 60UL) * 1000UL;
    clockSet = true;
    return;
  }
  (void)connected;                    // v22.22: jam hanya dari aplikasi MotorNav
  lastClockPoll = millis();
}

// menit dalam sehari, atau -1 jika jam belum pernah tersinkron
int clockMinutes(bool connected) {
  (void)connected;
  if (!clockSet) return -1;
  return (clockBaseMin + (int)((millis() - clockBaseMs) / 60000UL)) % 1440;
}

void loop() {
  updateBuzzer();
  handleTouch();
  tiltTick();                                                      // v22.35: tidak lagi membaca sudut miring (no-op, tampilan selalu tegak)
  if (cfgDirtyAt && millis() - cfgDirtyAt > 1500) { cfgDirtyAt = 0; saveCfg(); }   // v22.23: simpan pengaturan setelah berhenti diubah

#if MAP_BLE
  rlyTick();
  bleWpTick();
  {
    static uint32_t advT = 0;                                      // iklan tetap menyala selama belum ada HP tersambung
    if (millis() - advT > 2000) {
      advT = millis();
      NimBLEServer *sv = NimBLEDevice::getServer();
      if (sv && sv->getConnectedCount() < 1 && !NimBLEDevice::getAdvertising()->isAdvertising()) NimBLEDevice::startAdvertising();
    }
  }
#endif
  bool connected = isLinked();
  updateClock(connected);
  pollMusic(connected);                                            // detik lagu terus dihitung di semua halaman
  static Navigation nav;                                           // disalin tiap 40 ms saja (hemat heap, FPS naik)
  static uint32_t lastNavPoll = 0;
  static bool navMapReady = false;                                 // v22.41: true setelah frame peta pertama diterima utk sesi nav ini
  if (millis() - lastNavPoll >= 40) {
    lastNavPoll = millis();
    bool wantNav = appNavOn();
#if MAP_BLE
    if (wantNav) { if (!navMapReady && mapStamp != 0) navMapReady = true; }
    else navMapReady = false;
    nav.active = wantNav && navMapReady;                           // tunggu peta siap dulu -> layar nav baru muncul otomatis saat peta ada
#else
    nav.active = wantNav;
#endif
  }

  if (callEndPending) { callEndPending = false; if (callActive) endCall(); }
  if (callStartPending) { callStartPending = false; if (!callActive) startCall(); }   // jika sudah berdering: hanya nama diperbarui
  if (callActive) {
    notifPending = false;                 // panggilan lebih penting dari notifikasi
    updateCall();
    delay(1);
    return;
  }
  if (notifPending) {
    notifPending = false;
    startNotif();
  }
  if (notifActive) {
    updateNotif();
    delay(1);
    return;
  }

  if (nav.active) {
    lastActivity = millis();
    if (standby) { irisClose(); standby = false; lastSig = ""; pendingFade = true; }
  }
  if (nav.active && settingsActive) { settingsActive = false; lastSig = ""; pendingFade = true; }
  if (nav.active && !lastActive) {
    page = PAGE_NAV;
    lastSig = "";
    pendingFade = true;
  }
  if (!nav.active && lastActive && !standby && !settingsActive) {   // v22.18: navigasi selesai -> langsung ke wajah/wallpaper
    lastActive = false;
    enterStandby(connected);
    return;
  }
  if (nav.active && page != PAGE_NAV && !settingsActive) { page = PAGE_NAV; lastSig = ""; pendingFade = true; }   // saat navigasi tidak ada game
  lastActive = nav.active;

  if (settingsActive) {                                            // menu pengaturan menggantikan semua halaman
    if (millis() - setLastMs > 60000UL) { settingsLeave(); return; }
    if (setDirty) { setDirty = false; drawSettings(); fbDirty = true; }
    tiltRefresh();
    delay(10);
    return;
  }

  // v20: Bluetooth putus di halaman Navigasi langsung masuk standby (game tetap bisa dimainkan lewat sentuhan)
  if (!standby && !nav.active && (millis() - lastActivity > (uint32_t)cfg[8] * 1000UL || (!connected && page == PAGE_NAV))) {
    enterStandby(connected);
  }
  if (standby) {
    if (standbyMode == 1 && canvasOK) updatePhotoStandby(connected);
    else updateFace(connected);
    delay(1);
    return;
  }

  if (page >= PAGE_GAME) {                                          // halaman game: standby jika tidak bermain
    if (gState == GS_PLAY) lastActivity = millis();
    updateGamePage(connected);
    delay(1);
    return;
  }

  String sig = String(page);
  sig += "|"; sig += (int)connected;
  sig += "|"; sig += (int)nav.active;
  sig += "|"; sig += nav.directions;
  sig += "|"; sig += nav.title;
  sig += "|"; sig += nav.distance;
  sig += "|"; sig += nav.speed;
  sig += "|"; sig += nav.eta;
  sig += "|"; sig += nav.duration;
  sig += "|"; sig += nav.iconCRC;
#if MAP_BLE
  if (navStyle == 2 || appNavOn()) { sig += "|m"; sig += (unsigned)mapStamp; sig += mapFresh() ? "1" : "0"; }
  sig += appNavOn() ? "|A" : "|a";
#endif

  if (sig != lastSig) {
    lastSig = sig;

    if (nav.active) {
      Serial.printf("NAV dir='%s' title='%s' dist='%s' eta='%s' dur='%s' speed='%s' arah=%d\n",
                    nav.directions.c_str(), nav.title.c_str(), nav.distance.c_str(),
                    nav.eta.c_str(), nav.duration.c_str(), nav.speed.c_str(), turnDirection(nav));
    }

    if (pendingFade) {                                              // halaman timbul perlahan (palet diredupkan)
      pendingFade = false;
      static const float FS[4] = {0.16f, 0.38f, 0.64f, 0.86f};
      for (int k = 0; k < 4; k++) { buildPalette(FS[k]); drawCurrentPage(nav, connected); syncFB(); }
      buildPalette(1.0f);
    }
    drawCurrentPage(nav, connected);
    fbDirty = true;
  }
  tiltRefresh();
  delay(10);
}
