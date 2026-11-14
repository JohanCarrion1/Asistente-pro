package org.asistentepro.asistentepro

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/** User-enabled global actions only: no screen retrieval, event recording or autonomous loops. */
class XylonControlService : AccessibilityService() {
    override fun onServiceConnected() { super.onServiceConnected(); active = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onDestroy() { if (active === this) active = null; super.onDestroy() }
    fun navigate(destination: SystemNavigation): Boolean {
        return performGlobalAction(when (destination) {
        SystemNavigation.Back -> GLOBAL_ACTION_BACK
        SystemNavigation.Home -> GLOBAL_ACTION_HOME
        SystemNavigation.Recents -> GLOBAL_ACTION_RECENTS
        SystemNavigation.Notifications -> GLOBAL_ACTION_NOTIFICATIONS
        SystemNavigation.QuickSettings -> GLOBAL_ACTION_QUICK_SETTINGS
        SystemNavigation.Lock -> if (android.os.Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_LOCK_SCREEN else return false
        })
    }
    companion object {
        @Volatile var active: XylonControlService? = null
            private set
    }
}
