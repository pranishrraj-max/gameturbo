package com.gameturbo.m12

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.*
import android.provider.Settings
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

data class Snap(val ramUsed: Long, val ramTotal: Long, val stUsed: Long, val stTotal: Long, val batt: Int,
                val battTemp: Float, val charging: Boolean, val cpuTemp: Float?, val cpuMHz: Int?)

fun sh(vararg a: String): List<String> = try {
    val p = Runtime.getRuntime().exec(a); val r = p.inputStream.bufferedReader().readLines(); p.waitFor(); r
} catch (e: Exception) { emptyList() }

object Sys {
    @Volatile var ping: Int? = null
    @Volatile private var pinging = false

    /** Real TCP handshake time to Cloudflare (1.1.1.1:443), a good proxy for game-server latency. */
    fun pingAsync() {
        if (pinging) return; pinging = true
        Thread {
            ping = try {
                val s = Socket(); val t = SystemClock.elapsedRealtime()
                s.connect(InetSocketAddress("1.1.1.1", 443), 1500)
                val r = (SystemClock.elapsedRealtime() - t).toInt(); s.close(); r
            } catch (e: Exception) { null }
            pinging = false
        }.start()
    }

    private fun num(path: String): Long? = try { File(path).readText().trim().toLong() } catch (e: Exception) { null }

    fun snap(c: Context): Snap {
        val mi = ActivityManager.MemoryInfo(); c.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        val sf = StatFs(Environment.getDataDirectory().path)
        val b = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val sc = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val st = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        var cpuT: Float? = null
        for (i in 0..20) {
            val d = "/sys/class/thermal/thermal_zone$i"
            val type = try { File("$d/type").readText().lowercase() } catch (e: Exception) { continue }
            if (type.contains("cpu") || type.contains("soc") || type.contains("big") || type.contains("little")) {
                val v = num("$d/temp") ?: continue
                val t = if (v > 1000) v / 1000f else v.toFloat()
                if (t in 10f..120f) { cpuT = t; break }
            }
        }
        var f: Long? = null
        for (i in 0..7) { val v = num("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq"); if (v != null && (f == null || v > f)) f = v }
        return Snap(mi.totalMem - mi.availMem, mi.totalMem, sf.totalBytes - sf.availableBytes, sf.totalBytes, lvl * 100 / sc,
            (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL, cpuT, f?.let { (it / 1000).toInt() })
    }

    private fun avail(am: ActivityManager): Long { val mi = ActivityManager.MemoryInfo(); am.getMemoryInfo(mi); return mi.availMem }

    /** Asks Android to stop cached background apps (the only thing a non-root app may do) and measures real available RAM before/after. */
    fun boost(c: Context, keep: String?, cb: (Int, Long) -> Unit) {
        val app = c.applicationContext
        Thread {
            val am = app.getSystemService(ActivityManager::class.java); val pm = app.packageManager
            val before = avail(am)
            val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
            val ime = Settings.Secure.getString(app.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')
            var n = 0
            for (ri in Games.launcherApps(app)) {
                val p = ri.activityInfo.packageName
                if (p == app.packageName || p == keep || p == home || p == ime) continue
                val f = ri.activityInfo.applicationInfo.flags
                if ((f and ApplicationInfo.FLAG_SYSTEM) != 0 && (f and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) continue
                am.killBackgroundProcesses(p); n++
            }
            Thread.sleep(700)
            val freed = avail(am) - before
            Handler(Looper.getMainLooper()).post { cb(n, freed) }
        }.start()
    }

    fun freedText(n: Int, freed: Long) = "asked Android to stop $n background apps · " +
            if (freed > 4_000_000) "+${freed / 1048576} MB RAM available" else "no measurable RAM gain (already clean)"

    fun applyProfile(c: Context, p: Profile, keep: String?, cb: (String) -> Unit) {
        boost(c, keep) { n, freed ->
            val x = StringBuilder()
            if (p.dnd == true && dndAllowed(c)) { setDnd(c, true); x.append(" · DND on") }
            p.brightnessCap?.let { cap ->
                if (Settings.System.canWrite(c) && Settings.System.getInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) > cap) { setBrightness(c, cap); x.append(" · brightness capped") }
            }
            cb("${p.label}: ${freedText(n, freed)}$x")
        }
    }

    private fun nm(c: Context) = c.getSystemService(NotificationManager::class.java)
    fun dndAllowed(c: Context) = nm(c).isNotificationPolicyAccessGranted
    fun dndOn(c: Context) = nm(c).currentInterruptionFilter.let { it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN }
    fun setDnd(c: Context, on: Boolean): Boolean {
        if (!dndAllowed(c)) { c.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return false }
        nm(c).setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL); return true
    }

    fun setBrightness(c: Context, v: Int): Boolean {
        if (!Settings.System.canWrite(c)) {
            c.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${c.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return false
        }
        Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS, v.coerceIn(10, 255)); return true
    }
}

/** Real game FPS from SurfaceFlinger frame-present timestamps. Needs: adb shell pm grant com.gameturbo.m12 android.permission.DUMP */
object Fps {
    @Volatile var value: Int? = null
    @Volatile private var want = false
    @Volatile private var running = false
    private var layer: String? = null; private var layerPkg: String? = null; private var layerAt = 0L

    fun granted(c: Context) = c.checkSelfPermission("android.permission.DUMP") == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun setWanted(c: Context, w: Boolean) {
        want = w
        if (!w) value = null
        if (w && !running && granted(c)) { running = true; Thread { try { while (want) { value = sample(); Thread.sleep(1000) } } finally { running = false } }.start() }
    }

    private fun sample(): Int? {
        val pkg = GameAccess.fg ?: return null
        val now = SystemClock.elapsedRealtime()
        if (layer == null || layerPkg != pkg || now - layerAt > 5000) {
            layerAt = now; layerPkg = pkg
            val l = sh("dumpsys", "SurfaceFlinger", "--list").filter { it.contains(pkg) }
            layer = l.firstOrNull { it.contains("SurfaceView") && !it.contains("Bounds") } ?: l.firstOrNull { !it.contains("Background") }
        }
        val ly = layer ?: return null
        val ts = sh("dumpsys", "SurfaceFlinger", "--latency", ly).drop(1).mapNotNull {
            val p = it.trim().split(Regex("\\s+")); if (p.size == 3) p[1].toLongOrNull() else null
        }.filter { it > 0 && it < Long.MAX_VALUE }
        if (ts.size < 3) return null
        val mx = ts.max(); val win = ts.filter { it >= mx - 1_000_000_000L }
        if (win.size < 2) return null
        val span = win.max() - win.min()
        return if (span > 0) Math.round((win.size - 1) * 1e9 / span).toInt() else null
    }
}
