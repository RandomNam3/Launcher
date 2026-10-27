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

package ch.deletescape.lawnchair.applock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.UserHandle
import ch.deletescape.lawnchair.BlankActivity
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.platform.BiometricCompat
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey

/**
 * Protected apps: opening them from the launcher needs a fingerprint, the face or the screen
 * lock. The lists of hidden and protected apps are protected the same way.
 */
object AppLock {

    private const val REQUEST_CONFIRM_CREDENTIAL = 1050

    fun isProtected(context: Context, packageName: String?, user: UserHandle?): Boolean {
        if (packageName == null) return false
        val protected = context.lawnchairPrefs.protectedAppSet
        if (protected.isEmpty()) return false
        val userHandle = user ?: Process.myUserHandle()
        return protected.any {
            val key = ComponentKey(context, it)
            key.componentName.packageName == packageName && key.user == userHandle
        }
    }

    /**
     * Runs [onSuccess] once the user confirmed their identity. Runs it right away when the device
     * has no screen lock, there is nothing to confirm then.
     */
    fun authenticate(activity: Activity, title: CharSequence, onSuccess: () -> Unit) {
        if (!BiometricCompat.isDeviceSecure(activity)) {
            onSuccess()
            return
        }
        val subtitle = activity.getString(R.string.app_lock_subtitle)
        if (BiometricCompat.supportsPrompt()) {
            BiometricCompat.authenticate(activity, title, subtitle) { success ->
                if (success) onSuccess()
            }
            return
        }
        val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        @Suppress("DEPRECATION")
        val intent: Intent = keyguardManager.createConfirmDeviceCredentialIntent(title, subtitle)
                ?: run { onSuccess(); return }
        BlankActivity.startActivityForResult(activity, intent, REQUEST_CONFIRM_CREDENTIAL, 0) { resultCode, _ ->
            if (resultCode == Activity.RESULT_OK) onSuccess()
        }
    }
}
