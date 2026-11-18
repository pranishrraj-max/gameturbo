package com.gameturbo.m12

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.widget.Toast

object Games {
    val KNOWN = setOf(
        "com.pubg.imobile", "com.tencent.ig", "com.pubg.krmobile", "com.dts.freefiremax", "com.dts.freefireth",
        "com.mojang.minecraftpe", "com.activision.callofduty.shooter", "com.roblox.client",
        "com.supercell.clashofclans", "com.supercell.brawlstars", "com.kiloo.subwaysurf", "com.miHoYo.GenshinImpact",
        "com.garena.game.codm", "com.levelinfinite.sgameGlobal", "com.ea.gp.apexlegendsmobilefps", "com.gameloft.android.ANMP.GloftA9HM"
    )

    fun launcherApps(c: Context): List<ResolveInfo> =
        c.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)

    @Suppress("DEPRECATION")
    fun scan(c: Context): Set<String> = launcherApps(c).map { it.activityInfo.packageName to it.activityInfo.applicationInfo }
        .filter { (pkg, ai) -> pkg != c.packageName && (pkg in KNOWN || ai.category == ApplicationInfo.CATEGORY_GAME ||
                (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0) }.map { it.first }.toSet()

    fun label(c: Context, pkg: String): String? = try { c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { null }
    fun icon(c: Context, pkg: String): Drawable? = try { c.packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }

    fun launch(c: Context, pkg: String) {
        val i = c.packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        c.startActivity(i)
        Sys.applyProfile(c, Profile.values()[Prefs(c).profile], pkg) { Toast.makeText(c, it, Toast.LENGTH_LONG).show() }
    }
}
