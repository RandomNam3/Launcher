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

package ch.deletescape.lawnchair.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import ch.deletescape.lawnchair.LawnchairPreferences
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.widget.custom.CustomWidgetParser
import ch.deletescape.lawnchair.smartspace.AtAGlanceView

class SmartspacePreview(context: Context, attrs: AttributeSet?) :
        FrameLayout(context, attrs), LawnchairPreferences.OnPreferenceChangeListener,
        PreviewFrame.CustomWidgetPreview {

    private val prefs = Utilities.getLawnchairPrefs(context)
    private val prefsToWatch = arrayOf("pref_smartspace_time",  "pref_smartspace_time_above",
            "pref_smartspace_time_24_h", "pref_smartspace_date")
    private var currentView: AtAGlanceView? = null

    override val provider = CustomWidgetParser.getCustomWidgets(context)[0]!!

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        prefs.addOnPreferenceChangeListener(this, *prefsToWatch)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        prefs.removeOnPreferenceChangeListener(this, *prefsToWatch)
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        if (currentView == null) {
            removeAllViews()
            inflateCurrentView()
        } else {
            currentView!!.reloadCustomizations()
        }
    }

    private fun inflateCurrentView() {
        addView(inflateView(R.layout.search_container_workspace))
    }

    private fun inflateView(layout: Int): View {
        val view = LayoutInflater.from(context).inflate(layout, this, false)
        view.layoutParams.height = resources.getDimensionPixelSize(R.dimen.smartspace_preview_height)
        currentView = view as? AtAGlanceView
        return view
    }
}
