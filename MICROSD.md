> ESP32-C3 Super Mini (paket v22.32): TIDAK memakai microSD / upload WiFi. Berkas ini hanya untuk ESP32 biasa.

# microSD + upload WiFi (firmware v22.30, ESP32 biasa)

Modul: microSD Card Module SPI 6 pin (ada regulator 3V3 + level shifter, jadi VCC ke 5V).

| Modul | ESP32 |
|-------|-------|
| VCC   | 5V (VIN/USB) |
| GND   | GND |
| MISO  | GPIO19 |
| MOSI  | GPIO13 |
| SCK   | GPIO14 |
| CS    | GPIO26 |

Bus HSPI sendiri, terpisah dari layar (VSPI: SCK18/MOSI23/CS5). Ubah pin lewat `SD_SCK/SD_MISO/SD_MOSI/SD_CS` di .ino.
Jangan pakai GPIO12 (strapping tegangan flash), 6-11 (flash), 16/17 (PSRAM di modul WROVER).

File di kartu (FAT32): `/wp/wNNNNN.mnv` = perpustakaan wallpaper (banyak berkas; foto "MNPHOTO1" atau video "MNVIDEO2"/"MNVIDEO3"), `/boot/bNNNNN.mnv` = perpustakaan animasi boot (video "MNVIDEO2"/"MNVIDEO3", NNNNN = nomor slot 1..65535).
Batas satu berkas 1800 MB (v22.29). Boleh disalin manual dari PC (aplikasi bisa menyimpan berkas .mnv); `/wallpaper.mnv` di root otomatis dipindah ke `/wp/`.
Wallpaper aktif dipilih lewat menu LCD (Pengaturan > WALLPAPER, tahan = berikutnya) atau aplikasi, dan diingat setelah mati-hidup.
Foto di SD dipakai lewat salinan di partisi flash (otomatis, hanya bila isinya beda). Wallpaper SD tidak ada/rusak -> foto bawaan.
Animasi boot aktif dipilih lewat menu LCD (Pengaturan > ANIMASI BOOT, tahan = berikutnya; urutan: TANPA ANIMASI, lalu semua berkas di /boot) atau aplikasi, dan diingat setelah mati-hidup. `/bootanim.mnv` lama otomatis dipindah ke `/boot/` dan jadi aktif. Dengan SD terpasang partisi bootanim tidak dipakai; tanpa SD tetap dipakai seperti dulu.

Upload WiFi: Pengaturan (tahan sensor 4 dtk di standby) > UPLOAD WIFI (tahan). WiFi "MotorNav", sandi "motornav1234", alamat 192.168.4.1.
Selesai / batal (tahan sensor 1,2 dtk) = restart. Serial Monitor: "SD: terbaca", "WiFi upload: ap=1 buf=1 http=1 heap=...".
PARTISI BERUBAH di v22.28 (tetap sama di v22.30): wajib flash ulang penuh dengan partitions.csv yang ikut bila masih dari versi sebelum v22.28.

v22.31: PARTISI BERUBAH LAGI (dua slot program untuk OTA Bluetooth: app0 + app1 @ 1,625 MB, foto 192 KB, animasi boot flash 512 KB di 0x380000). Flash pertama kali WAJIB lewat kabel USB dengan partitions.csv yang ikut; sesudah itu firmware bisa diperbarui dari aplikasi (lihat README aplikasi, bagian 15). Bila "Sketch uses" > 1703936 byte pakai partitions_app_besar.csv (ganti nama jadi partitions.csv).
