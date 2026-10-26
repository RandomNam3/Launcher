/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.backup

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.provider.DocumentsContract
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.AsyncTask
import android.os.Environment
import android.support.v4.content.FileProvider
import android.util.Log
import ch.deletescape.lawnchair.LawnchairLauncher
import com.android.launcher3.BuildConfig
import com.android.launcher3.LauncherFiles
import com.android.launcher3.Utilities
import org.json.JSONArray
import java.io.*
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class LawnchairBackup(val context: Context, val uri: Uri) {

    val meta by lazy { readMeta() }

    private fun readMeta(): Meta? {
        var entry: ZipEntry?
        var meta: Meta? = null
        readZip { zipIs ->
            while (true) {
                entry = zipIs.nextEntry
                if (entry == null) break
                if (entry!!.name != Meta.FILE_NAME) continue
                meta = Meta.fromString(String(readLimited(zipIs, MAX_META_SIZE), StandardCharsets.UTF_8))
                break
            }
        }
        return meta
    }

    private fun readPreview(): Pair<Bitmap?, Bitmap?>? {
        var entry: ZipEntry?
        var screenshot: Bitmap? = null
        var wallpaper: Bitmap? = null
        readZip { zipIs ->
            while (true) {
                entry = zipIs.nextEntry
                if (entry == null) break
                if (entry!!.name == "screenshot.png") {
                    screenshot = decodeBounded(readLimited(zipIs, MAX_IMAGE_SIZE), PREVIEW_DIMENSION)
                } else if (entry!!.name == WALLPAPER_FILE_NAME) {
                    wallpaper = decodeBounded(readLimited(zipIs, MAX_IMAGE_SIZE), PREVIEW_DIMENSION)
                }
            }
        }
        if (screenshot == wallpaper) return null // both are null
        return Pair(Utilities.getScaledDownBitmap(screenshot, 1000, false),
                Utilities.getScaledDownBitmap(wallpaper, 1000, false))
    }

    private inline fun readZip(body: (ZipInputStream) -> Unit) {
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return
            ZipInputStream(BufferedInputStream(input)).use { zipIs ->
                try {
                    body(zipIs)
                } catch (t: Throwable) {
                    Log.e(TAG, "Unable to read zip for $uri", t)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to read zip for $uri", t)
        }
    }

    fun restore(contents: Int): Boolean {
        val contextWrapper = ContextWrapper(context)
        val dbFile = contextWrapper.getDatabasePath(LauncherFiles.LAUNCHER_DB)
        val dir = contextWrapper.cacheDir.parent
        val settingsFile = File(dir, "shared_prefs/" + LauncherFiles.SHARED_PREFERENCES_KEY + ".xml")
        val tmpDir = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        val tmpDb = File(tmpDir, "launcher.db")
        val tmpSettings = File(tmpDir, "settings.xml")
        var wallpaper: Bitmap? = null

        try {
            // Only the known entries are extracted, and only to fixed locations (no zip slip).
            // Everything is validated and staged first, the current data is only replaced once
            // the whole backup was read successfully.
            val input = context.contentResolver.openInputStream(uri) ?: return false
            ZipInputStream(BufferedInputStream(input)).use { zipIs ->
                while (true) {
                    val entry = zipIs.nextEntry ?: break
                    Log.d(TAG, "Found entry ${entry.name}")
                    when {
                        entry.name == dbFile.name -> if (contents and INCLUDE_HOMESCREEN != 0) {
                            copyLimited(zipIs, tmpDb, MAX_DB_SIZE)
                            if (!isSqliteDatabase(tmpDb)) throw IOException("Invalid database")
                        }
                        entry.name.endsWith("_preferences.xml") -> if (contents and INCLUDE_SETTINGS != 0) {
                            copyLimited(zipIs, tmpSettings, MAX_SETTINGS_SIZE)
                            if (!isPreferencesXml(tmpSettings)) throw IOException("Invalid settings")
                        }
                        entry.name == WALLPAPER_FILE_NAME -> if (contents and INCLUDE_WALLPAPER != 0) {
                            wallpaper = decodeBounded(readLimited(zipIs, MAX_IMAGE_SIZE), MAX_WALLPAPER_DIMENSION)
                        }
                    }
                }
            }

            var restored = false
            if (contents and INCLUDE_HOMESCREEN != 0 && tmpDb.exists()) {
                context.deleteDatabase(dbFile.path)
                dbFile.parentFile?.mkdirs()
                if (!tmpDb.renameTo(dbFile)) {
                    tmpDb.copyTo(dbFile, overwrite = true)
                }
                restored = true
            }
            if (contents and INCLUDE_SETTINGS != 0 && tmpSettings.exists()) {
                settingsFile.parentFile?.mkdirs()
                if (!tmpSettings.renameTo(settingsFile)) {
                    tmpSettings.copyTo(settingsFile, overwrite = true)
                }
                restored = true
            }
            wallpaper?.let {
                try {
                    WallpaperManager.getInstance(context).setBitmap(it)
                    restored = true
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to restore the wallpaper", e)
                }
            }
            return restored
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to restore $uri", t)
            return false
        } finally {
            wallpaper?.recycle()
            tmpDir.deleteRecursively()
        }
    }

    fun delete(): Boolean {
        return context.contentResolver.delete(uri, null, null) != 0
    }

    class MetaLoader(val backup: LawnchairBackup) {

        var callback: Callback? = null
        var meta: Meta? = null
        var withPreview = false
        var loaded = false
        private var loading = false

        fun loadMeta(withPreview: Boolean = false) {
            if (loading) return
            if (!loaded) {
                loading = true
                this.withPreview = withPreview
                LoadMetaTask().execute()
            } else {
                callback?.onMetaLoaded()
            }
        }

        @SuppressLint("StaticFieldLeak")
        inner class LoadMetaTask : AsyncTask<Void, Void, Meta?>() {

            override fun doInBackground(vararg params: Void?): Meta? {
                backup.meta
                if (withPreview) {
                    backup.meta?.preview = backup.readPreview()
                }
                return backup.meta
            }

            override fun onPostExecute(result: Meta?) {
                meta = result
                loaded = true
                callback?.onMetaLoaded()
            }
        }

        interface Callback {

            fun onMetaLoaded()
        }
    }

    data class Meta(val name: String, val contents: Int, val timestamp: String) {

        val localizedTimestamp: String = try {
            SimpleDateFormat.getDateTimeInstance().format(timestampFormat.parse(timestamp)!!)
        } catch (e: Exception) {
            timestamp
        }
        var preview: Pair<Bitmap?, Bitmap?>? = null

        override fun toString(): String {
            val arr = JSONArray()
            arr.put(VERSION)
            arr.put(name)
            arr.put(contents)
            arr.put(timestamp)
            return arr.toString()
        }

        fun recycle() {
            preview?.first?.recycle()
            preview?.second?.recycle()
        }

        companion object {

            const val VERSION = 1

            const val FILE_NAME = "lcbkp"

            @Suppress("unused")
            private const val VERSION_INDEX = 0
            private const val NAME_INDEX = 1
            private const val CONTENTS_INDEX = 2
            private const val TIMESTAMP_INDEX = 3

            fun fromString(string: String): Meta {
                val arr = JSONArray(string)
                return Meta(
                        name = arr.getString(NAME_INDEX),
                        contents = arr.getInt(CONTENTS_INDEX),
                        timestamp = arr.getString(TIMESTAMP_INDEX)
                )
            }
        }
    }

    companion object {
        const val TAG = "LawnchairBackup"

        const val INCLUDE_HOMESCREEN = 1 shl 0
        const val INCLUDE_SETTINGS = 1 shl 1
        const val INCLUDE_WALLPAPER = 1 shl 2
        const val INCLUDE_SCREENSHOT = 1 shl 3

        const val BUFFER = 2018

        const val EXTENSION = "shed"
        const val MIME_TYPE = "application/vnd.lawnchair.backup"
        val EXTRA_MIME_TYPES = arrayOf(MIME_TYPE, "application/x-zip", "application/octet-stream")

        const val WALLPAPER_FILE_NAME = "wallpaper.png"

        val timestampFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US)

        fun getFolder(): File {
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Lawnchair/backup")
            Log.d(TAG, "path: $folder")
            if (!folder.exists()) {
                folder.mkdirs()
            }
            return folder
        }

        fun listLocalBackups(context: Context): List<LawnchairBackup> {
            return getFolder().listFiles { file -> file.extension == EXTENSION }
                    ?.sortedByDescending { it.lastModified() }
                    ?.map { FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", it) }
                    ?.map { LawnchairBackup(context, it) }
                    ?: Collections.emptyList()
        }

        private fun prepareConfig(context: Context) {
            Utilities.getLawnchairPrefs(context).blockingEdit {
                restoreSuccess = true
                developerOptionsEnabled = false
            }
        }

        private fun cleanupConfig(context: Context, devOptionsEnabled: Boolean) {
            Utilities.getLawnchairPrefs(context).blockingEdit {
                restoreSuccess = false
                developerOptionsEnabled = devOptionsEnabled
            }
        }

        fun create(context: Context, name: String, location: Uri, contents: Int): Exception? {
            val contextWrapper = ContextWrapper(context)
            val files: MutableList<File> = ArrayList()

            if (contents and INCLUDE_HOMESCREEN != 0) {
                val dbFile = contextWrapper.getDatabasePath(LauncherFiles.LAUNCHER_DB)
                // The database is used in write-ahead logging mode, recent changes might still be
                // in the -wal file. Write them into the database file before copying it.
                checkpointDatabase(dbFile)
                files.add(dbFile)
            }
            if (contents and INCLUDE_SETTINGS != 0) {
                val dir = contextWrapper.cacheDir.parent
                files.add(File(dir, "shared_prefs/" + LauncherFiles.SHARED_PREFERENCES_KEY + ".xml"))
            }

            val prefs = Utilities.getLawnchairPrefs(context)
            val includeScreenshot: Boolean

            if (prefs.backupScreenshot) {
                val screenshotFile = File(context.filesDir, "tmp/screenshot.png")
                if (screenshotFile.exists()) screenshotFile.delete()
                try {
                    LawnchairLauncher.takeScreenshotSync(context)
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to take a screenshot for the backup", e)
                }
                includeScreenshot = screenshotFile.exists()
                if (includeScreenshot) files.add(screenshotFile)
            } else {
                includeScreenshot = false
            }

            val devOptionsEnabled = prefs.developerOptionsEnabled
            // Also writes (commits) all pending preference changes
            prepareConfig(context)
            try {
                val output = context.contentResolver.openOutputStream(location, "wt")
                        ?: throw IOException("Unable to open $location")
                ZipOutputStream(BufferedOutputStream(output)).use { out ->
                    out.putNextEntry(ZipEntry(Meta.FILE_NAME))
                    val actualContents = if (includeScreenshot) contents or INCLUDE_SCREENSHOT else contents
                    out.write(getMeta(name, actualContents).toString().toByteArray())
                    out.closeEntry()
                    if (contents and INCLUDE_WALLPAPER != 0) {
                        val wallpaperManager = WallpaperManager.getInstance(context)
                        // Reading the wallpaper needs storage access on newer Android versions
                        val wallpaperDrawable = try {
                            wallpaperManager.drawable
                        } catch (e: SecurityException) {
                            Log.w(TAG, "Unable to read the wallpaper", e)
                            null
                        }
                        val wallpaperBitmap = wallpaperDrawable?.let { Utilities.drawableToBitmap(it) }
                        if (wallpaperBitmap != null) {
                            out.putNextEntry(ZipEntry(WALLPAPER_FILE_NAME))
                            wallpaperBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            out.closeEntry()
                        }
                    }
                    files.filter { it.exists() }.forEach { file ->
                        out.putNextEntry(ZipEntry(file.name))
                        BufferedInputStream(FileInputStream(file), BUFFER).use { it.copyTo(out, BUFFER) }
                        out.closeEntry()
                    }
                }
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create backup", e)
                // Don't leave an incomplete backup behind
                deleteQuietly(context, location)
                return e
            } finally {
                cleanupConfig(context, devOptionsEnabled)
            }
        }

        private fun checkpointDatabase(dbFile: File) {
            if (!dbFile.exists()) return
            try {
                SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                    db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to checkpoint the database", e)
            }
        }

        private fun deleteQuietly(context: Context, location: Uri) {
            try {
                if (location.scheme == "file") {
                    location.path?.let { File(it).delete() }
                } else if (DocumentsContract.isDocumentUri(context, location)) {
                    DocumentsContract.deleteDocument(context.contentResolver, location)
                } else {
                    context.contentResolver.delete(location, null, null)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to delete incomplete backup $location", e)
            }
        }

        // Limits for restoring, backups are files from outside the app
        private const val MAX_DB_SIZE = 64L * 1024 * 1024
        private const val MAX_SETTINGS_SIZE = 8L * 1024 * 1024
        private const val MAX_IMAGE_SIZE = 64L * 1024 * 1024
        private const val MAX_META_SIZE = 64L * 1024
        private const val MAX_WALLPAPER_DIMENSION = 4096
        private const val PREVIEW_DIMENSION = 1000

        @Throws(IOException::class)
        private fun readLimited(input: InputStream, limit: Long): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) throw IOException("Backup entry too large")
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }

        @Throws(IOException::class)
        private fun copyLimited(input: InputStream, target: File, limit: Long) {
            FileOutputStream(target).use { out ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > limit) throw IOException("Backup entry too large")
                    out.write(buffer, 0, read)
                }
                out.fd.sync()
            }
        }

        private fun isSqliteDatabase(file: File): Boolean {
            val header = ByteArray(16)
            return FileInputStream(file).use { it.read(header) == 16 } &&
                    String(header, StandardCharsets.US_ASCII) == "SQLite format 3\u0000"
        }

        private fun isPreferencesXml(file: File): Boolean {
            val start = FileInputStream(file).use { input ->
                val buffer = ByteArray(512)
                val read = input.read(buffer)
                if (read <= 0) "" else String(buffer, 0, read, StandardCharsets.UTF_8)
            }
            return start.contains("<map")
        }

        /**
         * Decodes an image without ever allocating more than needed for [maxDimension].
         */
        private fun decodeBounded(data: ByteArray, maxDimension: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }
            return try {
                BitmapFactory.decodeByteArray(data, 0, data.size,
                        BitmapFactory.Options().apply { inSampleSize = sampleSize })
            } catch (e: OutOfMemoryError) {
                null
            }
        }

        private fun getMeta(name: String, contents: Int) = Meta(
                name = name,
                contents = contents,
                timestamp = getTimestamp()
        )

        private fun getTimestamp(): String {
            return timestampFormat.format(Date())
        }
    }
}
