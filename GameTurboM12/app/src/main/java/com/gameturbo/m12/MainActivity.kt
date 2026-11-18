package com.gameturbo.m12

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var dash: Dash
    private lateinit var setupBox: LinearLayout
    private lateinit var gamesBox: LinearLayout
    private lateinit var result: TextView
    private lateinit var profileDesc: TextView
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            dash.update(Sys.snap(this@MainActivity), Sys.ping, Fps.value); Sys.pingAsync()
            h.postDelayed(this, 2500)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        window.statusBarColor = Col.BG; window.navigationBarColor = Col.BG
        if (!prefs.scanned) { prefs.games = prefs.games + Games.scan(this); prefs.scanned = true }
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        build()
    }

    override fun onResume() { super.onResume(); refreshSetup(); refreshGames(); h.post(tick) }
    override fun onPause() { super.onPause(); h.removeCallbacks(tick) }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12))
        background = rounded(Col.CARD, dp(16).toFloat(), Color.argb(40, 255, 255, 255), 1)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
    }
    private fun title(s: String, c: Int = Col.GREEN) = tv(this, s, 12f, c, true).apply { letterSpacing = .1f; setPadding(0, dp(14), 0, dp(4)) }
    private fun switchRow(label: String, on: Boolean, f: (Boolean) -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(tv(this@MainActivity, label, 14f), LinearLayout.LayoutParams(0, -2, 1f))
        val s = Switch(this@MainActivity).apply { isChecked = on }
        s.setOnClickListener { f(s.isChecked) }; addView(s)
    }

    private fun build() {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(24), dp(16), dp(32)) }
        col.addView(tv(this, "GAME TURBO", 28f, Col.GREEN, true).apply { letterSpacing = .12f })
        col.addView(tv(this, "Galaxy M12 · 4 GB · lightweight, no root", 12f, Col.DIM))

        // service master switch
        col.addView(card().apply {
            addView(switchRow("Floating Game Turbo sidebar", OverlayService.inst != null) { on ->
                if (!on) OverlayService.stop(this@MainActivity)
                else if (!Settings.canDrawOverlays(this@MainActivity)) { toast("Allow “Display over other apps” first"); requestOverlay() ; refreshSetup() }
                else OverlayService.start(this@MainActivity)
            })
        })

        // one-tap boost
        val boost = tv(this, "⚡  ONE-TAP BOOST", 18f, Color.BLACK, true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(18), 0, dp(18))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Col.GREEN, Col.BLUE)).apply { cornerRadius = dp(18).toFloat() }
        }
        result = tv(this, "Measures real available RAM before and after.", 12f, Col.DIM).apply { setPadding(dp(4), dp(6), 0, 0) }
        boost.pressable {
            boost.text = "Optimizing…"
            Sys.boost(this, null) { n, f -> boost.text = "⚡  ONE-TAP BOOST"; result.text = Sys.freedText(n, f) }
        }
        col.addView(boost, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }); col.addView(result)

        // profiles
        col.addView(title("PERFORMANCE PROFILE", Col.BLUE))
        profileDesc = tv(this, Profile.values()[prefs.profile].desc, 12f, Col.DIM).apply { setPadding(dp(4), dp(6), 0, 0) }
        col.addView(profileChips(this, prefs) { p ->
            profileDesc.text = p.desc
            Sys.applyProfile(this, p, null) { result.text = it }
        })
        col.addView(profileDesc)

        col.addView(title("LIVE MONITOR", Col.PURPLE))
        dash = Dash(this); col.addView(dash)

        // sidebar settings
        col.addView(title("SIDEBAR"))
        col.addView(card().apply {
            val sideRow = LinearLayout(this@MainActivity)
            val chips = ArrayList<TextView>()
            fun paint() = chips.forEachIndexed { i, t ->
                val sel = (if (i == 0) "left" else "right") == prefs.side
                t.background = rounded(if (sel) Color.argb(60, 57, 255, 136) else Color.argb(30, 255, 255, 255), dp(12).toFloat())
                t.setTextColor(if (sel) Col.GREEN else Col.DIM)
            }
            listOf("◀ Left edge", "Right edge ▶").forEachIndexed { i, s ->
                val t = tv(this@MainActivity, s, 13f, Col.DIM, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10)) }
                t.pressable { prefs.side = if (i == 0) "left" else "right"; paint(); OverlayService.inst?.refreshHandle() }
                chips.add(t); sideRow.addView(t, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
            paint(); addView(sideRow)
            val lbl = tv(this@MainActivity, "Panel opacity ${prefs.alpha}%", 12f, Col.DIM).apply { setPadding(0, dp(12), 0, 0) }
            addView(lbl)
            addView(SeekBar(this@MainActivity).apply {
                max = 70; progress = prefs.alpha - 25
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar, p: Int, u: Boolean) { prefs.alpha = p + 25; lbl.text = "Panel opacity ${p + 25}%" }
                    override fun onStartTrackingTouch(s: SeekBar) {}
                    override fun onStopTrackingTouch(s: SeekBar) {}
                })
            })
            addView(switchRow("Show handle in every app (not only games)", prefs.always) { prefs.always = it; OverlayService.inst?.refreshHandle() })
            addView(switchRow("Floating FPS counter", prefs.fps) { prefs.fps = it; OverlayService.inst?.refreshFps() })
            addView(tv(this@MainActivity, "Tip: drag the handle up/down to reposition it.", 11f, Col.DIM))
        })

        col.addView(title("SETUP", Col.BLUE)); setupBox = card(); col.addView(setupBox)
        col.addView(title("MY GAMES")); gamesBox = card(); col.addView(gamesBox)
        col.addView(tv(this, "Honest limits: no root means no overclocking and no guaranteed FPS gain. Boost only asks Android to stop cached background apps; Android may restart some. CPU temp/clock are shown only if One UI exposes them.",
            11f, Col.DIM).apply { setPadding(dp(4), dp(16), dp(4), 0) })
        setContentView(ScrollView(this).apply { setBackgroundColor(Col.BG); addView(col) })
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun requestOverlay() = startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))

    private fun a11yOn(): Boolean = (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").contains("$packageName/")

    private fun refreshSetup() {
        setupBox.removeAllViews()
        fun row(name: String, ok: Boolean?, action: () -> Unit) {
            setupBox.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(8))
                addView(tv(this@MainActivity, name, 13f), LinearLayout.LayoutParams(0, -2, 1f))
                addView(tv(this@MainActivity, when (ok) { true -> "✔ ON"; false -> "SET UP ›"; null -> "OPEN ›" }, 12f, if (ok == true) Col.GREEN else Col.BLUE, true))
                pressable(action)
            })
        }
        row("Display over other apps (required)", Settings.canDrawOverlays(this)) { requestOverlay() }
        row("Accessibility: game detection, screenshot, recents", a11yOn()) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); toast("Find “Game Turbo M12” and turn it on") }
        row("Do Not Disturb access", getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        row("Modify system settings (brightness)", Settings.System.canWrite(this)) { startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))) }
        row("Ignore battery optimization", getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
        row("Real game FPS (one-time ADB grant)", Fps.granted(this)) {
            AlertDialog.Builder(this).setTitle("Enable real FPS").setMessage("Android does not let normal apps read another app's frame rate. Connect the phone to a PC with USB debugging on and run once:\n\nadb shell pm grant $packageName android.permission.DUMP\n\nThen restart Game Turbo. Without it the FPS tile shows no number (never a fake one).").setPositiveButton("OK", null).show() }
        row("Samsung touch sensitivity", null) { startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)); toast("Display → Touch sensitivity") }
    }

    private fun refreshGames() {
        gamesBox.removeAllViews()
        prefs.games.mapNotNull { pkg -> Games.label(this, pkg)?.let { pkg to it } }.sortedBy { it.second.lowercase() }.forEach { (pkg, name) ->
            gamesBox.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6))
                addView(ImageView(this@MainActivity).apply { setImageDrawable(Games.icon(this@MainActivity, pkg)) }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(12) })
                addView(tv(this@MainActivity, name, 14f), LinearLayout.LayoutParams(0, -2, 1f))
                addView(tv(this@MainActivity, "▶ PLAY", 12f, Col.GREEN, true))
                pressable { Games.launch(this@MainActivity, pkg) }
                setOnLongClickListener {
                    AlertDialog.Builder(this@MainActivity).setMessage("Remove $name from list?").setPositiveButton("Remove") { _, _ -> prefs.games = prefs.games - pkg; refreshGames() }.setNegativeButton("Cancel", null).show(); true }
            })
        }
        if (prefs.games.isEmpty()) gamesBox.addView(tv(this, "No games found yet. Add one manually.", 13f, Col.DIM))
        val btns = LinearLayout(this).apply { setPadding(0, dp(8), 0, 0) }
        btns.addView(tv(this, "＋ ADD GAME", 12f, Col.BLUE, true).apply { gravity = Gravity.CENTER; pressable { addGameDialog() } }, LinearLayout.LayoutParams(0, -2, 1f))
        btns.addView(tv(this, "↻ RESCAN", 12f, Col.PURPLE, true).apply { gravity = Gravity.CENTER
            pressable { prefs.games = prefs.games + Games.scan(this@MainActivity); refreshGames(); toast("Scan complete") } }, LinearLayout.LayoutParams(0, -2, 1f))
        gamesBox.addView(btns)
    }

    private fun addGameDialog() {
        val apps = Games.launcherApps(this).map { it.activityInfo.packageName }.distinct()
            .filter { it != packageName && it !in prefs.games }.mapNotNull { p -> Games.label(this, p)?.let { p to it } }.sortedBy { it.second.lowercase() }
        AlertDialog.Builder(this).setTitle("Add a game").setItems(apps.map { it.second }.toTypedArray()) { _, i ->
            prefs.games = prefs.games + apps[i].first; refreshGames() }.show()
    }
}
