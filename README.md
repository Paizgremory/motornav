# Nav160 (aplikasi Android untuk layar motor)

Aplikasi ini mengurus navigasi sendiri: cari tujuan, buat rute (OSRM), gambar peta gelap + garis rute + banner belokan +
kecepatan + ETA di HP, lalu kirim sebagai gambar 240x240 lewat BLE ke ESP32 "Motor Nav".
Tidak perlu aplikasi lain.

## 1. Flash firmware baru
Pakai folder `motor_nav_esp32_v22` (v22.26) dari zip firmware. Board: ESP32 biasa. PARTISI BERUBAH di v22.26 (program 1,25 MB, wallpaper 2,19 MB, animasi boot 512 KB):
flash ulang penuh (file .merged.bin di alamat 0x0) dengan partitions.csv yang ikut, lalu kirim ulang wallpaper dari aplikasi. Firmware v22.22 tidak lagi mendukung Chronos: semua data lewat aplikasi Nav160.

## 2. Bangun APK (tanpa Android Studio, lewat GitHub Actions)
Di Termux:

    pkg install git unzip
    termux-setup-storage
    cd ~/storage/downloads
    unzip MotorNavApp.zip -d MotorNavApp && cd MotorNavApp
    git init && git add -A && git commit -m "MotorNav"
    git branch -M main
    git remote add origin https://github.com/USERNAME/motornav.git
    git push -u origin main

- Buat dulu repo kosong `motornav` di github.com.
- Saat diminta password, pakai Personal Access Token (centang scope `repo` dan `workflow`).
- Buka tab **Actions** di repo, tunggu "Build APK" hijau, lalu unduh artifact `Nav160-debug-apk` (isinya app-debug.apk).
- Pasang APK (izinkan "pasang dari sumber tidak dikenal").

Alternatif: buka folder ini di Android Studio di komputer, lalu Build > Build APK.

## 3. Pakai
1. Nyalakan ESP32. Pengaturan TAMPILAN NAV tidak perlu diubah: frame dari aplikasi otomatis tampil penuh.
2. Buka Nav160, izinkan Lokasi dan Bluetooth, tekan **Sambungkan ESP32**.
3. Tulis tujuan (nama tempat, `lat,lon`, atau tempel link Google Maps) lalu **Cari tujuan** dan pilih hasilnya.
4. Aplikasi tetap jalan saat layar HP mati (ada notifikasi "Nav160 aktif"). Matikan penghematan baterai untuk aplikasi ini.

## Animasi boot
Menu Layar > "Animasi boot": ada **koleksi slot** seperti wallpaper (lihat bagian 12). Pilih video > Kirim, atau ketuk **Pakai** pada animasi yang sudah ada.
Video diputar sekali tiap ESP32 dinyalakan (sentuh sensor = lewati). Baris **Tanpa animasi** mematikannya.

## 4. Rute & pencarian Google (opsional)
Tempel Google API key di kolom atas aplikasi, tekan **Simpan API key**. Di Google Cloud aktifkan **Routes API** dan **Places API (New)**,
pasang billing, dan batasi key ke dua API itu. Kalau key kosong atau Google gagal, aplikasi otomatis memakai OSRM/Nominatim
(status di layar HP menunjukkan mesin yang dipakai). Peta Google (gaya malam) diambil lewat Map Tiles API, jadi aktifkan juga **Map Tiles API**. Ubin Google hanya disimpan di memori.

## 5. Wallpaper foto & video lewat Bluetooth (butuh firmware v22.14)
Mode upload WiFi sudah dihapus; semuanya lewat aplikasi. Di bagian "Wallpaper layar standby": **Pilih foto/video**, atur gelap/zoom/geser,
lalu **Kirim ke ESP32** (ESP32 harus tersambung). Hasil diverifikasi CRC dan langsung tampil tanpa restart.
- Foto: dikirim sekitar 10-30 detik. Dibaca dari file asli, diperkecil halus, dipertajam, di-dithering.
- Video: HP mengubah video jadi frame JPEG 240x240 (satu slot ~2,3 MB), lalu mengirimnya (beberapa menit). Atur **FPS** (5-30) dan
  **Kualitas/detail JPEG** (30-95); teks perkiraan menunjukkan berapa detik yang muat. "Otomatis" menurunkan FPS (min 6) agar seluruh video muat;
  matikan untuk memakai FPS persis seperti slider (video dipotong dari awal bila tak muat).
- **Hapus wallpaper** mengembalikan foto bawaan. Menu pengaturan di ESP32 (tahan 4 dtk di standby) item WALLPAPER hanya menampilkan isi slot.
Layar GC9A01 fisiknya 240x240, jadi detail maksimum ditentukan oleh ukuran itu.

## 6. Notifikasi & telepon di layar motor (v3.8 + firmware v22.15)
Butuh firmware v22.15. Di bagian "Notifikasi & telepon di layar motor":
1. **Beri akses notifikasi**: aktifkan Nav160 di daftar. Android 13+ bila pilihan abu-abu: Info aplikasi Nav160 > menu titik tiga > *Izinkan setelan terbatas*, lalu ulangi.
2. **Izin telepon**: izinkan Telepon, Log panggilan (untuk nomor penelepon), dan Kontak (untuk nama).
3. Sambungkan ESP32, lalu coba **Tes notifikasi** dan **Tes telepon**.
- Layar notifikasi: logo asli aplikasi, nama aplikasi, pengirim (huruf besar), isi pesan huruf ukuran 2 yang berganti halaman otomatis; pesan panjang tampil lebih lama (maks sekitar 26 dtk). Sentuh sensor untuk menutup.
- Yang diteruskan: notifikasi bersuara (bukan senyap/ongoing/ringkasan grup/media/navigasi), tidak saat Jangan Ganggu aktif. Tombol **Pilih aplikasi** memilih aplikasi mana yang boleh tampil (daftar terisi setelah aplikasi itu pernah mengirim notifikasi).
- **Mode berkendara** (bawaan aktif): saat navigasi berjalan, hanya aplikasi prioritas (bawaan: WhatsApp, Telegram, SMS, Signal, Line, Instagram, Messenger; bisa diubah lewat **Aplikasi prioritas berkendara**) yang tampil, dan tampilannya singkat: 4,5 dtk, satu halaman, bunyi pendek. Telepon tetap selalu tampil.
- Emoji dan aksen tidak bisa dicetak font layar: emoji dibuang, aksen disederhanakan (e -> e). Pesan berisi emoji saja tampil "(emoji / stiker)".
- **Antrean notifikasi:** bila beberapa pesan masuk berdekatan, HP menahannya (maks 5) dan mengirim satu per satu setelah layar motor selesai menampilkan yang sebelumnya. Lencana oranye "+N" di dekat logo menunjukkan berapa yang masih menunggu. Yang terlama dibuang bila antrean penuh.
- **Peringatan baterai HP rendah:** layar motor menampilkan peringatan saat baterai HP turun melewati 20%, 10%, dan 5% (tidak saat sedang diisi). Bisa dimatikan di menu.
- **Tolak telepon dari motor:** saat layar telepon tampil, TAHAN sensor 0,7 dtk = telepon ditolak (butuh izin Telepon lengkap, termasuk "Menjawab dan mengakhiri panggilan"; panggilan WhatsApp ditolak lewat tombol Tolak di notifikasinya). Sentuh singkat hanya menutup layar.
- **Panggilan tak terjawab** tampil sebagai notifikasi "Tidak terjawab" berisi nama/nomor dan jam. Notifikasi missed call bawaan dialer tidak ditampilkan dobel.
- Telepon biasa terbaca dari status telepon; panggilan WhatsApp/Telegram terbaca dari notifikasinya. Layar telepon menutup otomatis saat diangkat/ditolak/selesai.

## 7. Animasi musik di layar standby (v3.16 + firmware v22.24)
Saat lagu diputar di HP (Spotify, YouTube Music, dll.), Nav160 mengirim ke ESP32:
- **Judul, artis, status** (butuh Akses notifikasi aktif, sama seperti bagian 6).
- **Gambar sampul** 64x64, digambar semi-transparan di atas wallpaper (standby foto/video) dan hilang saat lagu dijeda/berganti.
- **Visualizer asli**: spektrum suara HP (16 band) dikirim ~20x/dtk, batang pelangi mengikuti musik. Butuh izin **Rekam audio**
  (tombol "3. Izin Rekam audio"). Tanpa izin atau bila HP menolak, visualizer memakai simulasi. Status tampil di bagian notifikasi.
- Standby **wajah**: wajah menari + not melayang, lompatannya mengikuti bass lagu.

## 8. Pengaturan tampilan standby & musik (v3.16 + firmware v22.24)
Bagian "Pengaturan tampilan standby & musik" di aplikasi. Semua perubahan langsung dikirim ke ESP32 dan disimpan di sana (tetap ada setelah mati-hidup);
nilainya juga diingat aplikasi dan dikirim ulang tiap tersambung.
- Opasitas gambar sampul, tampil/sembunyikan sampul
- Keputihan jam di tengah
- Visualizer: hidup/mati, tinggi batang, keterlihatan (tembus pandang)
- Kecepatan putaran cincin pelangi (0 = diam)
- Kartu pemutar (judul, artis, detik) hidup/mati
- Lama menunggu sebelum masuk standby (5-120 dtk)
- Wajah menari saat lagu diputar
- **Kembalikan ke bawaan** mengembalikan semuanya.
Pengaturan wallpaper (gelap/zoom/geser/FPS/kualitas) tetap di bagian "Wallpaper layar standby".

## 9. Tab, slider lagu, kecerahan (v3.16 + firmware v22.24)
Aplikasi kini punya 4 tab: **Navigasi**, **Musik**, **Layar**, **Notifikasi**. Bagian atas (status, sambungkan ESP32) selalu terlihat.
- **Musik**: kartu pemutar (judul, artis, slider geser lagu, sebelumnya/putar/berikutnya) + semua pengaturan tampilan musik.
  Di layar standby foto/video, posisi lagu tampil sebagai garis bergelombang putih (sudah diputar) dan garis datar abu-abu (sisa), lengkap dengan waktu.
- **Layar**: **kecerahan layar** (10-100%, diredupkan lewat perangkat lunak karena modul tanpa pin BL), keputihan jam, lama menunggu standby,
  kecepatan cincin pelangi, tombol kembalikan ke bawaan, dan wallpaper.

## 10. Koleksi wallpaper (v3.16)
Tab **Layar** > **Koleksi wallpaper**: daftar semua wallpaper yang pernah Anda siapkan, lengkap dengan thumbnail.
- **Pakai** (atau ketuk gambarnya) mengirim wallpaper itu ke ESP32; tanda ✓ menunjukkan yang sedang dipakai. Foto sekitar 10-30 dtk, video beberapa menit.
- **Foto bawaan** mengembalikan wallpaper dari firmware.
- Ketuk nama untuk mengganti nama, **Hapus** untuk membuang dari koleksi (wallpaper yang sudah ada di ESP32 tidak berubah).
- Wallpaper baru: di bagian "Tambah wallpaper baru", pilih foto/video, atur, lalu **Kirim ke ESP32** (otomatis masuk koleksi) atau **Simpan saja**
  (masuk koleksi tanpa dikirim). Yang tersimpan adalah hasil olahan siap kirim, jadi memilihnya nanti tidak perlu diolah ulang.
- Koleksi disimpan di HP (bukan di ESP32), jadi ESP32 tetap satu slot besar seperti sebelumnya.

## 10. microSD, FPS 60, durasi panjang & upload WiFi (v3.22 + firmware v22.28)
Video wallpaper dan animasi boot disimpan di kartu microSD, tidak lagi dibatasi partisi flash. Foto tetap di flash ESP32 (115 KB).
- **Kabel (ESP32 biasa):** SCK→GPIO14, MISO→GPIO19, MOSI→GPIO13, CS→GPIO26, VCC→5V, GND→GND. Kartu FAT32 (<= 32 GB).
- **Flash firmware v22.28 penuh (alamat 0x0)**: PARTISI BERUBAH (program 1,94 MB, wallpaper flash 1,5 MB, animasi boot 512 KB) karena library WiFi menambah ukuran program.
- **Batas ukuran** (di `Wallpaper.kt`): wallpaper 32 MB, animasi boot 8 MB (`CAP`, `BOOT_CAP`). Firmware menerima sampai 256 MB.
- **FPS** sampai 60 (slider 5-60). ESP32 realistisnya ±25-30 fps; di atas itu frame dilewati otomatis, jadi 60 fps hanya memperbesar file.
- **Durasi**: video > 900 frame otomatis memakai format MNVIDEO3 (khusus SD, sampai 30000 frame di aplikasi). Tanpa SD maksimal format lama 900 frame / 1,5 MB.
- **Upload WiFi** (lebih cepat dari Bluetooth, ±10x): di layar motor, Pengaturan > UPLOAD WIFI (tahan). BLE mati, ESP32 membuat WiFi "MotorNav" (sandi `motornav1234`).
  Di aplikasi centang "Kirim lewat WiFi", lalu Kirim. HP tersambung otomatis (Android 10+: setujui dialog sekali; Android 8-9: sambungkan manual dulu). Internet HP tidak putus.
  Selesai atau tahan sensor 1,2 dtk = ESP32 restart dan BLE aktif lagi. SSID/sandi bisa diubah di `WU_SSID/WU_PASS` (.ino) dan `WifiUp.kt`.
- **Simpan berkas .mnv**: di Koleksi wallpaper (tombol ".mnv") atau "Simpan animasi boot sebagai file .mnv", lalu salin ke root kartu SD dengan nama `wallpaper.mnv` / `bootanim.mnv`.

## 11. Banyak wallpaper di kartu SD, pilihan di LCD & aplikasi, batas upload besar (v3.23 + firmware v22.29)
Dibuat untuk kartu microSD 2 GB.
- **Batas upload dinaikkan:** wallpaper 512 MB, animasi boot 128 MB (`CAP`, `BOOT_CAP` di `Wallpaper.kt`); firmware menerima sampai 1800 MB (`SD_MAX_UP` di .ino).
  Durasi video sampai 65000 frame (±36 menit @ 30 fps). Video diolah ke BERKAS sementara di HP (tidak lagi dimuat ke RAM), jadi ratusan MB aman. Pastikan
  penyimpanan HP kosong minimal 2x ukuran hasil. Untuk berkas besar pakai **Kirim lewat WiFi** (Bluetooth bisa berjam-jam untuk ratusan MB).
- **Perpustakaan wallpaper di SD:** tiap wallpaper (foto ATAU video) disimpan sebagai `/wp/wNNNNN.mnv` di kartu. Bisa banyak sekaligus (maks 64 tampil di daftar),
  selama kartu muat. Foto sekarang juga disimpan di SD (tidak lagi satu-satunya di flash).
- **Pilihan di layar motor:** Pengaturan (tahan sensor 4 dtk di standby) > **WALLPAPER**: tampil `nomor/jumlah` + jenis (FOTO/VIDEO, durasi, ukuran).
  **TAHAN** = ganti ke wallpaper berikutnya (urutan: BAWAAN, lalu semua berkas di SD, lalu kembali ke BAWAAN). Pilihan tersimpan setelah mati-hidup.
- **Pilihan di aplikasi:** tab Layar > Koleksi wallpaper. Baris yang bertanda "ada di kartu SD" langsung diganti tanpa kirim ulang. Wallpaper di kartu yang tidak ada di HP
  tampil sebagai "Di kartu SD (slot N)". **Perbarui daftar kartu SD** membaca isi kartu lewat Bluetooth (otomatis saat tersambung). Hapus bisa dari HP, dari kartu, atau keduanya.
  Wallpaper yang baru dikirim langsung menjadi wallpaper aktif.
- **Kompatibilitas:** `/wallpaper.mnv` lama di root kartu otomatis dipindah ke `/wp/` saat ESP32 menyala dan jadi wallpaper aktif. Nomor slot = turunan CRC isi, jadi
  isi yang sama selalu memakai slot yang sama (tidak terkirim dobel). Salin manual dari PC: tombol ".mnv" menyimpan berkas dengan nama `w#####.mnv`, letakkan di folder `/wp` kartu.
- **Firmware wajib v22.29** untuk semua fitur di atas (Bluetooth: perintah 0x15, 0x18-0x1A). Partisi tidak berubah dari v22.28.

## 12. Slot animasi boot (v3.24 + firmware v22.30)
Animasi boot kini punya perpustakaan slot di kartu SD, sama seperti wallpaper, jadi tinggal pilih.
- **Di kartu SD:** tiap animasi = satu berkas `/boot/bNNNNN.mnv` (NNNNN = nomor slot). Banyak animasi bisa tersimpan sekaligus (maks 64 tampil di daftar).
  `/bootanim.mnv` lama otomatis dipindah ke `/boot/` (slot 65001) dan jadi animasi aktif. Dengan SD terpasang, partisi flash "bootanim" tidak dipakai lagi.
- **Di layar motor:** Pengaturan (tahan sensor 4 dtk di standby) > **ANIMASI BOOT** (di bawah WALLPAPER): tampil `nomor/jumlah` + durasi/ukuran. **TAHAN = berikutnya**;
  urutannya `TANPA ANIMASI`, lalu semua berkas di SD. Pilihan diingat setelah mati-hidup.
- **Di aplikasi:** Tab **Layar** > **Animasi boot** > daftar koleksi (thumbnail, tanda ✓ = aktif, "ada di kartu SD"). **Pakai** = langsung ganti bila sudah ada di SD, kalau belum dikirim dulu.
  **Tanpa animasi** mematikan. Ketuk nama = ganti nama, **.mnv** = simpan berkas, **Hapus** = dari HP / kartu SD / keduanya. **Perbarui daftar kartu SD** membaca isi folder `/boot`.
  Animasi baru: **Pilih video** > **Kirim ke ESP32** (otomatis masuk koleksi + jadi aktif) atau **Simpan saja**. Isi yang sama persis selalu masuk slot yang sama (tidak terkirim dua kali).
- **Salin manual dari PC:** simpan `.mnv` dari aplikasi, salin ke folder `/boot` di kartu dengan nama `bNNNNN.mnv` (NNNNN 1..65535). Lalu pilih di menu LCD.
- **BLE baru (firmware v22.30):** `[FF][1B][ukuran u32][slot u16]` upload ke slot, `[FF][1C]` daftar (balasan notify `0xE3`, format sama dengan `0xE2`),
  `[FF][1D][slot u16]` pilih (0 = tanpa animasi), `[FF][1E][slot u16]` hapus. Upload WiFi: `POST /up?tgt=1&crc=..&slot=N`.
- **Flash ulang:** partisi TIDAK berubah dari v22.29. Cukup upload sketch v22.30 (aplikasi lama tetap bisa kirim animasi lewat jalur lama, tapi tanpa slot).

## 13. Navigasi lebih cepat ke LCD (v3.25, hanya aplikasi: firmware tidak perlu di-flash ulang)
- **Jeda tunggu 250 ms dihapus** (jadi 40 ms): perubahan posisi/belokan langsung diproses.
- **Render dan kirim berjalan bersamaan:** frame berikutnya digambar & dikompres selagi frame sebelumnya dikirim; frame yang tertinggal dibuang, jadi LCD selalu menerima yang terbaru.
- **Kompres JPEG ±1-2 kali** (dulu ±6 kali pencarian biner), mulai dari kualitas frame sebelumnya.
- **Frame lebih kecil saat bergerak** (kecepatan >= 8 km/j: maks 7,5 KB; diam: 11,5 KB). Ubah `LIMIT_MOVING` / `LIMIT_STILL` di `Core.kt` bila ingin lebih tajam atau lebih cepat.
- **Paket BLE lebih besar** (sampai 239 B data per paket, dulu 200 B): ±16% lebih sedikit putaran tunggu.

## 14. Pilihan kecepatan peta navigasi (v3.26, hanya aplikasi)
Tab **Layar** > **Kecepatan peta navigasi** > tombol "Kualitas peta" (ketuk untuk berganti, tersimpan). Frame lebih kecil = sampai ke LCD lebih cepat lewat Bluetooth.
- **Cepat** (bawaan): bergerak maks 4,5 KB, diam 8 KB. **Seimbang**: 7,5 / 11,5 KB (perilaku v3.25). **Tajam**: 11,5 KB selalu.
- Status di bagian atas aplikasi menampilkan "Frame terkirim: N (terakhir X KB dalam Y ms)" untuk mengukur kecepatan nyata.

## 15. Update firmware ESP32 lewat Bluetooth / OTA (v3.27 + firmware v22.31)
Firmware ESP32 bisa diperbarui dari aplikasi tanpa kabel USB: halaman **Layar** > bagian **Update firmware ESP32 (OTA Bluetooth)**.

**A. Persiapan SEKALI (lewat kabel USB).** Tabel partisi berubah jadi dua slot program (app0 + app1 @ 1,625 MB), jadi firmware v22.31 pertama WAJIB di-flash lewat kabel:
1. Pakai folder `motor_nav_esp32_v22` dari zip firmware v22.31 (berisi `partitions.csv` baru). Arduino IDE: board "ESP32 Dev Module", Flash Size 4MB, tekan **Upload** (bukan "programmer").
2. Lihat baris `Sketch uses ... bytes` di akhir kompilasi. Batas tiap slot **1703936** byte. Bila program lebih besar: ganti nama `partitions_app_besar.csv` menjadi `partitions.csv` (slot 1,875 MB, partisi animasi boot flash dihapus; animasi boot hanya dari microSD), kompilasi ulang, upload.
3. Wallpaper foto di flash ikut terhapus oleh partisi baru: kirim ulang dari aplikasi (yang di kartu SD tetap aman).

**B. Update berikutnya (tanpa kabel).**
1. Arduino IDE: **Sketch > Export Compiled Binary**. Ambil berkas yang berakhiran **`.ino.bin`** di folder `build/...` sketch (BUKAN `.bootloader.bin`, `.partitions.bin`, `.merged.bin`).
2. Salin berkas itu ke HP (Download / Drive / kabel).
3. Aplikasi: sambungkan ESP32 (tombol 1) > Layar > **Pilih .bin firmware** > (opsional) **Cek versi ESP32** > **Kirim & update** > Kirim.
4. Tunggu 100% (±3-6 menit untuk ±1,4 MB; Bluetooth lambat dan stabil). ESP32 memeriksa CRC dan keabsahan citra, lalu restart sendiri; Bluetooth tersambung ulang beberapa detik kemudian.

**Keamanan.**
- Citra ditulis ke slot yang TIDAK sedang jalan. Gagal / terputus / dibatalkan di tengah jalan = program lama utuh, tidak terjadi apa-apa.
- Aplikasi dan firmware sama-sama menolak berkas yang bukan program ESP32 (mis. bootloader / merged.bin). CRC-32 dan `esp_ota_end` harus cocok sebelum slot dipindah.
- Batasan: bila firmware baru bisa boot tetapi hang / restart terus-menerus, tidak ada rollback otomatis. Cadangannya: flash ulang lewat kabel USB.
- Protokol BLE baru: `0xFF 0x2A` mulai, `0x2B` isi, `0x2C` selesai (CRC-32), `0x2D` info (notify `0xE4`), `0x2E` batal.

## 16. Navigasi pintar (v3.28)
Semua pengaturan di tab **Navigasi**, bagian "Navigasi pintar".

**Suara petunjuk.**
- Peringatan bertahap menurut kecepatan: makin cepat makin awal (±550 m / 200 m / sekarang pada 40 km/j, ±960 m pada 70 km/j). Di atas ±60 km/j kalimat dipendekkan.
- Belokan beruntun: "belok kanan ke Jalan A, lalu belok kiri". Bundaran menyebut nomor jalan keluar, percabangan menyebut kiri/kanan, "Bersiap merapat ke kanan" sebelum belok tajam, "Ikuti Jalan X sejauh 3 kilometer" setelah belokan bila lurusnya panjang, "Tujuan di depan, di sebelah kanan".
- Petunjuk jalur ("Gunakan jalur kiri") hanya bila data jalurnya ada (OSRM/OpenStreetMap); Google Routes tidak memberi data jalur, dan banyak jalan di Indonesia belum dipetakan jalurnya, jadi sering tidak ada.

**Pemilihan rute.** Aplikasi meminta rute utama + alternatif, lalu memilih menurut "Pilihan rute": Seimbang (waktu, ditambah penalti tiap belokan/putar balik), Tercepat, atau Terpendek (tidak lebih dari 1,6x lebih lambat dari yang tercepat). "Hindari tol dan feri" aktif secara bawaan: rute bertol hanya dipakai bila tidak ada pilihan lain, dan itu diucapkan sebagai peringatan.

**Pemeriksaan rute (supaya tidak tersesat).** Setiap rute diperiksa dan diucapkan bila bermasalah: lewat tol/feri, ujung rute >150 m dari titik tujuan, memutar >2,5x garis lurus, banyak putar balik, perkiraan waktu tidak masuk akal. Saat berkendara: "Sinyal GPS lemah" (akurasi >40 m selama 8 dtk), "Sinyal GPS terputus" (12 dtk tanpa lokasi), "Anda berlawanan arah dengan rute" (melaju berlawanan arah selama 4 dtk), dan peringatan khusus bila rute diulang 3x dalam 3 menit.

**Rute lebih cepat.** Tiap ±2,5 menit (hanya saat melaju, sisa >3 km, belokan berikutnya >600 m) aplikasi menghitung rute baru dari posisi sekarang. Berpindah otomatis hanya bila hemat minimal 3 menit dan 15%, jalannya benar-benar berbeda, tidak lewat tol, dan ujungnya tetap di tujuan. Bisa dimatikan. Tiap pengecekan = satu permintaan rute (kuota Google Routes / server publik OSRM).

**Batasan yang jujur.**
- Batas kecepatan jalan tidak ada: tidak tersedia di Routes API / OSRM publik.
- Aplikasi tidak tahu jalan satu arah, jalan tutup, atau larangan motor selain yang ada di data penyedia rute. Pemeriksaan di atas mengurangi, bukan menghilangkan, risiko tersesat. Tetap utamakan rambu dan kondisi jalan.
- OSRM publik memakai profil mobil (bukan motor). Google memakai mode sepeda motor bila tersedia.
- Teks bundaran/tol dari Google dikenali lewat kata kunci bahasa Indonesia/Inggris pada teks petunjuknya; bila Google mengubah kalimatnya, nomor keluar bundaran bisa tidak terbaca (aplikasi tetap bilang "masuk bundaran").
