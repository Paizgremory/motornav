package com.motornav.app

import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

/** Keadaan navigasi yang siap digambar (tidak berubah setelah dibuat). */
class NavState(
    val lat: Double, val lon: Double, val heading: Float, val speedKmh: Int,
    val zoom: Int, val headingUp: Boolean,
    val route: Route?, val routeIdx: Int, val along: Double,
    /** 0 = peta saja, 1 = rute aktif, 2 = mencari rute ulang, 3 = tiba */
    val mode: Int,
    val mDist: Double, val mAngle: Float, val mKind: Int,
    val street: String, val instr: String,
    val hasThen: Boolean, val thenAngle: Float, val thenKind: Int,
    val remDist: Double, val remSec: Double
)

/** Menggambar frame 240x240 untuk layar bulat ESP32: peta, rute, banner belokan, kecepatan, ETA. */
class Renderer(private val tiles: Tiles) {
    val bmp: Bitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
    private val cv = Canvas(bmp)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    // OSM terang -> gelap: balik warna lalu putar hue 180 derajat (air tetap kebiruan), sedikit diredupkan
    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        val h = arrayOf(
            floatArrayOf(-0.574f, 1.430f, 0.144f),
            floatArrayOf(0.426f, 0.430f, 0.144f),
            floatArrayOf(0.426f, 1.430f, -0.856f)
        )
        val k = 0.85f
        val m = FloatArray(20)
        for (i in 0..2) {
            for (j in 0..2) m[i * 5 + j] = -h[i][j] * k
            m[i * 5 + 4] = 255f * (h[i][0] + h[i][1] + h[i][2]) * k
        }
        m[18] = 1f
        colorFilter = ColorMatrixColorFilter(ColorMatrix(m))
    }
    private val path = Path()
    private val rect = RectF()

    private val cBg = Color.rgb(12, 28, 64)
    private val cBanner = Color.rgb(26, 102, 176)
    private val cBanner2 = Color.rgb(15, 70, 130)
    private val cChip = Color.rgb(10, 20, 46)
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.US)

    @Synchronized
    fun render(s: NavState): Bitmap {
        cv.drawColor(cBg)
        val z = s.zoom
        val px = Geo.worldX(s.lon, z)
        val py = Geo.worldY(s.lat, z)
        val cx = 120f
        val cy = 165f
        val rot = if (s.headingUp) s.heading else 0f

        cv.save()
        cv.translate(cx, cy)
        cv.rotate(-rot)
        val r = 215.0
        val x0 = floor((px - r) / 256).toInt()
        val x1 = floor((px + r) / 256).toInt()
        val y0 = floor((py - r) / 256).toInt()
        val y1 = floor((py + r) / 256).toInt()
        for (ty in y0..y1) for (tx in x0..x1) {
            val t = tiles.get(z, tx, ty) ?: continue
            val l = (tx * 256.0 - px).toFloat()
            val tt = (ty * 256.0 - py).toFloat()
            rect.set(l, tt, l + 256.6f, tt + 256.6f)
            cv.drawBitmap(t, null, rect, tilePaint)
        }
        drawRoute(s, px, py)
        cv.restore()

        // posisi saya: kerucut arah + titik
        cv.save()
        cv.translate(cx, cy)
        cv.rotate(if (s.headingUp) 0f else s.heading)
        p.style = Paint.Style.FILL
        p.color = Color.argb(90, 60, 130, 255)
        path.reset()
        path.moveTo(0f, 0f); path.lineTo(-24f, -42f); path.lineTo(24f, -42f); path.close()
        cv.drawPath(path, p)
        cv.restore()
        p.color = Color.WHITE
        cv.drawCircle(cx, cy, 11f, p)
        p.color = Color.rgb(66, 133, 244)
        cv.drawCircle(cx, cy, 8f, p)

        drawBanner(s)
        drawBottom(s)
        return bmp
    }

    private fun drawRoute(s: NavState, px: Double, py: Double) {
        val rt = s.route ?: return
        val n = rt.lat.size
        path.reset()
        path.moveTo(0f, 0f)                       // titik awal = posisi saya
        var i = s.routeIdx + 1
        var last = s.routeIdx
        while (i < n) {
            if (rt.cum[i] - s.along > 900) break
            val x = (Geo.worldX(rt.lon[i], s.zoom) - px).toFloat()
            val y = (Geo.worldY(rt.lat[i], s.zoom) - py).toFloat()
            path.lineTo(x, y)
            last = i
            i++
        }
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = Color.rgb(20, 90, 200); p.strokeWidth = 13f
        cv.drawPath(path, p)
        p.color = Color.rgb(100, 185, 255); p.strokeWidth = 8f
        cv.drawPath(path, p)
        p.style = Paint.Style.FILL
        if (last == n - 1) {                       // titik tujuan
            val x = (Geo.worldX(rt.lon[n - 1], s.zoom) - px).toFloat()
            val y = (Geo.worldY(rt.lat[n - 1], s.zoom) - py).toFloat()
            p.color = Color.WHITE; cv.drawCircle(x, y, 9f, p)
            p.color = Color.rgb(230, 60, 60); cv.drawCircle(x, y, 6f, p)
        }
    }

    private fun fmtDist(m: Double): String {
        return if (m < 1000) {
            val v = if (m < 100) ((m / 10).roundToInt() * 10) else ((m / 50).roundToInt() * 50)
            "${max(10, v)} m"
        } else String.format(Locale("id", "ID"), "%.1f km", m / 1000)
    }

    private fun ell(t: String, size: Float, w: Float): String {
        tp.textSize = size
        return TextUtils.ellipsize(t, tp, w, TextUtils.TruncateAt.END).toString()
    }

    private fun text(t: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT) {
        tp.textSize = size
        tp.color = color
        tp.textAlign = align
        cv.drawText(t, x, y, tp)
    }

    private fun drawBanner(s: NavState) {
        if (s.mode == 0) return
        p.style = Paint.Style.FILL
        p.color = cBanner
        cv.drawRoundRect(38f, 34f, 202f, 92f, 14f, 14f, p)
        when (s.mode) {
            2 -> {
                text("Mencari rute", 120f, 60f, 16f, Color.WHITE, Paint.Align.CENTER)
                text("baru...", 120f, 80f, 16f, Color.WHITE, Paint.Align.CENTER)
            }
            3 -> {
                drawTurn(64f, 64f, 15f, 0f, 1, Color.WHITE)
                text("Tiba di", 94f, 60f, 16f, Color.WHITE)
                text(ell(s.street, 12f, 104f), 94f, 80f, 12f, Color.rgb(221, 235, 255))
            }
            else -> {
                drawTurn(64f, 63f, 15f, s.mAngle, s.mKind, Color.WHITE)
                val dc = when {
                    s.mDist < 60 -> Color.rgb(255, 120, 110)
                    s.mDist < 200 -> Color.rgb(255, 196, 100)
                    else -> Color.WHITE
                }
                text(fmtDist(s.mDist), 94f, 62f, 20f, dc)
                text(ell(s.street, 13f, 104f), 94f, 82f, 13f, Color.rgb(221, 235, 255))
                if (s.hasThen) {
                    p.style = Paint.Style.FILL
                    p.color = cBanner2
                    cv.drawRoundRect(72f, 96f, 168f, 118f, 11f, 11f, p)
                    text("Kemudian", 80f, 112f, 12f, Color.WHITE)
                    drawTurn(151f, 107f, 5.5f, s.thenAngle, s.thenKind, Color.WHITE, cBanner2)
                }
            }
        }
    }

    private fun drawBottom(s: NavState) {
        // lingkaran kecepatan
        p.style = Paint.Style.FILL
        p.color = cChip
        cv.drawCircle(52f, 176f, 21f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.color = Color.WHITE
        cv.drawCircle(52f, 176f, 21f, p)
        p.style = Paint.Style.FILL
        text("${s.speedKmh}", 52f, 180f, 17f, Color.WHITE, Paint.Align.CENTER)
        text("km/j", 52f, 192f, 8f, Color.rgb(150, 190, 230), Paint.Align.CENTER)

        // chip bawah: jam tiba + sisa jarak (atau jam sekarang bila tanpa rute)
        val now = System.currentTimeMillis()
        val label = if (s.route != null && s.mode != 3) {
            "Tiba ${timeFmt.format(Date(now + (s.remSec * 1000).toLong()))} · ${fmtDist(s.remDist)}"
        } else timeFmt.format(Date(now))
        tp.textSize = 13f
        val w = tp.measureText(label) + 20f
        val l = 128f - w / 2
        p.color = cChip
        cv.drawRoundRect(l, 196f, l + w, 218f, 11f, 11f, p)
        text(label, 128f, 212f, 13f, Color.WHITE, Paint.Align.CENTER)
    }

    /** Panah belok: ang = derajat (0 lurus, + kanan, - kiri), kind 1 = penanda tujuan. */
    private fun drawTurn(cx: Float, cy: Float, s: Float, ang: Float, kind: Int, color: Int, hole: Int = cBanner) {
        p.color = color
        if (kind == 1) {
            p.style = Paint.Style.FILL
            cv.drawCircle(cx, cy - s * 0.1f, s * 0.7f, p)
            p.color = hole
            cv.drawCircle(cx, cy - s * 0.1f, s * 0.28f, p)
            return
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.3f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        path.reset()
        val head = Path()
        if (abs(ang) > 160f) {                     // putar balik
            val dx = 0.45f * s
            path.moveTo(cx - dx, cy + s)
            path.lineTo(cx - dx, cy - 0.2f * s)
            path.cubicTo(cx - dx, cy - 1.0f * s, cx + dx, cy - 1.0f * s, cx + dx, cy - 0.2f * s)
            path.lineTo(cx + dx, cy + 0.3f * s)
            cv.drawPath(path, p)
            val tx = cx + dx; val ty = cy + 0.3f * s
            head.moveTo(tx, ty + 0.6f * s); head.lineTo(tx - 0.6f * s, ty); head.lineTo(tx + 0.6f * s, ty); head.close()
        } else {
            val a = Math.toRadians(ang.toDouble())
            val dX = sin(a).toFloat()
            val dY = -cos(a).toFloat()
            val ex = cx
            val ey = cy + 0.15f * s
            val len = if (abs(ang) < 20f) 1.1f * s else 0.95f * s
            val tx = ex + dX * len
            val ty = ey + dY * len
            path.moveTo(cx, cy + s)
            path.lineTo(ex, ey)
            path.lineTo(tx, ty)
            cv.drawPath(path, p)
            val ax = -dY; val ay = dX             // tegak lurus arah
            head.moveTo(tx + dX * 0.6f * s, ty + dY * 0.6f * s)
            head.lineTo(tx + ax * 0.6f * s, ty + ay * 0.6f * s)
            head.lineTo(tx - ax * 0.6f * s, ty - ay * 0.6f * s)
            head.close()
        }
        p.style = Paint.Style.FILL
        cv.drawPath(head, p)
    }
}
