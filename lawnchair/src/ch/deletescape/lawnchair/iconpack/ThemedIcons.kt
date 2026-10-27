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

package ch.deletescape.lawnchair.iconpack

import android.content.Context
import android.graphics.drawable.Drawable
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.platform.ThemedIconCompat
import ch.deletescape.lawnchair.theme.ThemeManager

/**
 * Themed icons: app icons drawn from their monochrome layer in the colours of the system
 * palette (Android 13+), like on the Pixel launcher.
 */
object ThemedIcons {

    fun isEnabled(context: Context) =
            ThemedIconCompat.isSupported() && context.lawnchairPrefs.themedIcons

    private fun getColors(context: Context): IntArray? {
        return ThemedIconCompat.getColors(context, ThemeManager.getInstance(context).isDark)
    }

    /**
     * Returns the themed version of the icon, or null if themed icons are off or the app does
     * not provide a monochrome icon.
     */
    fun getThemedIcon(context: Context, icon: Drawable): Drawable? {
        if (!isEnabled(context)) return null
        val colors = getColors(context) ?: return null
        return try {
            // The icon cache expects wrapped adaptive icons, a plain AdaptiveIconDrawable is
            // rejected (and used to crash the loader)
            ThemedIconCompat.createThemedIcon(icon, colors[0], colors[1])
                    ?.let { AdaptiveIconCompat.wrap(it) }
        } catch (e: RuntimeException) {
            null
        }
    }

    /**
     * Reloads the icons if the colours changed since they were drawn (new wallpaper, dark mode).
     * The icons are cached across restarts, so the colours are remembered in the settings.
     */
    fun checkForChanges(context: Context) {
        val prefs = context.lawnchairPrefs
        val key = if (isEnabled(context)) getColors(context)?.joinToString(",") ?: "" else ""
        if (key != prefs.themedIconColors) {
            prefs.themedIconColors = key
            prefs.reloadIcons()
        }
    }
}
