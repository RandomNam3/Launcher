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
package ch.deletescape.lawnchair

import android.content.Context
import android.graphics.Point
import android.util.Log
import android.view.WindowManager
import com.android.launcher3.LauncherFiles
import com.android.launcher3.Utilities
import java.io.File

/**
 * Tablets and unfolded foldables get their own home screen: a separate grid and a separate
 * database. Folding and unfolding then switches between the two layouts instead of squeezing one
 * layout into the other grid and back. The launcher process restarts when the display size
 * changes, so this is decided once per process.
 */
object LargeScreen {

    private const val TAG = "LargeScreen"
    private const val MIN_WIDTH_DP = 600
    private const val LARGE_DB = "launcher_large.db"

    private var cached: Boolean? = null

    @JvmStatic
    fun isLargeScreen(context: Context): Boolean {
        cached?.let { return it }
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay
        val smallest = Point()
        val largest = Point()
        display.getCurrentSizeRange(smallest, largest)
        val metrics = context.resources.displayMetrics
        val minWidthDp = Utilities.dpiFromPx(Math.min(smallest.x, smallest.y), metrics)
        return (minWidthDp >= MIN_WIDTH_DP).also { cached = it }
    }

    /**
     * Suffix for the preferences that exist once per layout, e.g. the grid size.
     */
    @JvmStatic
    fun prefSuffix(context: Context) = if (isLargeScreen(context)) "_large" else ""

    @JvmStatic
    fun databaseName(context: Context) =
            if (isLargeScreen(context)) LARGE_DB else LauncherFiles.LAUNCHER_DB

    /**
     * Returns the name of the database for this screen. The first time a large screen is used,
     * the layout of the phone is copied, the grid migration then fits it into the larger grid.
     */
    @JvmStatic
    fun prepareDatabase(context: Context): String {
        val name = databaseName(context)
        if (name == LauncherFiles.LAUNCHER_DB) return name
        val target = context.getDatabasePath(name)
        val source = context.getDatabasePath(LauncherFiles.LAUNCHER_DB)
        if (target.exists() || !source.exists()) return name
        try {
            for (suffix in listOf("", "-wal", "-shm")) {
                val from = File(source.path + suffix)
                if (from.exists()) from.copyTo(File(target.path + suffix), overwrite = true)
            }
            val prefs = Utilities.getPrefs(context)
            val edit = prefs.edit()
            prefs.getString("migration_src_workspace_size", null)?.let {
                edit.putString("migration_src_workspace_size_large", it)
            }
            if (prefs.contains("migration_src_hotseat_count")) {
                edit.putInt("migration_src_hotseat_count_large",
                        prefs.getInt("migration_src_hotseat_count", 0))
            }
            edit.commit()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to copy the layout for the large screen", e)
            listOf("", "-wal", "-shm").forEach { File(target.path + it).delete() }
        }
        return name
    }
}
