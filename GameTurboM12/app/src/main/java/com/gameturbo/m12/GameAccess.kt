package com.gameturbo.m12

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/** Used only for: foreground-app detection, screenshot / recents / quick-settings global actions. Reads no screen content. */
class GameAccess : AccessibilityService() {
    companion object { @Volatile var inst: GameAccess? = null; @Volatile var fg: String? = null }
    override fun onServiceConnected() { inst = this }
    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val p = e.packageName?.toString() ?: return
        if (p == packageName || p == "com.android.systemui" || p.contains("inputmethod") || p.contains("keyboard")) return
        fg = p; OverlayService.inst?.onForeground(p)
    }
    override fun onInterrupt() {}
    override fun onUnbind(i: android.content.Intent?): Boolean { inst = null; return super.onUnbind(i) }
}
