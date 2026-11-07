package com.earthbox.game

import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

const val W = 192
const val H = 120
const val N = W * H
const val TS = 6
const val ADULT = 1200
const val YEAR = 120

object T {
    const val DEEP = 0; const val SHAL = 1; const val SAND = 2; const val SOIL = 3; const val GRASS = 4
    const val STONE = 5; const val SNOW = 6; const val LAVA = 7; const val BASALT = 8; const val BURNT = 9
}

const val F_CIV = 1
const val F_PREY = 2
const val F_PRED = 4
const val F_MON = 8
const val F_FLY = 16
const val F_MOUNT = 32
const val F_AGG = 64
const val F_FLIP = 128 // sprite faces left natively

class Species(
    val name: String, val spr: String, val h: Float, val speed: Float,
    val hp: Float, val atk: Float, val life: Int, val flags: Int
) {
    fun has(f: Int) = (flags and f) != 0
}

const val HUMAN = 0; const val ELF = 1; const val DWARF = 2; const val ORC = 3
const val PIG = 4; const val COW = 5; const val DEER = 6; const val HORSE = 7; const val WOLF = 8
const val SLIME = 9; const val SPIDER = 10; const val GOLEM = 11; const val DRAGON = 12

val SP = arrayOf(
    Species("Человек", "human2", 11f, .11f, 30f, 5f, 7200, F_CIV),
    Species("Эльф", "elf", 11f, .12f, 26f, 5f, 14400, F_CIV),
    Species("Дворф", "dwarf", 10f, .09f, 42f, 7f, 10000, F_CIV or F_MOUNT),
    Species("Орк", "orc", 12f, .11f, 48f, 8f, 6000, F_CIV or F_AGG),
    Species("Свинья", "a_pair0", 8f, .07f, 14f, 0f, 4500, F_PREY or F_FLIP),
    Species("Корова", "a_pair1", 10f, .06f, 22f, 0f, 5500, F_PREY or F_FLIP),
    Species("Олень", "deer", 11f, .12f, 20f, 0f, 5000, F_PREY or F_FLIP),
    Species("Лошадь", "elk", 12f, .14f, 26f, 0f, 6000, F_PREY or F_FLIP),
    Species("Волк", "a_pair21", 9f, .13f, 24f, 5f, 5000, F_PRED or F_FLIP),
    Species("Слизь", "slime", 9f, .04f, 25f, 3f, 9000, F_MON),
    Species("Паук", "a_trio2", 8f, .12f, 18f, 4f, 5000, F_MON),
    Species("Голем", "a_trio1", 15f, .05f, 150f, 12f, 30000, 0),
    Species("Дракон", "dragon", 24f, .16f, 300f, 25f, 60000, F_MON or F_FLY)
)
val SPECIES_CAP = intArrayOf(0, 0, 0, 0, 120, 120, 140, 90, 40, 30, 30, 10, 3)

val TREE_SPR = arrayOf("", "pine", "oak", "oak2", "pine2", "autumn", "palm", "cactus", "snowtree", "pine_dark", "bush", "snowoak")
val TREE_H = floatArrayOf(0f, 15f, 14f, 14f, 15f, 15f, 16f, 11f, 15f, 15f, 8f, 14f)

val BSPR = arrayOf(
    arrayOf("b_hut", "b_house", "b_house2", "b_castle"),
    arrayOf("e_hut", "e_house", "e_dome", "e_tower"),
    arrayOf("d_hut", "d_house", "d_house2", "d_tower"),
    arrayOf("o_hut", "o_house", "o_house2", "o_castle")
)
val BH = floatArrayOf(16f, 19f, 21f, 26f)
val BCAP = intArrayOf(3, 4, 5, 8)

val KCOLORS = intArrayOf(
    0xFFE6194B.toInt(), 0xFF3CB44B.toInt(), 0xFFFFE119.toInt(), 0xFF4363D8.toInt(), 0xFFF58231.toInt(),
    0xFF911EB4.toInt(), 0xFF46F0F0.toInt(), 0xFFF032E6.toInt(), 0xFFBCF60C.toInt(), 0xFFFABEBE.toInt(),
    0xFF008080.toInt(), 0xFFE6BEFF.toInt()
)
val NAME_A = arrayOf(
    arrayOf("Альд", "Бор", "Вест", "Грен", "Дор", "Кар", "Лин", "Мер", "Торн"),
    arrayOf("Эл", "Ли", "Сил", "Ар", "Фэй", "Ил", "Ор"),
    arrayOf("Брум", "Кхаз", "Тор", "Грим", "Дарг", "Мор"),
    arrayOf("Гор", "Ург", "Мог", "Зар", "Краг", "Шак")
)
val NAME_B = arrayOf(
    arrayOf("гард", "бург", "ленд", "хейм", "ия", "шир"),
    arrayOf("рион", "антир", "ваэль", "дор", "иэль", "орин"),
    arrayOf("дум", "хольм", "гранд", "форж", "каз"),
    arrayOf("тар", "ук", "гаш", "мор", "дук")
)

class Preset(val name: String, val f: Float, val w: Float, val edge: Float)

val PRESETS = listOf(
    Preset("Континенты", 1f / 36f, .44f, .24f),
    Preset("Острова", 1f / 20f, .49f, .22f),
    Preset("Архипелаг", 1f / 14f, .53f, .16f),
    Preset("Пангея", 1f / 44f, .38f, .22f),
    Preset("Пустой мир", 0f, 0f, 0f)
)

object Noise {
    fun hash(x: Int, y: Int, s: Int): Float {
        var h = x * 374761393 + y * 668265263 + s * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) ushr 8) / 16777216f
    }

    fun v(x: Float, y: Float, s: Int): Float {
        val xi = Math.floor(x.toDouble()).toInt(); val yi = Math.floor(y.toDouble()).toInt()
        val xf = x - xi; val yf = y - yi
        val u = xf * xf * (3 - 2 * xf); val w = yf * yf * (3 - 2 * yf)
        val a = hash(xi, yi, s); val b = hash(xi + 1, yi, s)
        val c = hash(xi, yi + 1, s); val d = hash(xi + 1, yi + 1, s)
        return a + (b - a) * u + (c - a) * w + (a - b - c + d) * u * w
    }

    fun fbm(x: Float, y: Float, s: Int): Float {
        var a = .5f; var f = 1f; var t = 0f; var n = 0f
        for (i in 0 until 5) { t += a * v(x * f, y * f, s + i * 17); n += a; a *= .5f; f *= 2f }
        return t / n
    }
}

class Creature(val id: Int, val type: Int, var x: Float, var y: Float) {
    val sp: Species get() = SP[type]
    var hp = SP[type].hp
    var age = 0
    var hunger = 0f
    var k = -1
    var tx = -1f; var ty = -1f
    var task = 0
    var cd = 0; var ttl = 0; var pause = 0
    var face = 1
    var role = 0
    var kills = 0
    var dead = false
    var tgt: Creature? = null
    var breath = 0
}

class Kingdom(val id: Int, val race: Int, val name: String, val color: Int) {
    var food = 30f; var wood = 12f; var tech = 0f
    var pop = 0; var bcount = 0; var cap = 2
    var homeX = 0; var homeY = 0
    var empty = 0
    val enemies = HashMap<Int, Int>()
}

class Building(val id: Int, val x: Int, val y: Int, var k: Int, var lvl: Int) {
    var hp = 0f; var maxHp = 0f
    var ruin = false; var ruinT = 0
    var attacked = 0
}

const val FX_BOOM = 0; const val FX_NUKE = 1; const val FX_BOLT = 2; const val FX_METEOR = 3
const val FX_TORNADO = 4; const val FX_HOLE = 5

class Fx(val type: Int, var x: Float, var y: Float, val max: Int) {
    var t = 0; var vx = 0f; var vy = 0f; var r = 0f
}

class Vol(val x: Int, val y: Int) { var life = 0 }

class Game {
    val tile = IntArray(N)
    val hgt = FloatArray(N)
    val tree = IntArray(N)
    val fire = IntArray(N)
    val owner = IntArray(N) { -1 }
    val bmap = IntArray(N) { -1 }
    val dirtyFlag = BooleanArray(N)
    val dirtyList = IntArray(N)
    var dirtyN = 0
    var territoryDirty = true

    var tick = 0
    var weather = 0 // 0 clear, 1 rain, 2 snow
    var shake = 0f
    var flash = 0f
    val rnd = Random()
    var seed = 1
    var nextId = 1
    var kcounter = 0
    var useCtr = 0

    val creatures = ArrayList<Creature>()
    val buildings = ArrayList<Building>()
    val kingdoms = HashMap<Int, Kingdom>()
    val vols = ArrayList<Vol>()
    val fxs = ArrayList<Fx>()
    var onLog: ((String) -> Unit)? = null

    // particles
    val PMAX = 2500
    val px = FloatArray(PMAX); val py = FloatArray(PMAX)
    val pvx = FloatArray(PMAX); val pvy = FloatArray(PMAX)
    val pl = IntArray(PMAX); val pc = IntArray(PMAX)
    var pn = 0

    // spatial hash
    private val GCS = 6
    private val GW = (W + GCS - 1) / GCS
    private val GH = (H + GCS - 1) / GCS
    private val grid = Array(GW * GH) { ArrayList<Creature>() }
    private val fireList = ArrayList<Int>()
    val counts = IntArray(SP.size)

    fun log(s: String) { onLog?.invoke(s) }
    fun year() = tick / YEAR

    // ---------- helpers ----------
    fun idx(x: Int, y: Int) = y * W + x
    fun inb(x: Int, y: Int) = x in 0 until W && y in 0 until H
    fun clampI(v: Int, lo: Int, hi: Int) = if (v < lo) lo else if (v > hi) hi else v
    fun idxF(x: Float, y: Float) = idx(clampI(x.toInt(), 0, W - 1), clampI(y.toInt(), 0, H - 1))
    fun isLand(t: Int) = t >= T.SAND && t != T.LAVA
    fun warm(i: Int): Float {
        val ny = (i / W).toFloat() / H
        return 1f - abs(ny - .5f) * 2f - max(0f, hgt[i] - .65f) * 1.2f
    }

    fun mark(i: Int) {
        val x = i % W; val y = i / W
        for (dy in -1..1) for (dx in -1..1) {
            val nx = x + dx; val ny = y + dy
            if (inb(nx, ny)) {
                val j = idx(nx, ny)
                if (!dirtyFlag[j]) { dirtyFlag[j] = true; dirtyList[dirtyN++] = j }
            }
        }
    }

    fun setTile(i: Int, t: Int) {
        if (tile[i] == t) return
        tile[i] = t
        if (t <= T.SHAL || t == T.LAVA || t == T.STONE) tree[i] = 0
        if (t <= T.SHAL) fire[i] = 0
        if ((t <= T.SHAL || t == T.LAVA) && bmap[i] >= 0) destroyBuildingAt(i, false)
        mark(i)
    }

    fun particle(x: Float, y: Float, vx: Float, vy: Float, life: Int, color: Int) {
        if (pn >= PMAX) return
        px[pn] = x; py[pn] = y; pvx[pn] = vx; pvy[pn] = vy; pl[pn] = life; pc[pn] = color; pn++
    }

    fun burst(x: Float, y: Float, n: Int, color: Int, spd: Float, life: Int) {
        for (i in 0 until n) {
            val a = rnd.nextFloat() * 6.283f; val s = rnd.nextFloat() * spd
            particle(x * TS, y * TS, cos(a) * s, sin(a) * s - spd * .2f, life / 2 + rnd.nextInt(life / 2 + 1), color)
        }
    }

    private fun updateParticles() {
        var i = 0
        while (i < pn) {
            pl[i]--
            if (pl[i] <= 0) {
                pn--; px[i] = px[pn]; py[i] = py[pn]; pvx[i] = pvx[pn]; pvy[i] = pvy[pn]; pl[i] = pl[pn]; pc[i] = pc[pn]
                continue
            }
            px[i] += pvx[i]; py[i] += pvy[i]; pvy[i] += .03f
            i++
        }
    }

    // ---------- world generation ----------
    fun generate(p: Preset, seedIn: Int) {
        seed = seedIn
        creatures.clear(); buildings.clear(); kingdoms.clear(); vols.clear(); fxs.clear(); fireList.clear()
        tick = 0; weather = 0; pn = 0; kcounter = 0
        java.util.Arrays.fill(tree, 0); java.util.Arrays.fill(fire, 0)
        java.util.Arrays.fill(bmap, -1); java.util.Arrays.fill(owner, -1)
        for (y in 0 until H) for (x in 0 until W) {
            val i = idx(x, y)
            if (p.f == 0f) { tile[i] = T.DEEP; hgt[i] = 0f; continue }
            val nx = x / W.toFloat(); val ny = y / H.toFloat()
            var e = Noise.fbm(x * p.f, y * p.f, seed)
            val d = max(abs(nx * 2 - 1), abs(ny * 2 - 1))
            e -= p.edge * d * d * d
            hgt[i] = e
            val w = p.w
            val moist = Noise.fbm(x / 30f, y / 30f, seed + 101)
            val lat = 1f - abs(ny - .5f) * 2f
            val temp = lat * 1.1f - max(0f, e - w - .1f) * 1.2f + (Noise.fbm(x / 40f, y / 40f, seed + 55) - .5f) * .4f
            var t = when {
                e < w - .08f -> T.DEEP
                e < w -> T.SHAL
                e < w + .03f -> T.SAND
                e < w + .14f -> T.GRASS
                e < w + .21f -> T.STONE
                else -> T.SNOW
            }
            if (t == T.GRASS || t == T.SAND) {
                if (temp < .2f && t == T.GRASS) t = T.SNOW
                else if (temp > .75f && moist < .42f && t == T.GRASS) t = T.SAND
                else if (t == T.GRASS && Noise.fbm(x / 12f, y / 12f, seed + 9) > .68f) t = T.SOIL
            }
            tile[i] = t
            // trees
            if (t == T.GRASS || t == T.SNOW || t == T.SAND) {
                val forest = moist > .5f && Noise.hash(x, y, seed) < (moist - .45f) * 1.9f
                if (t == T.GRASS && forest) tree[i] = pickTree(i, temp)
                else if (t == T.SNOW && e < w + .2f && temp < .2f && Noise.hash(x, y, seed + 3) < .25f) tree[i] = 8
                else if (t == T.SAND && temp > .55f && Noise.hash(x, y, seed + 4) < .02f) tree[i] = if (moist < .45f) 7 else 6
            }
        }
        for (i in 0 until N) { mark(i) }
        if (p.f != 0f) {
            repeat(14) { spawnRandom(PIG, T.GRASS) }; repeat(14) { spawnRandom(COW, T.GRASS) }
            repeat(18) { spawnRandom(DEER, T.GRASS) }; repeat(10) { spawnRandom(HORSE, T.GRASS) }
            repeat(5) { spawnRandom(WOLF, T.GRASS) }
        }
        territoryDirty = true
    }

    private fun pickTree(i: Int, temp: Float): Int {
        val r = rnd.nextFloat()
        return when {
            temp < .35f -> if (r < .6f) 9 else if (r < .85f) 4 else 11
            temp < .7f -> if (r < .3f) 1 else if (r < .55f) 2 else if (r < .75f) 3 else if (r < .88f) 5 else 10
            else -> if (r < .5f) 6 else if (r < .8f) 2 else 10
        }
    }

    private fun spawnRandom(type: Int, t: Int) {
        for (a in 0 until 80) {
            val x = rnd.nextInt(W); val y = rnd.nextInt(H)
            if (tile[idx(x, y)] == t) { spawn(type, x + .5f, y + .5f, -1, true); return }
        }
    }

    // ---------- entities ----------
    fun spawn(type: Int, x: Float, y: Float, k: Int, adult: Boolean): Creature {
        val c = Creature(nextId++, type, x, y)
        c.k = k
        c.age = if (adult) ADULT + rnd.nextInt(2500) else 0
        c.hunger = rnd.nextFloat() * 30
        val r = rnd.nextFloat()
        c.role = if (r < .45f) 0 else if (r < .75f) 1 else if (r < .87f) 2 else 3
        creatures.add(c)
        return c
    }

    fun kill(c: Creature) {
        if (c.dead) return
        c.dead = true
        burst(c.x, c.y, 6, 0xFFB02020.toInt(), 1.2f, 20)
    }

    fun passable(s: Species, x: Float, y: Float): Boolean {
        val ix = x.toInt(); val iy = y.toInt()
        if (!inb(ix, iy)) return false
        if (s.has(F_FLY)) return true
        val t = tile[idx(ix, iy)]
        if (t == T.DEEP || t == T.LAVA) return false
        if (t == T.STONE) return s.has(F_MOUNT)
        return true
    }

    private val COSA = floatArrayOf(1f, .825f, .825f, .362f, .362f, -.227f, -.227f)
    private val SINA = floatArrayOf(0f, .565f, -.565f, .932f, -.932f, .974f, -.974f)

    // returns 0 moved, 1 arrived, 2 blocked
    private fun move(c: Creature, tx: Float, ty: Float, mul: Float): Int {
        val s = c.sp
        val dx = tx - c.x; val dy = ty - c.y
        val d = hypot(dx, dy)
        if (d < .35f) return 1
        var sp = s.speed * mul
        if (tile[idxF(c.x, c.y)] == T.SHAL) sp *= .6f
        val ux = dx / d; val uy = dy / d
        for (a in COSA.indices) {
            val vx = ux * COSA[a] - uy * SINA[a]; val vy = ux * SINA[a] + uy * COSA[a]
            val nx = c.x + vx * sp; val ny = c.y + vy * sp
            if (passable(s, nx, ny)) {
                c.x = nx; c.y = ny
                if (abs(vx) > .1f) c.face = if (vx > 0) 1 else -1
                return 0
            }
        }
        return 2
    }

    private fun wander(c: Creature, r: Float, cx: Float = c.x, cy: Float = c.y) {
        for (a in 0 until 6) {
            val ang = rnd.nextFloat() * 6.283f; val dd = 2 + rnd.nextFloat() * r
            val nx = cx + cos(ang) * dd; val ny = cy + sin(ang) * dd
            if (passable(c.sp, nx, ny)) {
                c.tx = nx; c.ty = ny; c.ttl = 150 + rnd.nextInt(200); c.task = 9; return
            }
        }
        c.pause = 20
    }

    private fun rebuildGrid() {
        for (g in grid) g.clear()
        for (c in creatures) {
            if (c.dead) continue
            val gx = clampI((c.x / GCS).toInt(), 0, GW - 1); val gy = clampI((c.y / GCS).toInt(), 0, GH - 1)
            grid[gy * GW + gx].add(c)
        }
    }

    inline fun forNear(x: Float, y: Float, r: Float, fn: (Creature) -> Unit) {
        val gx0 = clampI(((x - r) / 6).toInt(), 0, (W + 5) / 6 - 1); val gx1 = clampI(((x + r) / 6).toInt(), 0, (W + 5) / 6 - 1)
        val gy0 = clampI(((y - r) / 6).toInt(), 0, (H + 5) / 6 - 1); val gy1 = clampI(((y + r) / 6).toInt(), 0, (H + 5) / 6 - 1)
        for (gy in gy0..gy1) for (gx in gx0..gx1) {
            val l = cell(gx, gy)
            for (n in 0 until l.size) {
                val c = l[n]
                if (!c.dead && hypot(c.x - x, c.y - y) <= r) fn(c)
            }
        }
    }

    fun cell(gx: Int, gy: Int): ArrayList<Creature> = grid[gy * GW + gx]

    fun atWar(a: Int, b: Int): Boolean {
        val ka = kingdoms[a] ?: return false
        val u = ka.enemies[b] ?: return false
        return u > tick
    }

    private fun hostile(a: Creature, o: Creature): Boolean {
        if (o === a || o.dead || o.type == a.type) return false
        if (o.sp.has(F_MON) || o.sp.has(F_PRED)) return true
        if (o.sp.has(F_CIV) && a.k >= 0 && o.k >= 0 && a.k != o.k && atWar(a.k, o.k)) return true
        if (a.sp.has(F_AGG) && o.sp.has(F_CIV) && o.k >= 0 && a.k >= 0 && a.k != o.k && rnd.nextInt(6) == 0) return true
        return false
    }

    private fun findTarget(c: Creature): Creature? {
        val r = if (c.role == 3) 10f else 4f
        var best: Creature? = null; var bd = 1e9f
        forNear(c.x, c.y, r) { o ->
            if (hostile(c, o)) {
                val d = hypot(o.x - c.x, o.y - c.y)
                if (d < bd) { bd = d; best = o }
            }
        }
        return best
    }

    private fun attack(c: Creature, o: Creature) {
        o.hp -= c.sp.atk * (.6f + rnd.nextFloat() * .8f)
        c.cd = 14
        particle(o.x * TS, (o.y - .4f) * TS, 0f, -.3f, 8, 0xFFFFFFFF.toInt())
        if (o.hp <= 0 && !o.dead) { kill(o); c.kills++ }
    }

    // ---------- tick ----------
    fun tick() {
        tick++
        rebuildGrid()
        naturalProcesses()
        updateFire()
        updateFx()
        updateVolcanoes()
        for (i in 0 until creatures.size) updateCreature(creatures[i])
        creatures.removeAll { it.dead }
        if (tick % 20 == 0) updateKingdoms()
        if (tick % 60 == 0) { updateBuildings(); computeTerritory() }
        if (tick % 200 == 0) autoWars()
        if (tick % 40 == 0) animalBreeding()
        updateParticles()
        if (shake > 0) shake = max(0f, shake - .5f)
        if (flash > 0) flash = max(0f, flash - .02f)
    }

    private fun updateCreature(c: Creature) {
        if (c.dead) return
        val s = c.sp
        c.age++
        if (c.cd > 0) c.cd--
        val i = idxF(c.x, c.y)
        if (!s.has(F_FLY)) {
            val t = tile[i]
            if (t == T.DEEP) { c.hp -= 1.2f; if (rnd.nextInt(4) == 0) particle(c.x * TS, c.y * TS, 0f, -.2f, 10, 0xFFBFE6FF.toInt()) }
            if (t == T.LAVA) c.hp -= 8f
            if (fire[i] > 0) c.hp -= .7f
        }
        if (c.hp <= 0) { kill(c); return }
        if (c.age > s.life && rnd.nextInt(200) == 0) { kill(c); return }
        if (c.pause > 0) { c.pause--; return }
        when {
            s.has(F_CIV) -> civAI(c)
            s.has(F_PREY) -> preyAI(c)
            else -> hunterAI(c)
        }
    }

    private fun idxF2(c: Creature) = idxF(c.x, c.y)

    private fun chase(c: Creature, tg: Creature) {
        val d = hypot(tg.x - c.x, tg.y - c.y)
        if (d < 1.4f) { if (c.cd <= 0) attack(c, tg) }
        else if (move(c, tg.x, tg.y, 1.15f) == 2) c.tgt = null
    }

    private fun preyAI(c: Creature) {
        val s = c.sp
        c.hunger += .02f
        if (c.hunger > 120f) c.hp -= .05f
        // flee from hunters
        if (tick % 6 == c.id % 6) {
            var th: Creature? = null; var bd = 1e9f
            forNear(c.x, c.y, 6f) { o ->
                if (o.sp.has(F_PRED) || o.sp.has(F_MON)) {
                    val d = hypot(o.x - c.x, o.y - c.y); if (d < bd) { bd = d; th = o }
                }
            }
            c.tgt = th
        }
        val th = c.tgt
        if (th != null && !th.dead) {
            val fx = c.x + (c.x - th.x) * 3; val fy = c.y + (c.y - th.y) * 3
            move(c, fx, fy, 1.5f); return
        }
        if (c.hunger > 30f && tile[idxF2(c)] == T.GRASS) {
            c.hunger = max(0f, c.hunger - 25f); c.pause = 40
            if (rnd.nextInt(12) == 0) setTile(idxF2(c), T.SOIL)
            return
        }
        if (c.task == 0 || c.task == 9) {
            if (c.ttl-- <= 0) { wander(c, 10f); return }
            if (move(c, c.tx, c.ty, 1f) != 0) { c.task = 0; c.pause = 20 + rnd.nextInt(80) }
        } else wander(c, 10f)
    }

    private fun hunterAI(c: Creature) {
        val s = c.sp
        c.hunger += .02f
        if (s.has(F_PRED) && c.hunger > 120f) c.hp -= .05f
        if (tick % 8 == c.id % 8) {
            var best: Creature? = null; var bd = 1e9f
            val r = if (s.has(F_FLY)) 16f else 10f
            val hungry = c.hunger > 35f || s.has(F_MON)
            if (hungry) forNear(c.x, c.y, r) { o ->
                if (o !== c && !o.dead && o.type != c.type && (o.sp.has(F_CIV) || o.sp.has(F_PREY))) {
                    val d = hypot(o.x - c.x, o.y - c.y); if (d < bd) { bd = d; best = o }
                }
            }
            c.tgt = best
        }
        val tg = c.tgt
        if (tg != null && !tg.dead) {
            val d = hypot(tg.x - c.x, tg.y - c.y)
            if (s.has(F_FLY) && c.breath-- <= 0 && d < 9f) {
                c.breath = 140
                val ix = tg.x.toInt(); val iy = tg.y.toInt()
                for (dy in -2..2) for (dx in -2..2) if (inb(ix + dx, iy + dy)) ignite(idx(ix + dx, iy + dy), 60 + rnd.nextInt(60))
                burst(tg.x, tg.y, 14, 0xFFFF8A20.toInt(), 1.6f, 30)
                tg.hp -= s.atk
                if (tg.hp <= 0) kill(tg)
            }
            if (d < 1.4f) {
                if (c.cd <= 0) {
                    attack(c, tg)
                    if (tg.dead && s.has(F_PRED)) c.hunger = max(0f, c.hunger - 70f)
                }
            } else if (move(c, tg.x, tg.y, 1.15f) == 2) c.tgt = null
            return
        }
        if (s.has(F_FLY) && rnd.nextInt(250) == 0) {
            // dragons raze buildings
            val alive = buildings.filter { !it.ruin }
            val b = if (alive.isEmpty()) null else alive[rnd.nextInt(alive.size)]
            if (b != null) { c.tx = b.x + .5f; c.ty = b.y + .5f; c.ttl = 400; c.task = 9 }
        }
        if (c.task == 9) {
            if (c.ttl-- <= 0 || move(c, c.tx, c.ty, 1f) != 0) {
                c.task = 0; c.pause = 10
                if (s.has(F_FLY)) {
                    val ix = c.x.toInt(); val iy = c.y.toInt()
                    for (dy in -2..2) for (dx in -2..2) if (inb(ix + dx, iy + dy)) {
                        val j = idx(ix + dx, iy + dy); ignite(j, 80)
                        if (bmap[j] >= 0) damageBuilding(buildings[bmap[j]], 40f)
                    }
                }
            }
        } else if (c.ttl-- <= 0) wander(c, 14f)
    }

    private fun civAI(c: Creature) {
        val K: Kingdom? = if (c.k >= 0) kingdoms[c.k] else null
        if (c.k >= 0 && K == null) c.k = -1
        val adult = c.age >= ADULT
        if (adult && tick % 8 == c.id % 8) c.tgt = findTarget(c)
        val tg = c.tgt
        if (tg != null) {
            if (tg.dead) c.tgt = null else if (adult) { chase(c, tg); return }
        }
        // soldiers at war attack enemy buildings
        if (K != null && adult && c.role == 3 && K.enemies.isNotEmpty() && c.task != 5) {
            var best: Building? = null; var bd = 30f
            for (b in buildings) {
                if (b.ruin || !atWar(K.id, b.k)) continue
                val d = hypot(b.x - c.x, b.y - c.y)
                if (d < bd) { bd = d; best = b }
            }
            if (best != null) {
                if (bd < 1.8f) {
                    if (c.cd <= 0) { c.cd = 14; damageBuilding(best, c.sp.atk * 1.5f, K.id) }
                } else move(c, best.x + .5f, best.y + .5f, 1.1f)
                return
            }
        }
        if (K == null) { wandererAI(c, adult); return }
        // jobs
        if (c.task == 0) {
            chooseJob(c, K, adult)
            return
        }
        if (c.ttl-- <= 0) { c.task = 0; return }
        val r = move(c, c.tx, c.ty, 1f)
        if (r == 2) { c.task = 0; return }
        if (r == 1) {
            when (c.task) {
                1 -> K.food += 6f
                2 -> {
                    val i = idxF(c.tx, c.ty)
                    if (tree[i] > 0) { tree[i] = 0; mark(i); K.wood += 5f }
                }
                3 -> tryBuild(K, c.tx.toInt(), c.ty.toInt())
            }
            c.task = 0; c.pause = 15 + rnd.nextInt(50)
        }
    }

    private fun chooseJob(c: Creature, K: Kingdom, adult: Boolean) {
        val hx = K.homeX + .5f; val hy = K.homeY + .5f
        if (!adult) { wander(c, 5f, hx, hy); return }
        when (c.role) {
            1 -> {
                for (a in 0 until 20) {
                    val x = (hx + (rnd.nextFloat() - .5f) * 28).toInt(); val y = (hy + (rnd.nextFloat() - .5f) * 28).toInt()
                    if (inb(x, y) && tree[idx(x, y)] > 0) {
                        c.tx = x + .5f; c.ty = y + .5f; c.task = 2; c.ttl = 400; return
                    }
                }
                wander(c, 6f, hx, hy)
            }
            2 -> {
                val cost = 6f + K.tech.toInt() * 2f
                if (K.wood >= cost && K.pop + 1 >= K.cap - 1) {
                    for (a in 0 until 25) {
                        val rad = 3f + sqrtF(K.bcount.toFloat()) * 2.2f
                        val ang = rnd.nextFloat() * 6.283f; val dd = 2 + rnd.nextFloat() * rad
                        val x = (hx + cos(ang) * dd).toInt(); val y = (hy + sin(ang) * dd).toInt()
                        if (canBuild(K, x, y)) { c.tx = x + .5f; c.ty = y + .5f; c.task = 3; c.ttl = 400; return }
                    }
                }
                c.tx = hx + (rnd.nextFloat() - .5f) * 8; c.ty = hy + (rnd.nextFloat() - .5f) * 8
                if (passable(c.sp, c.tx, c.ty)) { c.task = 1; c.ttl = 300 } else c.pause = 20
            }
            3 -> wander(c, 8f, hx, hy)
            else -> {
                c.tx = hx + (rnd.nextFloat() - .5f) * 10; c.ty = hy + (rnd.nextFloat() - .5f) * 10
                if (passable(c.sp, c.tx, c.ty)) { c.task = 1; c.ttl = 300 } else c.pause = 20
            }
        }
    }

    private fun sqrtF(v: Float) = Math.sqrt(v.toDouble()).toFloat()

    private fun wandererAI(c: Creature, adult: Boolean) {
        if (c.task == 9) {
            if (c.ttl-- <= 0 || move(c, c.tx, c.ty, 1f) != 0) { c.task = 0; c.pause = 20 + rnd.nextInt(60) }
        } else wander(c, 12f)
        if (!adult || tick % 60 != c.id % 60) return
        var nearest: Building? = null; var nd = 1e9f
        for (b in buildings) {
            if (b.ruin) continue
            val d = hypot(b.x - c.x, b.y - c.y)
            if (d < nd) { nd = d; nearest = b }
        }
        if (nearest != null && nd < 14f) {
            val K = kingdoms[nearest.k]
            if (K != null && K.race == c.type && K.pop < K.cap + 2) { c.k = K.id; c.task = 0 }
            return
        }
        if (nearest != null && nd < 10f) return
        val t = tile[idxF(c.x, c.y)]
        if (t == T.SAND || t == T.SOIL || t == T.GRASS || t == T.SNOW) {
            var mates = 0
            forNear(c.x, c.y, 6f) { o -> if (o !== c && o.type == c.type && o.k < 0 && o.age >= ADULT) mates++ }
            if (mates >= 1) foundKingdom(c.type, c.x.toInt(), c.y.toInt(), c)
        }
    }

    // ---------- kingdoms & buildings ----------
    fun foundKingdom(race: Int, x: Int, y: Int, founder: Creature?): Kingdom? {
        val i = idx(x, y)
        if (!isLand(tile[i]) || tile[i] == T.STONE || bmap[i] >= 0) return null
        val name = NAME_A[race][rnd.nextInt(NAME_A[race].size)] + NAME_B[race][rnd.nextInt(NAME_B[race].size)]
        val K = Kingdom(nextId++, race, name, KCOLORS[kcounter++ % KCOLORS.size])
        K.homeX = x; K.homeY = y
        kingdoms[K.id] = K
        placeBuilding(K, x, y, 0)
        founder?.k = K.id
        log("Основано королевство $name (${SP[race].name})")
        return K
    }

    fun placeBuilding(K: Kingdom, x: Int, y: Int, lvl: Int): Building {
        val b = Building(nextId++, x, y, K.id, lvl)
        b.maxHp = 40f + lvl * 25f; b.hp = b.maxHp
        buildings.add(b)
        val i = idx(x, y)
        bmap[i] = buildings.size - 1
        tree[i] = 0
        mark(i)
        territoryDirty = true
        return b
    }

    private fun reindexBuildings() {
        buildings.removeAll { it.ruin && it.ruinT <= 0 }
        java.util.Arrays.fill(bmap, -1)
        for (n in buildings.indices) bmap[idx(buildings[n].x, buildings[n].y)] = n
    }

    fun canBuild(K: Kingdom, x: Int, y: Int): Boolean {
        if (!inb(x, y)) return false
        val i = idx(x, y)
        val t = tile[i]
        if (t != T.SAND && t != T.SOIL && t != T.GRASS && t != T.SNOW && t != T.BASALT && t != T.BURNT) return false
        if (bmap[i] >= 0 || fire[i] > 0) return false
        val o = owner[i]
        if (o >= 0 && o != K.id) return false
        for (b in buildings) if (abs(b.x - x) < 2 && abs(b.y - y) < 2) return false
        return true
    }

    private fun tryBuild(K: Kingdom, x: Int, y: Int) {
        val lvl = min(3, K.tech.toInt())
        val cost = 6f + lvl * 2f
        if (K.wood < cost || !canBuild(K, x, y)) return
        var l = lvl
        if (l == 3) { var c3 = 0; for (b in buildings) if (b.k == K.id && b.lvl == 3 && !b.ruin) c3++; if (c3 >= K.bcount / 6 + 1) l = 2 }
        K.wood -= cost
        placeBuilding(K, x, y, l)
    }

    fun destroyBuildingAt(i: Int, ruin: Boolean) {
        val n = bmap[i]; if (n < 0 || n >= buildings.size) return
        val b = buildings[n]
        if (ruin && !b.ruin) { b.ruin = true; b.ruinT = 3000; b.hp = 0f } else { b.ruin = true; b.ruinT = 0 }
        territoryDirty = true
    }

    fun damageBuilding(b: Building, dmg: Float, by: Int = -1) {
        if (b.ruin) return
        b.hp -= dmg; b.attacked = 40
        burst(b.x + .5f, b.y.toFloat(), 3, 0xFFC0A070.toInt(), .8f, 14)
        if (b.hp <= 0) {
            b.ruin = true; b.ruinT = 3000
            burst(b.x + .5f, b.y.toFloat(), 14, 0xFF808080.toInt(), 1.3f, 30)
            val K = kingdoms[b.k]
            if (K != null) log("Здание королевства ${K.name} разрушено")
            territoryDirty = true
        }
    }

    private fun updateBuildings() {
        for (b in buildings) {
            if (b.ruin) { b.ruinT -= 60; continue }
            val i = idx(b.x, b.y)
            var burning = fire[i] > 0
            if (!burning) for (d in 0 until 4) {
                val nx = b.x + (if (d == 0) 1 else if (d == 1) -1 else 0); val ny = b.y + (if (d == 2) 1 else if (d == 3) -1 else 0)
                if (inb(nx, ny) && fire[idx(nx, ny)] > 0) burning = true
            }
            if (burning) { damageBuilding(b, 12f); if (rnd.nextInt(3) == 0) ignite(i, 50) }
            else if (b.hp < b.maxHp) b.hp = min(b.maxHp, b.hp + 1f)
        }
        reindexBuildings()
    }

    private fun updateKingdoms() {
        val pops = HashMap<Int, Int>()
        for (c in creatures) if (c.k >= 0 && !c.dead) pops[c.k] = (pops[c.k] ?: 0) + 1
        val toRemove = ArrayList<Int>()
        for (K in kingdoms.values) {
            K.pop = pops[K.id] ?: 0
            var bc = 0; var cap = 2
            var home: Building? = null
            for (b in buildings) if (b.k == K.id && !b.ruin) { bc++; cap += BCAP[b.lvl]; if (home == null) home = b }
            K.bcount = bc; K.cap = cap
            if (home != null) { K.homeX = home.x; K.homeY = home.y }
            // war expiry
            val eit = K.enemies.entries.iterator()
            while (eit.hasNext()) {
                val e = eit.next()
                if (e.value <= tick || kingdoms[e.key] == null) { eit.remove() }
            }
            if (K.pop == 0) {
                K.empty += 20
                if (K.empty > 800 || bc == 0) toRemove.add(K.id)
                continue
            }
            K.empty = 0
            K.food = min(300f, K.food) - K.pop * .16f
            if (K.food < 0) {
                K.food = 0f
                val v = creatures.firstOrNull { it.k == K.id && it.age >= ADULT }
                if (v != null && rnd.nextInt(8) == 0) { kill(v) }
            }
            K.tech += K.pop * .0006f
            if (K.pop < K.cap && K.food > 3f && K.pop >= 2 && rnd.nextFloat() < .35f && bc > 0) {
                val c = spawn(K.race, K.homeX + .5f, K.homeY + .8f, K.id, false)
                c.age = 0; K.food -= 1.5f
            }
            val want = min(3, K.tech.toInt())
            if (K.wood >= 22f && rnd.nextInt(12) == 0) {
                val cand = buildings.filter { it.k == K.id && !it.ruin && it.lvl < want }
                if (cand.isNotEmpty()) {
                    var c3 = 0; for (b in buildings) if (b.k == K.id && b.lvl == 3 && !b.ruin) c3++
                    val b = cand[rnd.nextInt(cand.size)]
                    if (b.lvl + 1 < 3 || c3 < bc / 6 + 1) { b.lvl++; b.maxHp = 40f + b.lvl * 25f; b.hp = b.maxHp; K.wood -= 10f }
                }
            }
        }
        for (id in toRemove) {
            val K = kingdoms.remove(id) ?: continue
            for (b in buildings) if (b.k == id && !b.ruin) { b.ruin = true; b.ruinT = 3000 }
            for (c in creatures) if (c.k == id) c.k = -1
            log("Королевство ${K.name} пало")
            territoryDirty = true
        }
    }

    private fun autoWars() {
        val list = kingdoms.values.toList()
        for (a in list) for (b in list) {
            if (a.id >= b.id || atWar(a.id, b.id)) continue
            if (hypot((a.homeX - b.homeX).toFloat(), (a.homeY - b.homeY).toFloat()) > 55f) continue
            val p = if (a.race == ORC || b.race == ORC) .45f else if (a.race != b.race) .25f else .06f
            if (rnd.nextFloat() < p) declareWar(a, b)
        }
    }

    fun declareWar(a: Kingdom, b: Kingdom) {
        val until = tick + 2500 + rnd.nextInt(3500)
        a.enemies[b.id] = until; b.enemies[a.id] = until
        log("${a.name} объявило войну ${b.name}!")
    }

    fun randomWar() {
        val list = kingdoms.values.toList()
        if (list.size < 2) { log("Нужно минимум два королевства"); return }
        for (n in 0 until 20) {
            val a = list[rnd.nextInt(list.size)]; val b = list[rnd.nextInt(list.size)]
            if (a.id != b.id && !atWar(a.id, b.id)) { declareWar(a, b); return }
        }
    }

    fun peace() { for (K in kingdoms.values) K.enemies.clear(); log("Мир во всём мире") }

    fun computeTerritory() {
        java.util.Arrays.fill(owner, -1)
        val best = FloatArray(N) { 1e9f }
        for (b in buildings) {
            if (b.ruin) continue
            val R = 5 + b.lvl * 2
            for (dy in -R..R) for (dx in -R..R) {
                val x = b.x + dx; val y = b.y + dy
                if (!inb(x, y)) continue
                val d = hypot(dx.toFloat(), dy.toFloat())
                if (d > R) continue
                val i = idx(x, y)
                if (tile[i] <= T.SHAL) continue
                if (d < best[i]) { best[i] = d; owner[i] = b.k }
            }
        }
        territoryDirty = true
    }

    // ---------- fire / nature ----------
    fun ignite(i: Int, dur: Int) {
        if (tile[i] <= T.SHAL || tile[i] == T.LAVA || fire[i] > 0) return
        fire[i] = dur; fireList.add(i)
    }

    private fun flammable(j: Int): Float {
        if (tree[j] > 0) return .06f
        if (bmap[j] >= 0) return .04f
        if (tile[j] == T.GRASS) return .012f
        return 0f
    }

    private fun updateFire() {
        if (fireList.isEmpty()) return
        val snapshot = ArrayList(fireList)
        fireList.clear()
        for (i in snapshot) {
            if (fire[i] <= 0) continue
            fire[i]--
            if (weather == 1 && rnd.nextInt(16) == 0) { fire[i] = 0; continue }
            if (fire[i] == 0) {
                if (tree[i] > 0) tree[i] = 0
                if (tile[i] == T.GRASS) tile[i] = T.BURNT
                mark(i); continue
            }
            val x = i % W; val y = i / W
            for (d in 0 until 4) {
                val nx = x + (if (d == 0) 1 else if (d == 1) -1 else 0); val ny = y + (if (d == 2) 1 else if (d == 3) -1 else 0)
                if (!inb(nx, ny)) continue
                val j = idx(nx, ny)
                if (fire[j] == 0 && rnd.nextFloat() < flammable(j)) ignite(j, 40 + rnd.nextInt(50))
            }
            if (rnd.nextInt(10) == 0) particle((x + .5f) * TS, y.toFloat() * TS, (rnd.nextFloat() - .5f) * .3f, -.4f, 22, 0xFF909090.toInt())
            fireList.add(i)
        }
    }

    private fun treeNeighbors(x: Int, y: Int): Int {
        var n = 0
        for (dy in -1..1) for (dx in -1..1) if (inb(x + dx, y + dy) && tree[idx(x + dx, y + dy)] > 0) n++
        return n
    }

    private fun naturalProcesses() {
        val samples = 260 + (if (weather != 0) 70 else 0)
        for (n in 0 until samples) {
            val i = rnd.nextInt(N); val x = i % W; val y = i / W
            when (tile[i]) {
                T.GRASS -> {
                    if (weather == 2 && rnd.nextInt(3) == 0) { setTile(i, T.SNOW); continue }
                    if (tree[i] == 0 && bmap[i] < 0) {
                        val nb = treeNeighbors(x, y)
                        if (nb in 1..3 && warm(i) > .25f && rnd.nextInt(if (weather == 1) 25 else 70) == 0) {
                            tree[i] = pickTree(i, warm(i)); mark(i)
                        }
                    } else if (tree[i] > 0 && rnd.nextInt(250) == 0 && treeNeighbors(x, y) > 4) { tree[i] = 0; mark(i) }
                }
                T.SOIL -> {
                    var g = false
                    for (d in 0 until 4) {
                        val nx = x + (if (d == 0) 1 else if (d == 1) -1 else 0); val ny = y + (if (d == 2) 1 else if (d == 3) -1 else 0)
                        if (inb(nx, ny) && tile[idx(nx, ny)] == T.GRASS) g = true
                    }
                    if (weather == 2) setTile(i, T.SNOW)
                    else if (g && rnd.nextInt(if (weather == 1) 4 else 15) == 0) setTile(i, T.GRASS)
                }
                T.BURNT -> if (rnd.nextInt(8) == 0) setTile(i, T.SOIL)
                T.SAND -> if (weather == 2 && rnd.nextInt(4) == 0) setTile(i, T.SNOW)
                T.SNOW -> if (weather != 2 && warm(i) > .34f && hgt[i] < .8f && rnd.nextInt(30) == 0) setTile(i, T.GRASS)
                T.LAVA -> {
                    var water = false
                    for (d in 0 until 4) {
                        val nx = x + (if (d == 0) 1 else if (d == 1) -1 else 0); val ny = y + (if (d == 2) 1 else if (d == 3) -1 else 0)
                        if (!inb(nx, ny)) continue
                        val j = idx(nx, ny)
                        if (tile[j] <= T.SHAL) water = true
                        else if (flammable(j) > 0f && rnd.nextInt(3) == 0) ignite(j, 60)
                    }
                    if (water) { setTile(i, T.BASALT); burst(x + .5f, y + .5f, 5, 0xFFE0E0E0.toInt(), .7f, 24) }
                    else if (vols.none { hypot((it.x - x).toFloat(), (it.y - y).toFloat()) < 12f && it.life > 0 } && rnd.nextInt(20) == 0) setTile(i, T.BASALT)
                }
            }
        }
        // the sea drowns buildings and ruins vanish handled elsewhere
    }

    private fun animalBreeding() {
        java.util.Arrays.fill(counts, 0)
        for (c in creatures) counts[c.type]++
        for (c in creatures.toList()) {
            val s = c.sp
            if (s.has(F_CIV) || c.dead) continue
            if (c.age > ADULT && c.hunger < 40f && counts[c.type] < SPECIES_CAP[c.type] && rnd.nextInt(18) == 0 && !s.has(F_MON) && c.type != GOLEM) {
                spawn(c.type, c.x, c.y, -1, false); counts[c.type]++
            }
        }
    }

    // ---------- volcanoes ----------
    private fun updateVolcanoes() {
        for (v in vols) {
            if (v.life <= 0) { if (rnd.nextInt(30000) == 0) v.life = 700; continue }
            v.life--
            val rad = 2f + (700 - v.life) / 90f
            for (n in 0 until 2) {
                val ang = rnd.nextFloat() * 6.283f; val d = rnd.nextFloat() * min(rad, 9f)
                val x = (v.x + cos(ang) * d).toInt(); val y = (v.y + sin(ang) * d).toInt()
                if (inb(x, y) && tile[idx(x, y)] > T.SHAL) setTile(idx(x, y), T.LAVA)
            }
            if (rnd.nextInt(2) == 0) particle((v.x + .5f) * TS, (v.y - 1f) * TS, (rnd.nextFloat() - .5f) * .6f, -.9f, 40, if (rnd.nextBoolean()) 0xFF707070.toInt() else 0xFFFF7A1A.toInt())
        }
    }

    // ---------- fx entities ----------
    private fun updateFx() {
        for (f in fxs.toList()) {
            f.t++
            when (f.type) {
                FX_METEOR -> {
                    f.x += f.vx; f.y += f.vy
                    if (f.t >= f.max) { explode(f.x, f.y, 5f, 60f, T.BASALT, .5f); fxs.remove(f); continue }
                }
                FX_TORNADO -> {
                    f.vx += (rnd.nextFloat() - .5f) * .03f; f.vy += (rnd.nextFloat() - .5f) * .03f
                    f.vx = f.vx.coerceIn(-.18f, .18f); f.vy = f.vy.coerceIn(-.18f, .18f)
                    f.x += f.vx; f.y += f.vy
                    if (f.x < 1 || f.y < 1 || f.x > W - 1 || f.y > H - 1) { fxs.remove(f); continue }
                    forNear(f.x, f.y, 4f) { c ->
                        c.hp -= 1.4f; c.x += (f.x - c.x) * .05f; c.y += (f.y - c.y) * .05f
                        if (c.hp <= 0) kill(c)
                    }
                    val cx = f.x.toInt(); val cy = f.y.toInt()
                    for (dy in -3..3) for (dx in -3..3) {
                        if (!inb(cx + dx, cy + dy)) continue
                        val j = idx(cx + dx, cy + dy)
                        if (tree[j] > 0 && rnd.nextInt(8) == 0) { tree[j] = 0; mark(j) }
                        if (bmap[j] >= 0 && rnd.nextInt(10) == 0) damageBuilding(buildings[bmap[j]], 14f)
                    }
                    particle(f.x * TS, f.y * TS, (rnd.nextFloat() - .5f), -.8f, 22, 0xFFB0B0B0.toInt())
                    if (f.t >= f.max) { fxs.remove(f); continue }
                }
                FX_HOLE -> {
                    val r = min(8f, 2f + f.t / 25f)
                    forNear(f.x, f.y, 14f) { c ->
                        val d = hypot(f.x - c.x, f.y - c.y)
                        if (d < 1.2f) kill(c) else { c.x += (f.x - c.x) / d * .12f; c.y += (f.y - c.y) / d * .12f }
                    }
                    val cx = f.x.toInt(); val cy = f.y.toInt(); val ri = r.toInt()
                    for (dy in -ri..ri) for (dx in -ri..ri) {
                        if (dx * dx + dy * dy > r * r || !inb(cx + dx, cy + dy)) continue
                        val j = idx(cx + dx, cy + dy)
                        tree[j] = 0
                        if (bmap[j] >= 0) destroyBuildingAt(j, true)
                        if (dx * dx + dy * dy < r * r * .4f && rnd.nextInt(6) == 0) setTile(j, T.DEEP)
                    }
                    if (f.t >= f.max) { fxs.remove(f); continue }
                }
                else -> if (f.t >= f.max) { fxs.remove(f); continue }
            }
        }
    }

    // ---------- explosions & powers ----------
    fun explode(x: Float, y: Float, r: Float, dmg: Float, center: Int, fireP: Float) {
        fxs.add(Fx(if (r > 12) FX_NUKE else FX_BOOM, x, y, if (r > 12) 140 else 24).also { it.r = r })
        shake = max(shake, min(14f, r))
        if (r > 12) flash = 1f
        forNear(x, y, r) { c ->
            val d = hypot(c.x - x, c.y - y)
            c.hp -= dmg * (1f - d / r * .6f)
            if (c.hp <= 0) kill(c)
        }
        val cx = x.toInt(); val cy = y.toInt(); val ri = r.toInt() + 1
        for (dy in -ri..ri) for (dx in -ri..ri) {
            val d = hypot(dx.toFloat(), dy.toFloat())
            if (d > r || !inb(cx + dx, cy + dy)) continue
            val j = idx(cx + dx, cy + dy)
            if (bmap[j] >= 0) destroyBuildingAt(j, true)
            if (tile[j] <= T.SHAL) continue
            tree[j] = 0
            if (d < r * .35f) setTile(j, center)
            else if (tile[j] == T.GRASS || tile[j] == T.SOIL || tile[j] == T.SNOW || tile[j] == T.SAND) setTile(j, if (rnd.nextInt(3) == 0) T.SOIL else T.BURNT)
            if (rnd.nextFloat() < fireP) ignite(j, 40 + rnd.nextInt(60))
            mark(j)
        }
        burst(x, y, 40, 0xFFFFB030.toInt(), 2.2f, 40)
        burst(x, y, 20, 0xFF606060.toInt(), 1.4f, 50)
    }

    private fun forCircle(cx: Int, cy: Int, r: Int, fn: (Int) -> Unit) {
        for (dy in -r..r) for (dx in -r..r) {
            if (dx * dx + dy * dy > r * r + 1) continue
            val x = cx + dx; val y = cy + dy
            if (inb(x, y)) fn(idx(x, y))
        }
    }

    fun tileOf(id: String): Int = when (id) {
        "t_deep" -> T.DEEP; "t_shal" -> T.SHAL; "t_sand" -> T.SAND; "t_soil" -> T.SOIL; "t_grass" -> T.GRASS
        "t_stone" -> T.STONE; "t_snow" -> T.SNOW; "t_lava" -> T.LAVA; else -> T.BASALT
    }

    fun usePower(id: String, x: Float, y: Float, r: Int, first: Boolean) {
        useCtr++
        val ix = x.toInt(); val iy = y.toInt()
        if (!inb(ix, iy)) return
        when {
            id.startsWith("t_") -> {
                val t = tileOf(id)
                forCircle(ix, iy, r) { i ->
                    setTile(i, t)
                    if (t == T.LAVA && rnd.nextInt(3) == 0) ignite(i, 10)
                }
            }
            id == "tree" -> forCircle(ix, iy, r) { i ->
                if (isLand(tile[i]) && tile[i] != T.STONE && tree[i] == 0 && bmap[i] < 0 && rnd.nextInt(3) == 0) {
                    tree[i] = pickTree(i, warm(i)); mark(i)
                }
            }
            id == "erase" -> {
                forNear(x, y, r.toFloat() + .5f) { c -> c.dead = true }
                forCircle(ix, iy, r) { i ->
                    tree[i] = 0; fire[i] = 0
                    if (bmap[i] >= 0) destroyBuildingAt(i, false)
                    mark(i)
                }
            }
            id.startsWith("sp") -> if (first || useCtr % 8 == 0) {
                val type = id.substring(2).toInt()
                val n = if (first) 1 else 1
                for (a in 0 until n) {
                    val sx = x + (rnd.nextFloat() - .5f) * r; val sy = y + (rnd.nextFloat() - .5f) * r
                    if (passable(SP[type], sx, sy) && creatures.size < 2200) {
                        spawn(type, sx, sy, -1, true)
                        particle(sx * TS, sy * TS, 0f, -.3f, 12, 0xFFFFFFFF.toInt())
                    }
                }
            }
            id == "fire" -> forCircle(ix, iy, r) { i -> if (rnd.nextInt(2) == 0) ignite(i, 50 + rnd.nextInt(50)) }
            id == "acid" -> {
                forNear(x, y, r.toFloat()) { c -> c.hp -= 3f; if (c.hp <= 0) kill(c) }
                forCircle(ix, iy, r) { i ->
                    tree[i] = 0
                    if (tile[i] == T.GRASS || tile[i] == T.SOIL) setTile(i, T.BURNT)
                    if (bmap[i] >= 0) damageBuilding(buildings[bmap[i]], 8f)
                    if (rnd.nextInt(6) == 0) particle((i % W + .5f) * TS, (i / W + .5f) * TS, 0f, -.3f, 14, 0xFF7CFF3C.toInt())
                }
            }
            id == "life" -> {
                forNear(x, y, r + 2f) { c -> c.hp = c.sp.hp }
                forCircle(ix, iy, r + 1) { i ->
                    if (tile[i] == T.SOIL || tile[i] == T.BURNT || tile[i] == T.SAND && rnd.nextInt(3) == 0) setTile(i, T.GRASS)
                    if (bmap[i] >= 0) buildings[bmap[i]].let { it.hp = it.maxHp }
                    fire[i] = 0
                    if (rnd.nextInt(5) == 0) particle((i % W + .5f) * TS, (i / W + .5f) * TS, 0f, -.3f, 18, 0xFF7CFF9C.toInt())
                }
            }
            id == "fert" -> forCircle(ix, iy, r) { i ->
                if (tile[i] == T.SOIL || tile[i] == T.BURNT) setTile(i, T.GRASS)
                if (tile[i] == T.GRASS && tree[i] == 0 && bmap[i] < 0 && rnd.nextInt(6) == 0) { tree[i] = pickTree(i, warm(i)); mark(i) }
            }
            !first -> {}
            id == "lightning" -> {
                fxs.add(Fx(FX_BOLT, x, y, 14))
                explode(x, y, 2f, 40f, T.BURNT, .9f); flash = .35f
            }
            id == "meteor" -> {
                val f = Fx(FX_METEOR, x - 18f, y - 26f, 40)
                f.vx = 18f / 40f; f.vy = 26f / 40f
                fxs.add(f)
            }
            id == "bomb" -> explode(x, y, 6f, 80f, T.SOIL, .6f)
            id == "nuke" -> { explode(x, y, 18f, 400f, T.BASALT, .35f) }
            id == "tornado" -> { val f = Fx(FX_TORNADO, x, y, 700); f.vx = .06f; f.vy = .03f; fxs.add(f) }
            id == "hole" -> fxs.add(Fx(FX_HOLE, x, y, 360))
            id == "quake" -> {
                shake = 14f
                forNear(x, y, 14f) { c -> c.hp -= rnd.nextFloat() * 10f; if (c.hp <= 0) kill(c) }
                for (b in buildings) if (!b.ruin && hypot(b.x - x, b.y - y) < 14f) damageBuilding(b, b.maxHp * .5f * rnd.nextFloat())
                forCircle(ix, iy, 14) { i ->
                    if (rnd.nextInt(14) == 0) {
                        if (tile[i] == T.GRASS) setTile(i, T.SOIL)
                        tree[i] = 0; mark(i)
                    }
                }
                burst(x, y, 30, 0xFF8A6A40.toInt(), 1.6f, 36)
            }
            id == "volcano" -> {
                forCircle(ix, iy, 3) { i -> if (tile[i] <= T.SHAL) tile[i] = T.SAND; setTile(i, T.STONE) }
                setTile(idx(ix, iy), T.LAVA)
                val v = Vol(ix, iy); v.life = 700; vols.add(v)
                shake = 10f; log("Проснулся вулкан!")
            }
            id == "flood" -> {
                forCircle(ix, iy, 10) { i ->
                    val d = hypot((i % W - ix).toFloat(), (i / W - iy).toFloat())
                    if (tile[i] != T.STONE || d < 3) { setTile(i, if (d < 4) T.DEEP else T.SHAL) }
                }
                burst(x, y, 40, 0xFF5AA8FF.toInt(), 2f, 30)
            }
            id == "rain" -> { weather = 1; log("Начался дождь") }
            id == "snowfall" -> { weather = 2; log("Пошёл снег") }
            id == "clear" -> { weather = 0; log("Ясно") }
            id.startsWith("found") -> {
                val race = id.substring(5).toInt()
                for (b in buildings) if (!b.ruin && hypot(b.x - x, b.y - y) < 6f) { log("Слишком близко к другому поселению"); return }
                val K = foundKingdom(race, ix, iy, null)
                if (K == null) { log("Здесь нельзя основать поселение"); return }
                for (n in 0 until 3) { val c = spawn(race, x + (rnd.nextFloat() - .5f) * 2, y + 1f, K.id, true); c.role = n }
                if (K.pop == 0) K.pop = 3
            }
            id == "war" -> randomWar()
            id == "peace" -> peace()
        }
    }

    fun creatureAt(x: Float, y: Float): Creature? {
        var best: Creature? = null; var bd = 2f
        for (c in creatures) { val d = hypot(c.x - x, c.y - y); if (d < bd) { bd = d; best = c } }
        return best
    }

    fun buildingAt(x: Int, y: Int): Building? {
        for (b in buildings) if (abs(b.x - x) <= 1 && abs(b.y - y) <= 1 && !b.ruin) return b
        return null
    }

    // ---------- save / load ----------
    fun save(o: DataOutputStream) {
        o.writeInt(0x45424F58); o.writeInt(1)
        o.writeInt(seed); o.writeInt(tick); o.writeInt(weather); o.writeInt(nextId); o.writeInt(kcounter)
        for (i in 0 until N) { o.writeByte(tile[i]); o.writeByte((hgt[i].coerceIn(0f, 1f) * 255).toInt()); o.writeByte(tree[i]) }
        o.writeInt(kingdoms.size)
        for (K in kingdoms.values) {
            o.writeInt(K.id); o.writeInt(K.race); o.writeUTF(K.name); o.writeInt(K.color)
            o.writeFloat(K.food); o.writeFloat(K.wood); o.writeFloat(K.tech); o.writeInt(K.homeX); o.writeInt(K.homeY)
            o.writeInt(K.enemies.size); for (e in K.enemies) { o.writeInt(e.key); o.writeInt(e.value) }
        }
        val live = buildings.filter { !(it.ruin && it.ruinT <= 0) }
        o.writeInt(live.size)
        for (b in live) { o.writeInt(b.id); o.writeInt(b.x); o.writeInt(b.y); o.writeInt(b.k); o.writeInt(b.lvl); o.writeFloat(b.hp); o.writeBoolean(b.ruin); o.writeInt(b.ruinT) }
        o.writeInt(creatures.size)
        for (c in creatures) { o.writeInt(c.id); o.writeInt(c.type); o.writeFloat(c.x); o.writeFloat(c.y); o.writeFloat(c.hp); o.writeInt(c.age); o.writeInt(c.k); o.writeInt(c.role); o.writeFloat(c.hunger) }
        o.writeInt(vols.size); for (v in vols) { o.writeInt(v.x); o.writeInt(v.y); o.writeInt(v.life) }
    }

    fun load(i: DataInputStream): Boolean {
        if (i.readInt() != 0x45424F58) return false
        if (i.readInt() != 1) return false
        seed = i.readInt(); tick = i.readInt(); weather = i.readInt(); nextId = i.readInt(); kcounter = i.readInt()
        for (n in 0 until N) { tile[n] = i.readUnsignedByte(); hgt[n] = i.readUnsignedByte() / 255f; tree[n] = i.readUnsignedByte() }
        java.util.Arrays.fill(fire, 0); fireList.clear(); fxs.clear(); pn = 0
        kingdoms.clear(); buildings.clear(); creatures.clear(); vols.clear()
        repeat(i.readInt()) {
            val K = Kingdom(i.readInt(), i.readInt(), i.readUTF(), i.readInt())
            K.food = i.readFloat(); K.wood = i.readFloat(); K.tech = i.readFloat(); K.homeX = i.readInt(); K.homeY = i.readInt()
            repeat(i.readInt()) { K.enemies[i.readInt()] = i.readInt() }
            kingdoms[K.id] = K
        }
        repeat(i.readInt()) {
            val b = Building(i.readInt(), i.readInt(), i.readInt(), i.readInt(), i.readInt())
            b.maxHp = 40f + b.lvl * 25f; b.hp = i.readFloat(); b.ruin = i.readBoolean(); b.ruinT = i.readInt()
            buildings.add(b)
        }
        repeat(i.readInt()) {
            val c = Creature(i.readInt(), i.readInt(), i.readFloat(), i.readFloat())
            c.hp = i.readFloat(); c.age = i.readInt(); c.k = i.readInt(); c.role = i.readInt(); c.hunger = i.readFloat()
            creatures.add(c)
        }
        repeat(i.readInt()) { val v = Vol(i.readInt(), i.readInt()); v.life = i.readInt(); vols.add(v) }
        java.util.Arrays.fill(bmap, -1)
        for (n in buildings.indices) bmap[idx(buildings[n].x, buildings[n].y)] = n
        for (n in 0 until N) mark(n)
        computeTerritory()
        return true
    }
}
