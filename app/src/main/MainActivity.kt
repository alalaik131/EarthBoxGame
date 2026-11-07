package com.earthbox.game

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

class MainActivity : Activity() {
    private lateinit var gv: GameView
    private lateinit var root: FrameLayout
    private lateinit var hud: TextView
    private lateinit var info: TextView
    private lateinit var logView: TextView
    private lateinit var powerRow: LinearLayout
    private lateinit var brushLabel: TextView
    private var tabIndex = 0
    private val tabViews = ArrayList<LinearLayout>()
    private val powerViews = ArrayList<LinearLayout>()
    private val logs = ArrayList<Pair<String, Long>>()
    private var splash: View? = null

    private val BG = 0xFF0B1526.toInt()
    private val PANEL = 0xE6142A44.toInt()
    private val BORDER = 0xFF2A4468.toInt()
    private val GOLD = 0xFFF5B83A.toInt()
    private val TEXT = 0xFFE8F0FF.toInt()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun saveFile() = File(filesDir, "earthbox.sav")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Sprites.load(this)
        gv = GameView(this)
        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        root.addView(gv, FrameLayout.LayoutParams(-1, -1))
        buildTopBar()
        buildInfo()
        buildDock()
        buildSplash()
        setContentView(root)
        hideSystemBars()

        gv.onHud = { s -> runOnUiThread { hud.text = s } }
        gv.onInfo = { s -> runOnUiThread { showInfo(s) } }
        gv.onStats = { s -> runOnUiThread { if (infoMode == 2) info.text = s } }
        gv.onInfoLog = { s -> runOnUiThread { pushLog(s) } }
        gv.power = TABS[0].powers[4]
        selectTab(0)
        selectPower(TABS[0].powers[4], null)
        gv.newWorld(PRESETS[0], (System.nanoTime() and 0x7FFFFFFF).toInt())
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // ---------- UI building ----------
    private fun bg(color: Int, stroke: Int = BORDER, r: Int = 8): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat(); setStroke(dp(1), stroke) }

    private fun chip(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text; setTextColor(TEXT); textSize = 12f; typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER; setPadding(dp(10), dp(6), dp(10), dp(6))
        background = bg(PANEL); setOnClickListener { onClick() }
        contentDescription = text
    }

    private fun buildTopBar() {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(8), dp(6), dp(8), dp(4))
        hud = TextView(this).apply { setTextColor(TEXT); textSize = 12f; typeface = Typeface.DEFAULT_BOLD; background = bg(PANEL); setPadding(dp(10), dp(6), dp(10), dp(6)); text = "Год 0" }
        bar.addView(hud)
        val sp = View(this); bar.addView(sp, LinearLayout.LayoutParams(0, 1, 1f))
        fun gap() = LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(6) }
        val speeds = ArrayList<TextView>()
        fun speedChip(label: String, v: Int) {
            val self = arrayOfNulls<TextView>(1)
            val t = chip(label) {
                gv.speed = v
                for (s in speeds) s.background = bg(PANEL)
                self[0]?.background = bg(0xFF2A4468.toInt(), GOLD)
            }
            self[0] = t
            speeds.add(t); bar.addView(t, gap())
        }
        speedChip("II", 0); speedChip("1×", 1); speedChip("2×", 2); speedChip("5×", 5)
        speeds[1].background = bg(0xFF2A4468.toInt(), GOLD)
        bar.addView(chip("Статистика") { toggleStats() }, gap())
        bar.addView(chip("Границы") { gv.showBorders = !gv.showBorders }, gap())
        bar.addView(chip("Меню") { showMenu() }, gap())
        root.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
    }

    // 0 = hidden, 1 = inspect text, 2 = stats
    private var infoMode = 0
    private var infoHide = 0L

    private fun buildInfo() {
        info = TextView(this).apply {
            setTextColor(TEXT); textSize = 12f; background = bg(PANEL); setPadding(dp(10), dp(8), dp(10), dp(8)); visibility = View.GONE
        }
        val lp = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { topMargin = dp(48); leftMargin = dp(8) }
        root.addView(info, lp)
        logView = TextView(this).apply { setTextColor(TEXT); textSize = 12f; setShadowLayer(3f, 1f, 1f, Color.BLACK) }
        val lp2 = FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { bottomMargin = dp(118); leftMargin = dp(10) }
        root.addView(logView, lp2)
    }

    private fun showInfo(s: String) {
        infoMode = 1; info.text = s; info.visibility = View.VISIBLE
        val stamp = System.currentTimeMillis(); infoHide = stamp
        info.postDelayed({ if (infoHide == stamp && infoMode == 1) { info.visibility = View.GONE; infoMode = 0 } }, 6000)
    }

    private fun toggleStats() {
        gv.statsOn = !gv.statsOn
        if (gv.statsOn) { infoMode = 2; info.visibility = View.VISIBLE; info.text = "…" }
        else { infoMode = 0; info.visibility = View.GONE }
    }

    private fun pushLog(s: String) {
        logs.add(Pair(s, System.currentTimeMillis()))
        while (logs.size > 5) logs.removeAt(0)
        renderLog()
        logView.postDelayed({ renderLog() }, 8200)
    }

    private fun renderLog() {
        val now = System.currentTimeMillis()
        logs.removeAll { now - it.second > 8000 }
        logView.text = logs.joinToString("\n") { it.first }
    }

    private fun buildDock() {
        val dock = LinearLayout(this)
        dock.orientation = LinearLayout.VERTICAL
        dock.background = bg(0xF0101E33.toInt(), BORDER, 0)
        dock.setPadding(dp(6), dp(4), dp(6), dp(4))

        val row1 = LinearLayout(this); row1.orientation = LinearLayout.HORIZONTAL; row1.gravity = Gravity.CENTER_VERTICAL
        val tabsScroll = HorizontalScrollView(this); tabsScroll.isHorizontalScrollBarEnabled = false
        val tabsRow = LinearLayout(this); tabsRow.orientation = LinearLayout.HORIZONTAL
        for ((n, tab) in TABS.withIndex()) {
            val v = LinearLayout(this); v.orientation = LinearLayout.HORIZONTAL; v.gravity = Gravity.CENTER_VERTICAL
            v.setPadding(dp(8), dp(4), dp(10), dp(4))
            val iv = ImageView(this); Sprites[tab.icon]?.let { iv.setImageBitmap(it) }
            v.addView(iv, LinearLayout.LayoutParams(dp(24), dp(24)))
            val tv = TextView(this); tv.text = tab.name; tv.setTextColor(TEXT); tv.textSize = 12f; tv.typeface = Typeface.DEFAULT_BOLD
            tv.setPadding(dp(5), 0, 0, 0); v.addView(tv)
            v.setOnClickListener { selectTab(n) }
            v.contentDescription = tab.name
            tabViews.add(v)
            tabsRow.addView(v, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(4) })
        }
        tabsScroll.addView(tabsRow)
        row1.addView(tabsScroll, LinearLayout.LayoutParams(0, -2, 1f))
        brushLabel = TextView(this).apply { setTextColor(TEXT); textSize = 12f; typeface = Typeface.DEFAULT_BOLD; text = "Кисть 3"; setPadding(dp(8), 0, dp(8), 0) }
        row1.addView(chip("−") { setBrush(gv.brush - 1) })
        row1.addView(brushLabel)
        row1.addView(chip("+") { setBrush(gv.brush + 1) })
        dock.addView(row1)

        val pScroll = HorizontalScrollView(this); pScroll.isHorizontalScrollBarEnabled = false
        powerRow = LinearLayout(this); powerRow.orientation = LinearLayout.HORIZONTAL
        pScroll.addView(powerRow)
        dock.addView(pScroll, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        root.addView(dock, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    }

    private fun setBrush(v: Int) { gv.brush = v.coerceIn(1, 10); brushLabel.text = "Кисть ${gv.brush}" }

    private fun selectTab(n: Int) {
        tabIndex = n
        for ((i, v) in tabViews.withIndex()) v.background = if (i == n) bg(0xFF2A4468.toInt(), GOLD) else bg(PANEL)
        powerRow.removeAllViews(); powerViews.clear()
        for (p in TABS[n].powers) {
            val v = LinearLayout(this); v.orientation = LinearLayout.VERTICAL; v.gravity = Gravity.CENTER_HORIZONTAL
            v.setPadding(dp(4), dp(4), dp(4), dp(2))
            val iv = ImageView(this); Sprites[p.icon]?.let { iv.setImageBitmap(it) }
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            v.addView(iv, LinearLayout.LayoutParams(dp(40), dp(40)))
            val tv = TextView(this); tv.text = p.name; tv.setTextColor(TEXT); tv.textSize = 9f; tv.gravity = Gravity.CENTER
            v.addView(tv, LinearLayout.LayoutParams(dp(52), -2))
            v.contentDescription = p.name
            v.setOnClickListener { selectPower(p, v) }
            powerViews.add(v)
            powerRow.addView(v, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(4) })
        }
    }

    private fun selectPower(p: Power, v: View?) {
        gv.power = p
        for ((i, pv) in powerViews.withIndex()) {
            pv.background = if (TABS[tabIndex].powers.getOrNull(i) === p) bg(0xFF2A4468.toInt(), GOLD) else bg(PANEL, BORDER)
        }
    }

    private fun buildSplash() {
        val s = LinearLayout(this)
        s.orientation = LinearLayout.VERTICAL; s.gravity = Gravity.CENTER
        s.setBackgroundColor(0xF00B1526.toInt())
        s.isClickable = true
        val logo = ImageView(this); Sprites.logo?.let { logo.setImageBitmap(it) }
        s.addView(logo, LinearLayout.LayoutParams(dp(170), dp(170)))
        val play = chip("Играть") { closeSplash() }
        play.textSize = 18f; play.setPadding(dp(40), dp(10), dp(40), dp(10)); play.background = bg(0xFF2A4468.toInt(), GOLD)
        s.addView(play, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        val cont = chip("Продолжить сохранённую игру") { gv.loadGame(saveFile()); closeSplash() }
        s.addView(cont, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })
        splash = s
        root.addView(s, FrameLayout.LayoutParams(-1, -1))
    }

    private fun closeSplash() {
        splash?.let { root.removeView(it) }
        splash = null
    }

    private fun showMenu() {
        val items = ArrayList<String>()
        for (p in PRESETS) items.add("Новый мир: ${p.name}")
        items.add("Сохранить игру"); items.add("Загрузить игру"); items.add("Вписать мир в экран")
        AlertDialog.Builder(this).setTitle("EarthBox").setItems(items.toTypedArray()) { _, which ->
            when {
                which < PRESETS.size -> gv.newWorld(PRESETS[which], (System.nanoTime() and 0x7FFFFFFF).toInt())
                which == PRESETS.size -> gv.saveGame(saveFile())
                which == PRESETS.size + 1 -> gv.loadGame(saveFile())
                else -> gv.fitCamera()
            }
        }.show()
    }
}
