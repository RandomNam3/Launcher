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

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import ch.deletescape.lawnchair.lawnchairPrefs
import com.android.launcher3.Utilities
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Automatic backups of the home screen and the settings into a folder the user picked, for
 * example on a memory card or in a cloud folder. Only the newest backups are kept.
 */
object AutoBackup {

    private const val TAG = "AutoBackup"
    private const val JOB_ID = 1070
    private const val FILE_PREFIX = "Launcher-auto-"
    private const val KEY_LAST_BACKUP = "auto_backup_last"
    private const val KEY_LAST_ERROR = "auto_backup_last_error"

    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US)

    fun isEnabled(context: Context): Boolean {
        val prefs = context.lawnchairPrefs
        return prefs.autoBackup && prefs.autoBackupFolder.isNotEmpty()
    }

    fun lastBackupTime(context: Context) = Utilities.getDevicePrefs(context).getLong(KEY_LAST_BACKUP, 0)

    fun lastBackupFailed(context: Context) =
            Utilities.getDevicePrefs(context).getBoolean(KEY_LAST_ERROR, false)

    /**
     * Schedules or cancels the daily check that creates a backup when one is due.
     */
    fun schedule(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
                ?: return
        if (!isEnabled(context)) {
            scheduler.cancel(JOB_ID)
            return
        }
        if (scheduler.allPendingJobs.any { it.id == JOB_ID }) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, AutoBackupJobService::class.java))
                .setPeriodic(TimeUnit.HOURS.toMillis(12))
                .setPersisted(true)
        if (Utilities.ATLEAST_OREO) {
            job.setRequiresBatteryNotLow(true)
        }
        try {
            scheduler.schedule(job.build())
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unable to schedule the automatic backups", e)
        }
    }

    fun reschedule(context: Context) {
        (context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler)?.cancel(JOB_ID)
        schedule(context)
    }

    fun isDue(context: Context): Boolean {
        if (!isEnabled(context)) return false
        val intervalDays = context.lawnchairPrefs.autoBackupInterval.toLongOrNull() ?: 7
        // A little early is fine, the job only runs about twice a day
        val interval = TimeUnit.DAYS.toMillis(intervalDays) - TimeUnit.HOURS.toMillis(6)
        return System.currentTimeMillis() - lastBackupTime(context) >= interval
    }

    /**
     * Creates a backup now. Runs on a background thread. Returns whether it worked.
     */
    fun backupNow(context: Context): Boolean {
        val prefs = context.lawnchairPrefs
        val tree = prefs.autoBackupFolder.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
                ?: return false
        val resolver = context.contentResolver
        val devicePrefs = Utilities.getDevicePrefs(context)
        val success = try {
            val folder = DocumentsContract.buildDocumentUriUsingTree(tree,
                    DocumentsContract.getTreeDocumentId(tree))
            val name = FILE_PREFIX + fileDateFormat.format(Date()) + "." + LawnchairBackup.EXTENSION
            val file = DocumentsContract.createDocument(resolver, folder,
                    LawnchairBackup.MIME_TYPE, name) ?: throw IllegalStateException("No file")
            val error = LawnchairBackup.create(context, name, file,
                    LawnchairBackup.INCLUDE_HOMESCREEN or LawnchairBackup.INCLUDE_SETTINGS,
                    allowScreenshot = false)
            if (error != null) throw error
            deleteOldBackups(context, tree, prefs.autoBackupKeep.toIntOrNull() ?: 5)
            true
        } catch (e: Exception) {
            // The folder is gone, the permission was revoked, the storage is full, ...
            Log.e(TAG, "Automatic backup failed", e)
            false
        }
        devicePrefs.edit()
                .putBoolean(KEY_LAST_ERROR, !success)
                .apply {
                    if (success) putLong(KEY_LAST_BACKUP, System.currentTimeMillis())
                }
                .apply()
        return success
    }

    private fun deleteOldBackups(context: Context, tree: Uri, keep: Int) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree,
                DocumentsContract.getTreeDocumentId(tree))
        val backups = ArrayList<Pair<String, String>>()
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                if (name.startsWith(FILE_PREFIX) && name.endsWith("." + LawnchairBackup.EXTENSION)) {
                    backups.add(id to name)
                }
            }
        }
        // The names contain the date, so they sort from old to new
        backups.sortBy { it.second }
        backups.dropLast(keep).forEach { (id, name) ->
            try {
                DocumentsContract.deleteDocument(resolver,
                        DocumentsContract.buildDocumentUriUsingTree(tree, id))
            } catch (e: Exception) {
                Log.w(TAG, "Unable to delete the old backup $name", e)
            }
        }
    }

    /**
     * Keeps the access to the picked folder across restarts of the device.
     */
    fun takeFolder(context: Context, tree: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val resolver = context.contentResolver
        val old = context.lawnchairPrefs.autoBackupFolder
        try {
            resolver.takePersistableUriPermission(tree, flags)
        } catch (e: SecurityException) {
            Log.w(TAG, "Unable to keep the access to $tree", e)
        }
        if (old.isNotEmpty() && old != tree.toString()) {
            try {
                resolver.releasePersistableUriPermission(Uri.parse(old), flags)
            } catch (e: SecurityException) {
                // Already gone
            }
        }
        context.lawnchairPrefs.autoBackupFolder = tree.toString()
    }

    fun folderName(context: Context): String? {
        val folder = context.lawnchairPrefs.autoBackupFolder.takeIf { it.isNotEmpty() } ?: return null
        return try {
            val tree = Uri.parse(folder)
            val document = DocumentsContract.buildDocumentUriUsingTree(tree,
                    DocumentsContract.getTreeDocumentId(tree))
            context.contentResolver.query(document,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) {
            null
        }
    }
}
