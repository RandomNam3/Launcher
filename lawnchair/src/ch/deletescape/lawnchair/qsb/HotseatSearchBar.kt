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

package ch.deletescape.lawnchair.qsb

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.colors.ColorEngine
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.Utilities

/**
 * Search bar below the dock icons. Opens the default browser, or the search of the chosen search
 * provider when there is no browser. The search bar in the app drawer searches the apps.
 */
class HotseatSearchBar(context: Context, attrs: AttributeSet?) : SearchBarView(context, attrs),
        LawnchairPreferences.OnPreferenceChangeListener {

    override val colorKey = ColorEngine.Resolvers.HOTSEAT_QSB_BG

    /**
     * True when used as a widget on the home screen instead of below the dock.
     */
    var widgetMode = false

    init {
        contentDescription = context.getString(R.string.search)
        hint.visibility = View.GONE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prefs.addOnPreferenceChangeListener(this, KEY_DOCK_SEARCHBAR, KEY_DOCK_HIDE)
        onFocusChangeListener = launcher.mFocusHandler
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        prefs.removeOnPreferenceChangeListener(this, KEY_DOCK_SEARCHBAR, KEY_DOCK_HIDE)
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        if (!widgetMode) {
            visibility = if (prefs.dockSearchBar) View.VISIBLE else View.GONE
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec)
        val width = if (widgetMode) {
            available
        } else {
            val hotseatLayout = launcher.hotseat.layout
            val inner = available - hotseatLayout.paddingLeft - hotseatLayout.paddingRight
            alignedWidth(inner) + paddingLeft + paddingRight
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        translationY = -getBottomMargin(launcher, widgetMode).toFloat()
    }

    override fun setInsets(insets: Rect) {
        super.setInsets(insets)
        if (!widgetMode) {
            visibility = if (launcher.deviceProfile.isVerticalBarLayout || !prefs.dockSearchBar)
                View.GONE else View.VISIBLE
        }
    }

    override fun onSearchBarClick() {
        val browser = Utilities.getDefaultBrowserIntent(launcher)
        if (browser != null) {
            launcher.startActivitySafely(this, browser, null)
            return
        }
        try {
            searchProvider.startSearch { intent ->
                launcher.startActivitySafely(this, intent, null)
            }
        } catch (e: RuntimeException) {
            // The app of the search provider is gone
        }
    }

    override fun setAlpha(alpha: Float) {
        super.setAlpha(alpha)
        launcher.findViewById<View>(R.id.scrim_view)?.invalidate()
    }

    companion object {

        const val KEY_DOCK_SEARCHBAR = "pref_dockSearchBar"
        const val KEY_DOCK_HIDE = "pref_hideHotseat"

        @JvmStatic
        fun getBottomMargin(launcher: Launcher, widgetMode: Boolean): Int {
            val resources = launcher.resources
            val profile = launcher.deviceProfile
            val minBottom = profile.insets.bottom +
                    resources.getDimensionPixelSize(R.dimen.hotseat_qsb_bottom_margin)
            val insets = if (widgetMode) Rect() else profile.insets
            val hotseatLayoutPadding = profile.hotseatLayoutPadding
            val hotseatTop = profile.hotseatBarSizePx + insets.bottom
            val hotseatIconsTop = hotseatTop - hotseatLayoutPadding.top
            val iconsCenter = (hotseatIconsTop - hotseatLayoutPadding.bottom +
                    profile.iconSizePx * 0.92f) / 2f
            val navBar = insets.bottom * 0.67f
            val bottomMargin = Math.round(navBar + (hotseatTop - navBar - iconsCenter -
                    resources.getDimension(R.dimen.qsb_widget_height) -
                    profile.verticalDragHandleSizePx) / 2f)
            return Math.max(minBottom, bottomMargin)
        }
    }
}
