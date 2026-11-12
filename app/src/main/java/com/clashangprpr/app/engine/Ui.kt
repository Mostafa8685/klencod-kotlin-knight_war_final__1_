package com.clashangprpr.app.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.min

/**
 * Tiny immediate-mode UI toolkit.
 * Convention: positions / rects are in SCREEN PIXELS, font and icon sizes are in "ui units"
 * (1 unit = screenShortSide / 720 px) so everything scales with the device.
 */
object Ui {
    var u = 1f
    var w = 1280f
    var h = 720f

    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val iconP = Paint()
    private val tmp = RectF()
    private val filters = HashMap<Int, PorterDuffColorFilter>()

    private val layoutCache = object : LinkedHashMap<String, StaticLayout>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StaticLayout>?): Boolean {
            return size > 90
        }
    }

    fun init(width: Float, height: Float) {
        w = width
        h = height
        u = min(width, height) / 720f
    }

    fun darken(col: Int, f: Float): Int {
        return Color.argb(
            Color.alpha(col),
            (Color.red(col) * f).toInt().coerceIn(0, 255),
            (Color.green(col) * f).toInt().coerceIn(0, 255),
            (Color.blue(col) * f).toInt().coerceIn(0, 255)
        )
    }

    fun withAlpha(col: Int, a: Int): Int = Color.argb(a.coerceIn(0, 255), Color.red(col), Color.green(col), Color.blue(col))

    private fun filterFor(col: Int): PorterDuffColorFilter {
        var f = filters[col]
        if (f == null) {
            f = PorterDuffColorFilter(col, PorterDuff.Mode.SRC_IN)
            filters[col] = f
        }
        return f
    }

    fun measure(s: String, size: Float, bold: Boolean = true): Float {
        textP.textSize = size * u
        textP.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        return textP.measureText(s)
    }

    /** Single line text. x,y in px (y = baseline), size in ui units. */
    fun text(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
        align: Paint.Align = Paint.Align.CENTER, bold: Boolean = true, shadow: Boolean = true
    ) {
        textP.textSize = size * u
        textP.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textP.textAlign = align
        if (shadow) {
            textP.color = Color.argb(210, 0, 0, 0)
            c.drawText(s, x + 1.6f * u, y + 1.6f * u, textP)
        }
        textP.color = color
        c.drawText(s, x, y, textP)
    }

    /** Wrapped paragraph (RTL aware). x,y = top-left px, width px. Returns nothing; use for descriptions. */
    fun wrapped(
        c: Canvas, s: String, x: Float, y: Float, width: Float, size: Float, color: Int,
        bold: Boolean = false, maxLines: Int = 6, center: Boolean = false, cache: Boolean = true
    ) {
        val wi = width.toInt().coerceAtLeast(1)
        val key = "$s|$size|$wi|$color|$bold|$maxLines|$center"
        var lay = if (cache) layoutCache[key] else null
        if (lay == null) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
            tp.textSize = size * u
            tp.color = color
            tp.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            lay = StaticLayout.Builder.obtain(s, 0, s.length, tp, wi)
                .setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setLineSpacing(0f, 1.12f)
                .build()
            if (cache) layoutCache[key] = lay
        }
        c.save()
        c.translate(x, y)
        lay!!.draw(c)
        c.restore()
    }

    fun panel(c: Canvas, r: RectF, accent: Int, bg: Int = Color.argb(240, 16, 21, 36), radius: Float = 14f) {
        val rr = radius * u
        fillP.style = Paint.Style.FILL
        fillP.color = Color.argb(90, 0, 0, 0)
        tmp.set(r.left + 3f * u, r.top + 6f * u, r.right + 3f * u, r.bottom + 6f * u)
        c.drawRoundRect(tmp, rr, rr, fillP)
        fillP.color = bg
        c.drawRoundRect(r, rr, rr, fillP)
        fillP.color = Color.argb(26, 255, 255, 255)
        tmp.set(r.left + 4f * u, r.top + 4f * u, r.right - 4f * u, r.top + r.height() * 0.42f)
        c.drawRoundRect(tmp, rr * 0.8f, rr * 0.8f, fillP)
        strokeP.color = accent
        strokeP.strokeWidth = 3f * u
        c.drawRoundRect(r, rr, rr, strokeP)
        strokeP.color = Color.argb(130, 0, 0, 0)
        strokeP.strokeWidth = 1.5f * u
        tmp.set(r.left + 4f * u, r.top + 4f * u, r.right - 4f * u, r.bottom - 4f * u)
        c.drawRoundRect(tmp, rr * 0.8f, rr * 0.8f, strokeP)
    }

    fun button(
        c: Canvas, r: RectF, label: String, color: Int, enabled: Boolean = true,
        size: Float = 20f, pressed: Boolean = false
    ) {
        val rr = 10f * u
        val base = if (enabled) color else Color.rgb(72, 76, 90)
        val dy = if (pressed) 3f * u else 0f
        fillP.style = Paint.Style.FILL
        fillP.color = darken(base, 0.5f)
        tmp.set(r.left, r.top + 4f * u, r.right, r.bottom + 4f * u)
        c.drawRoundRect(tmp, rr, rr, fillP)
        fillP.color = base
        tmp.set(r.left, r.top + dy, r.right, r.bottom + dy)
        c.drawRoundRect(tmp, rr, rr, fillP)
        fillP.color = Color.argb(55, 255, 255, 255)
        tmp.set(r.left + 3f * u, r.top + dy + 3f * u, r.right - 3f * u, r.top + dy + r.height() * 0.45f)
        c.drawRoundRect(tmp, rr * 0.8f, rr * 0.8f, fillP)
        strokeP.color = darken(base, 0.35f)
        strokeP.strokeWidth = 2f * u
        tmp.set(r.left, r.top + dy, r.right, r.bottom + dy)
        c.drawRoundRect(tmp, rr, rr, strokeP)
        text(c, label, r.centerX(), r.centerY() + dy + size * u * 0.35f, size, if (enabled) Color.WHITE else Color.rgb(170, 175, 190))
    }

    fun bar(c: Canvas, r: RectF, frac: Float, fill: Int, bg: Int = Color.argb(200, 8, 10, 18)) {
        val rr = r.height() / 2f
        fillP.style = Paint.Style.FILL
        fillP.color = bg
        c.drawRoundRect(r, rr, rr, fillP)
        val f = frac.coerceIn(0f, 1f)
        if (f > 0.01f) {
            tmp.set(r.left + 2f * u, r.top + 2f * u, r.left + 2f * u + (r.width() - 4f * u) * f, r.bottom - 2f * u)
            if (tmp.width() > 0f) {
                fillP.color = fill
                c.drawRoundRect(tmp, rr, rr, fillP)
                fillP.color = Color.argb(70, 255, 255, 255)
                tmp.set(tmp.left, tmp.top, tmp.right, tmp.top + tmp.height() * 0.4f)
                c.drawRoundRect(tmp, rr, rr, fillP)
            }
        }
        strokeP.color = Color.argb(220, 0, 0, 0)
        strokeP.strokeWidth = 2f * u
        c.drawRoundRect(r, rr, rr, strokeP)
    }

    fun icon(c: Canvas, path: String, cx: Float, cy: Float, size: Float, tint: Int? = null, alpha: Int = 255) {
        val b = Assets.bmp(path) ?: return
        val s = size * u
        iconP.alpha = alpha
        iconP.colorFilter = if (tint != null) filterFor(tint) else null
        tmp.set(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f)
        c.drawBitmap(b, null, tmp, iconP)
    }

    fun fillRound(c: Canvas, r: RectF, color: Int, radius: Float = 10f) {
        fillP.style = Paint.Style.FILL
        fillP.color = color
        c.drawRoundRect(r, radius * u, radius * u, fillP)
    }

    fun strokeRound(c: Canvas, r: RectF, color: Int, width: Float = 2f, radius: Float = 10f) {
        strokeP.color = color
        strokeP.strokeWidth = width * u
        c.drawRoundRect(r, radius * u, radius * u, strokeP)
    }

    fun fillRect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        fillP.style = Paint.Style.FILL
        fillP.color = color
        c.drawRect(l, t, r, b, fillP)
    }

    fun circle(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        fillP.style = Paint.Style.FILL
        fillP.color = color
        c.drawCircle(cx, cy, r, fillP)
    }

    fun ring(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, width: Float) {
        strokeP.color = color
        strokeP.strokeWidth = width
        c.drawCircle(cx, cy, r, strokeP)
    }

    fun pie(c: Canvas, cx: Float, cy: Float, r: Float, sweep: Float, color: Int) {
        fillP.style = Paint.Style.FILL
        fillP.color = color
        tmp.set(cx - r, cy - r, cx + r, cy + r)
        c.drawArc(tmp, -90f, sweep, true, fillP)
    }
}
