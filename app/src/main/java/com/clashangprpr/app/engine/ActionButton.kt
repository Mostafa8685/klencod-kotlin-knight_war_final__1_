package com.clashangprpr.app.engine

import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.sqrt

/** Round on-screen button with an icon, tiny label, cooldown sweep and count badge. */
class ActionButton(
    var iconPath: String,
    var label: String,
    var accent: Int,
    var tintIcon: Int? = null
) {
    var cx = 0f
    var cy = 0f
    var r = 40f
    var visible = true
    var enabled = true
    /** remaining fraction 0..1 of the cooldown (0 = ready) */
    var cooldown = 0f
    var badge: String? = null
    var isPressed = false
        private set
    var pointerId = -1
        private set

    private fun inside(x: Float, y: Float): Boolean {
        val dx = x - cx
        val dy = y - cy
        return sqrt(dx * dx + dy * dy) <= r * 1.18f
    }

    fun onDown(id: Int, x: Float, y: Float): Boolean {
        if (!visible || isPressed) return false
        if (inside(x, y)) {
            isPressed = true
            pointerId = id
            return true
        }
        return false
    }

    fun onUp(id: Int) {
        if (isPressed && pointerId == id) {
            isPressed = false
            pointerId = -1
        }
    }

    fun cancel() {
        isPressed = false
        pointerId = -1
    }

    fun draw(c: Canvas) {
        if (!visible) return
        val u = Ui.u
        val rr = if (isPressed) r * 0.92f else r
        val dim = if (enabled) 255 else 120
        Ui.circle(c, cx + 2f * u, cy + 4f * u, rr, Color.argb(90, 0, 0, 0))
        Ui.circle(c, cx, cy, rr, Color.argb(205, 14, 18, 32))
        Ui.circle(c, cx, cy, rr * 0.86f, Ui.withAlpha(accent, if (isPressed) 120 else 55))
        Ui.ring(c, cx, cy, rr - 2f * u, Ui.withAlpha(accent, dim), 4f * u)
        val iconSize = rr * 1.05f / u
        Ui.icon(c, iconPath, cx, cy - rr * 0.10f, iconSize, tintIcon, dim)
        if (label.isNotEmpty()) {
            Ui.text(c, label, cx, cy + rr * 0.72f, 11.5f, Color.argb(dim, 255, 255, 255))
        }
        if (cooldown > 0.01f) {
            Ui.pie(c, cx, cy, rr - 3f * u, 360f * cooldown.coerceIn(0f, 1f), Color.argb(165, 0, 0, 0))
        }
        val b = badge
        if (b != null) {
            val bx = cx + rr * 0.72f
            val by = cy - rr * 0.72f
            Ui.circle(c, bx, by, 13f * u, Color.argb(240, 10, 12, 22))
            Ui.ring(c, bx, by, 13f * u, accent, 2f * u)
            Ui.text(c, b, bx, by + 5f * u, 13f, Color.WHITE)
        }
    }
}
