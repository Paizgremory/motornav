# motor_nav_esp32

Firmware ESP32 untuk MotorNav (layar navigasi motor berbasis GC9A01 + BLE).

## Compile otomatis via GitHub Actions

Repo ini sudah berisi workflow `.github/workflows/build.yml` yang akan mengompilasi
sketch secara otomatis setiap kali ada `push`, untuk dua target:

- `esp32-devkit` — board "ESP32 Dev Module" (ESP32 biasa, 30/38 pin)
- `esp32c3-super-mini` — board "ESP32C3 Dev Module"

### Cara pakai
1. Push/upload folder `motor_nav_esp32_v22/` (berisi `.ino`, `.h`, dan `partitions.csv`)
   beserta folder `.github/workflows/` ini ke repository GitHub Anda.
2. Buka tab **Actions** di repo GitHub → workflow "Compile motor_nav ESP32 firmware"
   akan berjalan otomatis.
3. Setelah selesai (centang hijau), klik run tersebut → scroll ke bagian **Artifacts**
   → unduh `firmware-esp32-devkit.zip` atau `firmware-esp32c3-super-mini.zip`.
   Di dalamnya ada beberapa file:
   - `motor_nav_esp32_v22.ino.merged.bin` → **ini yang dipakai untuk flash lewat web flasher**
     (mis. https://esp.huhn.me , https://espressif.github.io/esptool-js/ , atau
     ESP Web Tools). File ini sudah gabungan bootloader + partition table + app,
     tinggal pilih **"Flash Address: 0x0"** lalu flash — tidak perlu isi alamat lain.
   - `motor_nav_esp32_v22.ino.bin` → file app saja (dipakai untuk update OTA lewat
     aplikasi MotorNav, BUKAN untuk web flasher / flash awal).
   - `.elf` → untuk debugging, tidak perlu diflash.
4. Setelah merged.bin diflash sekali (full flash pertama), update selanjutnya cukup
   lewat OTA Bluetooth dari aplikasi memakai `.ino.bin` (bukan merged.bin).

### Mengganti Partition Scheme
Jika log build menunjukkan ukuran sketch melebihi batas partisi `app0`/`app1`
(lihat komentar di atas `motor_nav_esp32_v22.ino`, cari "PARTISI BERUBAH"),
ganti nama `partitions_app_besar.csv` menjadi `partitions.csv` (timpa yang lama)
sebelum push ulang.

### Library yang dipakai (otomatis diinstal oleh workflow)
- GFX Library for Arduino
- NimBLE-Arduino (v2.x)
- Board core: `esp32:esp32` (Espressif)
