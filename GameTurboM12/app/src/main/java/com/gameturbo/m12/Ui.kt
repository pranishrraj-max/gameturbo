package com.gameturbo.m12

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

object Col {
    val BG = Color.parseColor("#07090F"); val CARD = Color.parseColor("#121826")
    val GREEN = Color.parseColor("#39FF88"); val BLUE = Color.parseColor("#2DA8FF"); val PURPLE = Color.parseColor("#A55BFF")
    val TEXT = Color.parseColor("#E8ECF5"); val DIM = Color.parseColor("#7C879C")
}

fun rounded(color: Int, r: Float, stroke: Int = 0, sw: Int = 0) = GradientDrawable().apply {
    setColor(color); cornerRadius = r; if (stroke != 0) setStroke(sw, stroke)
}

fun tv(c: Context, text: String, size: Float, color: Int = Col.TEXT, bold: Boolean = false) = TextView(c).apply {
    this.text = text; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
}

/** Cheap press feedback (one 80 ms scale) – no continuous animations, so zero idle CPU cost. */
fun View.pressable(onClick: () -> Unit) {
    isClickable = true
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(.94f).scaleY(.94f).setDuration(80).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }
        false
    }
    setOnClickListener { onClick() }
}

class Bar(c: Context, private val color: Int) : View(c) {
    var frac = 0f; set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(cv: Canvas) {
        val r = height / 2f
        p.color = Color.argb(50, 255, 255, 255); cv.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, p)
        p.color = color; cv.drawRoundRect(0f, 0f, width * frac, height.toFloat(), r, r, p)
    }
}

class Tile(c: Context, label: String, accent: Int) : LinearLayout(c) {
    private val value = tv(c, "…", 14f, accent, true)
    private val bar = Bar(c, accent)
    init {
        orientation = VERTICAL; setPadding(c.dp(10), c.dp(8), c.dp(10), c.dp(8))
        background = rounded(Col.CARD, c.dp(12).toFloat(), Color.argb(40, 255, 255, 255), 1)
        addView(tv(c, label, 10f, Col.DIM, true)); addView(value)
        addView(bar, LayoutParams(-1, c.dp(4)).apply { topMargin = c.dp(5) })
    }
    fun show(v: String, f: Float) { value.text = v; bar.frac = f }
}

class Dash(private val ctx: Context) : LinearLayout(ctx) {
    private val ram = Tile(ctx, "RAM", Col.GREEN); private val sto = Tile(ctx, "STORAGE", Col.BLUE)
    private val bat = Tile(ctx, "BATTERY", Col.GREEN); private val tmp = Tile(ctx, "TEMP  BAT · CPU", Col.PURPLE)
    private val png = Tile(ctx, "PING", Col.BLUE); private val fps = Tile(ctx, "GAME FPS", Col.PURPLE)
    private val cpu = Tile(ctx, "CPU CLOCK", Col.GREEN); private val hz = Tile(ctx, "DISPLAY", Col.BLUE)
    init {
        orientation = VERTICAL
        listOf(ram to sto, bat to tmp, png to fps, cpu to hz).forEach { (a, b) ->
            addView(LinearLayout(ctx).apply {
                val lp = { LayoutParams(0, -2, 1f).apply { setMargins(ctx.dp(3), ctx.dp(3), ctx.dp(3), ctx.dp(3)) } }
                addView(a, lp()); addView(b, lp())
            })
        }
    }
    fun update(s: Snap, ping: Int?, fpsV: Int?) {
        val gb = 1073741824f
        ram.show("%.1f / %.1f GB".format(s.ramUsed / gb, s.ramTotal / gb), s.ramUsed.toFloat() / s.ramTotal)
        sto.show("%.0f / %.0f GB".format(s.stUsed / gb, s.stTotal / gb), s.stUsed.toFloat() / s.stTotal)
        bat.show("${s.batt}%" + if (s.charging) " ⚡" else "", s.batt / 100f)
        tmp.show("%.1f°".format(s.battTemp) + (s.cpuTemp?.let { " · %.0f°C".format(it) } ?: "C"), s.battTemp / 50f)
        png.show(ping?.let { "$it ms" } ?: "…", (ping ?: 0) / 200f)
        fps.show(fpsV?.toString() ?: if (Fps.granted(ctx)) "–" else "ADB setup", (fpsV ?: 0) / 90f)
        cpu.show(s.cpuMHz?.let { "$it MHz" } ?: "restricted", (s.cpuMHz ?: 0) / 2000f)
        val r = (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.refreshRate
        hz.show("%.0f Hz".format(r), r / 90f)
    }
}

fun profileChips(c: Context, prefs: Prefs, onPick: (Profile) -> Unit): LinearLayout {
    val row = LinearLayout(c); val chips = ArrayList<TextView>()
    fun paint() = chips.forEachIndexed { i, t ->
        val sel = i == prefs.profile
        t.background = rounded(if (sel) Color.argb(60, 57, 255, 136) else Col.CARD, c.dp(12).toFloat(), if (sel) Col.GREEN else Color.TRANSPARENT, c.dp(1))
        t.setTextColor(if (sel) Col.GREEN else Col.DIM)
    }
    Profile.values().forEachIndexed { i, p ->
        val t = tv(c, p.label, 11f, Col.DIM, true).apply { gravity = Gravity.CENTER; setPadding(0, c.dp(10), 0, c.dp(10)) }
        t.pressable { prefs.profile = i; paint(); onPick(p) }
        chips.add(t); row.addView(t, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(c.dp(2), 0, c.dp(2), 0) })
    }
    paint(); return row
}
