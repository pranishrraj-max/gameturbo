package com.gameturbo.m12

import android.accessibilityservice.AccessibilityService
import android.app.*
import android.content.*
import android.content.res.ColorStateList
import android.graphics.*
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.*
import kotlin.math.abs

class OverlayService : Service() {
    companion object {
        @Volatile var inst: OverlayService? = null
        fun start(c: Context) { c.startForegroundService(Intent(c, OverlayService::class.java)) }
        fun stop(c: Context) { c.stopService(Intent(c, OverlayService::class.java)) }
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private val ui by lazy { ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault) }
    private val h = Handler(Looper.getMainLooper())
    private var handle: View? = null; private var panel: View? = null; private var fpsView: TextView? = null; private var lockView: View? = null
    private var dash: Dash? = null; private var panelRight = true
    private var fgPkg: String? = null

    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY

    override fun onCreate() {
        super.onCreate(); inst = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager; prefs = Prefs(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("gt", "Game Turbo", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        startForeground(1, Notification.Builder(this, "gt").setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Game Turbo is ready").setContentText("Swipe from the screen edge handle while gaming").setContentIntent(pi).build())
        fgPkg = GameAccess.fg
        refreshHandle(); refreshFps()
    }

    override fun onDestroy() {
        inst = null; h.removeCallbacksAndMessages(null); Fps.setWanted(this, false)
        listOf(handle, panel, fpsView, lockView).forEach { v -> v?.let { try { wm.removeView(it) } catch (e: Exception) {} } }
        super.onDestroy()
    }

    fun onForeground(p: String) { fgPkg = p; h.post { if (panel == null) refreshHandle() } }
    private fun showHandle() = prefs.always || GameAccess.inst == null || (fgPkg != null && fgPkg in prefs.games)

    private fun lp(w: Int, hh: Int, flags: Int, grav: Int) = WindowManager.LayoutParams(w, hh, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT).apply {
        gravity = grav; layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
    private fun add(v: View, p: WindowManager.LayoutParams) { try { wm.addView(v, p) } catch (e: Exception) { Toast.makeText(this, "Overlay permission missing", Toast.LENGTH_SHORT).show() } }
    private fun rm(v: View?) { v?.let { try { wm.removeView(it) } catch (e: Exception) {} } }

    // ---------- Edge handle (swipe inward or tap to open) ----------
    fun refreshHandle() {
        rm(handle); handle = null
        if (panel != null || !showHandle()) return
        val right = prefs.side == "right"
        val box = FrameLayout(ui)
        box.addView(View(ui).apply { background = rounded(Color.argb(170, 57, 255, 136), dp(6).toFloat()) },
            FrameLayout.LayoutParams(dp(6), dp(90), if (right) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.START or Gravity.CENTER_VERTICAL))
        val p = lp(dp(30), dp(130), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            (if (right) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL)
        p.y = prefs.handleY
        var x0 = 0f; var y0 = 0f; var base = 0; var drag = false
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        box.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { x0 = e.rawX; y0 = e.rawY; base = p.y; drag = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - x0; val dy = e.rawY - y0
                    if (!drag && abs(dy) > slop && abs(dy) > abs(dx)) drag = true
                    if (drag) { p.y = base + dy.toInt(); try { wm.updateViewLayout(v, p) } catch (_: Exception) {} }
                    else if (abs(dx) > dp(22) && ((right && dx < 0) || (!right && dx > 0))) openPanel()
                }
                MotionEvent.ACTION_UP -> if (drag) prefs.handleY = p.y else if (panel == null && abs(e.rawX - x0) < slop) openPanel()
            }
            true
        }
        handle = box; add(box, p)
    }

    // ---------- Glass panel ----------
    fun openPanel() {
        if (panel != null) return
        rm(handle); handle = null
        panelRight = prefs.side == "right"
        val root = buildPanel(panelRight)
        val p = lp(dp(316), WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            if (panelRight) Gravity.END else Gravity.START)
        if (Build.VERSION.SDK_INT >= 31 && wm.isCrossWindowBlurEnabled) { p.flags = p.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND; p.blurBehindRadius = dp(14) }
        panel = root
        root.translationX = (if (panelRight) dp(316) else -dp(316)).toFloat()
        add(root, p)
        root.animate().translationX(0f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        Fps.setWanted(this, true); Sys.pingAsync(); h.post(poll)
    }

    fun closePanel() {
        val p = panel ?: return; panel = null; dash = null
        h.removeCallbacks(poll)
        p.animate().translationX((if (panelRight) dp(316) else -dp(316)).toFloat()).setDuration(180).withEndAction {
            rm(p); refreshHandle(); Fps.setWanted(this, prefs.fps)
        }.start()
    }

    private val poll = object : Runnable {
        var n = 0
        override fun run() {
            val d = dash ?: return
            d.update(Sys.snap(this@OverlayService), Sys.ping, Fps.value)
            if (n++ % 3 == 0) Sys.pingAsync()
            h.postDelayed(this, Profile.values()[prefs.profile].pollMs)
        }
    }

    private fun dp(v: Int) = ui.dp(v)

    private fun buildPanel(right: Boolean): FrameLayout {
        val root = object : FrameLayout(ui) {
            override fun dispatchTouchEvent(e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_OUTSIDE) { closePanel(); return true }
                return super.dispatchTouchEvent(e)
            }
        }
        root.setPadding(dp(8), dp(8), dp(8), dp(8))
        val sv = ScrollView(ui).apply {
            isVerticalScrollBarEnabled = false
            background = rounded(Color.argb(prefs.alpha * 255 / 100, 8, 10, 18), dp(22).toFloat(), Color.argb(90, 57, 255, 136), dp(1))
            clipToOutline = true
        }
        val col = LinearLayout(ui).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(14)) }
        // header (swipe toward the edge to close)
        val head = LinearLayout(ui).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(tv(ui, "⚡ GAME TURBO", 16f, Col.GREEN, true).apply { letterSpacing = .08f }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(tv(ui, "✕", 18f, Col.DIM).apply { setPadding(dp(10), dp(4), dp(4), dp(4)); pressable { closePanel() } })
        var hx = 0f
        head.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) hx = e.rawX
            else if (e.action == MotionEvent.ACTION_MOVE) { val d = e.rawX - hx; if ((right && d > dp(60)) || (!right && d < -dp(60))) closePanel() }
            true
        }
        col.addView(head)
        col.addView(profileChips(ui, prefs) { p -> Sys.applyProfile(this, p, fgPkg) { Toast.makeText(this, it, Toast.LENGTH_LONG).show() } },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(6) })
        dash = Dash(ui).also { col.addView(it) }
        dash!!.update(Sys.snap(this), Sys.ping, Fps.value)

        val am = getSystemService(AudioManager::class.java)
        col.addView(slider("☀ Brightness", 255, Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128), Col.GREEN) { Sys.setBrightness(this, it) })
        col.addView(slider("🔊 Volume", am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), am.getStreamVolume(AudioManager.STREAM_MUSIC), Col.BLUE) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0) })

        val tools = listOf(
            tool("⚡", "BOOST", Col.GREEN) { Sys.boost(this, fgPkg) { n, f -> toast(Sys.freedText(n, f)) } },
            tool("☾", "DND", Col.PURPLE) { val on = !Sys.dndOn(this); if (Sys.setDnd(this, on)) toast(if (on) "Do Not Disturb on" else "Do Not Disturb off") },
            tool("📷", "SCREENSHOT", Col.BLUE) { global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, 450, null) },
            tool("⏺", "RECORD", Col.PURPLE) { global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, 350, "Tap “Screen recorder” in Quick Settings") },
            tool("🔒", "LOCK TOUCH", Col.GREEN) { closePanel(); h.postDelayed({ showLock() }, 250) },
            tool("⇄", "APP SWITCH", Col.BLUE) { global(AccessibilityService.GLOBAL_ACTION_RECENTS, 250, null) },
            tool("◉", "FPS HUD", Col.PURPLE) { prefs.fps = !prefs.fps; refreshFps(); toast(if (prefs.fps) (if (Fps.granted(this)) "FPS HUD on" else "FPS HUD on – needs one-time ADB grant (see app)") else "FPS HUD off") },
            tool("📶", "PING TEST", Col.BLUE) { Sys.pingAsync(); h.postDelayed({ toast(Sys.ping?.let { "Latency $it ms" } ?: "No network") }, 1700) },
            tool("⚙", "SETTINGS", Col.GREEN) { closePanel(); startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            tool("👆", "TOUCH", Col.PURPLE) { closePanel(); startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); toast("Samsung: Display → Touch sensitivity") }
        )
        tools.chunked(3).forEach { r ->
            col.addView(LinearLayout(ui).apply { r.forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }) } })
        }
        // quick game switch row
        val games = prefs.games.filter { Games.label(this, it) != null && it != fgPkg }.take(8)
        if (games.isNotEmpty()) {
            col.addView(tv(ui, "QUICK SWITCH", 10f, Col.DIM, true).apply { setPadding(dp(4), dp(10), 0, dp(4)) })
            val row = LinearLayout(ui)
            games.forEach { pkg -> row.addView(ImageView(ui).apply { setImageDrawable(Games.icon(this@OverlayService, pkg)); pressable { closePanel(); Games.launch(this@OverlayService, pkg) } },
                LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(8) }) }
            col.addView(HorizontalScrollView(ui).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        }
        sv.addView(col); root.addView(sv); return root
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun tool(glyph: String, label: String, accent: Int, onClick: () -> Unit) = LinearLayout(ui).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(8))
        background = rounded(Col.CARD, dp(14).toFloat(), Color.argb(110, Color.red(accent), Color.green(accent), Color.blue(accent)), dp(1))
        addView(tv(ui, glyph, 20f)); addView(tv(ui, label, 9f, Col.DIM, true).apply { maxLines = 1 })
        pressable(onClick)
    }

    private fun slider(label: String, max: Int, cur: Int, color: Int, onChange: (Int) -> Unit) = LinearLayout(ui).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(8), dp(4), 0)
        addView(tv(ui, label, 11f, Col.DIM, true))
        addView(SeekBar(ui).apply {
            this.max = max; progress = cur
            progressTintList = ColorStateList.valueOf(color); thumbTintList = ColorStateList.valueOf(color)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, user: Boolean) { if (user) onChange(p) }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        })
    }

    private fun global(action: Int, delay: Long, hint: String?) {
        val g = GameAccess.inst
        if (g == null) { toast("Enable Game Turbo in Accessibility settings first"); closePanel()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return }
        closePanel(); h.postDelayed({ g.performGlobalAction(action); hint?.let { toast(it) } }, delay)
    }

    // ---------- Accidental-touch lock ----------
    private fun showLock() {
        if (lockView != null) return
        val v = FrameLayout(ui).apply { setBackgroundColor(0x01000000); isClickable = true }
        v.addView(tv(ui, "🔒 Touch locked – hold here to unlock", 12f, Color.WHITE, true).apply {
            setPadding(dp(16), dp(10), dp(16), dp(10)); background = rounded(Color.argb(150, 0, 0, 0), dp(20).toFloat(), Col.GREEN, dp(1))
            setOnLongClickListener { rm(lockView); lockView = null; true }
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(24) })
        lockView = v
        add(v, lp(-1, -1, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, Gravity.TOP))
        toast("Touch lock on (system gestures still work)")
    }

    // ---------- Floating FPS HUD ----------
    private val fpsTick = object : Runnable {
        override fun run() {
            val v = fpsView ?: return
            v.visibility = if (showHandle()) View.VISIBLE else View.GONE
            v.text = Fps.value?.let { "$it FPS" } ?: "FPS –"
            h.postDelayed(this, 1000)
        }
    }
    fun refreshFps() {
        h.removeCallbacks(fpsTick)
        if (prefs.fps) {
            if (fpsView == null) {
                val t = tv(ui, "FPS –", 12f, Col.GREEN, true).apply { setPadding(dp(10), dp(3), dp(10), dp(3)); background = rounded(Color.argb(140, 0, 0, 0), dp(10).toFloat()) }
                fpsView = t
                add(t, lp(-2, -2, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { y = dp(6) })
            }
            Fps.setWanted(this, true); h.post(fpsTick)
        } else { rm(fpsView); fpsView = null; Fps.setWanted(this, panel != null) }
    }
}
