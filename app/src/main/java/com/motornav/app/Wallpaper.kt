package com.motornav.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Menyiapkan foto wallpaper untuk layar GC9A01 240x240 (RGB565, 115200 byte).
 * Layar fisik hanya 240x240, jadi "HD" di sini = kualitas terbaik di 240x240:
 *  - dibaca dari foto asli (bukan thumbnail), dipotong persegi di tengah
 *  - diperkecil bertahap (rata-rata area) supaya tidak bergerigi
 *  - ketajaman ditambah sedikit (unsharp mask)
 *  - dikonversi ke RGB565 dengan dithering Floyd-Steinberg: gradasi langit/bayangan tidak "berpita"
 */
object Wallpaper {
    const val SIZE = 240

    /** Baca foto dari Uri sebesar mungkin yang masih wajar (sisi pendek >= 960 px), putar sesuai EXIF. */
    fun load(ctx: Context, uri: Uri): Bitmap? {
        val cr = ctx.contentResolver
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var sample = 1
        while (min(o.outWidth, o.outHeight) / (sample * 2) >= 960) sample *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) } ?: return null
        val rot = try {
            cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) { 0f }
        if (rot != 0f) {
            val m = Matrix().apply { postRotate(rot) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        return bmp
    }

    /**
     * Hasil: pair(RGB565 little-endian 115200 byte, bitmap pratinjau persis seperti yang tampil di layar).
     * zoom 1.0 = potong persegi penuh; geser (panX/panY -1..1) menggeser area potong bila zoom > 1.
     * dim 0..60 = % gelap di tengah (supaya jam terbaca), sama seperti halaman WiFi.
     */
    fun prepare(src: Bitmap, dim: Int, zoom: Float = 1f, panX: Float = 0f, panY: Float = 0f, sharpen: Boolean = true): Pair<ByteArray, Bitmap> {
        // 1. potong persegi (dengan zoom/geser)
        val side0 = min(src.width, src.height)
        val side = max(1, (side0 / zoom.coerceIn(1f, 4f)).toInt())
        val cx = src.width / 2f + panX.coerceIn(-1f, 1f) * (src.width - side) / 2f
        val cy = src.height / 2f + panY.coerceIn(-1f, 1f) * (src.height - side) / 2f
        val x0 = (cx - side / 2f).toInt().coerceIn(0, src.width - side)
        val y0 = (cy - side / 2f).toInt().coerceIn(0, src.height - side)
        var b = Bitmap.createBitmap(src, x0, y0, side, side)

        // 2. perkecil bertahap ke 480 (tiap langkah <= 2x, rata-rata area), lalu 2x2 rata-rata ke 240 di ruang cahaya linear
        val mid = SIZE * 2
        while (b.width / 2 >= mid) b = Bitmap.createScaledBitmap(b, b.width / 2, b.height / 2, true)
        if (b.width != mid) b = Bitmap.createScaledBitmap(b, mid, mid, true)
        val big = IntArray(mid * mid)
        b.getPixels(big, 0, mid, 0, 0, mid, mid)
        val lin = FloatArray(256) { Math.pow(it / 255.0, 2.2).toFloat() }
        val r = FloatArray(SIZE * SIZE); val g = FloatArray(SIZE * SIZE); val bl = FloatArray(SIZE * SIZE)
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            var sr = 0f; var sg = 0f; var sb = 0f
            for (dy in 0..1) for (dx in 0..1) {
                val p = big[(y * 2 + dy) * mid + x * 2 + dx]
                sr += lin[(p shr 16) and 255]; sg += lin[(p shr 8) and 255]; sb += lin[p and 255]
            }
            val i = y * SIZE + x
            r[i] = (Math.pow((sr / 4).toDouble(), 1 / 2.2) * 255.0).toFloat()
            g[i] = (Math.pow((sg / 4).toDouble(), 1 / 2.2) * 255.0).toFloat()
            bl[i] = (Math.pow((sb / 4).toDouble(), 1 / 2.2) * 255.0).toFloat()
        }

        // 3. ketajaman: unsharp mask 3x3 (tepi tidak diubah)
        if (sharpen) for (ch in arrayOf(r, g, bl)) {
            val o = ch.copyOf()
            for (y in 1 until SIZE - 1) for (x in 1 until SIZE - 1) {
                val i = y * SIZE + x
                val blur = (o[i - 1] + o[i + 1] + o[i - SIZE] + o[i + SIZE]) * 0.125f + o[i] * 0.5f
                ch[i] = (o[i] + 0.6f * (o[i] - blur)).coerceIn(0f, 255f)
            }
        }

        // 4. gelapkan tengah (jam) + potong lingkaran
        val k = dim.coerceIn(0, 60) / 100f
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val i = y * SIZE + x
            val d = hypot(x - 119.5, y - 119.5)
            if (d > 121) { r[i] = 0f; g[i] = 0f; bl[i] = 0f; continue }
            val f = 1f - k * max(0f, 1f - (d / 110f).toFloat())
            r[i] *= f; g[i] *= f; bl[i] *= f
        }

        // 5. RGB565 + dithering Floyd-Steinberg (serpentine)
        val out = ByteArray(SIZE * SIZE * 2)
        val chans = arrayOf(r, g, bl)
        val q = IntArray(SIZE * SIZE * 3)
        for (y in 0 until SIZE) {
            val ltr = y % 2 == 0
            var x = if (ltr) 0 else SIZE - 1
            while (x in 0 until SIZE) {
                val i = y * SIZE + x
                val dir = if (ltr) 1 else -1
                if (hypot(x - 119.5, y - 119.5) > 121) { q[i * 3] = 0; q[i * 3 + 1] = 0; q[i * 3 + 2] = 0; x += dir; continue }
                for (c in 0..2) {
                    val ch = chans[c]
                    val old = ch[i].coerceIn(0f, 255f)
                    val bits = if (c == 1) 6 else 5
                    val lv = (1 shl bits) - 1
                    val qi = (old / 255f * lv).roundToInt().coerceIn(0, lv)
                    q[i * 3 + c] = qi
                    val err = old - qi * 255f / lv
                    if (x + dir in 0 until SIZE) ch[i + dir] += err * 7f / 16f
                    if (y + 1 < SIZE) {
                        if (x - dir in 0 until SIZE) ch[i + SIZE - dir] += err * 3f / 16f
                        ch[i + SIZE] += err * 5f / 16f
                        if (x + dir in 0 until SIZE) ch[i + SIZE + dir] += err * 1f / 16f
                    }
                }
                x += dir
            }
        }
        val pv = IntArray(SIZE * SIZE)
        for (i in 0 until SIZE * SIZE) {
            val rq = q[i * 3]; val gq = q[i * 3 + 1]; val bq = q[i * 3 + 2]
            val v = (rq shl 11) or (gq shl 5) or bq
            out[i * 2] = (v and 255).toByte()
            out[i * 2 + 1] = (v shr 8).toByte()
            pv[i] = (0xFF shl 24) or ((rq * 255 / 31) shl 16) or ((gq * 255 / 63) shl 8) or (bq * 255 / 31)
        }
        val prevBmp = Bitmap.createBitmap(pv, SIZE, SIZE, Bitmap.Config.ARGB_8888)
        return Pair(out, prevBmp)
    }
}
