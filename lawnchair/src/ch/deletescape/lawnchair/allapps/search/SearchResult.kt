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
package ch.deletescape.lawnchair.allapps.search

import android.graphics.drawable.Drawable
import android.view.View

/**
 * A search result in the app drawer that is not an app: a calculation, a contact or a page of
 * the system settings.
 */
class SearchResult(
        val icon: Drawable?,
        val title: CharSequence,
        val subtitle: CharSequence?,
        /** Whether the icon is a symbol that is tinted in the text colour */
        val tintIcon: Boolean,
        /** Shown above the apps instead of below them */
        val top: Boolean,
        val action: (View) -> Unit)
