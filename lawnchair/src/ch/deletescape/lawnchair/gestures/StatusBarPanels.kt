/*
 *     This file is part of Launcher, a fork of Lawnchair Launcher.
 *
 *     Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */
package ch.deletescape.lawnchair.gestures

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import ch.deletescape.lawnchair.lawnchairApp

/**
 * Opens and closes the notification shade and the quick settings. Uses the status bar service
 * like the system launcher, and the accessibility service of the launcher (if it is switched on)
 * where the status bar service is not available.
 */
object StatusBarPanels {

    fun openNotifications(context: Context): Boolean =
            invoke(context, "expandNotificationsPanel") ||
                    globalAction(context, AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)

    fun openQuickSettings(context: Context): Boolean =
            invoke(context, "expandSettingsPanel") ||
                    globalAction(context, AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)

    fun close(context: Context): Boolean = invoke(context, "collapsePanels")

    @SuppressLint("WrongConstant", "PrivateApi")
    private fun invoke(context: Context, method: String): Boolean {
        return try {
            Class.forName("android.app.StatusBarManager")
                    .getMethod(method)
                    .invoke(context.getSystemService("statusbar"))
            true
        } catch (e: Exception) {
            // ReflectiveOperationException, SecurityException, ...
            false
        }
    }

    private fun globalAction(context: Context, action: Int): Boolean {
        val service = context.lawnchairApp.accessibilityService ?: return false
        return service.performGlobalAction(action)
    }
}
