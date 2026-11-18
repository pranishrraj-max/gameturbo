package com.gameturbo.m12

import android.content.Context

enum class Profile(val label: String, val desc: String, val pollMs: Long, val dnd: Boolean?, val brightnessCap: Int?) {
    PERFORMANCE("Performance", "Stops background apps, turns on Do Not Disturb (if allowed), slower UI polling so the booster itself uses less CPU.", 2000, true, null),
    BALANCED("Balanced", "Stops background apps once; leaves brightness and notifications alone.", 1500, null, null),
    SAVER("Battery Saver", "Stops background apps, caps brightness at ~40% (needs Modify System Settings) and polls least often.", 3000, null, 100)
}

class Prefs(c: Context) {
    private val p = c.getSharedPreferences("gt", 0)
    var side: String get() = p.getString("side", "right")!! ; set(v) = p.edit().putString("side", v).apply()
    var alpha: Int get() = p.getInt("alpha", 70) ; set(v) = p.edit().putInt("alpha", v).apply()
    var always: Boolean get() = p.getBoolean("always", false) ; set(v) = p.edit().putBoolean("always", v).apply()
    var fps: Boolean get() = p.getBoolean("fps", false) ; set(v) = p.edit().putBoolean("fps", v).apply()
    var profile: Int get() = p.getInt("profile", 1) ; set(v) = p.edit().putInt("profile", v).apply()
    var handleY: Int get() = p.getInt("hy", 0) ; set(v) = p.edit().putInt("hy", v).apply()
    var scanned: Boolean get() = p.getBoolean("scanned", false) ; set(v) = p.edit().putBoolean("scanned", v).apply()
    var games: Set<String> get() = HashSet(p.getStringSet("games", emptySet())!!) ; set(v) = p.edit().putStringSet("games", HashSet(v)).apply()
}
