# Hidupkan motor dari HP (firmware v22.33 + aplikasi Nav160 v3.29)

## Wiring modul relay 2 channel (aktif LOW, opto-isolator)
| Modul relay | ESP32 DevKit | ESP32-C3 |
|---|---|---|
| VCC | 5V / VIN | 5V |
| GND | GND (satu massa dengan aki motor lewat buck) | GND |
| IN1 (kunci kontak) | GPIO32 | GPIO1 |
| IN2 (starter) | GPIO33 | GPIO20 |

Jika modul Anda aktif HIGH, ubah `RLY_ACTIVE_LOW` jadi 0. Jika relay tidak mau tarik dengan sinyal 3,3 V, beri VCC 5 V dan lepas jumper JD-VCC sesuai modul.

## Wiring ke motor (relay hanya MENGGANTIKAN tombol/kunci, dipasang PARALEL)
- Relay 1 (kunci kontak): COM + NO dipasang paralel dengan kontak kunci. Kabel kunci kontak asli tetap utuh, kunci/tombol asli tetap berfungsi.
  Pada motor yang kunci kontaknya membawa arus besar, relay 1 sebaiknya menarik relay otomotif 12 V 30 A, bukan langsung.
- Relay 2 (starter): COM + NO paralel dengan TOMBOL STARTER (kabel sinyal ke relay starter/solenoid, arus kecil). JANGAN langsung ke kabel motor starter (arus 100 A+).
- Pasang sekring 2-5 A di +12 V ke modul/buck. Pasang dioda/snubber bila ada derau.

## Keamanan
- Kode keamanan 258525: dipakai sebagai PIN perintah di firmware (`RLY_PIN_CODE`) dan untuk membuka kunci tab Motor di aplikasi (terkunci lagi tiap pindah tab / keluar aplikasi). Fitur lain dan Bluetooth tidak dikunci. Bluetooth tidak dienkripsi: PIN menahan orang iseng, bukan penyerang yang menyadap. Untuk anti-maling sungguhan, tetap pakai kunci asli/imobilizer.
- Starter: hanya bila kontak ON, maksimal 3 dtk (timer perangkat keras), jeda 4 dtk, lepas seketika bila Bluetooth putus.
- Kunci kontak TIDAK mati sendiri bila Bluetooth putus (mesin tetap hidup saat di jalan). Bila ESP32 restart lunak (OTA/crash) saat kontak ON, kontak dinyalakan lagi otomatis.
- Jangan matikan kontak dari aplikasi saat motor melaju (ada dialog konfirmasi).

## Lupa kode (reset lewat OTP email)
1. Tab Motor > "Lupa kode? Kirim OTP ke email". OTP 6 angka (berlaku 10 menit, 3 kali salah = hangus, minta ulang tiap 60 dtk) dikirim ke email penerima yang DITANAM di `MotorRecovery.kt` (daftar `RECIPIENTS`). Tidak ada layar untuk mengubah penerima.
2. Nyalakan ulang ESP32, sambungkan aplikasi, masukkan OTP + kode baru 6 angka. ESP32 hanya menerima ganti kode 2 menit pertama setelah menyala.
3. Email pengirim: sekali saja, saat menu terbuka, isi Gmail + sandi aplikasi Google (myaccount.google.com/apppasswords, perlu verifikasi 2 langkah). Disimpan di HP saja.
Kode awal tetap 258525 sampai diganti. Kode tersimpan di NVS ESP32 dan di HP; jika ESP32 di-flash penuh (hapus NVS) kode kembali ke 258525.
