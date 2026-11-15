# MotorNav (aplikasi Android untuk layar motor)

Aplikasi ini mengurus navigasi sendiri: cari tujuan, buat rute (OSRM), gambar peta gelap + garis rute + banner belokan +
kecepatan + ETA di HP, lalu kirim sebagai gambar 240x240 lewat BLE ke ESP32 "Motor Nav".
Tidak perlu aplikasi lain.

## 1. Flash firmware baru
Pakai folder `motor_nav_esp32_v22` (v22.33) dari zip firmware. Board: ESP32 biasa atau ESP32-C3 Super Mini, partisi
sama seperti sebelumnya (file partitions.csv ikut). Firmware v22.22 tidak lagi mendukung Chronos: semua data lewat
aplikasi MotorNav. Mulai v22.31, setelah flash USB pertama kali, pembaruan berikutnya bisa lewat Bluetooth dari
aplikasi ini (lihat bagian 11).

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
- Buka tab **Actions** di repo, tunggu "Build APK" hijau, lalu unduh artifact `MotorNav-debug-apk` (isinya app-debug.apk).
- Pasang APK (izinkan "pasang dari sumber tidak dikenal").

Alternatif: buka folder ini di Android Studio di komputer, lalu Build > Build APK.

## 3. Pakai
1. Nyalakan ESP32. Pengaturan TAMPILAN NAV tidak perlu diubah: frame dari aplikasi otomatis tampil penuh.
2. Buka MotorNav, izinkan Lokasi dan Bluetooth, tekan **Sambungkan ESP32**.
3. Tulis tujuan (nama tempat, `lat,lon`, atau tempel link Google Maps) lalu **Cari tujuan** dan pilih hasilnya.
4. Aplikasi tetap jalan saat layar HP mati (ada notifikasi "MotorNav aktif"). Matikan penghematan baterai untuk aplikasi ini.

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
1. **Beri akses notifikasi**: aktifkan MotorNav di daftar. Android 13+ bila pilihan abu-abu: Info aplikasi MotorNav > menu titik tiga > *Izinkan setelan terbatas*, lalu ulangi.
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
Saat lagu diputar di HP (Spotify, YouTube Music, dll.), MotorNav mengirim ke ESP32:
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
- Ketuk gambarnya (bukan tombol **Pakai**) untuk melihat pratinjau besar dulu sebelum mengirim; **Pakai** di dialog itu baru benar-benar mengirim.

## 11. Update firmware (OTA Bluetooth) (v3.17 + firmware v22.31)
Tab **Layar** > **Update firmware (OTA Bluetooth)**. Mengganti firmware ESP32 tanpa kabel USB, setelah flash USB pertama kali di v22.31 ke atas.
1. **Cek versi & lihat perubahan**: aplikasi menanyakan versi firmware yang sedang berjalan di ESP32, lalu menampilkan daftar perubahan
   (changelog) sejak versi itu **sebelum** Anda memilih berkas atau menekan kirim.
2. Pilih berkas `.ino.bin` (hasil Arduino IDE: Sketch > Export Compiled Binary -- **bukan** `.bootloader.bin`/`.partitions.bin`/`.merged.bin`).
3. **Kirim ke ESP32**: ada dialog konfirmasi terakhir, lalu berkas dikirim dengan progres %. ESP32 menulis ke slot program yang sedang
   tidak dipakai (app0/app1 bergantian); program lama tetap utuh dan tetap berjalan bila upload gagal atau terputus di tengah jalan.
   Setelah sukses (CRC & citra terverifikasi), ESP32 pindah slot dan restart sendiri sekitar 2-3 detik kemudian.
- Tombol **Lihat daftar changelog** menampilkan seluruh riwayat perubahan tanpa perlu tersambung ke ESP32.
- Bila ESP32 tidak membalas sama sekali, firmware yang terpasang masih di bawah v22.31 (OTA lewat Bluetooth belum ada): flash dulu lewat USB.

## 12. Riwayat & favorit tujuan (v3.17)
Tab **Navigasi**, di bawah hasil pencarian: setiap tujuan yang rutenya berhasil dibuat otomatis masuk **Riwayat & favorit** (disimpan di HP,
maksimal 25 tujuan bukan-favorit; favorit tidak pernah tersingkir).
- Ketuk nama tujuan untuk langsung menuju ke sana lagi (tanpa mengetik ulang).
- ☆ menandai sebagai favorit (berubah jadi ★ dan dipindah ke atas daftar); ketuk lagi untuk melepas tanda.
- **Hapus** membuang satu entri dari riwayat/favorit (tidak memengaruhi navigasi yang sedang berjalan).

## 13. Re-routing lebih halus (firmware tidak berubah, hanya aplikasi, v3.17)
Hitung ulang rute saat keluar jalur kini menunggu jeda minimum (sekitar 4 detik keluar jalur terus-menerus, bukan cuma beberapa sampel GPS)
sebelum benar-benar membuat rute baru, supaya telat belok sebentar atau lonjakan sinyal GPS tunggal tidak langsung memicu rute baru.
