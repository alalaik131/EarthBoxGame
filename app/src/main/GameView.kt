package com.earthbox.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class Tex(val w: Int, val h: Int, val px: IntArray)

class GameView(ctx: Context) : SurfaceView(ctx), SurfaceHolder.Callback, Runnable {
    val g = Game()
    private var thread: Thread? = null
    @Volatile private var running = false
    private val queue = ConcurrentLinkedQueue<Runnable>()

    @Volatile var speed = 1
    @Volatile var power: Power? = null
    @Volatile var brush = 3
    @Volatile var showBorders = true
    @Volatile var statsOn = false

    var onHud: ((String) -> Unit)? = null
    var onInfo: ((String) -> Unit)? = null
    var onStats: ((String) -> Unit)? = null

    @Volatile var camX = 0f
    @Volatile var camY = 0f
    @Volatile var zoom = 1f
    private var minZoom = .5f

    // touch state
    @Volatile private var down = false
    @Volatile private var firstPending = false
    @Volatile private var tx = 0f
    @Volatile private var ty = 0f
    private var lastTx = 0f
    private var lastTy = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var pinchDist = 0f
    private var pinchCx = 0f
    private var pinchCy = 0f
    private var multi = false
    private var selected: Creature? = null

    // render
    private val TW = W * TS
    private val TH = H * TS
    private val terrPix = IntArray(TW * TH)
    private val terrBmp: Bitmap = Bitmap.createBitmap(TW, TH, Bitmap.Config.ARGB_8888)
    private val borderPix = IntArray(N)
    private val borderBmp: Bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    private val texs = HashMap<String, Tex>()
    private val rowB = Array(H) { ArrayList<Building>() }
    private val rowC = Array(H) { ArrayList<Creature>() }
    private val pNear = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pSharp = Paint()
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val rnd = java.util.Random()
    private var frame = 0
    private var viewW = 1
    private var viewH = 1

    private val TEXMAP = arrayOf(
        arrayOf("t_deep"), arrayOf("t_shallow", "t_water2"), arrayOf("t_sand"), arrayOf("t_dirt"),
        arrayOf("t_grass", "t_grass2"), arrayOf("t_stone", "t_stone2"), arrayOf("t_snow", "t_snow2"),
        arrayOf("t_lava"), arrayOf("t_basalt"), arrayOf("t_scorch")
    )

    init {
        holder.addCallback(this)
        pSharp.isFilterBitmap = false
        for (names in TEXMAP) for (n in names) {
            val b = Sprites[n] ?: continue
            val px = IntArray(b.width * b.height)
            b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
            texs[n] = Tex(b.width, b.height, px)
        }
        g.onLog = { s -> onInfoLog?.invoke(s) }
    }

    var onInfoLog: ((String) -> Unit)? = null

    fun post2(r: Runnable) { queue.add(r) }

    // ---------- lifecycle ----------
    override fun surfaceCreated(h: SurfaceHolder) {
        running = true
        thread = Thread(this, "earthbox-loop").also { it.start() }
    }

    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {
        viewW = w; viewH = hh
        minZoom = min(w.toFloat() / TW, hh.toFloat() / TH) * .8f
        if (camX == 0f && camY == 0f && zoom == 1f) fitCamera()
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        running = false
        try { thread?.join(1000) } catch (_: InterruptedException) {}
    }

    fun fitCamera() {
        zoom = min(viewW.toFloat() / TW, viewH.toFloat() / TH)
        camX = (TW - viewW / zoom) / 2f
        camY = (TH - viewH / zoom) / 2f
    }

    // ---------- commands (thread-safe) ----------
    fun newWorld(p: Preset, seed: Int) = post2(Runnable { g.generate(p, seed); flushAll(); selected = null })

    fun saveGame(f: File) = post2(Runnable {
        try { DataOutputStream(f.outputStream().buffered()).use { g.save(it) }; g.log("Игра сохранена") }
        catch (e: Exception) { g.log("Ошибка сохранения") }
    })

    fun loadGame(f: File) = post2(Runnable {
        try {
            if (!f.exists()) { g.log("Сохранение не найдено"); return@Runnable }
            val ok = DataInputStream(f.inputStream().buffered()).use { g.load(it) }
            flushAll(); g.log(if (ok) "Игра загружена" else "Файл сохранения повреждён")
        } catch (e: Exception) { g.log("Ошибка загрузки") }
    })

    // ---------- input ----------
    private fun toTileX(sx: Float) = (sx / zoom + camX) / TS
    private fun toTileY(sy: Float) = (sy / zoom + camY) / TS

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val p = power
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                multi = false
                lastX = e.x; lastY = e.y
                tx = toTileX(e.x); ty = toTileY(e.y)
                if (p != null && !p.isTool) { down = true; firstPending = true }
                else if (p != null && p.id == "inspect") {
                    val x = tx; val y = ty
                    post2(Runnable { inspectAt(x, y) })
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multi = true; down = false
                if (e.pointerCount >= 2) {
                    pinchDist = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
                    pinchCx = (e.getX(0) + e.getX(1)) / 2; pinchCy = (e.getY(0) + e.getY(1)) / 2
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount >= 2) {
                    val d = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
                    val cx = (e.getX(0) + e.getX(1)) / 2; val cy = (e.getY(0) + e.getY(1)) / 2
                    camX -= (cx - pinchCx) / zoom; camY -= (cy - pinchCy) / zoom
                    if (pinchDist > 10f) zoomAt(cx, cy, d / pinchDist)
                    pinchDist = d; pinchCx = cx; pinchCy = cy
                } else if (!multi) {
                    if (p == null || p.id == "hand") {
                        camX -= (e.x - lastX) / zoom; camY -= (e.y - lastY) / zoom
                    } else if (!p.isTool) { tx = toTileX(e.x); ty = toTileY(e.y) }
                    lastX = e.x; lastY = e.y
                } else { lastX = e.x; lastY = e.y }
                clampCam()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // continue as one-finger pan after a pinch, without painting
                val idx = if (e.actionIndex == 0) 1 else 0
                lastX = e.getX(idx); lastY = e.getY(idx)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { down = false; multi = false }
        }
        return true
    }

    private fun zoomAt(sx: Float, sy: Float, f: Float) {
        val wx = sx / zoom + camX; val wy = sy / zoom + camY
        zoom = (zoom * f).coerceIn(minZoom, 14f)
        camX = wx - sx / zoom; camY = wy - sy / zoom
    }

    private fun clampCam() {
        val vw = viewW / zoom; val vh = viewH / zoom
        camX = if (vw >= TW) (TW - vw) / 2 else camX.coerceIn(-vw * .2f, TW - vw * .8f)
        camY = if (vh >= TH) (TH - vh) / 2 else camY.coerceIn(-vh * .2f, TH - vh * .8f)
    }

    private fun inspectAt(x: Float, y: Float) {
        val c = g.creatureAt(x, y)
        selected = c
        if (c != null) {
            val K = if (c.k >= 0) g.kingdoms[c.k] else null
            onInfo?.invoke("${c.sp.name} · ${c.age / YEAR} лет · HP ${c.hp.toInt()}/${c.sp.hp.toInt()}" +
                (if (K != null) " · ${K.name}" else "") + (if (c.kills > 0) " · убийств: ${c.kills}" else ""))
            return
        }
        val b = g.buildingAt(x.toInt(), y.toInt())
        if (b != null) {
            val K = g.kingdoms[b.k]
            onInfo?.invoke("Здание ур.${b.lvl + 1} · ${K?.name ?: "?"} · HP ${b.hp.toInt()}/${b.maxHp.toInt()}" +
                (if (K != null) " · население ${K.pop}, еда ${K.food.toInt()}, дерево ${K.wood.toInt()}" else ""))
            return
        }
        val i = g.idxF(x, y)
        val names = arrayOf("Глубокая вода", "Мелководье", "Песок", "Земля", "Трава", "Горы", "Снег", "Лава", "Камень", "Пепелище")
        onInfo?.invoke(names[g.tile[i]] + (if (g.tree[i] > 0) " · дерево" else "") + (if (g.owner[i] >= 0) " · ${g.kingdoms[g.owner[i]]?.name ?: ""}" else ""))
    }

    private fun applyTouch() {
        val p = power ?: return
        if (!down || p.isTool) return
        if (firstPending) {
            g.usePower(p.id, tx, ty, brush, true)
            firstPending = false; lastTx = tx; lastTy = ty
        } else if (p.hold) {
            val dx = tx - lastTx; val dy = ty - lastTy
            val dist = hypot(dx, dy)
            val steps = max(1, ceil(dist / max(1f, brush * .6f)).toInt())
            for (s in 1..steps) {
                val f = s / steps.toFloat()
                g.usePower(p.id, lastTx + dx * f, lastTy + dy * f, brush, false)
            }
            lastTx = tx; lastTy = ty
        }
    }

    // ---------- main loop ----------
    override fun run() {
        flushAll()
        var last = System.nanoTime()
        var acc = 0f
        var statT = 0
        while (running) {
            val now = System.nanoTime()
            val dt = min(.1f, (now - last) / 1e9f); last = now
            while (true) { val r = queue.poll() ?: break; r.run() }
            applyTouch()
            val sp = speed
            if (sp > 0) {
                acc += dt * 20f * sp
                var n = 0
                while (acc >= 1f && n < 16) { g.tick(); acc -= 1f; n++ }
                if (acc > 4f) acc = 0f
            }
            if (++statT >= 30) {
                statT = 0
                emitHud()
            }
            draw()
            val spent = (System.nanoTime() - now) / 1_000_000
            if (spent < 14) try { Thread.sleep(14 - spent) } catch (_: InterruptedException) {}
        }
    }

    private fun emitHud() {
        var pop = 0
        java.util.Arrays.fill(g.counts, 0)
        for (c in g.creatures) { g.counts[c.type]++; if (c.sp.has(F_CIV)) pop++ }
        onHud?.invoke("Год ${g.year()} · жителей $pop · существ ${g.creatures.size}")
        if (statsOn) {
            val sb = StringBuilder()
            for (i in SP.indices) if (g.counts[i] > 0) sb.append(SP[i].name).append(": ").append(g.counts[i]).append("   ")
            sb.append('\n')
            val ks = g.kingdoms.values.sortedByDescending { it.pop }
            if (ks.isEmpty()) sb.append("Королевств нет")
            for (K in ks.take(8)) {
                sb.append(K.name).append(" (").append(SP[K.race].name).append(") нас.").append(K.pop)
                    .append(" зд.").append(K.bcount)
                if (K.enemies.isNotEmpty()) sb.append(" ВОЙНА")
                sb.append('\n')
            }
            onStats?.invoke(sb.toString())
        }
    }

    // ---------- terrain painting ----------
    private fun hg(x: Int, y: Int): Float = g.hgt[g.idx(g.clampI(x, 0, W - 1), g.clampI(y, 0, H - 1))]

    private fun drawTile(x: Int, y: Int) {
        val i = g.idx(x, y)
        val t = g.tile[i]
        val names = TEXMAP[t]
        val variant = if (names.size > 1 && Noise.v(x * .2f, y * .2f, 3) > .5f) 1 else 0
        val tex = texs[names[variant]] ?: return
        var f = 1f
        if (t >= T.SAND) {
            f = 1f + (hg(x - 1, y - 1) - hg(x + 1, y + 1)) * 6f + (g.hgt[i] - .5f) * .35f
            if (t == T.STONE || t == T.SNOW) f += (g.hgt[i] - .6f) * .5f
            f = f.coerceIn(.65f, 1.35f)
            if (g.tree[i] > 0) f *= .9f
        }
        var landL = false; var landR = false; var landU = false; var landD = false
        if (t <= T.SHAL) {
            landL = x > 0 && g.tile[i - 1] > T.SHAL; landR = x < W - 1 && g.tile[i + 1] > T.SHAL
            landU = y > 0 && g.tile[i - W] > T.SHAL; landD = y < H - 1 && g.tile[i + W] > T.SHAL
        }
        val tw = tex.w; val th = tex.h
        for (py in 0 until TS) {
            val wy = y * TS + py
            var v = wy % (2 * th); if (v >= th) v = 2 * th - 1 - v
            for (px in 0 until TS) {
                val wx = x * TS + px
                var u = wx % (2 * tw); if (u >= tw) u = 2 * tw - 1 - u
                val c = tex.px[v * tw + u]
                var r = ((c shr 16) and 255) * f; var gg = ((c shr 8) and 255) * f; var b = (c and 255) * f
                if (t <= T.SHAL) {
                    val foam = (landL && px < 2) || (landR && px >= TS - 2) || (landU && py < 2) || (landD && py >= TS - 2)
                    if (foam) { r = r * .5f + 120f; gg = gg * .5f + 125f; b = b * .5f + 127f }
                }
                val ri = if (r > 255f) 255 else r.toInt(); val gi = if (gg > 255f) 255 else gg.toInt(); val bi = if (b > 255f) 255 else b.toInt()
                terrPix[wy * TW + wx] = (255 shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }
    }

    private fun flushDirty() {
        val n = g.dirtyN
        if (n == 0) return
        var x0 = W; var y0 = H; var x1 = 0; var y1 = 0
        for (k in 0 until n) {
            val i = g.dirtyList[k]
            val x = i % W; val y = i / W
            drawTile(x, y)
            g.dirtyFlag[i] = false
            if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
        }
        g.dirtyN = 0
        val px0 = x0 * TS; val py0 = y0 * TS
        terrBmp.setPixels(terrPix, py0 * TW + px0, TW, px0, py0, (x1 - x0 + 1) * TS, (y1 - y0 + 1) * TS)
    }

    private fun flushAll() {
        for (i in 0 until N) if (!g.dirtyFlag[i]) { g.dirtyFlag[i] = true; g.dirtyList[g.dirtyN++] = i }
        flushDirty()
        g.territoryDirty = true
    }

    private fun rebuildBorders() {
        g.territoryDirty = false
        for (y in 0 until H) for (x in 0 until W) {
            val i = y * W + x
            val o = g.owner[i]
            if (o < 0) { borderPix[i] = 0; continue }
            val K = g.kingdoms[o]
            if (K == null) { borderPix[i] = 0; continue }
            val edge = (x > 0 && g.owner[i - 1] != o) || (x < W - 1 && g.owner[i + 1] != o) ||
                (y > 0 && g.owner[i - W] != o) || (y < H - 1 && g.owner[i + W] != o)
            val a = if (edge) 200 else 45
            borderPix[i] = (a shl 24) or (K.color and 0xFFFFFF)
        }
        borderBmp.setPixels(borderPix, 0, W, 0, 0, W, H)
    }

    // ---------- drawing ----------
    private fun spr(c: Canvas, name: String, cx: Float, bottom: Float, h: Float, flip: Boolean = false, alpha: Int = 255) {
        val b = Sprites[name] ?: return
        val w = b.width * h / b.height
        rect.set(cx - w / 2, bottom - h, cx + w / 2, bottom)
        pNear.alpha = alpha
        if (flip) {
            c.save(); c.scale(-1f, 1f, cx, 0f); c.drawBitmap(b, null, rect, pNear); c.restore()
        } else c.drawBitmap(b, null, rect, pNear)
        pNear.alpha = 255
    }

    private fun draw() {
        frame++
        flushDirty()
        if (g.territoryDirty) rebuildBorders()
        val surface = holder.surface
        if (!surface.isValid) return
        var c: Canvas? = null
        try { c = holder.lockHardwareCanvas() } catch (e: Exception) { c = null }
        if (c == null) return
        try { render(c) } finally { holder.unlockCanvasAndPost(c) }
    }

    private fun render(c: Canvas) {
        c.drawColor(0xFF0B1526.toInt())
        c.save()
        var sx = 0f; var sy = 0f
        if (g.shake > 0) { sx = (rnd.nextFloat() - .5f) * g.shake; sy = (rnd.nextFloat() - .5f) * g.shake }
        c.translate(-camX * zoom + sx, -camY * zoom + sy)
        c.scale(zoom, zoom)
        pSharp.isFilterBitmap = zoom < 1f
        c.drawBitmap(terrBmp, 0f, 0f, pSharp)
        if (showBorders) {
            rect.set(0f, 0f, TW.toFloat(), TH.toFloat())
            pSharp.isFilterBitmap = false
            c.drawBitmap(borderBmp, null, rect, pSharp)
        }

        val x0 = max(0, (camX / TS).toInt() - 2); val x1 = min(W - 1, ((camX + viewW / zoom) / TS).toInt() + 2)
        val y0 = max(0, (camY / TS).toInt() - 2); val y1 = min(H - 1, ((camY + viewH / zoom) / TS).toInt() + 4)

        for (y in 0 until H) { rowB[y].clear(); rowC[y].clear() }
        for (b in g.buildings) if (b.y in y0..y1) rowB[b.y].add(b)
        for (cr in g.creatures) {
            val ry = cr.y.toInt()
            if (ry in y0..y1 && cr.x > x0 - 2 && cr.x < x1 + 2) rowC[ry].add(cr)
        }
        for (v in g.vols) if (v.y in y0..y1) spr(c, "big_volcano", (v.x + .5f) * TS, (v.y + 1f) * TS, if (v.life > 0) 30f else 22f)

        for (y in y0..y1) {
            for (x in x0..x1) {
                val i = y * W + x
                val tr = g.tree[i]
                if (tr > 0) {
                    val sz = TREE_H[tr] * (.9f + Noise.hash(x, y, 5) * .2f)
                    spr(c, TREE_SPR[tr], (x + .5f) * TS, (y + 1f) * TS, sz)
                }
                if (g.fire[i] > 0) {
                    val fl = 7f + (frame / 3 + x + y) % 3
                    spr(c, "fx_fire", (x + .5f) * TS, (y + 1f) * TS, fl)
                }
            }
            for (b in rowB[y]) drawBuilding(c, b)
            for (cr in rowC[y]) drawCreature(c, cr)
        }
        drawFx(c)
        // particles
        for (n in 0 until g.pn) {
            pFill.color = g.pc[n]
            val a = min(255, g.pl[n] * 14)
            pFill.alpha = a
            c.drawRect(g.px[n], g.py[n], g.px[n] + 1.6f, g.py[n] + 1.6f, pFill)
        }
        c.restore()

        // screen-space overlays
        if (g.weather != 0) drawWeather(c)
        if (g.flash > 0f) { pFill.color = Color.WHITE; pFill.alpha = (g.flash * 230).toInt().coerceIn(0, 255); c.drawRect(0f, 0f, viewW.toFloat(), viewH.toFloat(), pFill) }
        val p = power
        if (down && p != null && p.brush) {
            pStroke.color = 0xCCFFFFFF.toInt(); pStroke.strokeWidth = 2f
            c.drawCircle((tx * TS - camX) * zoom, (ty * TS - camY) * zoom, brush * TS * zoom, pStroke)
        }
    }

    private fun drawBuilding(c: Canvas, b: Building) {
        val cx = (b.x + .5f) * TS; val by = (b.y + 1f) * TS
        if (b.ruin) { spr(c, "ruin", cx, by, 18f, false, 200); return }
        val K = g.kingdoms[b.k] ?: return
        pSharp.alpha = 255
        val name = BSPR[K.race][b.lvl]
        val h = BH[b.lvl]
        spr(c, name, cx, by, h)
        pFill.color = K.color; pFill.alpha = 255
        c.drawRect(cx + 3f, by - h - 3f, cx + 6f, by - h - 1.5f, pFill)
        if (b.attacked > 0) { b.attacked--; pFill.color = Color.RED; pFill.alpha = 90; c.drawCircle(cx, by - h / 2, h / 2, pFill) }
    }

    private fun drawCreature(c: Canvas, cr: Creature) {
        val s = cr.sp
        val cx = cr.x * TS; val by = cr.y * TS
        val kid = s.has(F_CIV) && cr.age < ADULT
        val h = s.h * (if (kid) .7f else 1f)
        pFill.color = Color.BLACK; pFill.alpha = 60
        c.drawOval(cx - h * .3f, by - 1f, cx + h * .3f, by + 1.2f, pFill)
        val bob = if (cr.pause == 0) Math.sin((frame * .5 + cr.id).toDouble()).toFloat() * .6f else 0f
        val flip = if (s.has(F_FLIP)) cr.face > 0 else false
        spr(c, s.spr, cx, by + bob, h, flip)
        if (cr === selected) { pStroke.color = Color.YELLOW; pStroke.strokeWidth = .8f; c.drawCircle(cx, by - h / 2, h * .7f, pStroke) }
        if (s.has(F_CIV) && cr.k >= 0 && zoom > 2f) {
            val K = g.kingdoms[cr.k]
            if (K != null) { pFill.color = K.color; pFill.alpha = 255; c.drawRect(cx - 1.2f, by - h - 2f, cx + 1.2f, by - h, pFill) }
        }
        if (zoom > 2.5f && cr.hp < s.hp) {
            pFill.color = Color.BLACK; pFill.alpha = 160; c.drawRect(cx - 4f, by - h - 3f, cx + 4f, by - h - 2f, pFill)
            pFill.color = Color.GREEN; pFill.alpha = 255; c.drawRect(cx - 4f, by - h - 3f, cx - 4f + 8f * (cr.hp / s.hp).coerceIn(0f, 1f), by - h - 2f, pFill)
        }
    }

    private fun drawFx(c: Canvas) {
        for (f in g.fxs) {
            val px = f.x * TS; val py = f.y * TS
            when (f.type) {
                FX_BOOM -> {
                    val p = f.t / f.max.toFloat()
                    spr(c, "fx_boom1", px, py + f.r * TS * p * .6f, f.r * TS * (.8f + p * 1.6f), false, ((1f - p) * 255).toInt())
                }
                FX_NUKE -> {
                    val p = f.t / f.max.toFloat()
                    val a = if (p < .7f) 255 else ((1f - p) / .3f * 255).toInt()
                    spr(c, "big_nuke", px, py + 10f, f.r * TS * (1.2f + p * 1.6f), false, a)
                }
                FX_BOLT -> {
                    spr(c, "fx_lightning", px, py + 4f, 110f, false, (255 * (1f - f.t / f.max.toFloat())).toInt())
                }
                FX_METEOR -> spr(c, "fx_tiny0", px, py, 18f)
                FX_TORNADO -> spr(c, "fx_tornado", px, py + 8f, 34f)
                FX_HOLE -> {
                    val r = min(8f, 2f + f.t / 25f)
                    spr(c, "fx_blackhole", px, py + r * TS, r * TS * 2f)
                }
            }
        }
    }

    private fun drawWeather(c: Canvas) {
        if (g.weather == 1) {
            pStroke.color = 0x889AC8FF.toInt(); pStroke.strokeWidth = 1.5f
            for (n in 0 until 90) {
                val x = rnd.nextFloat() * viewW; val y = rnd.nextFloat() * viewH
                c.drawLine(x, y, x - 4f, y + 14f, pStroke)
            }
            pFill.color = 0xFF0B2A5A.toInt(); pFill.alpha = 40; c.drawRect(0f, 0f, viewW.toFloat(), viewH.toFloat(), pFill)
        } else {
            pFill.color = Color.WHITE; pFill.alpha = 200
            for (n in 0 until 80) c.drawCircle(rnd.nextFloat() * viewW, rnd.nextFloat() * viewH, 2f, pFill)
        }
    }
}
