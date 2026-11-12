package com.motornav.app

/**
 * Ringkasan perubahan firmware per versi (diambil dari komentar riwayat di motor_nav_esp32_v22.ino),
 * ditampilkan di aplikasi sebelum update OTA supaya pengguna tahu apa yang berubah sebelum menekan "Kirim".
 */
object Changelog {
    /** Terbaru dulu. */
    val FIRMWARE = listOf(
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
