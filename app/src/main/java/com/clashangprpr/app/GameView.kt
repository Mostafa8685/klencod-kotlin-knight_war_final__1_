package com.clashangprpr.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.clashangprpr.app.engine.ActionButton
import com.clashangprpr.app.engine.Assets
import com.clashangprpr.app.engine.Camera2D
import com.clashangprpr.app.engine.SaveStore
import com.clashangprpr.app.engine.Sound
import com.clashangprpr.app.engine.SpriteSheet
import com.clashangprpr.app.engine.Ui
import com.clashangprpr.app.engine.VirtualJoystick
import com.clashangprpr.app.model.*
import com.clashangprpr.app.story.DialogueLine
import com.clashangprpr.app.story.Story
import com.clashangprpr.app.world.Pt
import com.clashangprpr.app.world.TileMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private enum class State { TITLE, PLAYING, GAME_OVER, VICTORY }
    private enum class Modal { NONE, SHOP, CRAFT, PAUSE, DIALOGUE }
    private enum class Target { NONE, SHOP, CRAFT, GATE, CHEST, MOUND, PORTAL }

    private class Touch(val type: Int, val id: Int, val x: Float, val y: Float)
    private class Hit(val rect: RectF, val id: String)

    // ---------------------------------------------------------------- boot
    private val boot: Boolean = run {
        Assets.init(context)
        SaveStore.init(context)
        Sound.init(context)
        Sound.sfxOn = SaveStore.bool("sfx", true)
        Sound.musicOn = SaveStore.bool("music", true)
        true
    }

    private var thread: Thread? = null
    @Volatile private var running = false
    private val queue = ConcurrentLinkedQueue<Touch>()
    @Volatile private var surfW = 0
    @Volatile private var surfH = 0
    private var curW = 0
    private var curH = 0

    private var state = State.TITLE
    private var modal = Modal.NONE
    private var player = Player(300f, 880f)
    private var era = EraType.ERA_1_MEDIEVAL
    private var world = TileMap(EraType.ERA_1_MEDIEVAL, 1077L)
    private var camera = Camera2D(world.width, world.height)
    private var zoom = 1f
    private var time = 0f
    private var titleT = 0f
    private var endT = 0f
    private var fade = 1f
    private var shake = 0f
    private var hitStop = 0f
    private var deathTimer = 0f
    private var hurtFlash = 0f
    private var toastText = ""
    private var toastTimer = 0f
    private var bannerTimer = 0f
    private var waveTimer = 6f
    private val rnd = java.util.Random(99)

    // world lists
    private val enemies = ArrayList<Enemy>()
    private val projectiles = ArrayList<Projectile>()
    private val explosions = ArrayList<ExplosionFx>()
    private val floats = ArrayList<FloatingText>()
    private val coins = ArrayList<CoinDrop>()
    private val chests = ArrayList<TreasureChest>()
    private val crates = ArrayList<WoodCrate>()
    private val rocks = ArrayList<MiningRock>()
    private val trees = ArrayList<ChoppableTree>()
    private val traps = ArrayList<DungeonTrap>()
    private val props = ArrayList<WorldProp>()
    private val gens = ArrayList<PowerGenerator>()
    private val particles = ParticleSystem()
    private var merchant = MerchantNPC(0f, 0f)
    private var table = CraftTable(0f, 0f)
    private var portal = TimePortal(0f, 0f)
    private var mound: BuriedMound? = null
    private var boss: Enemy? = null
    private var bossDown = false
    private var greeted = false
    private var target = Target.NONE
    private var targetChest: TreasureChest? = null
    private val atkRect = RectF()

    // ui
    private val joystick = VirtualJoystick()
    private val attackBtn = ActionButton("icons/item_1.png", "هجوم", Color.parseColor("#FF7043"))
    private val dashBtn = ActionButton("ui/ico/arr_r.png", "اندفاع", Color.parseColor("#26C6DA"), Color.WHITE)
    private val magicBtn = ActionButton("ui/ico/cross.png", "سحر", Color.parseColor("#AB47BC"), Color.WHITE)
    private val slowBtn = ActionButton("ui/ico/refresh.png", "إبطاء", Color.parseColor("#5C6BC0"), Color.WHITE)
    private val bombBtn = ActionButton("icons/bomb.png", "قنبلة", Color.parseColor("#EF5350"))
    private val potionBtn = ActionButton("icons/pot1.png", "علاج", Color.parseColor("#66BB6A"))
    private val interactBtn = ActionButton("ui/ico/excl.png", "تفاعل", Color.parseColor("#FFCA28"), Color.WHITE)
    private var pauseCx = 0f
    private var pauseCy = 0f
    private var pauseR = 0f
    private var vignette: Paint? = null
    private var darkPaint: Paint? = null
    private val screenPaint = Paint()

    // modal state
    private val hits = ArrayList<Hit>()
    private var shopTab = 0
    private var scrollY = 0f
    private var selectedId = ""
    private var listRect = RectF()
    private var maxScroll = 0f
    private var mPointer = -1
    private var mDownX = 0f
    private var mDownY = 0f
    private var mLastY = 0f
    private var mMoved = false
    private var dialogue: List<DialogueLine> = emptyList()
    private var dialogueIdx = 0
    private var dialogueTime = 0f
    private var dialogueDone: (() -> Unit)? = null
    private val tmpR = RectF()

    init {
        holder.addCallback(this)
        isFocusable = true
        world.build(Pt(300f, 880f), emptyList())
        Sound.playMusic(1)
    }

    // ---------------------------------------------------------------- lifecycle
    override fun surfaceCreated(h: SurfaceHolder) {
        startLoop()
    }

    override fun surfaceChanged(h: SurfaceHolder, format: Int, w: Int, hh: Int) {
        surfW = w
        surfH = hh
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        stopLoop()
    }

    private fun startLoop() {
        if (thread != null) return
        running = true
        val t = Thread(this)
        thread = t
        t.start()
    }

    private fun stopLoop() {
        running = false
        try {
            thread?.join(700)
        } catch (e: InterruptedException) {
        }
        thread = null
    }

    fun pause() {
        stopLoop()
        if (state == State.PLAYING) {
            player.save(era.ordinal)
            if (modal == Modal.NONE) modal = Modal.PAUSE
        }
        Sound.pauseAll()
    }

    fun resume() {
        Sound.resumeAll()
        if (holder.surface != null && holder.surface.isValid) startLoop()
    }

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            var dt = (now - last) / 1_000_000_000f
            last = now
            if (dt > 0.05f) dt = 0.05f
            if (surfW > 0 && surfH > 0 && (surfW != curW || surfH != curH)) {
                curW = surfW
                curH = surfH
                layoutUi()
            }
            if (curW > 0) {
                try {
                    processInput()
                    update(dt)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                var c: Canvas? = null
                try {
                    c = holder.lockCanvas()
                    if (c != null) render(c)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    if (c != null) {
                        try {
                            holder.unlockCanvasAndPost(c)
                        } catch (e: Exception) {
                        }
                    }
                }
            }
            val spent = (System.nanoTime() - now) / 1_000_000L
            if (spent < 16L) {
                try {
                    Thread.sleep(16L - spent)
                } catch (e: InterruptedException) {
                }
            }
        }
    }

    // ---------------------------------------------------------------- layout
    private fun layoutUi() {
        val w = curW.toFloat()
        val h = curH.toFloat()
        Ui.init(w, h)
        val u = Ui.u
        zoom = h / 720f
        camera.viewW = w / zoom
        camera.viewH = h / zoom
        fun place(b: ActionButton, dx: Float, dy: Float, r: Float) {
            b.cx = w - dx * u
            b.cy = h - dy * u
            b.r = r * u
        }
        place(attackBtn, 120f, 120f, 60f)
        place(dashBtn, 255f, 85f, 38f)
        place(magicBtn, 95f, 245f, 42f)
        place(slowBtn, 235f, 210f, 38f)
        place(bombBtn, 345f, 120f, 34f)
        place(potionBtn, 345f, 220f, 34f)
        place(interactBtn, 190f, 335f, 44f)
        joystick.layout(w, h, u)
        pauseCx = w - 38f * u
        pauseCy = 38f * u
        pauseR = 26f * u
        val diag = sqrt(w * w + h * h) / 2f
        val vg = Paint()
        vg.shader = RadialGradient(
            w / 2f, h / 2f, diag,
            intArrayOf(0x00000000, 0x00000000, Color.argb(255, 200, 0, 0)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        vignette = vg
        buildDark()
        camera.snapTo(player.cx, player.cy)
    }

    private fun buildDark() {
        val d = era.darkness
        if (d <= 0f || curW <= 0) {
            darkPaint = null
            return
        }
        val r = 470f * Ui.u
        val a = (d * 255f).toInt().coerceIn(0, 255)
        val dp = Paint()
        dp.shader = RadialGradient(
            0f, 0f, r,
            intArrayOf(0x00000000, 0x00000000, Color.argb((a * 0.7f).toInt(), 4, 4, 14), Color.argb(a, 4, 4, 14)),
            floatArrayOf(0f, 0.30f, 0.75f, 1f), Shader.TileMode.CLAMP
        )
        darkPaint = dp
    }

    // ---------------------------------------------------------------- input
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                queue.add(Touch(0, e.getPointerId(i), e.getX(i), e.getY(i)))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) queue.add(Touch(1, e.getPointerId(i), e.getX(i), e.getY(i)))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                queue.add(Touch(2, e.getPointerId(i), e.getX(i), e.getY(i)))
            }
            MotionEvent.ACTION_CANCEL -> queue.add(Touch(3, -1, 0f, 0f))
        }
        return true
    }

    private fun processInput() {
        while (true) {
            val t = queue.poll() ?: break
            handleTouch(t)
        }
    }

    private fun handleTouch(t: Touch) {
        when (state) {
            State.TITLE -> if (t.type == 2) tapTitle(t.x, t.y)
            State.GAME_OVER, State.VICTORY -> if (t.type == 2) tapEnd(t.x, t.y)
            State.PLAYING -> {
                if (modal != Modal.NONE) {
                    modalTouch(t)
                    return
                }
                when (t.type) {
                    0 -> touchDown(t)
                    1 -> joystick.onMove(t.id, t.x, t.y)
                    2 -> releaseAll(t.id)
                    3 -> cancelAll()
                }
            }
        }
    }

    private fun releaseAll(id: Int) {
        joystick.onUp(id)
        attackBtn.onUp(id)
        dashBtn.onUp(id)
        magicBtn.onUp(id)
        slowBtn.onUp(id)
        bombBtn.onUp(id)
        potionBtn.onUp(id)
        interactBtn.onUp(id)
    }

    private fun cancelAll() {
        joystick.cancel()
        attackBtn.cancel()
        dashBtn.cancel()
        magicBtn.cancel()
        slowBtn.cancel()
        bombBtn.cancel()
        potionBtn.cancel()
        interactBtn.cancel()
        mPointer = -1
    }

    private fun touchDown(t: Touch) {
        val dx = t.x - pauseCx
        val dy = t.y - pauseCy
        if (sqrt(dx * dx + dy * dy) <= pauseR * 1.3f) {
            Sound.play("click")
            cancelAll()
            modal = Modal.PAUSE
            return
        }
        if (attackBtn.onDown(t.id, t.x, t.y)) return
        if (dashBtn.onDown(t.id, t.x, t.y)) {
            if (player.dash()) Sound.play("dash") else Sound.play("deny", 0.4f)
            return
        }
        if (magicBtn.onDown(t.id, t.x, t.y)) {
            val p = player.magic()
            if (p != null) {
                projectiles.add(p)
                Sound.play("magic")
            } else Sound.play("deny", 0.5f)
            return
        }
        if (slowBtn.onDown(t.id, t.x, t.y)) {
            if (player.slowmo()) {
                Sound.play("slowmo")
                toast("الزمن يتباطأ!")
            } else Sound.play("deny", 0.5f)
            return
        }
        if (bombBtn.onDown(t.id, t.x, t.y)) {
            val b = player.throwBomb()
            if (b != null) {
                projectiles.add(b)
                Sound.play("swing")
            } else Sound.play("deny", 0.5f)
            return
        }
        if (potionBtn.onDown(t.id, t.x, t.y)) {
            if (player.usePotion()) {
                Sound.play("potion")
                addFloat("+85", player.cx, player.y, Color.parseColor("#69F0AE"), 30f)
                particles.burst(player.cx, player.cy, 14, Color.parseColor("#69F0AE"), 120f, 6f, 0.7f)
            } else Sound.play("deny", 0.5f)
            return
        }
        if (interactBtn.onDown(t.id, t.x, t.y)) {
            doInteract()
            return
        }
        joystick.onDown(t.id, t.x, t.y)
    }

    private fun toast(s: String) {
        toastText = s
        toastTimer = 2.6f
    }

    private fun addFloat(s: String, x: Float, y: Float, color: Int, size: Float = 26f) {
        floats.add(FloatingText(s, x, y, color, size))
    }

    // ---------------------------------------------------------------- game flow
    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return sqrt(dx * dx + dy * dy)
    }

    private fun bossName(e: EraType): String = when (e) {
        EraType.ERA_1_MEDIEVAL -> "الفارس الأسود"
        EraType.ERA_2_CAVES -> "الحارس الحجري"
        EraType.ERA_3_ZOMBIE -> "الجرثومة الأم"
        EraType.ERA_4_ISLAND -> "قبطان القراصنة"
        EraType.ERA_5_CYBER -> "قائد النظام"
        EraType.ERA_6_VOID -> "سيد الفراغ"
    }

    private fun minionRole(e: EraType): EnemyRole = when (e) {
        EraType.ERA_1_MEDIEVAL -> EnemyRole.MELEE
        EraType.ERA_2_CAVES -> EnemyRole.GHOUL
        EraType.ERA_3_ZOMBIE -> EnemyRole.GHOUL
        EraType.ERA_4_ISLAND -> EnemyRole.TRIBAL
        EraType.ERA_5_CYBER -> EnemyRole.COMMANDO
        EraType.ERA_6_VOID -> EnemyRole.CLONE
    }

    private fun backToTitle() {
        player = Player(300f, 880f)
        era = EraType.ERA_1_MEDIEVAL
        world = TileMap(era, 1077L)
        world.build(Pt(300f, 880f), emptyList())
        camera = Camera2D(world.width, world.height)
        camera.viewW = curW / zoom
        camera.viewH = curH / zoom
        enemies.clear()
        projectiles.clear()
        cancelAll()
        modal = Modal.NONE
        state = State.TITLE
        buildDark()
        Sound.playMusic(1)
    }

    private fun startNewGame() {
        SaveStore.clear()
        player = Player(300f, 880f)
        state = State.PLAYING
        startEra(EraType.ERA_1_MEDIEVAL)
    }

    private fun continueGame() {
        val p = Player(300f, 880f)
        val idx = p.load()
        player = p
        state = State.PLAYING
        startEra(EraType.values()[idx.coerceIn(0, EraType.values().size - 1)])
    }

    private fun retryFromCheckpoint() {
        val idx = player.load()
        state = State.PLAYING
        startEra(EraType.values()[idx.coerceIn(0, EraType.values().size - 1)])
    }

    private fun addEnemy(role: EnemyRole, x: Float, y: Float, key: Boolean = false): Enemy {
        val e = Enemy(x, y, role, era, key)
        enemies.add(e)
        return e
    }

    private fun startEra(e: EraType) {
        era = e
        world = TileMap(e, 1000L + e.ordinal * 77L)
        enemies.clear()
        projectiles.clear()
        explosions.clear()
        floats.clear()
        coins.clear()
        chests.clear()
        crates.clear()
        rocks.clear()
        trees.clear()
        traps.clear()
        props.clear()
        gens.clear()
        particles.list.clear()
        mound = null
        boss = null
        bossDown = false
        greeted = false
        waveTimer = 8f
        cancelAll()
        val pts = ArrayList<Pt>()
        fun reg(x: Float, y: Float, ox: Float = 32f, oy: Float = 40f) {
            pts.add(Pt(x + ox, y + oy))
        }
        val sx = 300f
        val sy = 880f

        // hub: merchant, workbench, camp
        merchant = MerchantNPC(470f, 760f)
        reg(470f, 760f, 48f, 70f)
        world.setDynamic(518f, 840f, true)
        table = CraftTable(620f, 970f)
        reg(620f, 970f)
        world.setDynamic(652f, 1002f, true)
        props.add(WorldProp("environment/fire.png", 392f, 808f, 56f, 56f))
        props.add(WorldProp("environment/torch.png", 350f, 730f, 36f, 36f, 0.15f))
        props.add(WorldProp("environment/torch.png", 580f, 720f, 36f, 36f, 0.15f))

        // loot
        chests.add(TreasureChest(250f, 440f, 45))
        chests.add(TreasureChest(1500f, 1560f, 60))
        chests.add(TreasureChest(1560f, 520f, 80))
        for (c in chests) {
            reg(c.x, c.y)
            world.setDynamic(c.x + 32f, c.y + 32f, true)
        }
        val crateSpots = listOf(Pt(860f, 470f), Pt(1290f, 340f), Pt(1300f, 1100f), Pt(560f, 1250f))
        for ((i, s) in crateSpots.withIndex()) {
            crates.add(WoodCrate(s.x, s.y, i % 2 == 1))
            reg(s.x, s.y)
            world.setDynamic(s.x + 32f, s.y + 32f, true)
        }
        val rockSpots = listOf(Triple(950f, 320f, false), Triple(1180f, 1560f, true), Triple(700f, 1560f, false), Triple(1480f, 300f, true))
        for (r in rockSpots) {
            rocks.add(MiningRock(r.first, r.second, r.third))
            reg(r.first, r.second)
            world.setDynamic(r.first + 32f, r.second + 32f, true)
        }
        val treeSpots = listOf(Pt(520f, 1380f), Pt(820f, 1480f), Pt(1220f, 1280f), Pt(1560f, 1400f), Pt(430f, 520f))
        for ((i, s) in treeSpots.withIndex()) {
            trees.add(ChoppableTree(s.x, s.y, i % 2 == 0))
            reg(s.x, s.y)
            world.setDynamic(s.x + 32f, s.y + 44f, true)
        }

        // hazards
        if (e.ordinal >= 1) {
            traps.add(DungeonTrap(1000f, 780f, true))
            traps.add(DungeonTrap(1280f, 1180f, true))
            traps.add(DungeonTrap(1130f, 1000f, false))
            traps.add(DungeonTrap(1380f, 600f, false))
            traps.add(DungeonTrap(1450f, 1340f, false))
            for (t in traps) reg(t.baseX, t.baseY, 32f, 32f)
        }

        // enemies, per era
        when (e) {
            EraType.ERA_1_MEDIEVAL -> {
                addEnemy(EnemyRole.SPEARMAN, 1000f, 500f)
                addEnemy(EnemyRole.SPEARMAN, 1250f, 1300f)
                addEnemy(EnemyRole.ARCHER, 1400f, 720f)
                addEnemy(EnemyRole.ARCHER, 900f, 1450f)
                addEnemy(EnemyRole.MELEE, 1560f, 900f)
                addEnemy(EnemyRole.HEAVY, 1450f, 1080f, true)
            }
            EraType.ERA_2_CAVES -> {
                addEnemy(EnemyRole.GHOUL, 1000f, 500f)
                addEnemy(EnemyRole.GHOUL, 1100f, 1400f)
                addEnemy(EnemyRole.SPEARMAN, 1300f, 800f)
                addEnemy(EnemyRole.ARCHER, 1450f, 420f)
                addEnemy(EnemyRole.HEAVY, 1500f, 1120f, true)
            }
            EraType.ERA_3_ZOMBIE -> {
                addEnemy(EnemyRole.MELEE, 1000f, 520f)
                addEnemy(EnemyRole.MELEE, 1200f, 1350f)
                addEnemy(EnemyRole.GHOUL, 1350f, 760f)
                addEnemy(EnemyRole.GHOUL, 900f, 1450f)
                addEnemy(EnemyRole.GHOUL, 1560f, 1000f)
                addEnemy(EnemyRole.HEAVY, 1480f, 1180f, true)
            }
            EraType.ERA_4_ISLAND -> {
                addEnemy(EnemyRole.TRIBAL, 1000f, 560f)
                addEnemy(EnemyRole.TRIBAL, 1250f, 1250f)
                addEnemy(EnemyRole.TRIBAL, 1500f, 800f)
                addEnemy(EnemyRole.ARCHER, 1350f, 480f)
                addEnemy(EnemyRole.ARCHER, 900f, 1420f)
                addEnemy(EnemyRole.SPEARMAN, 1550f, 1150f)
                val m = BuriedMound(1500f, 1300f)
                mound = m
                reg(m.x, m.y)
            }
            EraType.ERA_5_CYBER -> {
                addEnemy(EnemyRole.COMMANDO, 1000f, 520f)
                addEnemy(EnemyRole.COMMANDO, 1250f, 1330f)
                addEnemy(EnemyRole.COMMANDO, 1450f, 760f)
                addEnemy(EnemyRole.COMMANDO, 900f, 1450f)
                addEnemy(EnemyRole.HEAVY, 1500f, 1100f, true)
                gens.add(PowerGenerator(700f, 520f, 1))
                gens.add(PowerGenerator(1300f, 1480f, 2))
                gens.add(PowerGenerator(1500f, 420f, 3))
                for (g in gens) {
                    reg(g.x, g.y, 42f, 42f)
                    world.setDynamic(g.x + 42f, g.y + 42f, true)
                }
            }
            EraType.ERA_6_VOID -> {
                addEnemy(EnemyRole.CLONE, 1000f, 620f)
                addEnemy(EnemyRole.CLONE, 1250f, 1300f)
                addEnemy(EnemyRole.COMMANDO, 1400f, 820f)
                addEnemy(EnemyRole.COMMANDO, 1000f, 1400f)
            }
        }
        for (en in enemies) reg(en.x, en.y, 40f, 60f)

        // boss area (behind the gate)
        val b = addEnemy(EnemyRole.BOSS, 2060f, 880f)
        b.shielded = (e == EraType.ERA_5_CYBER)
        boss = b
        reg(b.x, b.y, b.size / 2f, b.size * 0.7f)
        val right1 = addEnemy(minionRole(e), 2000f, 520f)
        val right2 = addEnemy(minionRole(e), 2000f, 1330f)
        reg(right1.x, right1.y, 40f, 60f)
        reg(right2.x, right2.y, 40f, 60f)
        val rc = TreasureChest(2250f, 420f, 100)
        chests.add(rc)
        reg(rc.x, rc.y)
        world.setDynamic(rc.x + 32f, rc.y + 32f, true)
        if (e == EraType.ERA_4_ISLAND) {
            val gem = TreasureChest(2250f, 1420f, 0, true)
            chests.add(gem)
            reg(gem.x, gem.y)
            world.setDynamic(gem.x + 32f, gem.y + 32f, true)
        }
        portal = TimePortal(2230f, 1120f)
        reg(portal.x, portal.y, 70f, 70f)

        world.build(Pt(sx + 48f, sy + 60f), pts)

        player.x = sx
        player.y = sy
        player.resetState()
        camera = Camera2D(world.width, world.height)
        camera.viewW = curW / zoom
        camera.viewH = curH / zoom
        camera.snapTo(player.cx, player.cy)
        buildDark()
        fade = 1f
        bannerTimer = 4.2f
        hitStop = 0f
        deathTimer = 0f
        Sound.playMusic(e.track)
        player.save(e.ordinal)
        openDialogue(Story.intro(e)) { }
    }

    private fun completeEra() {
        Sound.play("portal")
        player.save(min(era.ordinal + 1, EraType.values().size - 1))
        val n = era.next()
        if (n == null) {
            state = State.VICTORY
            endT = 0f
            cancelAll()
            Sound.play("victory")
        } else {
            startEra(n)
        }
    }

    // ---------------------------------------------------------------- dialogue
    private fun openDialogue(lines: List<DialogueLine>, done: () -> Unit) {
        if (lines.isEmpty()) {
            done()
            return
        }
        dialogue = lines
        dialogueIdx = 0
        dialogueTime = 0f
        dialogueDone = done
        modal = Modal.DIALOGUE
        cancelAll()
    }

    private fun dialogueChars(): Int = (dialogueTime * 42f).toInt()

    private fun advanceDialogue() {
        if (dialogueIdx >= dialogue.size) {
            modal = Modal.NONE
            return
        }
        val full = dialogue[dialogueIdx].text.length
        if (dialogueChars() < full) {
            dialogueTime = full / 42f + 0.05f
            return
        }
        Sound.play("click", 0.5f)
        dialogueIdx++
        dialogueTime = 0f
        if (dialogueIdx >= dialogue.size) {
            modal = Modal.NONE
            val d = dialogueDone
            dialogueDone = null
            d?.invoke()
        }
    }

    // ---------------------------------------------------------------- update
    private fun update(dt0: Float) {
        time += dt0
        if (fade > 0f) fade = max(0f, fade - dt0 * 1.1f)
        if (toastTimer > 0f) toastTimer -= dt0
        if (hurtFlash > 0f) hurtFlash -= dt0
        if (shake > 0f) shake = max(0f, shake - dt0 * 30f)
        when (state) {
            State.TITLE -> {
                titleT += dt0
                return
            }
            State.GAME_OVER, State.VICTORY -> {
                endT += dt0
                return
            }
            State.PLAYING -> {
            }
        }
        if (bannerTimer > 0f) bannerTimer -= dt0
        if (modal == Modal.DIALOGUE) dialogueTime += dt0
        if (modal != Modal.NONE) return
        if (hitStop > 0f) {
            hitStop -= dt0
            return
        }
        val dt = dt0
        val edt = if (player.slowTimer > 0f) dt * 0.3f else dt
        val hpBefore = player.hp

        if (attackBtn.isPressed && !player.isDead) {
            if (player.attack()) Sound.play("swing", 0.7f)
        }
        player.update(dt, joystick.dx, joystick.dy, world)
        camera.update(player.cx, player.cy, dt)
        if (player.isDead) {
            deathTimer += dt
            if (deathTimer > 1.8f) {
                state = State.GAME_OVER
                endT = 0f
                cancelAll()
                Sound.play("gameover")
                return
            }
        }
        resolveMelee()

        var i = 0
        while (i < enemies.size) {
            val en = enemies[i]
            en.update(edt, player, world, projectiles)
            if (en.wantSummon) {
                en.wantSummon = false
                summonMinions(en)
            }
            i++
        }
        separateEnemies()
        val eit = enemies.iterator()
        while (eit.hasNext()) {
            val en = eit.next()
            if (en.isDead && en.deadTimer > 2.5f) eit.remove()
        }

        // projectiles
        var pi = projectiles.size - 1
        while (pi >= 0) {
            val pr = projectiles[pi]
            pr.update(edt)
            if (!pr.dead) updateProjectile(pr)
            if (pr.dead) {
                if (pr.kind == Projectile.BOMB) explode(pr.x, pr.y)
                projectiles.removeAt(pi)
            }
            pi--
        }
        var xi = explosions.size - 1
        while (xi >= 0) {
            explosions[xi].update(dt)
            if (explosions[xi].done) explosions.removeAt(xi)
            xi--
        }
        particles.update(dt)
        var fi = floats.size - 1
        while (fi >= 0) {
            floats[fi].update(dt)
            if (floats[fi].dead) floats.removeAt(fi)
            fi--
        }
        var ci = coins.size - 1
        while (ci >= 0) {
            val c = coins[ci]
            c.update(dt, player)
            if (c.collected) {
                player.coins += c.value
                addFloat("+${c.value}", player.cx, player.y - 10f, Color.parseColor("#FFD740"), 24f)
                Sound.play("coin", 0.7f)
                particles.burst(c.x, c.y, 6, Color.parseColor("#FFD740"), 110f, 5f, 0.4f)
                coins.removeAt(ci)
            }
            ci--
        }

        merchant.update(dt)
        for (p in props) p.update(dt)
        for (t in traps) t.update(dt, player)
        for (r in rocks) r.update(dt)
        for (c in crates) c.update(dt)
        for (t in trees) t.update(dt)
        for (g in gens) g.update(dt)
        mound?.update(dt)
        portal.update(dt)
        portal.isActive = bossDown && (era != EraType.ERA_4_ISLAND || player.hasOceanGem)

        if (era == EraType.ERA_4_ISLAND && !player.hasOceanGem && !bossDown) {
            waveTimer -= dt
            if (waveTimer <= 0f) {
                waveTimer = 15f
                spawnWave()
            }
        }
        if (era == EraType.ERA_6_VOID && !world.gateOpen) {
            val gateX = TileMap.GATE_COL * TileMap.TS
            var left = 0
            for (en in enemies) if (!en.isDead && en !== boss && en.x < gateX) left++
            if (left == 0) openGate()
        }
        if (player.hp < hpBefore) onPlayerHurt(hpBefore - player.hp)
        updateTarget()
        updateButtons()
    }

    private fun onPlayerHurt(d: Int) {
        Sound.play("hurt")
        shake = max(shake, 9f)
        hurtFlash = 0.4f
        addFloat("-$d", player.cx, player.y - 6f, Color.parseColor("#FF5252"), 28f)
        particles.burst(player.cx, player.cy, 10, Color.RED, 170f, 6f, 0.5f)
    }

    private fun separateEnemies() {
        for (i in 0 until enemies.size) {
            val a = enemies[i]
            if (a.isDead) continue
            for (j in i + 1 until enemies.size) {
                val b = enemies[j]
                if (b.isDead) continue
                val dx = b.cx - a.cx
                val dy = b.cy - a.cy
                val d2 = dx * dx + dy * dy
                val minD = 46f
                if (d2 < minD * minD && d2 > 0.01f) {
                    val d = sqrt(d2)
                    val push = (minD - d) * 0.5f
                    world.move(a, -dx / d * push, -dy / d * push)
                    world.move(b, dx / d * push, dy / d * push)
                }
            }
        }
    }

    private fun updateProjectile(pr: Projectile) {
        if (world.isSolidAt(pr.x, pr.y)) {
            pr.dead = true
            particles.burst(pr.x, pr.y, 5, Color.LTGRAY, 90f, 4f, 0.3f)
            return
        }
        if (pr.friendly) {
            for (en in enemies) {
                if (en.isDead || pr.hitSet.contains(en)) continue
                if (en.hitbox.contains(pr.x, pr.y)) {
                    if (pr.kind == Projectile.BOMB) {
                        pr.dead = true
                        return
                    }
                    damageEnemy(en, pr.damage, pr.x - pr.vx * 0.05f, pr.y - pr.vy * 0.05f, 0.8f)
                    if (pr.pierce) pr.hitSet.add(en) else {
                        pr.dead = true
                        return
                    }
                }
            }
            for (g in gens) {
                if (!g.isDestroyed && !pr.hitSet.contains(g) && g.hitbox.contains(pr.x, pr.y)) {
                    pr.hitSet.add(g)
                    hitGen(g)
                    if (!pr.pierce) {
                        pr.dead = true
                        return
                    }
                }
            }
        } else {
            if (player.hitbox.contains(pr.x, pr.y)) {
                player.takeDamage(pr.damage)
                pr.dead = true
            }
        }
    }

    private fun resolveMelee() {
        if (!player.inHitWindow) return
        player.attackRect(atkRect)
        val base = player.weapon.damage
        for (en in enemies) {
            if (en.isDead || player.swingHit.contains(en)) continue
            if (RectF.intersects(atkRect, en.hitbox)) {
                player.swingHit.add(en)
                val crit = rnd.nextFloat() < 0.12f
                damageEnemy(en, if (crit) (base * 1.6f).toInt() else base, player.cx, player.cy, 1f, crit)
            }
        }
        for (r in rocks) if (!r.isDestroyed && !player.swingHit.contains(r) && RectF.intersects(atkRect, r.hitbox)) {
            player.swingHit.add(r)
            hitRock(r)
        }
        for (c in crates) if (!c.isDestroyed && !player.swingHit.contains(c) && RectF.intersects(atkRect, c.hitbox)) {
            player.swingHit.add(c)
            hitCrate(c)
        }
        for (t in trees) if (!t.isDestroyed && !player.swingHit.contains(t) && RectF.intersects(atkRect, t.hitbox)) {
            player.swingHit.add(t)
            hitTree(t)
        }
        for (g in gens) if (!g.isDestroyed && !player.swingHit.contains(g) && RectF.intersects(atkRect, g.hitbox)) {
            player.swingHit.add(g)
            hitGen(g)
        }
    }

    private fun damageEnemy(en: Enemy, dmg: Int, fx: Float, fy: Float, knock: Float, crit: Boolean = false) {
        val dealt = en.takeHit(dmg, fx, fy, knock)
        if (dealt == 0) {
            addFloat("محصّن", en.cx, en.y, Color.CYAN, 22f)
            Sound.play("deny", 0.5f)
            return
        }
        val col = if (crit) Color.parseColor("#FFD740") else Color.WHITE
        addFloat(if (crit) "$dealt!" else "$dealt", en.cx, en.y, col, if (crit) 34f else 26f)
        particles.burst(en.cx, en.cy, if (crit) 14 else 8, col, 220f, 6f, 0.4f)
        Sound.play("hit", 0.8f)
        hitStop = if (crit) 0.07f else 0.035f
        shake = max(shake, if (crit) 6f else 3f)
        if (en.isDead) onEnemyDeath(en)
    }

    private fun onEnemyDeath(en: Enemy) {
        val n = if (en.isBoss) 6 else 1
        for (k in 0 until n) coins.add(CoinDrop(en.cx, en.cy, max(1, en.reward / n)))
        particles.burst(en.cx, en.cy, 18, Color.parseColor("#B0BEC5"), 200f, 7f, 0.7f)
        if (en.holdsKey) {
            player.hasBossKey = true
            Sound.play("gate")
            toast("حصلت على مفتاح البوابة! اذهب إليها")
        }
        if (en === boss) {
            bossDown = true
            shake = 14f
            Sound.play("era_clear")
            openDialogue(Story.clear(era)) { toast("بوابة الزمن أصبحت مفتوحة") }
        }
    }

    private fun summonMinions(b: Enemy) {
        var alive = 0
        for (en in enemies) if (!en.isDead && en !== b) alive++
        if (alive >= 7) return
        for (k in 0 until 2) {
            val sx = b.x + (if (k == 0) -110f else 110f)
            val sy = b.y + 40f
            val m = Enemy(sx, sy, minionRole(era), era)
            if (world.blocked(m.x + m.fOffX, m.y + m.fOffY, m.x + m.fOffX + m.fW, m.y + m.fOffY + m.fH)) continue
            m.aggro = true
            enemies.add(m)
            particles.burst(m.cx, m.cy, 12, Color.parseColor("#E040FB"), 150f, 6f, 0.6f)
        }
        Sound.play("boss", 0.8f)
        addFloat("استدعاء!", b.cx, b.y - 10f, Color.parseColor("#E040FB"), 28f)
    }

    private fun spawnWave() {
        var alive = 0
        for (en in enemies) if (!en.isDead && en !== boss) alive++
        if (alive >= 8) return
        val gateX = TileMap.GATE_COL * TileMap.TS
        var made = 0
        var tries = 0
        while (made < 2 && tries < 30) {
            tries++
            val a = rnd.nextFloat() * 6.2832f
            val r = 480f + rnd.nextFloat() * 160f
            val m = Enemy(
                player.cx + kotlin.math.cos(a) * r - 42f,
                player.cy + kotlin.math.sin(a) * r - 60f,
                EnemyRole.TRIBAL, era
            )
            if ((m.x < gateX) != (player.x < gateX)) continue
            if (world.blocked(m.x + m.fOffX, m.y + m.fOffY, m.x + m.fOffX + m.fW, m.y + m.fOffY + m.fH)) continue
            m.aggro = true
            enemies.add(m)
            made++
        }
        if (made > 0) toast("موجة جديدة من القبائل!")
    }

    private fun explode(x: Float, y: Float) {
        explosions.add(ExplosionFx(x, y, 150f))
        Sound.play("explode")
        shake = max(shake, 12f)
        particles.burst(x, y, 24, Color.parseColor("#FF9800"), 320f, 8f, 0.6f)
        for (en in enemies) {
            if (en.isDead) continue
            if (dist(en.cx, en.cy, x, y) < 150f + en.size * 0.3f) damageEnemy(en, 120, x, y, 1.8f)
        }
        for (r in rocks) if (!r.isDestroyed && dist(r.x + 32f, r.y + 32f, x, y) < 150f) {
            hitRock(r)
            if (!r.isDestroyed) hitRock(r)
        }
        for (c in crates) if (!c.isDestroyed && dist(c.x + 32f, c.y + 32f, x, y) < 150f) {
            hitCrate(c)
            if (!c.isDestroyed) hitCrate(c)
        }
        for (t in trees) if (!t.isDestroyed && dist(t.x + 32f, t.y + 40f, x, y) < 150f) hitTree(t)
        for (g in gens) if (!g.isDestroyed && dist(g.x + 42f, g.y + 42f, x, y) < 160f) hitGen(g)
    }

    private fun hitRock(r: MiningRock) {
        if (!r.hit()) return
        Sound.play("mine", 0.8f)
        particles.burst(r.x + 32f, r.y + 32f, 6, Color.parseColor("#B0BEC5"), 150f, 5f, 0.4f)
        val amount = if (r.isDestroyed) 2 else 1
        if (r.isIron) player.iron += amount else player.stone += amount
        addFloat("+$amount ${if (r.isIron) "حديد" else "حجر"}", r.x + 32f, r.y - 6f, if (r.isIron) Color.parseColor("#80D8FF") else Color.parseColor("#CFD8DC"), 22f)
        if (r.isDestroyed) world.setDynamic(r.x + 32f, r.y + 32f, false)
    }

    private fun hitCrate(c: WoodCrate) {
        if (!c.hit()) return
        Sound.play("chop", 0.8f)
        particles.burst(c.x + 32f, c.y + 32f, 6, Color.parseColor("#A1887F"), 150f, 5f, 0.4f)
        if (c.isDestroyed) {
            world.setDynamic(c.x + 32f, c.y + 32f, false)
            player.wood += 1
            addFloat("+1 خشب", c.x + 32f, c.y - 6f, Color.parseColor("#FFCC80"), 22f)
            if (rnd.nextFloat() < 0.5f) coins.add(CoinDrop(c.x + 32f, c.y + 32f, 8 + rnd.nextInt(8)))
        }
    }

    private fun hitTree(t: ChoppableTree) {
        if (!t.hit()) return
        Sound.play("chop", 0.8f)
        particles.burst(t.x + 32f, t.y + 30f, 6, Color.parseColor("#66BB6A"), 140f, 5f, 0.4f)
        val amount = if (t.isDestroyed) 2 else 1
        player.wood += amount
        addFloat("+$amount خشب", t.x + 32f, t.y - 30f, Color.parseColor("#FFCC80"), 22f)
        if (t.isDestroyed) world.setDynamic(t.x + 32f, t.y + 44f, false)
    }

    private fun hitGen(g: PowerGenerator) {
        if (!g.hit()) return
        Sound.play("mine", 0.9f)
        particles.burst(g.x + 42f, g.y + 42f, 10, Color.parseColor("#00E5FF"), 200f, 6f, 0.5f)
        if (g.isDestroyed) {
            world.setDynamic(g.x + 42f, g.y + 42f, false)
            Sound.play("explode", 0.8f)
            var left = 0
            for (o in gens) if (!o.isDestroyed) left++
            if (left == 0) {
                boss?.shielded = false
                toast("انهار درع القائد! هاجمه الآن")
            } else toast("تبقّى $left مولد")
        }
    }

    // ---------------------------------------------------------------- interaction
    private fun openGate() {
        if (world.gateOpen) return
        world.gateOpen = true
        Sound.play("gate")
        shake = 10f
        toast("انفتحت البوابة!")
    }

    private fun updateTarget() {
        var best = Target.NONE
        var bestD = 1e9f
        targetChest = null
        fun cand(t: Target, d: Float, lim: Float) {
            if (d < lim && d < bestD) {
                best = t
                bestD = d
            }
        }
        val px = player.cx
        val py = player.cy
        cand(Target.SHOP, dist(px, py, merchant.x + 48f, merchant.y + 60f), 130f)
        cand(Target.CRAFT, dist(px, py, table.x + 32f, table.y + 32f), 112f)
        if (!world.gateOpen && era != EraType.ERA_6_VOID) cand(Target.GATE, dist(px, py, world.gateCenterX, world.gateCenterY), 150f)
        for (c in chests) {
            if (c.isNear(px, py)) {
                val d = dist(px, py, c.x + 32f, c.y + 32f)
                if (d < bestD) {
                    cand(Target.CHEST, d, 100f)
                    if (best == Target.CHEST) targetChest = c
                }
            }
        }
        val m = mound
        if (m != null && m.isNear(px, py)) cand(Target.MOUND, dist(px, py, m.x + 32f, m.y + 32f), 95f)
        if (portal.isNear(px, py)) cand(Target.PORTAL, dist(px, py, portal.x + 70f, portal.y + 70f), 110f)
        target = best
        interactBtn.visible = best != Target.NONE && !player.isDead
        when (best) {
            Target.SHOP -> { interactBtn.iconPath = "ui/ico/cart.png"; interactBtn.label = "المتجر"; interactBtn.tintIcon = Color.WHITE }
            Target.CRAFT -> { interactBtn.iconPath = "ui/ico/axe.png"; interactBtn.label = "صناعة"; interactBtn.tintIcon = Color.WHITE }
            Target.GATE -> { interactBtn.iconPath = "icons/key.png"; interactBtn.label = "البوابة"; interactBtn.tintIcon = null }
            Target.CHEST -> { interactBtn.iconPath = "ui/ico/dollar.png"; interactBtn.label = "افتح"; interactBtn.tintIcon = Color.WHITE }
            Target.MOUND -> { interactBtn.iconPath = "ui/ico/pencil.png"; interactBtn.label = "احفر"; interactBtn.tintIcon = Color.WHITE }
            Target.PORTAL -> { interactBtn.iconPath = "ui/ico/enter.png"; interactBtn.label = "ادخل"; interactBtn.tintIcon = Color.WHITE }
            Target.NONE -> {}
        }
    }

    private fun doInteract() {
        when (target) {
            Target.SHOP -> {
                Sound.play("click")
                if (!greeted) {
                    greeted = true
                    openDialogue(Story.merchantGreeting()) { openShop() }
                } else openShop()
            }
            Target.CRAFT -> openCraft()
            Target.GATE -> {
                if (player.hasBossKey) openGate() else {
                    toast(Story.keyHint(era))
                    Sound.play("deny")
                }
            }
            Target.CHEST -> {
                val c = targetChest
                if (c != null) openChest(c)
            }
            Target.MOUND -> {
                val m = mound
                if (m != null) {
                    if (!player.hasPickaxe) {
                        toast("تحتاج فأساً. اصنعه من طاولة الصناعة")
                        Sound.play("deny")
                    } else {
                        m.isDug = true
                        player.hasBossKey = true
                        Sound.play("mine")
                        particles.burst(m.x + 32f, m.y + 32f, 16, Color.parseColor("#BCAAA4"), 200f, 6f, 0.6f)
                        toast("وجدت مفتاح البوابة تحت الرمال!")
                    }
                }
            }
            Target.PORTAL -> completeEra()
            Target.NONE -> {}
        }
    }

    private fun openChest(c: TreasureChest) {
        val g = c.open()
        Sound.play("coin")
        particles.burst(c.x + 32f, c.y + 20f, 14, Color.parseColor("#FFD740"), 180f, 6f, 0.6f)
        if (c.isGem) {
            player.hasOceanGem = true
            toast("حصلت على جوهرة المحيط!")
        } else {
            player.coins += g
            addFloat("+$g", c.x + 32f, c.y - 6f, Color.parseColor("#FFD740"), 30f)
        }
    }

    private fun updateButtons() {
        attackBtn.iconPath = player.weapon.iconAsset
        attackBtn.accent = player.weapon.color
        dashBtn.cooldown = player.cooldownFrac(player.dashCd, Player.DASH_CD)
        magicBtn.cooldown = player.cooldownFrac(player.magicCd, Player.MAGIC_CD)
        magicBtn.enabled = player.mana >= Player.MAGIC_COST
        slowBtn.cooldown = if (player.slowTimer > 0f) 0f else player.cooldownFrac(player.slowCd, Player.SLOW_CD)
        slowBtn.enabled = player.mana >= Player.SLOW_COST || player.slowTimer > 0f
        bombBtn.badge = player.bombs.toString()
        bombBtn.enabled = player.bombs > 0
        bombBtn.cooldown = player.cooldownFrac(player.bombCd, Player.BOMB_CD)
        potionBtn.badge = player.potions.toString()
        potionBtn.enabled = player.potions > 0 && player.hp < player.maxHp
    }

    // ---------------------------------------------------------------- modal logic
    private fun openShop() {
        modal = Modal.SHOP
        shopTab = 0
        scrollY = 0f
        selectedId = ""
        mPointer = -1
        cancelAll()
    }

    private fun openCraft() {
        modal = Modal.CRAFT
        scrollY = 0f
        selectedId = ""
        mPointer = -1
        cancelAll()
        Sound.play("click")
    }

    private fun modalTouch(t: Touch) {
        when (t.type) {
            0 -> if (mPointer == -1) {
                mPointer = t.id
                mDownX = t.x
                mDownY = t.y
                mLastY = t.y
                mMoved = false
            }
            1 -> if (t.id == mPointer) {
                if (!mMoved && abs(t.y - mDownY) > 14f * Ui.u) mMoved = true
                if (mMoved && (modal == Modal.SHOP || modal == Modal.CRAFT)) {
                    scrollY = (scrollY - (t.y - mLastY)).coerceIn(0f, maxScroll)
                }
                mLastY = t.y
            }
            2 -> if (t.id == mPointer) {
                mPointer = -1
                if (!mMoved) tapModal(t.x, t.y)
            }
            3 -> mPointer = -1
        }
    }

    private fun hitAt(x: Float, y: Float): String? {
        var i = hits.size - 1
        while (i >= 0) {
            if (hits[i].rect.contains(x, y)) return hits[i].id
            i--
        }
        return null
    }

    private fun tapModal(x: Float, y: Float) {
        when (modal) {
            Modal.DIALOGUE -> advanceDialogue()
            Modal.PAUSE -> tapPause(hitAt(x, y))
            Modal.SHOP -> tapList(hitAt(x, y), x, y, true)
            Modal.CRAFT -> tapList(hitAt(x, y), x, y, false)
            Modal.NONE -> {}
        }
    }

    private fun tapPause(id: String?) {
        when (id) {
            "resume" -> {
                Sound.play("click")
                modal = Modal.NONE
            }
            "sfx" -> {
                Sound.sfxOn = !Sound.sfxOn
                SaveStore.putAll(mapOf("sfx" to Sound.sfxOn), false)
                Sound.play("click")
            }
            "music" -> {
                Sound.setMusic(!Sound.musicOn)
                SaveStore.putAll(mapOf("music" to Sound.musicOn), false)
                Sound.play("click")
            }
            "title" -> {
                Sound.play("click")
                player.save(era.ordinal)
                backToTitle()
            }
        }
    }

    private fun tapList(id: String?, x: Float, y: Float, shop: Boolean) {
        if (id == null) return
        if (id == "close") {
            Sound.play("click")
            player.save(era.ordinal)
            modal = Modal.NONE
            return
        }
        if (id.startsWith("tab:")) {
            shopTab = id.substring(4).toInt()
            scrollY = 0f
            selectedId = ""
            Sound.play("click")
            return
        }
        if (!listRect.contains(x, y)) return
        val isBuy = id.startsWith("buy:")
        val key = id.substring(4)
        if (selectedId != key) {
            selectedId = key
            Sound.play("click")
            return
        }
        if (!isBuy) return
        // second tap on the buy button confirms
        if (shop) {
            val item = GameCatalogue.shopItems.firstOrNull { it.id == key } ?: return
            buyItem(item)
        } else {
            val r = CraftingRegistry.recipes.firstOrNull { it.id == key } ?: return
            craftItem(r)
        }
    }

    private fun upgCount(id: String): Int = if (id == "up_hp") player.upgHp else player.upgMana

    private fun shopState(item: ShopItem): Int {
        val done = when (item.type) {
            ItemType.WEAPON, ItemType.ARMOR -> player.owned.contains(item.id)
            ItemType.UPGRADE -> upgCount(item.id) >= item.maxCount
            ItemType.CONSUMABLE -> false
        }
        if (done) return 1
        return if (player.coins >= item.price) 0 else 2
    }

    private fun applyArmor() {
        val old = player.maxHp
        player.armorId = if (player.owned.contains("armor_nano")) "armor_nano"
        else if (player.owned.contains("armor_royal")) "armor_royal" else ""
        player.recomputeStats()
        player.hp = min(player.maxHp, player.hp + max(0, player.maxHp - old))
    }

    private fun buyItem(item: ShopItem) {
        val st = shopState(item)
        if (st == 1) {
            toast("اشتريته بالفعل")
            Sound.play("deny")
            return
        }
        if (st == 2) {
            toast("ذهبك لا يكفي")
            Sound.play("deny")
            return
        }
        player.coins -= item.price
        when (item.id) {
            "sword_fire", "spear_chrono" -> {
                player.owned.add(item.id)
                player.equipBest()
            }
            "armor_royal" -> {
                player.owned.add(item.id)
                applyArmor()
            }
            "potion_heal" -> player.potions += 2
            "bombs" -> player.bombs += 2
            "up_hp" -> {
                player.upgHp++
                player.recomputeStats()
                player.hp = min(player.maxHp, player.hp + 30)
            }
            "up_mana" -> {
                player.upgMana++
                player.recomputeStats()
            }
        }
        Sound.play("buy")
        toast("تم الشراء: ${item.titleAr}")
        selectedId = ""
        player.save(era.ordinal)
    }

    private fun craftState(r: CraftRecipe): Int {
        val done = when (r.resultType) {
            "PICKAXE" -> player.hasPickaxe
            "PLASMA" -> player.hasPlasma
            "SHIELD" -> player.owned.contains("armor_nano")
            "GODBLADE" -> player.owned.contains("godblade")
            else -> false
        }
        if (done) return 1
        val ok = player.wood >= r.woodCost && player.stone >= r.stoneCost && player.iron >= r.ironCost
        return if (ok) 0 else 2
    }

    private fun craftItem(r: CraftRecipe) {
        val st = craftState(r)
        if (st == 1) {
            toast("صنعته بالفعل")
            Sound.play("deny")
            return
        }
        if (st == 2) {
            toast("المواد غير كافية")
            Sound.play("deny")
            return
        }
        player.wood -= r.woodCost
        player.stone -= r.stoneCost
        player.iron -= r.ironCost
        when (r.resultType) {
            "PICKAXE" -> player.hasPickaxe = true
            "BOMB" -> player.bombs += 2
            "PLASMA" -> player.hasPlasma = true
            "SHIELD" -> {
                player.owned.add("armor_nano")
                applyArmor()
            }
            "GODBLADE" -> {
                player.owned.add("godblade")
                player.equipBest()
            }
        }
        Sound.play("buy")
        toast("تم الصنع: ${r.titleAr}")
        selectedId = ""
        player.save(era.ordinal)
    }

    private fun tapTitle(x: Float, y: Float) {
        when (hitAt(x, y)) {
            "new" -> {
                Sound.play("click")
                startNewGame()
            }
            "cont" -> {
                Sound.play("click")
                continueGame()
            }
            "sfx" -> {
                Sound.sfxOn = !Sound.sfxOn
                SaveStore.putAll(mapOf("sfx" to Sound.sfxOn), false)
                Sound.play("click")
            }
            "music" -> {
                Sound.setMusic(!Sound.musicOn)
                SaveStore.putAll(mapOf("music" to Sound.musicOn), false)
            }
        }
    }

    private fun tapEnd(x: Float, y: Float) {
        if (endT < 0.8f) return
        when (hitAt(x, y)) {
            "retry" -> {
                Sound.play("click")
                retryFromCheckpoint()
            }
            "title" -> {
                Sound.play("click")
                backToTitle()
            }
        }
    }

    // ---------------------------------------------------------------- rendering
    private val GOLD = Color.parseColor("#FFD54F")

    private fun render(c: Canvas) {
        c.drawColor(Color.BLACK)
        hits.clear()
        when (state) {
            State.TITLE -> drawTitle(c)
            State.PLAYING -> {
                drawWorld(c)
                drawHud(c)
                when (modal) {
                    Modal.SHOP -> drawList(c, true)
                    Modal.CRAFT -> drawList(c, false)
                    Modal.PAUSE -> drawPause(c)
                    Modal.DIALOGUE -> drawDialogue(c)
                    Modal.NONE -> {}
                }
            }
            State.GAME_OVER -> {
                drawWorld(c)
                drawGameOver(c)
            }
            State.VICTORY -> drawVictory(c)
        }
        drawToast(c)
        if (fade > 0f) {
            screenPaint.color = Color.argb((fade * 255f).toInt().coerceIn(0, 255), 0, 0, 0)
            c.drawRect(0f, 0f, curW.toFloat(), curH.toFloat(), screenPaint)
        }
    }

    private fun drawWorld(c: Canvas) {
        val sh = shake
        val ox = if (sh > 0f) (rnd.nextFloat() - 0.5f) * sh * 2f else 0f
        val oy = if (sh > 0f) (rnd.nextFloat() - 0.5f) * sh * 2f else 0f
        c.save()
        c.scale(zoom, zoom)
        c.translate(-camera.x + ox, -camera.y + oy)
        val vx = camera.x - 12f
        val vy = camera.y - 12f
        val vw = camera.viewW + 24f
        val vh = camera.viewH + 24f
        world.draw(c, vx, vy, vw, vh, time)
        world.drawDecor(c, vx, vy, vw, vh)
        for (t in traps) t.draw(c)
        for (p in props) p.draw(c)
        mound?.draw(c)
        for (r in rocks) r.draw(c, time)
        for (cr in crates) cr.draw(c)
        for (g in gens) g.draw(c)
        table.draw(c, time)
        for (ch in chests) ch.draw(c, time)
        merchant.draw(c)
        portal.draw(c)
        for (t in trees) t.draw(c)
        for (co in coins) co.draw(c)
        for (en in enemies) if (en.isDead) en.draw(c, time)
        val py = player.y
        for (en in enemies) if (!en.isDead && en.y < py) en.draw(c, time)
        player.draw(c, time)
        for (en in enemies) if (!en.isDead && en.y >= py) en.draw(c, time)
        for (pr in projectiles) pr.draw(c)
        for (ex in explosions) ex.draw(c)
        particles.draw(c)
        for (f in floats) f.draw(c)
        c.restore()

        val dp = darkPaint
        if (dp != null) {
            val sx = (player.cx - camera.x) * zoom
            val sy = (player.cy - camera.y) * zoom
            c.save()
            c.translate(sx, sy)
            c.drawRect(-curW * 2f, -curH * 2f, curW * 2f, curH * 2f, dp)
            c.restore()
        }
        val vg = vignette
        if (vg != null && state == State.PLAYING) {
            val low = player.hp.toFloat() / max(1, player.maxHp)
            var a = 0f
            if (hurtFlash > 0f) a = hurtFlash / 0.4f * 190f
            if (low < 0.3f) a = max(a, (0.3f - low) / 0.3f * (80f + 40f * sin(time * 6f)))
            if (a > 1f) {
                vg.alpha = a.toInt().coerceIn(0, 255)
                c.drawRect(0f, 0f, curW.toFloat(), curH.toFloat(), vg)
            }
        }
    }

    private fun hpColor(f: Float): Int = when {
        f > 0.5f -> Color.parseColor("#43A047")
        f > 0.25f -> Color.parseColor("#FB8C00")
        else -> Color.parseColor("#E53935")
    }

    private fun drawHud(c: Canvas) {
        val u = Ui.u
        val w = curW.toFloat()
        val h = curH.toFloat()

        // ---- status panel (top-left)
        val px = 14f * u
        val py = 14f * u
        val pw = 380f * u
        val ph = 138f * u
        tmpR.set(px, py, px + pw, py + ph)
        Ui.panel(c, tmpR, Color.parseColor("#B8860B"), Color.argb(205, 12, 16, 30), 12f)
        tmpR.set(px + 10f * u, py + 12f * u, px + 68f * u, py + 70f * u)
        Ui.fillRound(c, tmpR, Color.argb(210, 24, 30, 52), 10f)
        Ui.strokeRound(c, tmpR, player.weapon.color, 2.5f, 10f)
        Ui.icon(c, player.weapon.iconAsset, tmpR.centerX(), tmpR.centerY(), 42f)
        val armor = GameCatalogue.armorById(player.armorId)
        if (armor != null) Ui.icon(c, "ui/ico/shield.png", px + 58f * u, py + 62f * u, 20f, Color.parseColor("#4DD0E1"))

        val bx = px + 80f * u
        val bw = pw - 94f * u
        Ui.icon(c, "ui/ico/heart.png", bx + 10f * u, py + 26f * u, 24f, Color.parseColor("#FF5252"))
        tmpR.set(bx + 28f * u, py + 14f * u, bx + bw, py + 38f * u)
        val hf = player.hp.toFloat() / max(1, player.maxHp)
        Ui.bar(c, tmpR, hf, hpColor(hf))
        Ui.text(c, "${player.hp} / ${player.maxHp}", tmpR.centerX(), tmpR.centerY() + 5.5f * u, 15f, Color.WHITE)
        Ui.icon(c, "icons/pot10.png", bx + 10f * u, py + 54f * u, 26f)
        tmpR.set(bx + 28f * u, py + 44f * u, bx + bw, py + 62f * u)
        Ui.bar(c, tmpR, player.mana / max(1, player.maxMana), Color.parseColor("#2979FF"))
        Ui.text(c, "${player.mana.toInt()}", tmpR.centerX(), tmpR.centerY() + 5f * u, 13f, Color.WHITE)

        Ui.icon(c, "icons/coin.png", px + 24f * u, py + 90f * u, 28f)
        Ui.text(c, "${player.coins}", px + 44f * u, py + 98f * u, 21f, GOLD, Paint.Align.LEFT)
        var sx = px + pw - 22f * u
        if (player.hasOceanGem) {
            Ui.icon(c, "icons/pot40.png", sx, py + 88f * u, 28f)
            sx -= 34f * u
        }
        if (player.hasPickaxe) {
            Ui.icon(c, "ui/ico/axe.png", sx, py + 88f * u, 24f, Color.parseColor("#FFB74D"))
            sx -= 34f * u
        }
        if (player.hasBossKey) Ui.icon(c, "icons/key.png", sx, py + 88f * u, 28f)

        val rx = px + 14f * u
        val ry = py + 120f * u
        val step = 72f * u
        Ui.icon(c, "icons/wood.png", rx + 8f * u, ry, 22f)
        Ui.text(c, "${player.wood}", rx + 22f * u, ry + 6f * u, 16f, Color.WHITE, Paint.Align.LEFT)
        Ui.icon(c, "icons/stone.png", rx + step + 8f * u, ry, 22f)
        Ui.text(c, "${player.stone}", rx + step + 22f * u, ry + 6f * u, 16f, Color.WHITE, Paint.Align.LEFT)
        Ui.icon(c, "icons/iron.png", rx + step * 2f + 8f * u, ry, 22f)
        Ui.text(c, "${player.iron}", rx + step * 2f + 22f * u, ry + 6f * u, 16f, Color.WHITE, Paint.Align.LEFT)
        Ui.icon(c, "icons/bomb.png", rx + step * 3f + 8f * u, ry, 22f)
        Ui.text(c, "${player.bombs}", rx + step * 3f + 22f * u, ry + 6f * u, 16f, Color.WHITE, Paint.Align.LEFT)
        Ui.icon(c, "icons/pot1.png", rx + step * 4f + 8f * u, ry, 22f)
        Ui.text(c, "${player.potions}", rx + step * 4f + 22f * u, ry + 6f * u, 16f, Color.WHITE, Paint.Align.LEFT)

        // ---- era chip + objective (top-centre)
        val cw = min(560f * u, w * 0.42f)
        tmpR.set(w / 2f - cw / 2f, 10f * u, w / 2f + cw / 2f, 40f * u)
        Ui.panel(c, tmpR, Color.parseColor("#6D4C41"), Color.argb(190, 12, 16, 30), 10f)
        Ui.text(c, era.title, tmpR.centerX(), tmpR.centerY() + 6f * u, 16f, GOLD)
        tmpR.set(w / 2f - cw / 2f, 46f * u, w / 2f + cw / 2f, 92f * u)
        Ui.panel(c, tmpR, Color.parseColor("#455A64"), Color.argb(180, 10, 14, 26), 10f)
        Ui.icon(c, "ui/ico/excl.png", tmpR.right - 20f * u, tmpR.centerY(), 22f, Color.parseColor("#FFCA28"))
        Ui.wrapped(c, objectiveText(), tmpR.left + 12f * u, tmpR.top + 7f * u, cw - 54f * u, 15f, Color.WHITE, false, 2)

        // ---- boss bar
        val b = boss
        if (b != null && !b.isDead && b.aggro) {
            val bwid = min(520f * u, w * 0.44f)
            tmpR.set(w / 2f - bwid / 2f, 98f * u, w / 2f + bwid / 2f, 134f * u)
            Ui.panel(c, tmpR, Color.parseColor("#B71C1C"), Color.argb(200, 20, 8, 10), 10f)
            Ui.text(c, bossName(era) + if (b.shielded) "  (محصّن)" else "", tmpR.centerX(), tmpR.top + 17f * u, 15f, Color.WHITE)
            val br = RectF(tmpR.left + 12f * u, tmpR.top + 21f * u, tmpR.right - 12f * u, tmpR.bottom - 7f * u)
            Ui.bar(c, br, b.hp.toFloat() / max(1, b.maxHp), if (b.shielded) Color.parseColor("#00BCD4") else Color.parseColor("#E53935"))
        }

        // ---- minimap (top-right)
        val mw = 190f * u
        val mh = mw * TileMap.ROWS / TileMap.COLS
        val mx = w - 14f * u - mw
        val my = 76f * u
        tmpR.set(mx - 4f * u, my - 4f * u, mx + mw + 4f * u, my + mh + 4f * u)
        Ui.panel(c, tmpR, Color.parseColor("#455A64"), Color.argb(215, 6, 8, 16), 8f)
        val mmR = RectF(mx, my, mx + mw, my + mh)
        c.drawBitmap(world.minimap, null, mmR, null)
        val fx = mw / world.width
        val fy = mh / world.height
        for (en in enemies) {
            if (en.isDead) continue
            Ui.circle(c, mx + en.cx * fx, my + en.cy * fy, if (en.isBoss) 5f * u else 2.8f * u, if (en.isBoss) Color.parseColor("#FF1744") else Color.parseColor("#FF8A80"))
        }
        Ui.circle(c, mx + (merchant.x + 48f) * fx, my + (merchant.y + 60f) * fy, 3.4f * u, Color.parseColor("#FFD54F"))
        if (portal.isActive) Ui.circle(c, mx + (portal.x + 70f) * fx, my + (portal.y + 70f) * fy, 4.5f * u, Color.CYAN)
        Ui.circle(c, mx + player.cx * fx, my + player.cy * fy, 4f * u, Color.WHITE)
        Ui.strokeRound(c, RectF(mx + camera.x * fx, my + camera.y * fy, mx + (camera.x + camera.viewW) * fx, my + (camera.y + camera.viewH) * fy), Color.argb(150, 255, 255, 255), 1.2f, 2f)

        // ---- controls
        joystick.draw(c)
        attackBtn.draw(c)
        dashBtn.draw(c)
        magicBtn.draw(c)
        slowBtn.draw(c)
        bombBtn.draw(c)
        potionBtn.draw(c)
        interactBtn.draw(c)
        Ui.circle(c, pauseCx, pauseCy, pauseR, Color.argb(200, 14, 18, 32))
        Ui.ring(c, pauseCx, pauseCy, pauseR - 1.5f * u, Color.parseColor("#B0BEC5"), 3f * u)
        Ui.icon(c, "ui/ico/pause.png", pauseCx, pauseCy, 26f, Color.WHITE)

        // ---- era banner
        if (bannerTimer > 0f && modal != Modal.DIALOGUE) {
            val a = min(1f, min(bannerTimer / 0.9f, (4.2f - bannerTimer) / 0.6f)).coerceIn(0f, 1f)
            Ui.fillRect(c, 0f, h * 0.27f, w, h * 0.27f + 170f * u, Color.argb((150 * a).toInt(), 0, 0, 0))
            Ui.text(c, era.title, w / 2f, h * 0.27f + 62f * u, 38f, Color.argb((255 * a).toInt(), 255, 213, 79))
            Ui.text(c, era.subtitle, w / 2f, h * 0.27f + 98f * u, 22f, Color.argb((230 * a).toInt(), 160, 220, 255))
            Ui.wrapped(c, era.description, w / 2f - 340f * u, h * 0.27f + 112f * u, 680f * u, 17f, Color.argb((235 * a).toInt(), 235, 235, 235), false, 2, true, false)
        }
    }

    private fun objectiveText(): String {
        val b = boss
        if (bossDown && !portal.isActive && era == EraType.ERA_4_ISLAND) return "جد جوهرة المحيط في الصندوق خلف البوابة"
        if (bossDown) return "ادخل بوابة الزمن لتكمل رحلتك"
        if (world.gateOpen) {
            if (era == EraType.ERA_5_CYBER && b != null && b.shielded) return "عطّل المولدات لتكسر درع القائد"
            return "اهزم ${bossName(era)}"
        }
        return when (era) {
            EraType.ERA_4_ISLAND -> when {
                !player.hasBossKey && !player.hasPickaxe -> "اصنع فأساً من طاولة الصناعة (3 خشب، 2 حجر، 2 حديد)"
                !player.hasBossKey -> "احفر الكنز المدفون لتجد المفتاح"
                else -> "افتح البوابة المقفلة"
            }
            EraType.ERA_6_VOID -> {
                var left = 0
                val gateX = TileMap.GATE_COL * TileMap.TS
                for (en in enemies) if (!en.isDead && en !== b && en.x < gateX) left++
                "اهزم أشباه الفارس لفتح الحاجز ($left)"
            }
            EraType.ERA_5_CYBER -> if (!player.hasBossKey) "اهزم حامل المفتاح وعطّل المولدات" else "افتح البوابة المقفلة"
            else -> if (!player.hasBossKey) "اهزم الحارس حامل المفتاح (رمز المفتاح فوقه)" else "افتح البوابة المقفلة"
        }
    }

    private fun drawToast(c: Canvas) {
        if (toastTimer <= 0f || toastText.isEmpty()) return
        val u = Ui.u
        val a = min(1f, toastTimer / 0.4f)
        val tw = min(Ui.measure(toastText, 19f) + 50f * u, curW * 0.8f)
        val cx = curW / 2f
        val cy = curH - 56f * u
        tmpR.set(cx - tw / 2f, cy - 24f * u, cx + tw / 2f, cy + 24f * u)
        Ui.fillRound(c, tmpR, Color.argb((220 * a).toInt(), 14, 18, 34), 24f)
        Ui.strokeRound(c, tmpR, Color.argb((255 * a).toInt(), 255, 213, 79), 2f, 24f)
        Ui.text(c, toastText, cx, cy + 7f * u, 19f, Color.argb((255 * a).toInt(), 255, 255, 255))
    }

    // ---- modal frames
    private fun modalRect(): RectF {
        val w = curW.toFloat()
        val h = curH.toFloat()
        return RectF(w * 0.07f, h * 0.06f, w * 0.93f, h * 0.94f)
    }

    private fun dim(c: Canvas) {
        Ui.fillRect(c, 0f, 0f, curW.toFloat(), curH.toFloat(), Color.argb(170, 0, 0, 0))
    }

    private fun closeButton(c: Canvas, mr: RectF) {
        val u = Ui.u
        val r = RectF(mr.left + 14f * u, mr.top + 12f * u, mr.left + 62f * u, mr.top + 60f * u)
        Ui.button(c, r, "", Color.parseColor("#C62828"), true, 18f)
        Ui.icon(c, "ui/ico/x.png", r.centerX(), r.centerY(), 26f, Color.WHITE)
        hits.add(Hit(RectF(r.left - 6f * u, r.top - 6f * u, r.right + 6f * u, r.bottom + 6f * u), "close"))
    }

    private fun drawList(c: Canvas, shop: Boolean) {
        val u = Ui.u
        dim(c)
        val mr = modalRect()
        Ui.panel(c, mr, GOLD, Color.argb(246, 18, 22, 40), 16f)
        closeButton(c, mr)
        // coin chip
        val chip = RectF(mr.left + 74f * u, mr.top + 14f * u, mr.left + 74f * u + 150f * u, mr.top + 58f * u)
        Ui.fillRound(c, chip, Color.argb(220, 8, 10, 20), 12f)
        Ui.strokeRound(c, chip, GOLD, 2f, 12f)
        Ui.icon(c, "icons/coin.png", chip.left + 24f * u, chip.centerY(), 28f)
        Ui.text(c, "${player.coins}", chip.left + 44f * u, chip.centerY() + 8f * u, 22f, GOLD, Paint.Align.LEFT)
        Ui.text(c, if (shop) "متجر إلريك" else "طاولة الصناعة", mr.right - 24f * u, mr.top + 46f * u, 30f, GOLD, Paint.Align.RIGHT)

        var listTop = mr.top + 78f * u
        if (shop) {
            val names = listOf("أسلحة", "دروع", "مستهلكات", "ترقيات")
            var tx = mr.right - 20f * u
            for (i in names.indices) {
                val r = RectF(tx - 150f * u, listTop, tx, listTop + 42f * u)
                val sel = i == shopTab
                Ui.button(c, r, names[i], if (sel) Color.parseColor("#F9A825") else Color.parseColor("#37474F"), true, 18f, sel)
                hits.add(Hit(RectF(r), "tab:$i"))
                tx -= 160f * u
            }
            listTop += 54f * u
        }
        listRect = RectF(mr.left + 16f * u, listTop, mr.right - 16f * u, mr.bottom - 16f * u)

        val rowH = (if (shop) 98f else 118f) * u
        val gap = 10f * u
        val shopItems: List<ShopItem> = if (shop) {
            val type = listOf(ItemType.WEAPON, ItemType.ARMOR, ItemType.CONSUMABLE, ItemType.UPGRADE)[shopTab]
            GameCatalogue.shopItems.filter { it.type == type }
        } else emptyList()
        val count = if (shop) shopItems.size else CraftingRegistry.recipes.size
        val contentH = count * (rowH + gap)
        maxScroll = max(0f, contentH - listRect.height())
        scrollY = scrollY.coerceIn(0f, maxScroll)

        c.save()
        c.clipRect(listRect)
        c.translate(0f, -scrollY)
        for (i in 0 until count) {
            val top = listRect.top + i * (rowH + gap)
            val card = RectF(listRect.left, top, listRect.right, top + rowH)
            if (shop) drawShopCard(c, shopItems[i], card) else drawCraftCard(c, CraftingRegistry.recipes[i], card)
        }
        c.restore()
        // scrollbar
        if (maxScroll > 0f) {
            val th = listRect.height() * listRect.height() / (listRect.height() + maxScroll)
            val ty = listRect.top + (listRect.height() - th) * (scrollY / maxScroll)
            tmpR.set(listRect.right - 6f * u, ty, listRect.right - 2f * u, ty + th)
            Ui.fillRound(c, tmpR, Color.argb(170, 255, 213, 79), 3f)
        }
    }

    private fun cardBase(c: Canvas, card: RectF, selected: Boolean, accent: Int) {
        val u = Ui.u
        Ui.fillRound(c, card, if (selected) Color.argb(235, 40, 50, 84) else Color.argb(225, 28, 34, 58), 12f)
        Ui.strokeRound(c, card, if (selected) GOLD else Color.argb(160, 90, 100, 140), if (selected) 3f else 1.5f, 12f)
        val box = RectF(card.right - 84f * u, card.top + 12f * u, card.right - 12f * u, card.top + 84f * u)
        Ui.fillRound(c, box, Color.argb(235, 10, 12, 24), 10f)
        Ui.strokeRound(c, box, accent, 2.5f, 10f)
    }

    private fun addCardHits(card: RectF, btn: RectF, key: String) {
        val sr = RectF(card.left, card.top - scrollY, card.right, card.bottom - scrollY)
        val br = RectF(btn.left, btn.top - scrollY, btn.right, btn.bottom - scrollY)
        if (!RectF.intersects(sr, listRect)) return
        hits.add(Hit(sr, "sel:$key"))
        hits.add(Hit(br, "buy:$key"))
    }

    private fun drawShopCard(c: Canvas, item: ShopItem, card: RectF) {
        val u = Ui.u
        val selected = selectedId == item.id
        val accent = when (item.type) {
            ItemType.WEAPON -> Color.parseColor("#FF7043")
            ItemType.ARMOR -> Color.parseColor("#4DD0E1")
            ItemType.CONSUMABLE -> Color.parseColor("#66BB6A")
            ItemType.UPGRADE -> Color.parseColor("#AB47BC")
        }
        cardBase(c, card, selected, accent)
        val boxLeft = card.right - 84f * u
        Ui.icon(c, item.iconAsset, boxLeft + 36f * u, card.top + 48f * u, 54f, item.tint)
        Ui.text(c, item.titleAr, boxLeft - 14f * u, card.top + 34f * u, 21f, Color.WHITE, Paint.Align.RIGHT)
        var desc = item.descAr
        if (item.type == ItemType.UPGRADE) desc += "  (${upgCount(item.id)}/${item.maxCount})"
        if (item.type == ItemType.CONSUMABLE && item.id == "potion_heal") desc += "  عندك: ${player.potions}"
        if (item.type == ItemType.CONSUMABLE && item.id == "bombs") desc += "  عندك: ${player.bombs}"
        val btn = RectF(card.left + 14f * u, card.centerY() - 26f * u, card.left + 164f * u, card.centerY() + 26f * u)
        Ui.wrapped(c, desc, btn.right + 14f * u, card.top + 44f * u, boxLeft - 14f * u - (btn.right + 14f * u), 15f, Color.rgb(200, 208, 230), false, 2)
        val st = shopState(item)
        when {
            st == 1 -> {
                val label = if (item.type == ItemType.WEAPON && player.weapon.id == item.id) "مجهّز ✓" else "مملوك ✓"
                Ui.button(c, btn, label, Color.parseColor("#5D4037"), false, 18f)
            }
            selected && st == 0 -> Ui.button(c, btn, "تأكيد الشراء", Color.parseColor("#2E7D32"), true, 17f)
            st == 0 -> {
                Ui.button(c, btn, "    ${item.price}", Color.parseColor("#F9A825"), true, 20f)
                Ui.icon(c, "icons/coin.png", btn.left + 28f * u, btn.centerY(), 26f)
            }
            else -> {
                Ui.button(c, btn, "    ${item.price}", Color.parseColor("#B71C1C"), false, 20f)
                Ui.icon(c, "icons/coin.png", btn.left + 28f * u, btn.centerY(), 26f, null, 150)
            }
        }
        addCardHits(card, btn, item.id)
    }

    private fun drawCraftCard(c: Canvas, r: CraftRecipe, card: RectF) {
        val u = Ui.u
        val selected = selectedId == r.id
        cardBase(c, card, selected, Color.parseColor("#FFB74D"))
        val boxLeft = card.right - 84f * u
        Ui.icon(c, r.iconAsset, boxLeft + 36f * u, card.top + 48f * u, 54f, r.tint)
        Ui.text(c, r.titleAr, boxLeft - 14f * u, card.top + 32f * u, 21f, Color.WHITE, Paint.Align.RIGHT)
        val btn = RectF(card.left + 14f * u, card.centerY() - 26f * u, card.left + 164f * u, card.centerY() + 26f * u)
        Ui.wrapped(c, r.descAr, btn.right + 14f * u, card.top + 42f * u, boxLeft - 14f * u - (btn.right + 14f * u), 15f, Color.rgb(200, 208, 230), false, 2)
        // cost chips (right to left)
        var cx = boxLeft - 14f * u
        val cy = card.bottom - 24f * u
        val costs = listOf(Triple("icons/wood.png", player.wood, r.woodCost), Triple("icons/stone.png", player.stone, r.stoneCost), Triple("icons/iron.png", player.iron, r.ironCost))
        for (cst in costs) {
            val ok = cst.second >= cst.third
            Ui.icon(c, cst.first, cx - 12f * u, cy, 24f)
            Ui.text(c, "${cst.second}/${cst.third}", cx - 28f * u, cy + 6f * u, 16f, if (ok) Color.parseColor("#A5D6A7") else Color.parseColor("#EF9A9A"), Paint.Align.RIGHT)
            cx -= 106f * u
        }
        val st = craftState(r)
        when {
            st == 1 -> Ui.button(c, btn, "مصنوع ✓", Color.parseColor("#5D4037"), false, 18f)
            selected && st == 0 -> Ui.button(c, btn, "تأكيد الصنع", Color.parseColor("#2E7D32"), true, 17f)
            st == 0 -> Ui.button(c, btn, "اصنع", Color.parseColor("#F9A825"), true, 20f)
            else -> Ui.button(c, btn, "مواد ناقصة", Color.parseColor("#B71C1C"), false, 17f)
        }
        addCardHits(card, btn, r.id)
    }

    private fun drawPause(c: Canvas) {
        val u = Ui.u
        dim(c)
        val w = curW.toFloat()
        val h = curH.toFloat()
        val pr = RectF(w / 2f - 230f * u, h / 2f - 220f * u, w / 2f + 230f * u, h / 2f + 220f * u)
        Ui.panel(c, pr, GOLD, Color.argb(248, 18, 22, 40), 16f)
        Ui.text(c, "إيقاف مؤقت", pr.centerX(), pr.top + 56f * u, 32f, GOLD)
        val bw = 340f * u
        var y = pr.top + 86f * u
        val items = listOf(
            Triple("resume", "متابعة اللعب", Color.parseColor("#2E7D32")),
            Triple("sfx", "المؤثرات: " + (if (Sound.sfxOn) "تشغيل" else "إيقاف"), Color.parseColor("#1565C0")),
            Triple("music", "الموسيقى: " + (if (Sound.musicOn) "تشغيل" else "إيقاف"), Color.parseColor("#6A1B9A")),
            Triple("title", "حفظ والعودة للقائمة", Color.parseColor("#C62828"))
        )
        for (it in items) {
            val r = RectF(pr.centerX() - bw / 2f, y, pr.centerX() + bw / 2f, y + 62f * u)
            Ui.button(c, r, it.second, it.third, true, 21f)
            hits.add(Hit(r, it.first))
            y += 80f * u
        }
    }

    private fun drawDialogue(c: Canvas) {
        val u = Ui.u
        val w = curW.toFloat()
        val h = curH.toFloat()
        if (dialogueIdx >= dialogue.size) return
        val line = dialogue[dialogueIdx]
        val pr = RectF(w * 0.06f, h - 236f * u, w * 0.94f, h - 20f * u)
        Ui.panel(c, pr, GOLD, Color.argb(240, 14, 18, 34), 16f)
        // portrait
        val box = RectF(pr.right - 150f * u, pr.top + 18f * u, pr.right - 18f * u, pr.top + 150f * u)
        Ui.fillRound(c, box, Color.argb(255, 8, 10, 22), 12f)
        Ui.strokeRound(c, box, GOLD, 2.5f, 12f)
        val bm = Assets.bmp(line.portrait)
        if (bm != null) {
            val sw = if (bm.width > bm.height * 3 / 2) bm.height else bm.width
            val src = android.graphics.Rect(0, 0, sw, bm.height)
            val dstR = RectF(box.left + 10f * u, box.top + 10f * u, box.right - 10f * u, box.bottom - 10f * u)
            c.drawBitmap(bm, src, dstR, null)
        }
        // name chip
        val nr = RectF(pr.right - 330f * u, pr.top - 20f * u, pr.right - 160f * u, pr.top + 14f * u)
        Ui.fillRound(c, nr, Color.parseColor("#F9A825"), 12f)
        Ui.text(c, line.speaker, nr.centerX(), nr.centerY() + 6f * u, 18f, Color.parseColor("#3E2723"), Paint.Align.CENTER, true, false)
        val shown = min(line.text.length, dialogueChars())
        val txt = line.text.substring(0, shown)
        Ui.wrapped(c, txt, pr.left + 28f * u, pr.top + 28f * u, pr.width() - 200f * u, 23f, Color.WHITE, false, 5, false, false)
        if (shown >= line.text.length) {
            val a = (160 + 90 * sin(time * 5f)).toInt().coerceIn(0, 255)
            Ui.text(c, "اضغط للمتابعة", pr.left + 28f * u, pr.bottom - 20f * u, 15f, Color.argb(a, 255, 213, 79), Paint.Align.LEFT)
        }
        hits.add(Hit(RectF(0f, 0f, w, h), "adv"))
    }

    // ---- title / end screens
    private val heroSheet = SpriteSheet("sprites/player/idle.png", 0.2f)

    private fun drawTitle(c: Canvas) {
        val u = Ui.u
        val w = curW.toFloat()
        val h = curH.toFloat()
        val camX = (world.width - camera.viewW) / 2f + sin(titleT * 0.12f) * 520f
        val camY = (world.height - camera.viewH) / 2f + sin(titleT * 0.09f + 1f) * 300f
        c.save()
        c.scale(zoom, zoom)
        c.translate(-camX, -camY)
        world.draw(c, camX, camY, camera.viewW, camera.viewH, titleT)
        world.drawDecor(c, camX, camY, camera.viewW, camera.viewH)
        c.restore()
        Ui.fillRect(c, 0f, 0f, w, h, Color.argb(150, 4, 6, 16))
        Ui.text(c, "حرب الفارس", w / 2f, h * 0.24f, 74f, GOLD)
        Ui.text(c, "عابر العصور", w / 2f, h * 0.24f + 54f * u, 34f, Color.parseColor("#80DEEA"))
        Ui.text(c, "ست عصور، ست شظايا من حجر كرونو، وفارس واحد", w / 2f, h * 0.24f + 92f * u, 18f, Color.rgb(220, 224, 235), Paint.Align.CENTER, false)
        heroSheet.update(0.016f)
        heroSheet.draw(c, w / 2f - 90f * u, h * 0.40f, 180f * u, 180f * u)
        val bw = 340f * u
        var y = h * 0.40f + 196f * u
        if (SaveStore.hasSave()) {
            val r = RectF(w / 2f - bw / 2f, y, w / 2f + bw / 2f, y + 60f * u)
            Ui.button(c, r, "متابعة الرحلة", Color.parseColor("#2E7D32"), true, 22f)
            hits.add(Hit(r, "cont"))
            y += 74f * u
        }
        val r2 = RectF(w / 2f - bw / 2f, y, w / 2f + bw / 2f, y + 60f * u)
        Ui.button(c, r2, if (SaveStore.hasSave()) "رحلة جديدة" else "ابدأ الرحلة", Color.parseColor("#F9A825"), true, 22f)
        hits.add(Hit(r2, "new"))
        val s1 = RectF(w - 330f * u, h - 64f * u, w - 176f * u, h - 16f * u)
        Ui.button(c, s1, "المؤثرات: " + (if (Sound.sfxOn) "نعم" else "لا"), Color.parseColor("#37474F"), true, 15f)
        hits.add(Hit(s1, "sfx"))
        val s2 = RectF(w - 166f * u, h - 64f * u, w - 16f * u, h - 16f * u)
        Ui.button(c, s2, "الموسيقى: " + (if (Sound.musicOn) "نعم" else "لا"), Color.parseColor("#37474F"), true, 15f)
        hits.add(Hit(s2, "music"))
    }

    private fun drawGameOver(c: Canvas) {
        val u = Ui.u
        val w = curW.toFloat()
        val h = curH.toFloat()
        Ui.fillRect(c, 0f, 0f, w, h, Color.argb(min(200, (endT * 300f).toInt()), 40, 0, 0))
        val pr = RectF(w / 2f - 260f * u, h / 2f - 170f * u, w / 2f + 260f * u, h / 2f + 170f * u)
        Ui.panel(c, pr, Color.parseColor("#B71C1C"), Color.argb(245, 22, 10, 14), 16f)
        Ui.text(c, "سقط الفارس", pr.centerX(), pr.top + 66f * u, 44f, Color.parseColor("#FF5252"))
        Ui.text(c, "الزمن لم ينتهِ بعد... انهض من جديد", pr.centerX(), pr.top + 104f * u, 18f, Color.rgb(230, 220, 224), Paint.Align.CENTER, false)
        val r1 = RectF(pr.centerX() - 160f * u, pr.top + 132f * u, pr.centerX() + 160f * u, pr.top + 192f * u)
        Ui.button(c, r1, "حاول مرة أخرى", Color.parseColor("#2E7D32"), endT > 0.8f, 21f)
        hits.add(Hit(r1, "retry"))
        val r2 = RectF(pr.centerX() - 160f * u, pr.top + 208f * u, pr.centerX() + 160f * u, pr.top + 268f * u)
        Ui.button(c, r2, "القائمة الرئيسية", Color.parseColor("#455A64"), endT > 0.8f, 21f)
        hits.add(Hit(r2, "title"))
    }

    private fun drawVictory(c: Canvas) {
        val u = Ui.u
        val w = curW.toFloat()
        val h = curH.toFloat()
        Ui.fillRect(c, 0f, 0f, w, h, Color.rgb(10, 8, 24))
        for (i in 0 until 60) {
            val sx = ((i * 97) % 100) / 100f * w
            val sy = (((i * 53) % 100) / 100f * h + endT * (10f + (i % 5) * 6f)) % h
            Ui.circle(c, sx, sy, (1.5f + (i % 3)) * u, Color.argb(200, 255, 240, 180))
        }
        Ui.text(c, "تحرر الزمن", w / 2f, h * 0.25f, 66f, GOLD)
        Ui.text(c, "جمعتَ الشظايا الست وأصلحتَ حجر كرونو", w / 2f, h * 0.25f + 50f * u, 24f, Color.parseColor("#80DEEA"))
        Ui.wrapped(
            c, "لم يكن العدو في أي عصر سوى صدى لقرارٍ قديم. قبلتَ ما حدث، فاستأنف الزمن مساره، وأصبحتَ أنت حارسه الحقيقي.",
            w / 2f - 360f * u, h * 0.25f + 78f * u, 720f * u, 21f, Color.rgb(230, 232, 240), false, 3, true, false
        )
        Ui.icon(c, "icons/coin.png", w / 2f - 70f * u, h * 0.25f + 200f * u, 32f)
        Ui.text(c, "${player.coins}", w / 2f - 46f * u, h * 0.25f + 210f * u, 28f, GOLD, Paint.Align.LEFT)
        val r = RectF(w / 2f - 170f * u, h * 0.25f + 250f * u, w / 2f + 170f * u, h * 0.25f + 312f * u)
        Ui.button(c, r, "القائمة الرئيسية", Color.parseColor("#F9A825"), endT > 0.8f, 22f)
        hits.add(Hit(r, "title"))
    }
}
