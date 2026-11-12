package com.clashangprpr.app.engine

import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.atan2

/** Floating joystick: appears wherever the thumb lands inside the left zone. */
class VirtualJoystick {
    var homeX = 0f
    var homeY = 0f
    var radius = 80f
    var zoneRight = 0f
    var zoneTop = 0f

    var active = false
        private set
    var pointerId = -1
        private set
    var dx = 0f
        private set
    var dy = 0f
        private set

    private var baseX = 0f
    private var baseY = 0f
    private var curX = 0f
    private var curY = 0f

    fun layout(w: Float, h: Float, u: Float) {
        radius = 78f * u
        homeX = 150f * u
        homeY = h - 150f * u
        zoneRight = w * 0.45f
        zoneTop = h * 0.22f
        if (!active) {
            baseX = homeX
            baseY = homeY
            curX = homeX
            curY = homeY
        }
    }

    fun onDown(id: Int, x: Float, y: Float): Boolean {
        if (active) return false
        if (x > zoneRight || y < zoneTop) return false
        active = true
        pointerId = id
        baseX = x
        baseY = y
        curX = x
        curY = y
        dx = 0f
        dy = 0f
        return true
    }

    fun onMove(id: Int, x: Float, y: Float) {
        if (!active || id != pointerId) return
        val ox = x - baseX
        val oy = y - baseY
        val dist = sqrt(ox * ox + oy * oy)
        if (dist < 0.001f) {
            dx = 0f
            dy = 0f
            curX = baseX
            curY = baseY
            return
        }
        val a = atan2(oy, ox)
        val d = min(dist, radius)
        curX = baseX + cos(a) * d
        curY = baseY + sin(a) * d
        val mag = d / radius
        if (mag < 0.16f) {
            dx = 0f
            dy = 0f
        } else {
            dx = cos(a) * mag
            dy = sin(a) * mag
        }
    }

    fun onUp(id: Int) {
        if (active && id == pointerId) cancel()
    }

    fun cancel() {
        active = false
        pointerId = -1
        dx = 0f
        dy = 0f
        baseX = homeX
        baseY = homeY
        curX = homeX
        curY = homeY
    }

    fun draw(c: Canvas) {
        val u = Ui.u
        val a = if (active) 1f else 0.55f
        Ui.circle(c, baseX, baseY, radius, Color.argb((70 * a).toInt(), 255, 255, 255))
        Ui.ring(c, baseX, baseY, radius, Color.argb((200 * a).toInt(), 255, 215, 0), 4f * u)
        Ui.ring(c, baseX, baseY, radius * 0.35f, Color.argb((90 * a).toInt(), 255, 255, 255), 2f * u)
        Ui.circle(c, curX, curY, radius * 0.42f, Color.argb((220 * a).toInt(), 255, 205, 40))
        Ui.ring(c, curX, curY, radius * 0.42f, Color.argb((255 * a).toInt(), 120, 80, 0), 3f * u)
    }
}
