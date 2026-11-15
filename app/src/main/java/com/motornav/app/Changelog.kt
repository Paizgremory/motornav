package com.motornav.app

/**
 * Ringkasan perubahan firmware per versi (diambil dari komentar riwayat di motor_nav_esp32_v22.ino),
 * ditampilkan di aplikasi sebelum update OTA supaya pengguna tahu apa yang berubah sebelum menekan "Kirim".
 */
object Changelog {
    /** Terbaru dulu. */
    val FIRMWARE = listOf(
        "v22.43" to "Standby FOTO/VIDEO kini bisa jalan BERSAMAAN dengan navigasi peta (sebelumnya otomatis fallback ke wajah). Catatan: jam di standby foto/video untuk sementara belum transparan (ada kotak solid di belakang angka), dan visualizer/sampul album/kartu pemutar lagu di mode ini belum tersedia -- akan menyusul di update berikutnya. Tidak ada perubahan protokol Bluetooth, jadi tidak wajib update aplikasi untuk versi ini.",
        "v22.42" to "Perbaikan bug navigasi peta tidak tampil (layar nyangkut di standby meski aplikasi sudah mengirim peta): urutan alokasi memori saat boot diperbaiki, mesin dekode JPEG peta tidak lagi bergantung ke kanvas tampilan besar.",
        "v22.41" to "Sampul album di standby foto/video diperkecil dan dipindah ke sebelah progress bar/durasi lagu (sebelumnya besar, menutupi area jam).",
        "v22.40" to "Pembacaan kartu microSD lebih andal: ESP32 mencoba beberapa kecepatan SPI yang lebih pelan bila kecepatan tercepat gagal (membantu kabel/jumper yang kurang stabil).",
        "v22.39" to "Perbaikan mode UPLOAD WIFI agar bisa menyala di board dengan RAM terbatas (kanvas tampilan dilepas sementara selama proses upload).",
        "v22.38" to "Pembebasan memori Bluetooth Classic yang tidak dipakai di ESP32 biasa, supaya Bluetooth Low Energy (BLE) punya lebih banyak RAM untuk fitur peta & lainnya.",
        "v22.37" to "Menu LCD Pengaturan > WALLPAPER / ANIMASI BOOT: TAHAN langsung masuk mode WiFi upload (juga di ESP32-C3); item UPLOAD WIFI kini tampil di semua board.",
        "v22.36" to "Efek getar jalan di wajah standby (gimmick, opsional di pengaturan): wajah ikut bergoyang kecil mengikuti guncangan jalan, dibaca dari akselerometer HP lewat Bluetooth -- pengganti sensor MPU6050 fisik yang sudah dicabut.",
        "v22.35" to "Modul sensor MPU6050 (sudut miring) dan voltase aki 12V dihapus seluruhnya (tidak dipakai lagi); tampilan selalu tegak.",
        "v22.34" to "Tema visualizer baru: batang \"Api\" (gradien merah-oranye-kuning mengikuti tinggi batang) sebagai alternatif batang pelangi. Diatur dari aplikasi (tab Musik). Cincin pelangi di tepi layar tidak berubah.",
        "v22.33" to "Kendali motor dari HP: relay kunci kontak + starter, dilindungi kode PIN 6 digit, dengan reset kode lewat OTP email.",
        "v22.32" to "Dukungan ESP32-C3 Super Mini (kode & wiring menyesuaikan chip otomatis); item menu UPLOAD WIFI disembunyikan di C3.",
        "v22.31" to "Update firmware (OTA) langsung lewat Bluetooth dari aplikasi ini -- tidak perlu kabel USB lagi setelah ini. Partisi program berubah (WAJIB flash USB sekali untuk naik ke versi ini).",
        "v22.30" to "Perpustakaan animasi boot: banyak animasi bisa disimpan di kartu SD dan dipilih dari menu LCD atau aplikasi.",
        "v22.29" to "Perpustakaan wallpaper di kartu SD (banyak foto/video sekaligus, dipilih dari menu/aplikasi) dan batas ukuran upload naik sampai 1800 MB.",
        "v22.28" to "Upload lewat WiFi (lebih cepat dari Bluetooth) dan dukungan video sampai 60 FPS.",
        "v22.27" to "Dukungan kartu microSD untuk wallpaper video & animasi boot berukuran besar (ESP32 biasa, bukan C3).",
        "v22.26" to "Partisi wallpaper diperbesar agar video standby bisa lebih panjang."
    )

    /** Daftar perubahan sejak versi yang sedang berjalan di ESP32 (tidak termasuk versi itu sendiri). Tidak dikenali -> semua. */
    fun since(current: String): List<Pair<String, String>> {
        val cur = current.trim()
        if (cur.isBlank()) return FIRMWARE
        val idx = FIRMWARE.indexOfFirst { it.first.equals(cur, ignoreCase = true) }
        return if (idx < 0) FIRMWARE else FIRMWARE.subList(0, idx)
    }
}
