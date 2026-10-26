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

package ch.deletescape.lawnchair.blur

import android.content.Context
import android.graphics.Bitmap
import ch.deletescape.lawnchair.LawnchairPreferences

class BlurWallpaperFilter(private val context: Context) : WallpaperFilter {

    private var blurRadius = 25

    override fun applyPrefs(prefs: LawnchairPreferences) {
        blurRadius = prefs.blurRadius.toInt() / BlurWallpaperProvider.DOWNSAMPLE_FACTOR
        blurRadius = Math.max(1, Math.min(blurRadius, 25))
    }

    override fun apply(wallpaper: Bitmap): WallpaperFilter.ApplyTask {
        return WallpaperFilter.ApplyTask.create { emitter ->
            StackBlur.blurAsync(wallpaper, blurRadius,
                    BlurWallpaperProvider.DOWNSAMPLE_FACTOR.toFloat(), true) { bitmap, error ->
                if (bitmap != null) {
                    emitter.onSuccess(bitmap)
                } else {
                    emitter.onError(error ?: IllegalStateException("Blur failed"))
                }
            }
        }
    }
}
