package com.clashangprpr.app.model

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.RectF
import com.clashangprpr.app.engine.Assets
import com.clashangprpr.app.engine.SaveStore
import com.clashangprpr.app.engine.SpriteSheet
import com.clashangprpr.app.world.Body
import com.clashangprpr.app.world.TileMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private object Fx {
    val white = Paint().apply { colorFilter = LightingColorFilter(0x000000, 0xFFFFFF) }
    val red = Paint().apply { colorFilter = LightingColorFilter(0xFFFFFF, 0x703030) }
    val blue = Paint().apply { colorFilter = LightingColorFilter(0xB0C8FF, 0x101840) }
    val ghost = Paint().apply { alpha = 120 }
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(85, 0, 0, 0) }
    val slash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 8f; strokeCap = Paint.Cap.ROUND }
    val bar = Paint()
    val oval = RectF()
}

class Player(override var x: Float, override var y: Float) : Body {
    companion object {
        const val DASH_CD = 0.9f
        const val SLOW_CD = 9f
        const val SLOW_DUR = 3.5f
        const val MAGIC_CD = 0.45f
        const val BOMB_CD = 0.6f
        const val MAGIC_COST = 25f
        const val SLOW_COST = 30f
    }

    val width = 96f
    val height = 96f
    override val fOffX = 33f
    override val fOffY = 66f
    override val fW = 30f
    override val fH = 22f

    var hp = 180
    var maxHp = 180
    var mana = 100f
    var maxMana = 100
    var coins = 70
    var wood = 6
    var stone = 6
    var iron = 5
    var bombs = 2
    var potions = 3
    var hasBossKey = false
    var hasPickaxe = false
    var hasOceanGem = false
    var hasPlasma = false
    val owned = HashSet<String>()
    var weapon: Weapon = GameCatalogue.WEAPON_BASIC
    var armorId = ""
    var upgHp = 0
    var upgMana = 0

    var aimX = 1f
    var aimY = 0f
    var facingLeft = false
    var moving = false
    var isDead = false
    var isAttacking = false
    var swingTime = 0f
    var attackCd = 0f
    val swingHit = HashSet<Any>()
    var isDashing = false
    var dashTimer = 0f
    var dashCd = 0f
    var slowTimer = 0f
    var slowCd = 0f
    var magicCd = 0f
    var bombCd = 0f
    var invincible = 0f
    var hurtTimer = 0f

    private val idleA = SpriteSheet("sprites/player/idle.png", 0.2f)
    private val walkA = SpriteSheet("sprites/player/walk.png", 0.11f)
    private val attackA = SpriteSheet("sprites/player/attack.png", 0.07f, false)
    private val deadA = SpriteSheet("sprites/player/dead.png", 0.25f, false)

    val cx: Float get() = x + width / 2f
    val cy: Float get() = y + 60f
    val hitbox = RectF()

    init {
        owned.add("sword_basic")
        recomputeStats()
        hp = maxHp
    }

    fun armorReduction(): Float = GameCatalogue.armorById(armorId)?.reduction ?: 0f

    fun recomputeStats() {
        val bonus = GameCatalogue.armorById(armorId)?.hpBonus ?: 0
        maxHp = 180 + bonus + upgHp * 30
        maxMana = 100 + upgMana * 25
        hp = min(hp, maxHp)
        mana = min(mana, maxMana.toFloat())
    }

    fun equipBest() {
        var best = GameCatalogue.WEAPON_BASIC
        for (w in GameCatalogue.weapons) if (owned.contains(w.id) && w.damage > best.damage) best = w
        weapon = best
    }

    fun resetState() {
        isDead = false
        isAttacking = false
        isDashing = false
        slowTimer = 0f
        slowCd = 0f
        dashCd = 0f
        attackCd = 0f
        magicCd = 0f
        bombCd = 0f
        hurtTimer = 0f
        invincible = 1.5f
        hasBossKey = false
        hasOceanGem = false
        refillAll()
    }

    fun refillAll() {
        hp = maxHp
        mana = maxMana.toFloat()
    }

    val hasGodblade: Boolean get() = owned.contains("godblade")

    fun update(dt: Float, mx: Float, my: Float, world: TileMap) {
        if (isDead) {
            deadA.update(dt)
            return
        }
        if (attackCd > 0f) attackCd -= dt
        if (dashCd > 0f) dashCd -= dt
        if (slowCd > 0f) slowCd -= dt
        if (magicCd > 0f) magicCd -= dt
        if (bombCd > 0f) bombCd -= dt
        if (invincible > 0f) invincible -= dt
        if (hurtTimer > 0f) hurtTimer -= dt
        if (slowTimer > 0f) slowTimer -= dt
        mana = min(maxMana.toFloat(), mana + 14f * dt)

        val len = sqrt(mx * mx + my * my)
        moving = len > 0.1f
        if (moving) {
            aimX = mx / len
            aimY = my / len
            if (abs(mx) > 0.2f) facingLeft = mx < 0f
        }
        if (isDashing) {
            dashTimer -= dt
            world.move(this, aimX * 640f * dt, aimY * 640f * dt)
            if (dashTimer <= 0f) isDashing = false
        } else if (moving) {
            val spd = 250f * (if (isAttacking) 0.6f else 1f)
            world.move(this, mx * spd * dt, my * spd * dt)
        }
        if (isAttacking) {
            swingTime += dt
            attackA.update(dt)
            if (swingTime >= 0.30f) isAttacking = false
        }
        idleA.update(dt)
        walkA.update(dt)
        hitbox.set(x + 28f, y + 26f, x + 68f, y + 88f)
    }

    val inHitWindow: Boolean get() = isAttacking && swingTime in 0.06f..0.22f

    fun attackRect(out: RectF) {
        val r = weapon.reach
        val ax = cx + aimX * r * 0.60f
        val ay = cy + aimY * r * 0.60f
        val half = r * 0.62f
        out.set(ax - half, ay - half, ax + half, ay + half)
    }

    fun attack(): Boolean {
        if (isDead || isAttacking || attackCd > 0f || isDashing) return false
        isAttacking = true
        swingTime = 0f
        attackCd = weapon.cooldown
        swingHit.clear()
        attackA.reset()
        return true
    }

    fun dash(): Boolean {
        if (isDead || isDashing || dashCd > 0f) return false
        isDashing = true
        dashTimer = 0.2f
        dashCd = DASH_CD
        invincible = max(invincible, 0.32f)
        return true
    }

    fun slowmo(): Boolean {
        if (isDead || slowCd > 0f || slowTimer > 0f || mana < SLOW_COST) return false
        mana -= SLOW_COST
        slowTimer = SLOW_DUR
        slowCd = SLOW_CD
        return true
    }

    fun magic(): Projectile? {
        if (isDead || magicCd > 0f || mana < MAGIC_COST) return null
        mana -= MAGIC_COST
        magicCd = MAGIC_CD
        val god = hasGodblade
        val dmg = if (god) 160 else if (hasPlasma) 120 else 75
        val kind = if (god || hasPlasma) Projectile.LASER else Projectile.MAGIC
        return Projectile(cx + aimX * 30f, cy + aimY * 30f, aimX * 640f, aimY * 640f, dmg, kind, true)
    }

    fun throwBomb(): Projectile? {
        if (isDead || bombs <= 0 || bombCd > 0f) return null
        bombs--
        bombCd = BOMB_CD
        return Projectile(cx + aimX * 30f, cy + aimY * 30f, aimX * 430f, aimY * 430f, 120, Projectile.BOMB, true)
    }

    fun usePotion(): Boolean {
        if (isDead || potions <= 0 || hp >= maxHp) return false
        potions--
        hp = min(maxHp, hp + 85)
        return true
    }

    fun takeDamage(amount: Int): Boolean {
        if (isDead || invincible > 0f) return false
        val dmg = max(1, (amount * (1f - armorReduction())).toInt())
        hp -= dmg
        invincible = 0.5f
        hurtTimer = 0.22f
        if (hp <= 0) {
            hp = 0
            isDead = true
            deadA.reset()
        }
        return true
    }

    fun cooldownFrac(cd: Float, total: Float): Float = if (cd > 0f) (cd / total).coerceIn(0f, 1f) else 0f

    fun draw(c: Canvas, time: Float) {
        Fx.oval.set(x + 26f, y + 76f, x + 70f, y + 92f)
        c.drawOval(Fx.oval, Fx.shadow)
        var paint: Paint? = null
        if (hurtTimer > 0f) paint = Fx.white
        else if (isDashing) paint = Fx.ghost
        else if (slowTimer > 0f) paint = Fx.blue
        else if (invincible > 0f && ((time * 18f).toInt() % 2 == 0)) paint = Fx.ghost
        val sheet = when {
            isDead -> deadA
            isAttacking -> attackA
            moving || isDashing -> walkA
            else -> idleA
        }
        sheet.draw(c, x, y, width, height, facingLeft, paint)
        if (isAttacking && !isDead) {
            val p = (swingTime / 0.30f).coerceIn(0f, 1f)
            val ang = Math.toDegrees(atan2(aimY.toDouble(), aimX.toDouble())).toFloat()
            val rad = weapon.reach * 0.82f
            Fx.oval.set(cx - rad, cy - rad, cx + rad, cy + rad)
            Fx.slash.color = weapon.color
            Fx.slash.alpha = (230 * (1f - p)).toInt().coerceIn(0, 255)
            c.drawArc(Fx.oval, ang - 70f, 140f * min(1f, p * 2.2f), false, Fx.slash)
        }
    }

    // -------------------------------------------------------- persistence
    fun save(eraIndex: Int) {
        val m = HashMap<String, Any>()
        m["era"] = eraIndex
        m["coins"] = coins
        m["wood"] = wood
        m["stone"] = stone
        m["iron"] = iron
        m["bombs"] = bombs
        m["potions"] = potions
        m["owned"] = owned.joinToString(",")
        m["armor"] = armorId
        m["upgHp"] = upgHp
        m["upgMana"] = upgMana
        m["pickaxe"] = hasPickaxe
        m["plasma"] = hasPlasma
        SaveStore.putAll(m)
    }

    /** Loads inventory from the last checkpoint and returns the era index. */
    fun load(): Int {
        coins = SaveStore.int("coins", 70)
        wood = SaveStore.int("wood", 6)
        stone = SaveStore.int("stone", 6)
        iron = SaveStore.int("iron", 5)
        bombs = SaveStore.int("bombs", 2)
        potions = SaveStore.int("potions", 3)
        owned.clear()
        owned.add("sword_basic")
        for (s in SaveStore.str("owned", "sword_basic").split(",")) if (s.isNotEmpty()) owned.add(s)
        armorId = SaveStore.str("armor", "")
        upgHp = SaveStore.int("upgHp", 0)
        upgMana = SaveStore.int("upgMana", 0)
        hasPickaxe = SaveStore.bool("pickaxe", false)
        hasPlasma = SaveStore.bool("plasma", false)
        hasBossKey = false
        hasOceanGem = false
        equipBest()
        recomputeStats()
        refillAll()
        isDead = false
        return SaveStore.int("era", 0)
    }
}

enum class EnemyRole { MELEE, SPEARMAN, ARCHER, HEAVY, GHOUL, TRIBAL, COMMANDO, CLONE, BOSS }

class Enemy(
    override var x: Float,
    override var y: Float,
    val role: EnemyRole,
    val era: EraType,
    val holdsKey: Boolean = false
) : Body {
    val isBoss = role == EnemyRole.BOSS
    private val eraMul = 1f + 0.12f * era.ordinal

    val size: Float = when (role) {
        EnemyRole.BOSS -> 128f + 8f * era.ordinal
        EnemyRole.HEAVY -> 100f
        EnemyRole.CLONE -> 92f
        EnemyRole.COMMANDO -> 90f
        else -> 84f
    }
    val maxHp: Int = (when (role) {
        EnemyRole.MELEE -> 85
        EnemyRole.SPEARMAN -> 90
        EnemyRole.ARCHER -> 60
        EnemyRole.HEAVY -> 220
        EnemyRole.GHOUL -> 70
        EnemyRole.TRIBAL -> 95
        EnemyRole.COMMANDO -> 85
        EnemyRole.CLONE -> 110
        EnemyRole.BOSS -> 650 + 160 * era.ordinal
    } * (if (isBoss) 1f else eraMul)).toInt()
    var hp = maxHp
    val damage: Int = (when (role) {
        EnemyRole.MELEE -> 16
        EnemyRole.SPEARMAN -> 15
        EnemyRole.ARCHER -> 12
        EnemyRole.HEAVY -> 24
        EnemyRole.GHOUL -> 14
        EnemyRole.TRIBAL -> 17
        EnemyRole.COMMANDO -> 13
        EnemyRole.CLONE -> 20
        EnemyRole.BOSS -> 26 + 3 * era.ordinal
    } * (if (isBoss) 1f else (1f + 0.06f * era.ordinal))).toInt()
    val speed: Float = when (role) {
        EnemyRole.MELEE -> 108f
        EnemyRole.SPEARMAN -> 100f
        EnemyRole.ARCHER -> 90f
        EnemyRole.HEAVY -> 74f
        EnemyRole.GHOUL -> 150f
        EnemyRole.TRIBAL -> 118f
        EnemyRole.COMMANDO -> 92f
        EnemyRole.CLONE -> 132f
        EnemyRole.BOSS -> 98f
    }
    val reach: Float = when (role) {
        EnemyRole.SPEARMAN -> 104f
        EnemyRole.BOSS -> 112f
        EnemyRole.HEAVY -> 86f
        else -> 74f
    }
    val ranged = role == EnemyRole.ARCHER || role == EnemyRole.COMMANDO
    val reward: Int = if (isBoss) 220 else (18 + 3 * era.ordinal + (if (role == EnemyRole.HEAVY) 14 else 0))

    override val fOffX = size * 0.34f
    override val fOffY = size * 0.68f
    override val fW = size * 0.32f
    override val fH = size * 0.22f

    val homeX = x
    val homeY = y
    var aggro = false
    var isDead = false
    var deadTimer = 0f
    var facingLeft = true
    var moving = false
    var hurtTimer = 0f
    var windup = 0f
    private var cooldown = 0.4f
    private var shootTimer = 1.5f
    private var volleyTimer = 3f
    var summonTimer = 9f
    var wantSummon = false
    private var kx = 0f
    private var ky = 0f
    private var stuck = 0f
    private var sideSign = 1f
    var shielded = false
    var lastKnownHp = hp

    val phase2: Boolean get() = isBoss && hp < maxHp / 2

    val cx: Float get() = x + size / 2f
    val cy: Float get() = y + size * 0.7f
    val hitbox = RectF()

    private val folder: String
    private val tintPaint: Paint?
    private val idleA: SpriteSheet
    private val walkA: SpriteSheet
    private val attackA: SpriteSheet
    private val deadA: SpriteSheet

    init {
        var f = "kit7"
        var tint = 0
        when (role) {
            EnemyRole.BOSS -> when (era) {
                EraType.ERA_1_MEDIEVAL -> f = "kit2"
                EraType.ERA_2_CAVES -> { f = "kit1"; tint = 0xFFC09070.toInt() }
                EraType.ERA_3_ZOMBIE -> f = "zombie_titan"
                EraType.ERA_4_ISLAND -> f = "pirate_captain"
                EraType.ERA_5_CYBER -> f = "cyber_boss"
                EraType.ERA_6_VOID -> { f = "kit2"; tint = 0xFFD070FF.toInt() }
            }
            EnemyRole.MELEE -> f = when (era) {
                EraType.ERA_3_ZOMBIE -> "zombie"
                EraType.ERA_4_ISLAND -> "tribal_warrior"
                else -> "kit7"
            }
            EnemyRole.SPEARMAN -> {
                f = if (era == EraType.ERA_4_ISLAND) "island_spearman" else "kit5"
                if (era == EraType.ERA_2_CAVES) tint = 0xFF9AAAAA.toInt()
            }
            EnemyRole.ARCHER -> f = "archer"
            EnemyRole.HEAVY -> {
                f = when (era) {
                    EraType.ERA_5_CYBER -> "cyber_heavy"
                    EraType.ERA_1_MEDIEVAL -> "kit8"
                    else -> "kit9"
                }
                if (era == EraType.ERA_2_CAVES) tint = 0xFFAA9988.toInt()
            }
            EnemyRole.GHOUL -> {
                f = "zombie_ghoul"
                if (era == EraType.ERA_2_CAVES) tint = 0xFF99BBAA.toInt()
            }
            EnemyRole.TRIBAL -> f = "tribal_warrior"
            EnemyRole.COMMANDO -> {
                f = "cyber_commando"
                if (era == EraType.ERA_6_VOID) tint = 0xFFD090FF.toInt()
            }
            EnemyRole.CLONE -> { f = "kit2"; tint = 0xFFC070FF.toInt() }
        }
        folder = f
        tintPaint = if (tint != 0) Paint().apply { colorFilter = LightingColorFilter(tint, 0) } else null
        idleA = SpriteSheet(Assets.firstExisting("sprites/$f/idle.png") ?: "sprites/$f/idle.png", 0.2f)
        walkA = SpriteSheet(Assets.firstExisting("sprites/$f/walk.png", "sprites/$f/walk+shot.png", "sprites/$f/idle.png") ?: "sprites/$f/walk.png", 0.12f)
        attackA = SpriteSheet(
            Assets.firstExisting("sprites/$f/attack.png", "sprites/$f/attack_1.png", "sprites/$f/shot.png", "sprites/$f/walk.png") ?: "sprites/$f/attack.png",
            0.09f, false
        )
        deadA = SpriteSheet(Assets.firstExisting("sprites/$f/dead.png", "sprites/$f/idle.png") ?: "sprites/$f/dead.png", 0.2f, false)
    }

    /** returns damage actually dealt (0 when shielded) */
    fun takeHit(dmg: Int, fromX: Float, fromY: Float, knock: Float = 1f): Int {
        if (isDead) return 0
        aggro = true
        if (shielded) {
            hurtTimer = 0.05f
            return 0
        }
        hp -= dmg
        hurtTimer = 0.14f
        if (!isBoss) {
            val dx = cx - fromX
            val dy = cy - fromY
            val l = max(1f, sqrt(dx * dx + dy * dy))
            kx = dx / l * 300f * knock
            ky = dy / l * 300f * knock
        }
        if (hp <= 0) {
            hp = 0
            isDead = true
            deadTimer = 0f
            deadA.reset()
        }
        return dmg
    }

    private fun stepTo(dxn: Float, dyn: Float, mul: Float, dt: Float, world: TileMap) {
        val len = sqrt(dxn * dxn + dyn * dyn)
        if (len < 0.001f) return
        val nx = dxn / len
        val ny = dyn / len
        val spd = speed * mul * (if (phase2) 1.25f else 1f)
        val hit = world.move(this, nx * spd * dt, ny * spd * dt)
        if (hit) {
            stuck += dt
            world.move(this, -ny * sideSign * spd * dt, nx * sideSign * spd * dt)
            if (stuck > 0.5f) {
                sideSign = -sideSign
                stuck = 0f
            }
        } else {
            stuck = max(0f, stuck - dt)
        }
        moving = true
        if (abs(nx) > 0.2f) facingLeft = nx < 0f
    }

    fun update(dt: Float, p: Player, world: TileMap, proj: MutableList<Projectile>) {
        if (isDead) {
            deadA.update(dt)
            deadTimer += dt
            return
        }
        idleA.update(dt)
        walkA.update(dt)
        if (hurtTimer > 0f) hurtTimer -= dt
        if (cooldown > 0f) cooldown -= dt
        if (kx != 0f || ky != 0f) {
            world.move(this, kx * dt, ky * dt)
            val k = min(1f, 9f * dt)
            kx -= kx * k
            ky -= ky * k
            if (abs(kx) < 6f) kx = 0f
            if (abs(ky) < 6f) ky = 0f
        }
        hitbox.set(x + size * 0.25f, y + size * 0.2f, x + size * 0.75f, y + size * 0.95f)
        val dx = p.cx - cx
        val dy = p.cy - cy
        val dist = sqrt(dx * dx + dy * dy)
        moving = false
        if (p.isDead) return
        if (!aggro && dist < 560f) aggro = true
        if (aggro && !isBoss && dist > 1150f) aggro = false

        if (windup > 0f) {
            windup -= dt
            attackA.update(dt)
            if (windup <= 0f) finishAttack(p, proj, dx, dy, dist)
            return
        }
        if (!aggro) {
            val hx = homeX - x
            val hy = homeY - y
            if (hx * hx + hy * hy > 900f) stepTo(hx, hy, 0.6f, dt, world)
            return
        }
        facingLeft = dx < 0f
        if (isBoss) {
            volleyTimer -= dt
            if (volleyTimer <= 0f && dist < 800f) {
                volley(proj, dx, dy)
                volleyTimer = if (phase2) 2.4f else 3.6f
            }
            if (phase2) {
                summonTimer -= dt
                if (summonTimer <= 0f) {
                    wantSummon = true
                    summonTimer = 11f
                }
            }
        }
        if (ranged) {
            shootTimer -= dt
            if (dist < 220f) stepTo(-dx, -dy, 1f, dt, world)
            else if (dist > 430f) stepTo(dx, dy, 1f, dt, world)
            else stepTo(-dy * sideSign, dx * sideSign, 0.5f, dt, world)
            if (shootTimer <= 0f && dist < 640f) {
                windup = 0.4f
                attackA.reset()
                shootTimer = 2.1f + (x.toInt() % 5) * 0.12f
            }
        } else {
            if (dist > reach * 0.85f) {
                stepTo(dx, dy, 1f, dt, world)
            } else if (cooldown <= 0f) {
                windup = if (isBoss) 0.5f else 0.42f
                cooldown = if (isBoss) 1.3f else 1.1f
                attackA.reset()
            }
        }
    }

    private fun finishAttack(p: Player, proj: MutableList<Projectile>, dx: Float, dy: Float, dist: Float) {
        if (ranged) {
            val l = max(1f, dist)
            val kind = if (role == EnemyRole.COMMANDO) Projectile.LASER else Projectile.ARROW
            proj.add(Projectile(cx, cy, dx / l * 360f, dy / l * 360f, damage, kind, false))
        } else {
            if (dist < reach * 1.4f) p.takeDamage(damage)
        }
    }

    private fun volley(proj: MutableList<Projectile>, dx: Float, dy: Float) {
        val base = atan2(dy, dx)
        val laser = era == EraType.ERA_5_CYBER
        val kind = if (laser) Projectile.LASER else Projectile.ORB
        val dmg = (damage * 0.5f).toInt()
        if (phase2 && era == EraType.ERA_6_VOID) {
            for (i in 0 until 12) {
                val a = base + i * (Math.PI.toFloat() * 2f / 12f)
                proj.add(Projectile(cx, cy, cos(a) * 300f, sin(a) * 300f, dmg, kind, false))
            }
            return
        }
        val n = if (phase2) 7 else 5
        for (i in 0 until n) {
            val a = base + (i - n / 2) * 0.27f
            proj.add(Projectile(cx, cy, cos(a) * 330f, sin(a) * 330f, dmg, kind, false))
        }
    }

    fun draw(c: Canvas, time: Float) {
        Fx.oval.set(x + size * 0.22f, y + size * 0.80f, x + size * 0.78f, y + size * 0.98f)
        c.drawOval(Fx.oval, Fx.shadow)
        val sheet = when {
            isDead -> deadA
            windup > 0f -> attackA
            moving -> walkA
            else -> idleA
        }
        val paint: Paint? = if (hurtTimer > 0f) Fx.white else if (windup > 0f) Fx.red else tintPaint
        sheet.draw(c, x, y, size, size, facingLeft, paint)
        if (isDead) return
        if (shielded) {
            Fx.slash.color = Color.CYAN
            Fx.slash.alpha = (150 + 80 * sin(time * 6f)).toInt().coerceIn(0, 255)
            Fx.slash.strokeWidth = 5f
            c.drawCircle(cx, cy - size * 0.1f, size * 0.55f, Fx.slash)
            Fx.slash.strokeWidth = 8f
        }
        if (!isBoss && hp < maxHp) {
            val bw = 54f
            val bx = x + size / 2f - bw / 2f
            val by = y + size * 0.08f
            Fx.bar.color = Color.argb(200, 0, 0, 0)
            c.drawRect(bx - 2f, by - 2f, bx + bw + 2f, by + 8f, Fx.bar)
            Fx.bar.color = if (hp > maxHp / 3) Color.rgb(230, 70, 60) else Color.rgb(255, 160, 40)
            c.drawRect(bx, by, bx + bw * hp / maxHp, by + 6f, Fx.bar)
        }
        if (holdsKey) {
            val k = Assets.bmp("icons/key.png")
            if (k != null) {
                val bob = sin(time * 4f) * 4f
                Fx.oval.set(x + size / 2f - 14f, y - 14f + bob, x + size / 2f + 14f, y + 14f + bob)
                c.drawBitmap(k, null, Fx.oval, null)
            }
        }
    }
}
