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
import android.graphics.drawable.Drawable
import android.support.v4.graphics.ColorUtils
import android.text.InputType
import android.text.Selection
import android.text.SpannableStringBuilder
import android.text.method.TextKeyListener
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.font.CustomFontManager
import ch.deletescape.lawnchair.globalsearch.providers.AppSearchSearchProvider
import ch.deletescape.lawnchair.globalsearch.providers.web.WebSearchProvider
import com.android.launcher3.ExtendedEditText
import com.android.launcher3.R
import com.android.launcher3.allapps.AllAppsContainerView
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.allapps.SearchUiManager
import com.android.launcher3.allapps.search.AllAppsSearchBarController
import com.android.launcher3.util.ComponentKey
import com.google.android.apps.nexuslauncher.allapps.PredictionsFloatingHeader
import com.google.android.apps.nexuslauncher.search.SearchThread
import java.util.ArrayList

/**
 * Search bar at the top of the app drawer. Searches the installed apps while typing.
 */
class DrawerSearchBar(context: Context, attrs: AttributeSet?) : SearchBarView(context, attrs),
        SearchUiManager, AllAppsSearchBarController.Callbacks, AllAppsStore.OnUpdateListener {

    override val colorKey = ColorEngine.Resolvers.ALLAPPS_QSB_BG

    private val controller = AllAppsSearchBarController()
    private val queryBuilder = SpannableStringBuilder()
    private lateinit var appsView: AllAppsContainerView
    private lateinit var apps: AlphabeticalAppsList

    private val marginTopAdjusting =
            resources.getDimensionPixelSize(R.dimen.qsb_margin_top_adjusting)
    private val verticalOffset =
            resources.getDimensionPixelSize(R.dimen.all_apps_search_vertical_offset).toFloat()

    val input = ExtendedEditText(context).apply {
        background = null
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setHint(R.string.all_apps_search_bar_hint)
        setSingleLine(true)
        maxLines = 1
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                InputType.TYPE_TEXT_FLAG_CAP_WORDS
        isSaveEnabled = false
        setPadding(0, 0, 0, 0)
    }

    init {
        contentDescription = context.getString(R.string.all_apps_search_bar_hint)
        hint.visibility = View.GONE
        mic.visibility = View.GONE
        addView(input, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            marginStart = iconWidth
            marginEnd = iconWidth / 2
            topMargin = shadowMargin
            bottomMargin = shadowMargin
        })
        CustomFontManager.getInstance(context).setCustomFont(input, CustomFontManager.FONT_TEXT)
        Selection.setSelection(queryBuilder, 0)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        launcher.appsView.appsStore.addUpdateListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        launcher.appsView.appsStore.removeUpdateListener(this)
    }

    override fun onForegroundColorChanged(color: Int) {
        input.setTextColor(color)
        input.setHintTextColor(ColorUtils.setAlphaComponent(color, 0xA0))
    }

    override fun getLogo(): Drawable? = AppSearchSearchProvider(context).getIcon(true)

    // Voice search belongs to the web search on the home screen
    override fun getMicIcon(): Drawable? = null

    override fun onSearchBarClick() {
        input.showKeyboard()
    }

    override fun initialize(appsView: AllAppsContainerView) {
        this.appsView = appsView
        apps = appsView.apps
        controller.initialize(SearchThread(context), input, launcher, this)
        appsView.setRecyclerViewVerticalFadingEdgeEnabled(!prefs.lowPerformanceMode)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec)
        val inner = if (launcher.deviceProfile.isVerticalBarLayout) {
            val rv = appsView.activeRecyclerView
            available - rv.paddingLeft - rv.paddingRight
        } else {
            val hotseatLayout = launcher.hotseat.layout
            available - hotseatLayout.paddingLeft - hotseatLayout.paddingRight
        }
        val width = alignedWidth(inner) + paddingLeft + paddingRight
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Centered in the parent
        val parent = parent as View
        val available = parent.width - parent.paddingLeft - parent.paddingRight
        translationX = (parent.paddingLeft + (available - (right - left)) / 2 - left).toFloat()
    }

    override fun setInsets(insets: Rect) {
        val mlp = layoutParams as MarginLayoutParams
        mlp.topMargin = getTopMargin(insets)
        requestLayout()
        if (launcher.deviceProfile.isVerticalBarLayout) {
            launcher.allAppsController.setScrollRangeDelta(0f)
        } else {
            var delta = HotseatSearchBar.getBottomMargin(launcher, false) + verticalOffset
            if (!prefs.dockHide) {
                delta += mlp.height + mlp.topMargin
                if (!prefs.dockSearchBar) {
                    delta -= (mlp.height + mlp.topMargin + mlp.bottomMargin).toFloat()
                    delta += verticalOffset
                }
            } else {
                delta -= resources.getDimensionPixelSize(R.dimen.vertical_drag_handle_size)
            }
            launcher.allAppsController.setScrollRangeDelta(Math.round(delta).toFloat())
        }
    }

    fun getTopMargin(insets: Rect): Int {
        return Math.max(Math.round(-verticalOffset), insets.top - marginTopAdjusting)
    }

    override fun onAppsUpdated() {
        controller.refreshSearchResult()
    }

    override fun resetSearch() {
        controller.reset()
    }

    override fun startSearch() {
        post { input.showKeyboard() }
    }

    override fun preDispatchKeyEvent(event: KeyEvent) {
        // Typing on a hardware keyboard starts the search
        if (!controller.isSearchFieldFocused && event.action == KeyEvent.ACTION_DOWN) {
            val unicodeChar = event.unicodeChar
            val isKeyNotWhitespace = unicodeChar > 0 &&
                    !Character.isWhitespace(unicodeChar) && !Character.isSpaceChar(unicodeChar)
            if (isKeyNotWhitespace) {
                val gotKey = TextKeyListener.getInstance()
                        .onKeyDown(this, queryBuilder, event.keyCode, event)
                if (gotKey && queryBuilder.isNotEmpty()) {
                    controller.focusSearchField()
                }
            }
        }
    }

    override fun onSearchResult(query: String, apps: ArrayList<ComponentKey>?,
                                suggestions: MutableList<String>?) {
        if (apps != null) {
            this.apps.setOrderedFilter(apps)
        }
        if (suggestions != null) {
            this.apps.setSearchSuggestions(suggestions)
        }
        if (apps != null || suggestions != null) {
            appsView.onSearchResultsChanged()
            setHeaderCollapsed(true)
            appsView.setLastSearchQuery(query)
        }
    }

    override fun clearSearchResult() {
        if (apps.setOrderedFilter(null) or apps.setSearchSuggestions(null)) {
            appsView.onSearchResultsChanged()
        }
        setHeaderCollapsed(false)
        queryBuilder.clear()
        queryBuilder.clearSpans()
        Selection.setSelection(queryBuilder, 0)
        appsView.onClearSearchResult()
    }

    override fun onSubmitSearch(): Boolean {
        if (apps.hasNoFilteredResults()) {
            return false
        }
        val provider = searchProvider
        if (provider is WebSearchProvider && apps.filteredApps.isEmpty()) {
            provider.openResults(input.text.toString())
            return true
        }
        val first = apps.filteredApps.firstOrNull() ?: return false
        launcher.startActivitySafely(this, first.intent, first)
        return true
    }

    private fun setHeaderCollapsed(collapsed: Boolean) {
        (appsView.floatingHeaderView as? PredictionsFloatingHeader)?.setCollapsed(collapsed)
    }

    override fun hasOverlappingRendering() = false
}
