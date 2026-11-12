package com.clashangprpr.app.model

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.clashangprpr.app.engine.Assets
import com.clashangprpr.app.engine.SpriteSheet
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** World-space text (scaled together with the camera zoom). */
object WText {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    fun draw(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int) {
        p.textSize = size
        p.color = Color.argb(Color.alpha(color), 0, 0, 0)
        c.drawText(s, x + 1.5f, y + 1.5f, p)
        p.color = color
        c.drawText(s, x, y, p)
    }
}

private object Tmp {
    val r = RectF()
    val src = Rect()
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
}

private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = ax - bx
    val dy = ay - by
    return sqrt(dx * dx + dy * dy)
}

// ------------------------------------------------------------------ projectiles / fx

class Projectile(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val damage: Int,
    val kind: Int,
    val friendly: Boolean
) {
    companion object {
        const val MAGIC = 0
        const val ORB = 1
        const val BOMB = 2
        const val LASER = 3
        const val ARROW = 4
    }

    var dead = false
    private var life = if (kind == BOMB) 0.75f else 1.9f
    val hitSet = HashSet<Any>()
    val pierce: Boolean get() = kind == LASER && friendly
    private var spin = 0f

    fun update(dt: Float) {
        life -= dt
        spin += dt * 12f
        if (life <= 0f) {
            dead = true
            return
        }
        x += vx * dt
        y += vy * dt
    }

    fun draw(c: Canvas) {
        if (dead) return
        val p = Tmp.p
        p.style = Paint.Style.FILL
        when (kind) {
            MAGIC -> {
                p.color = Color.argb(90, 0, 229, 255)
                c.drawCircle(x - vx * 0.03f, y - vy * 0.03f, 10f, p)
                p.color = Color.parseColor("#00E5FF")
                c.drawCircle(x, y, 13f, p)
                p.color = Color.WHITE
                c.drawCircle(x, y, 6f, p)
            }
            ORB -> {
                p.color = Color.argb(110, 233, 30, 99)
                c.drawCircle(x, y, 17f, p)
                p.color = Color.parseColor("#E91E63")
                c.drawCircle(x, y, 11f, p)
                p.color = Color.WHITE
                c.drawCircle(x, y, 4f, p)
            }
            LASER -> {
                val col = if (friendly) Color.parseColor("#76FF03") else Color.parseColor("#FF1744")
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = 11f
                p.color = Color.argb(110, Color.red(col), Color.green(col), Color.blue(col))
                c.drawLine(x - vx * 0.06f, y - vy * 0.06f, x, y, p)
                p.strokeWidth = 5f
                p.color = Color.WHITE
                c.drawLine(x - vx * 0.045f, y - vy * 0.045f, x, y, p)
                p.style = Paint.Style.FILL
            }
            ARROW -> {
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = 4f
                p.color = Color.parseColor("#C8A064")
                c.drawLine(x - vx * 0.05f, y - vy * 0.05f, x, y, p)
                p.color = Color.WHITE
                p.strokeWidth = 7f
                c.drawLine(x - vx * 0.008f, y - vy * 0.008f, x, y, p)
                p.style = Paint.Style.FILL
            }
            BOMB -> {
                val b = Assets.bmp("icons/bomb.png")
                if (b != null) {
                    c.save()
                    c.rotate(spin * 20f, x, y)
                    Tmp.r.set(x - 17f, y - 17f, x + 17f, y + 17f)
                    c.drawBitmap(b, null, Tmp.r, null)
                    c.restore()
                } else {
                    p.color = Color.BLACK
                    c.drawCircle(x, y, 14f, p)
                }
            }
        }
    }
}

class ExplosionFx(val x: Float, val y: Float, val radius: Float) {
    private var t = 0f
    private val life = 0.55f
    val done: Boolean get() = t >= life

    fun update(dt: Float) {
        t += dt
    }

    fun draw(c: Canvas) {
        val bm = Assets.filtered(
            "environment/bomb_fx.png", "orange",
            ColorMatrixColorFilter(floatArrayOf(0f, 0f, 1f, 0f, 0f, 0f, 0.62f, 0f, 0f, 0f, 0f, 0f, 0.05f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
        )
        val k = (t / life).coerceIn(0f, 1f)
        val p = Tmp.p
        p.style = Paint.Style.FILL
        p.color = Color.argb(((1f - k) * 110).toInt(), 255, 140, 30)
        c.drawCircle(x, y, radius * (0.4f + 0.6f * k), p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        p.color = Color.argb(((1f - k) * 255).toInt(), 255, 220, 120)
        c.drawCircle(x, y, radius * k, p)
        p.style = Paint.Style.FILL
        if (bm != null) {
            val f = min(11, 5 + (k * 7f).toInt())
            Tmp.src.set(f * 48, 0, f * 48 + 48, 48)
            val s = radius * 1.7f
            Tmp.r.set(x - s / 2f, y - s / 2f, x + s / 2f, y + s / 2f)
            c.drawBitmap(bm, Tmp.src, Tmp.r, null)
        }
    }
}

class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float, val color: Int, val size: Float)

class ParticleSystem {
    val list = ArrayList<Particle>()
    private val rnd = java.util.Random(5)

    fun burst(x: Float, y: Float, n: Int, color: Int, speed: Float, size: Float, life: Float) {
        if (list.size > 380) return
        for (i in 0 until n) {
            val a = rnd.nextFloat() * 6.2832f
            val s = speed * (0.35f + rnd.nextFloat() * 0.65f)
            list.add(Particle(x, y, cos(a) * s, sin(a) * s, life * (0.6f + rnd.nextFloat() * 0.4f), life, color, size))
        }
    }

    fun update(dt: Float) {
        var i = list.size - 1
        while (i >= 0) {
            val p = list[i]
            p.life -= dt
            if (p.life <= 0f) {
                list.removeAt(i)
            } else {
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.vx *= (1f - 3f * dt).coerceAtLeast(0f)
                p.vy *= (1f - 3f * dt).coerceAtLeast(0f)
            }
            i--
        }
    }

    fun draw(c: Canvas) {
        val pt = Tmp.p
        pt.style = Paint.Style.FILL
        for (p in list) {
            pt.color = p.color
            pt.alpha = (255 * (p.life / p.maxLife)).toInt().coerceIn(0, 255)
            c.drawRect(p.x - p.size / 2f, p.y - p.size / 2f, p.x + p.size / 2f, p.y + p.size / 2f, pt)
        }
    }
}

class FloatingText(val text: String, var x: Float, var y: Float, val color: Int, val size: Float = 26f) {
    var life = 1.1f
    val dead: Boolean get() = life <= 0f

    fun update(dt: Float) {
        life -= dt
        y -= 46f * dt
    }

    fun draw(c: Canvas) {
        val a = (255 * min(1f, life / 0.4f)).toInt().coerceIn(0, 255)
        WText.draw(c, text, x, y, size, Color.argb(a, Color.red(color), Color.green(color), Color.blue(color)))
    }
}

class CoinDrop(var x: Float, var y: Float, val value: Int) {
    var collected = false
    private var t = (x * 0.013f)
    private var vx = (Math.random().toFloat() - 0.5f) * 220f
    private var vy = -120f - Math.random().toFloat() * 80f
    private var popTime = 0.35f

    fun update(dt: Float, p: Player) {
        t += dt
        if (popTime > 0f) {
            popTime -= dt
            x += vx * dt
            y += vy * dt
            vy += 520f * dt
            return
        }
        val d = dist(p.cx, p.cy, x, y)
        if (d < 190f && !p.isDead) {
            val k = 420f * dt / max(1f, d)
            x += (p.cx - x) * k
            y += (p.cy - y) * k
        }
        if (d < 34f && !p.isDead) collected = true
    }

    fun draw(c: Canvas) {
        val b = Assets.bmp("icons/coin.png") ?: return
        val bob = sin(t * 6f) * 3f
        Tmp.r.set(x - 14f, y - 14f + bob, x + 14f, y + 14f + bob)
        c.drawBitmap(b, null, Tmp.r, null)
    }
}

// ------------------------------------------------------------------ interactables

class TreasureChest(var x: Float, var y: Float, val gold: Int, val isGem: Boolean = false) {
    var isOpened = false
    private val anim = SpriteSheet("environment/chest.png", 0.1f, false)
    val size = 64f

    fun open(): Int {
        if (isOpened) return 0
        isOpened = true
        anim.setFrame(anim.frameCount - 1)
        return gold
    }

    fun isNear(px: Float, py: Float): Boolean = !isOpened && dist(px, py, x + size / 2f, y + size / 2f) < 100f

    fun draw(c: Canvas, time: Float) {
        anim.draw(c, x, y, size, size)
        if (!isOpened) {
            val a = (120 + 100 * sin(time * 3f)).toInt().coerceIn(0, 255)
            Tmp.p.style = Paint.Style.FILL
            Tmp.p.color = Color.argb(a, 255, 215, 64)
            c.drawCircle(x + size - 8f, y + 10f, 4f, Tmp.p)
        }
    }
}

class MerchantNPC(var x: Float, var y: Float) {
    val size = 96f
    private val idle = SpriteSheet("sprites/merchant/idle.png", 0.22f)

    fun update(dt: Float) = idle.update(dt)

    fun isNear(px: Float, py: Float): Boolean = dist(px, py, x + size / 2f, y + size * 0.6f) < 130f

    fun draw(c: Canvas) {
        Tmp.p.style = Paint.Style.FILL
        Tmp.p.color = Color.argb(85, 0, 0, 0)
        Tmp.r.set(x + 22f, y + 76f, x + 74f, y + 92f)
        c.drawOval(Tmp.r, Tmp.p)
        idle.draw(c, x, y, size, size)
        WText.draw(c, "التاجر إلريك", x + size / 2f, y - 6f, 20f, Color.parseColor("#FFD54F"))
    }
}

class WorldProp(val path: String, var x: Float, var y: Float, val w: Float, val h: Float, dur: Float = 0.12f) {
    private val anim = SpriteSheet(path, dur)
    fun update(dt: Float) = anim.update(dt)
    fun draw(c: Canvas) = anim.draw(c, x, y, w, h)
}

class CraftTable(var x: Float, var y: Float) {
    val size = 64f
    fun isNear(px: Float, py: Float): Boolean = dist(px, py, x + size / 2f, y + size / 2f) < 110f

    fun draw(c: Canvas, time: Float) {
        val crate = Assets.tinted("tiles/crate.png", 0xFFD9A864.toInt())
        if (crate != null) {
            Tmp.r.set(x, y, x + size, y + size)
            c.drawBitmap(crate, null, Tmp.r, null)
        }
        val axe = Assets.tinted("ui/ico/axe.png", 0xFFFFE082.toInt())
        if (axe != null) {
            val bob = sin(time * 3f) * 3f
            Tmp.r.set(x + 16f, y - 26f + bob, x + 48f, y + 6f + bob)
            c.drawBitmap(axe, null, Tmp.r, null)
        }
        WText.draw(c, "طاولة الصناعة", x + size / 2f, y - 34f, 18f, Color.parseColor("#FFCC80"))
    }
}

class TimePortal(var x: Float, var y: Float) {
    var isActive = false
    private var t = 0f
    val size = 140f

    fun update(dt: Float) {
        t += dt
    }

    fun isNear(px: Float, py: Float): Boolean = isActive && dist(px, py, x + size / 2f, y + size / 2f) < 110f

    fun draw(c: Canvas) {
        val cx = x + size / 2f
        val cy = y + size / 2f
        val circle = Assets.bmp("environment/magic_circle.png")
        val p = Tmp.p
        p.style = Paint.Style.FILL
        if (isActive) {
            p.color = Color.argb((70 + 30 * sin(t * 4f)).toInt(), 0, 229, 255)
            c.drawCircle(cx, cy, size * 0.62f, p)
        }
        if (circle != null) {
            c.save()
            c.rotate(t * (if (isActive) 60f else 12f), cx, cy)
            Tmp.r.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
            p.alpha = if (isActive) 255 else 90
            c.drawBitmap(circle, null, Tmp.r, p)
            p.alpha = 255
            c.restore()
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        for (i in 0 until 3) {
            val rr = size * (0.18f + 0.11f * i) + sin(t * 3f + i) * 4f
            p.color = if (isActive) Color.argb(220, 130, 255, 255) else Color.argb(80, 160, 160, 170)
            c.drawCircle(cx, cy, rr, p)
        }
        p.style = Paint.Style.FILL
        WText.draw(c, if (isActive) "بوابة الزمن" else "بوابة الزمن (مغلقة)", cx, y - 8f, 20f,
            if (isActive) Color.parseColor("#80F0FF") else Color.GRAY)
    }
}

// ------------------------------------------------------------------ resource nodes

class MiningRock(var x: Float, var y: Float, val isIron: Boolean) {
    var hp = 4
    var isDestroyed = false
    val hitbox = RectF(x - 6f, y - 6f, x + 70f, y + 70f)
    var flash = 0f

    fun hit(): Boolean {
        if (isDestroyed) return false
        hp--
        flash = 0.12f
        if (hp <= 0) isDestroyed = true
        return true
    }

    fun update(dt: Float) {
        if (flash > 0f) flash -= dt
    }

    fun draw(c: Canvas, time: Float) {
        if (isDestroyed) return
        val bm = if (isIron) Assets.tinted("environment/rock_rgba.png", 0xFFB4CCFF.toInt(), 0x001030) else Assets.bmp("environment/rock_rgba.png")
        val off = if (flash > 0f) 3f else 0f
        if (bm != null) {
            Tmp.r.set(x + off, y, x + 64f + off, y + 64f)
            c.drawBitmap(bm, null, Tmp.r, null)
        }
        Tmp.p.style = Paint.Style.FILL
        val ore = if (isIron) Color.rgb(120, 220, 255) else Color.rgb(255, 214, 80)
        val tw = (sin(time * 5f + x) * 0.5f + 0.5f)
        Tmp.p.color = Color.argb((120 + 120 * tw).toInt(), Color.red(ore), Color.green(ore), Color.blue(ore))
        c.drawRect(x + 18f, y + 22f, x + 24f, y + 28f, Tmp.p)
        c.drawRect(x + 40f, y + 36f, x + 46f, y + 42f, Tmp.p)
        c.drawRect(x + 30f, y + 14f, x + 34f, y + 18f, Tmp.p)
    }
}

class WoodCrate(var x: Float, var y: Float, val isBarrel: Boolean) {
    var hp = 2
    var isDestroyed = false
    val hitbox = RectF(x - 4f, y - 4f, x + 68f, y + 68f)
    var flash = 0f

    fun hit(): Boolean {
        if (isDestroyed) return false
        hp--
        flash = 0.12f
        if (hp <= 0) isDestroyed = true
        return true
    }

    fun update(dt: Float) {
        if (flash > 0f) flash -= dt
    }

    fun draw(c: Canvas) {
        if (isDestroyed) return
        val bm = Assets.bmp(if (isBarrel) "tiles/barrel.png" else "tiles/crate.png") ?: return
        val off = if (flash > 0f) 3f else 0f
        Tmp.r.set(x + off, y, x + 64f + off, y + 64f)
        c.drawBitmap(bm, null, Tmp.r, null)
    }
}

class ChoppableTree(var x: Float, var y: Float, val isPine: Boolean) {
    var hp = 3
    var isDestroyed = false
    val hitbox = RectF(x - 6f, y - 6f, x + 70f, y + 70f)
    var flash = 0f

    fun hit(): Boolean {
        if (isDestroyed) return false
        hp--
        flash = 0.12f
        if (hp <= 0) isDestroyed = true
        return true
    }

    fun update(dt: Float) {
        if (flash > 0f) flash -= dt
    }

    fun draw(c: Canvas) {
        if (isDestroyed) return
        val bm = Assets.bmp(if (isPine) "tiles/tree_pine.png" else "tiles/tree_green.png") ?: return
        val off = if (flash > 0f) 3f else 0f
        Tmp.r.set(x - 8f + off, y - 40f, x + 72f + off, y + 62f)
        c.drawBitmap(bm, null, Tmp.r, null)
    }
}

class BuriedMound(var x: Float, var y: Float) {
    var isDug = false
    private var t = 0f

    fun update(dt: Float) {
        t += dt
    }

    fun isNear(px: Float, py: Float): Boolean = !isDug && dist(px, py, x + 32f, y + 32f) < 95f

    fun draw(c: Canvas) {
        if (isDug) return
        val p = Tmp.p
        p.style = Paint.Style.FILL
        p.color = Color.rgb(176, 140, 88)
        Tmp.r.set(x + 6f, y + 22f, x + 58f, y + 58f)
        c.drawOval(Tmp.r, p)
        p.color = Color.rgb(148, 112, 66)
        Tmp.r.set(x + 14f, y + 18f, x + 50f, y + 46f)
        c.drawOval(Tmp.r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        p.strokeCap = Paint.Cap.ROUND
        p.color = Color.argb((170 + 80 * sin(t * 4f)).toInt().coerceIn(0, 255), 255, 214, 64)
        c.drawLine(x + 22f, y + 24f, x + 42f, y + 44f, p)
        c.drawLine(x + 42f, y + 24f, x + 22f, y + 44f, p)
        p.style = Paint.Style.FILL
        WText.draw(c, "كنز مدفون", x + 32f, y + 8f, 18f, Color.parseColor("#FFE082"))
    }
}

class PowerGenerator(var x: Float, var y: Float, val index: Int) {
    var hp = 5
    var isDestroyed = false
    val hitbox = RectF(x - 6f, y - 6f, x + 90f, y + 90f)
    private var t = 0f
    var flash = 0f

    fun update(dt: Float) {
        t += dt * 5f
        if (flash > 0f) flash -= dt
    }

    fun hit(): Boolean {
        if (isDestroyed) return false
        hp--
        flash = 0.12f
        if (hp <= 0) isDestroyed = true
        return true
    }

    fun draw(c: Canvas) {
        val p = Tmp.p
        val w = 84f
        if (isDestroyed) {
            p.style = Paint.Style.FILL
            p.color = Color.rgb(34, 36, 42)
            Tmp.r.set(x + 8f, y + 34f, x + w - 8f, y + w - 6f)
            c.drawRoundRect(Tmp.r, 8f, 8f, p)
            return
        }
        Tmp.r.set(x, y, x + w, y + w)
        p.style = Paint.Style.FILL
        p.color = if (flash > 0f) Color.WHITE else Color.rgb(38, 50, 56)
        c.drawRoundRect(Tmp.r, 14f, 14f, p)
        p.color = Color.rgb(20, 28, 34)
        Tmp.r.set(x + 8f, y + 8f, x + w - 8f, y + w - 8f)
        c.drawRoundRect(Tmp.r, 10f, 10f, p)
        p.color = Color.argb((160 + 90 * sin(t)).toInt().coerceIn(0, 255), 0, 229, 255)
        c.drawCircle(x + w / 2f, y + w / 2f, 15f + sin(t) * 2f, p)
        p.color = Color.WHITE
        c.drawCircle(x + w / 2f, y + w / 2f, 6f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f + sin(t) * 1.5f
        p.color = Color.parseColor("#00E5FF")
        Tmp.r.set(x, y, x + w, y + w)
        c.drawRoundRect(Tmp.r, 14f, 14f, p)
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#76FF03")
        for (i in 0 until hp) c.drawCircle(x + 14f + i * 14f, y - 10f, 4f, p)
        WText.draw(c, "مولد طاقة $index", x + w / 2f, y + w + 20f, 16f, Color.WHITE)
    }
}

// ------------------------------------------------------------------ traps

class DungeonTrap(val baseX: Float, val baseY: Float, val isSaw: Boolean) {
    var x = baseX
    var y = baseY
    private var t = (baseX * 0.01f) % 6f
    private var dmgCd = 0f
    val hitbox = RectF()
    private val fire = SpriteSheet("environment/fire.png", 0.08f)

    val isFireActive: Boolean get() = !isSaw && (t % 3f) > 1.9f
    private val isWarning: Boolean get() = !isSaw && (t % 3f) in 1.4f..1.9f

    fun update(dt: Float, p: Player) {
        t += dt
        if (dmgCd > 0f) dmgCd -= dt
        if (isSaw) {
            x = baseX + sin(t * 1.7f) * 110f
            hitbox.set(x + 12f, y + 12f, x + 52f, y + 52f)
        } else {
            fire.update(dt)
            hitbox.set(x + 6f, y - 24f, x + 58f, y + 58f)
        }
        val active = isSaw || isFireActive
        if (active && dmgCd <= 0f && !p.isDead && RectF.intersects(hitbox, p.hitbox)) {
            p.takeDamage(22)
            dmgCd = 0.8f
        }
    }

    fun draw(c: Canvas) {
        val p = Tmp.p
        if (isSaw) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 6f
            p.color = Color.argb(110, 20, 20, 30)
            c.drawLine(baseX - 110f, baseY + 32f, baseX + 174f, baseY + 32f, p)
            p.style = Paint.Style.FILL
            val bm = Assets.bmp("environment/blades.png") ?: return
            val f = ((t * 14f).toInt() % 4)
            Tmp.src.set(0, f * 48, 48, f * 48 + 48)
            Tmp.r.set(x, y, x + 64f, y + 64f)
            c.drawBitmap(bm, Tmp.src, Tmp.r, null)
        } else {
            p.style = Paint.Style.FILL
            p.color = Color.rgb(40, 40, 52)
            Tmp.r.set(x + 8f, y + 24f, x + 56f, y + 58f)
            c.drawRoundRect(Tmp.r, 8f, 8f, p)
            p.color = if (isWarning) Color.argb(220, 255, 90, 30) else Color.argb(140, 120, 30, 20)
            Tmp.r.set(x + 18f, y + 32f, x + 46f, y + 50f)
            c.drawRoundRect(Tmp.r, 6f, 6f, p)
            if (isFireActive) fire.draw(c, x - 4f, y - 40f, 72f, 96f)
        }
    }
}
