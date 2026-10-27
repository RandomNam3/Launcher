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
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.support.v4.graphics.ColorUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.android.launcher3.graphics.IconShape
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.font.CustomFontManager
import ch.deletescape.lawnchair.globalsearch.SearchProvider
import ch.deletescape.lawnchair.globalsearch.SearchProviderController
import com.android.launcher3.Insettable
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.Utilities

/**
 * Material 3 search bar: a flat tonal pill with a leading icon, an optional hint and an optional
 * trailing voice or assistant button.
 */
abstract class SearchBarView(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs),
        Insettable, ColorEngine.OnColorChangeListener,
        SearchProviderController.OnProviderChangeListener,
        SharedPreferences.OnSharedPreferenceChangeListener, View.OnClickListener {

    protected val launcher: Launcher = Launcher.getLauncher(context)
    protected val prefs = Utilities.getLawnchairPrefs(context)
    protected val isRtl = Utilities.isRtl(resources)

    protected val iconWidth = resources.getDimensionPixelSize(R.dimen.qsb_mic_width)
    protected val shadowMargin = resources.getDimensionPixelSize(R.dimen.qsb_shadow_margin)

    val logo = ImageView(context).apply {
        id = R.id.g_icon
        scaleType = ImageView.ScaleType.CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    val mic = ImageView(context).apply {
        id = R.id.mic_icon
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(R.string.label_voice_search)
    }
    protected val hint = TextView(context).apply {
        id = View.generateViewId()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private val pill = GradientDrawable()
    private var backgroundColor = 0
    protected var foregroundColor = 0
        private set

    protected abstract val colorKey: String

    protected val searchProvider: SearchProvider
        get() = SearchProviderController.getInstance(context).searchProvider

    init {
        clipToPadding = false
        addView(logo, LayoutParams(iconWidth, LayoutParams.MATCH_PARENT,
                                   Gravity.START or Gravity.CENTER_VERTICAL))
        addView(hint, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                                   Gravity.START or Gravity.CENTER_VERTICAL).apply {
            marginStart = iconWidth
            marginEnd = iconWidth
        })
        addView(mic, LayoutParams(iconWidth, LayoutParams.MATCH_PARENT,
                                  Gravity.END or Gravity.CENTER_VERTICAL))
        CustomFontManager.getInstance(context).setCustomFont(hint, CustomFontManager.FONT_TEXT)
        setOnClickListener(this)
        logo.setOnClickListener(this)
        mic.setOnClickListener(this)
        updateBackground()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ColorEngine.getInstance(context).addColorChangeListeners(this, colorKey)
        SearchProviderController.getInstance(context).addOnProviderChangeListener(this)
        Utilities.getPrefs(context).registerOnSharedPreferenceChangeListener(this)
        loadIcons()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ColorEngine.getInstance(context).removeColorChangeListeners(this, colorKey)
        SearchProviderController.getInstance(context).removeOnProviderChangeListener(this)
        Utilities.getPrefs(context).unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onColorChange(resolveInfo: ColorEngine.ResolveInfo) {
        if (resolveInfo.key == colorKey) {
            backgroundColor = resolveInfo.color
            foregroundColor = resolveInfo.foregroundColor
            hint.setTextColor(ColorUtils.setAlphaComponent(foregroundColor, 0xB3))
            onForegroundColorChanged(foregroundColor)
            updateBackground()
        }
    }

    protected open fun onForegroundColorChanged(color: Int) {}

    override fun onSearchProviderChanged() {
        loadIcons()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "opa_enabled", "opa_assistant" -> loadIcons()
            "pref_searchbarRadius" -> updateBackground()
        }
    }

    override fun setInsets(insets: Rect) {
        requestLayout()
    }

    protected fun updateBackground() {
        pill.shape = GradientDrawable.RECTANGLE
        pill.cornerRadius = getCornerRadius(context, Float.MAX_VALUE)
        pill.setColor(backgroundColor)
        val rippleColor = ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(foregroundColor, 0x1F))
        val mask = GradientDrawable().apply {
            cornerRadius = pill.cornerRadius
            setColor(-0x1)
        }
        background = InsetDrawable(RippleDrawable(rippleColor, pill, mask), 0, shadowMargin, 0,
                                   shadowMargin)
        val iconMask = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(-0x1)
        }
        mic.background = RippleDrawable(rippleColor, null, iconMask)
        logo.background = RippleDrawable(rippleColor, null, iconMask)
    }

    /**
     * Loads the icons of the current search provider.
     */
    protected open fun loadIcons() {
        logo.setImageDrawable(getLogo())
        val micIcon = getMicIcon()
        mic.setImageDrawable(micIcon)
        mic.visibility = if (micIcon != null) View.VISIBLE else View.GONE
    }

    protected open fun getLogo(): Drawable? = searchProvider.getIcon(true)

    protected open fun getMicIcon(): Drawable? {
        if (!Utilities.getPrefs(context).getBoolean("opa_enabled", true)) return null
        val provider = searchProvider
        return when {
            showAssistant && provider.supportsAssistant -> provider.getAssistantIcon(true)
            provider.supportsVoiceSearch -> provider.getVoiceIcon(true)
            else -> null
        }
    }

    private val showAssistant get() = Utilities.getPrefs(context).getBoolean("opa_assistant", true)

    protected fun startVoiceSearch() {
        val provider = searchProvider
        try {
            if (showAssistant && provider.supportsAssistant) {
                provider.startAssistant { context.startActivity(it) }
            } else if (provider.supportsVoiceSearch) {
                provider.startVoiceSearch { context.startActivity(it) }
            }
        } catch (e: RuntimeException) {
            // The app of the search provider is gone
        }
    }

    override fun onClick(v: View) {
        if (v == mic) {
            startVoiceSearch()
        } else {
            onSearchBarClick()
        }
    }

    protected abstract fun onSearchBarClick()

    /**
     * Width of the bar: aligned with the outer edges of the first and last dock icon.
     */
    protected fun alignedWidth(availableWidth: Int): Int {
        val dp = launcher.deviceProfile
        val cellWidth = availableWidth / Math.max(1, dp.inv.numHotseatIcons)
        val iconVisibleSize = Math.round(0.92f * dp.iconSizePx)
        return availableWidth - (cellWidth - iconVisibleSize)
    }

    companion object {

        @JvmStatic
        fun getCornerRadius(context: Context, defaultRadius: Float): Float {
            val radius = Math.round(Utilities.getLawnchairPrefs(context).searchBarRadius).toFloat()
            if (radius > 0f) {
                return radius
            }
            val edgeRadius = IconShape.getShape().getAttrValue(R.attr.qsbEdgeRadius)
            return edgeRadius?.getDimension(context.resources.displayMetrics) ?: defaultRadius
        }
    }
}
