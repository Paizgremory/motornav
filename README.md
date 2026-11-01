# MotorNav (aplikasi Android pengganti Chronos)

Aplikasi ini mengurus navigasi sendiri: cari tujuan, buat rute (OSRM), gambar peta gelap + garis rute + banner belokan +
kecepatan + ETA di HP, lalu kirim sebagai gambar 240x240 lewat BLE ke ESP32 "Motor Nav".
Tidak perlu Chronos maupun Google Maps.

## 1. Flash firmware baru
Pakai folder `motor_nav_esp32_v22` (v22.12) dari zip firmware. Board: ESP32 biasa, partisi sama seperti sebelumnya
(file partitions.csv ikut). Setelah flash, ESP32 tetap bisa dipakai dengan Chronos.

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

## 5. Bagikan dari Google Maps
Di Google Maps pilih tempat, tekan **Bagikan**, lalu pilih **MotorNav**. Aplikasi mengambil koordinatnya dan langsung membuat rute.

## Catatan
- Rute dari server demo OSRM (profil mobil, data OpenStreetMap). Untuk pemakaian pribadi ringan.
- Notifikasi, telepon, dan info lagu dari Chronos belum ada di aplikasi ini.

## 5. Wallpaper layar standby lewat Bluetooth (butuh firmware v22.13)
Di aplikasi, bagian "Wallpaper layar standby": Pilih foto, atur gelap/zoom/geser, tekan **Kirim ke ESP32** (ESP32 harus sudah tersambung).
Foto dikirim lewat BLE (sekitar 10-30 detik), diverifikasi CRC, lalu langsung tampil tanpa restart. Mode WiFi lama tetap ada (dipakai untuk video).
Layar GC9A01 fisiknya 240x240, jadi foto diproses sebaik mungkin di ukuran itu: dibaca dari foto asli, diperkecil halus, dipertajam, dan di-dithering.
