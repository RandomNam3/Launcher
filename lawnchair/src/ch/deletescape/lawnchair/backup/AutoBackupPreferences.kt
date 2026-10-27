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
package ch.deletescape.lawnchair.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.support.v7.preference.Preference
import android.text.format.DateUtils
import android.util.AttributeSet
import android.widget.Toast
import ch.deletescape.lawnchair.BlankActivity
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.preferences.StyledSwitchPreferenceCompat
import com.android.launcher3.R
import com.android.launcher3.Utilities

private const val REQUEST_PICK_FOLDER = 1071

private fun pickFolder(context: Context, onPicked: () -> Unit) {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    BlankActivity.startActivityForResult(context, intent, REQUEST_PICK_FOLDER, 0) { _, data ->
        @Suppress("DEPRECATION")
        val tree = data?.getParcelable<Uri>(BlankActivity.EXTRA_DATA_URI)
        if (tree != null) {
            AutoBackup.takeFolder(context, tree)
            onPicked()
        }
    }
}

/**
 * Switch for the automatic backups. Asks for the folder when there is none yet.
 */
class AutoBackupSwitchPreference(context: Context, attrs: AttributeSet?) :
        StyledSwitchPreferenceCompat(context, attrs) {

    override fun onClick() {
        if (!isChecked && context.lawnchairPrefs.autoBackupFolder.isEmpty()) {
            pickFolder(context) {
                if (callChangeListener(true)) isChecked = true
            }
            return
        }
        super.onClick()
    }
}

/**
 * The folder for the automatic backups.
 */
class AutoBackupFolderPreference(context: Context, attrs: AttributeSet?) :
        Preference(context, attrs), LawnchairPreferences.OnPreferenceChangeListener {

    private val prefs = context.lawnchairPrefs

    override fun onAttached() {
        super.onAttached()
        prefs.addOnPreferenceChangeListener(this, "pref_autoBackupFolder")
    }

    override fun onDetached() {
        super.onDetached()
        prefs.removeOnPreferenceChangeListener(this, "pref_autoBackupFolder")
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        summary = AutoBackup.folderName(context) ?: context.getString(R.string.auto_backup_no_folder)
    }

    override fun onClick() {
        pickFolder(context) {}
    }
}

/**
 * Shows when the last automatic backup was made, a tap makes one right away.
 */
class AutoBackupNowPreference(context: Context, attrs: AttributeSet?) :
        Preference(context, attrs), LawnchairPreferences.OnPreferenceChangeListener {

    private val prefs = context.lawnchairPrefs
    private val mainHandler = Handler(Looper.getMainLooper())
    private var running = false

    override fun onAttached() {
        super.onAttached()
        prefs.addOnPreferenceChangeListener(this, "pref_autoBackupFolder")
    }

    override fun onDetached() {
        super.onDetached()
        prefs.removeOnPreferenceChangeListener(this, "pref_autoBackupFolder")
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        isEnabled = prefs.autoBackupFolder.isNotEmpty() && !running
        updateSummary()
    }

    private fun updateSummary() {
        val last = AutoBackup.lastBackupTime(context)
        summary = when {
            running -> context.getString(R.string.auto_backup_running)
            AutoBackup.lastBackupFailed(context) -> context.getString(R.string.auto_backup_failed)
            last > 0 -> context.getString(R.string.auto_backup_last,
                    DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS))
            else -> context.getString(R.string.auto_backup_never)
        }
    }

    override fun onClick() {
        if (running) return
        running = true
        isEnabled = false
        updateSummary()
        Utilities.THREAD_POOL_EXECUTOR.execute {
            val success = AutoBackup.backupNow(context)
            mainHandler.post {
                running = false
                isEnabled = true
                updateSummary()
                Toast.makeText(context, if (success) R.string.auto_backup_done
                        else R.string.auto_backup_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
