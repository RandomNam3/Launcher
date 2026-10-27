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

package ch.deletescape.lawnchair.smartspace

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.RectF
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextClock
import android.widget.TextView
import ch.deletescape.lawnchair.font.CustomFontManager
import ch.deletescape.lawnchair.lawnchairApp
import ch.deletescape.lawnchair.openPopupMenu
import ch.deletescape.lawnchair.smartspace.LawnchairSmartspaceController.CardData
import ch.deletescape.lawnchair.smartspace.LawnchairSmartspaceController.Line
import ch.deletescape.lawnchair.smartspace.LawnchairSmartspaceController.WeatherData
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.Themes
import com.google.android.apps.nexuslauncher.smartspace.SmartspacePreferencesShortcut
import java.util.Locale

/**
 * "At a Glance" on the first home screen page: the date (and optionally the time), the current
 * weather and the most relevant event, for example the next appointment or the song that is
 * playing. Start aligned with the first icon column, like on Pixel phones.
 */
class AtAGlanceView(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs),
        LawnchairSmartspaceController.Listener, View.OnLongClickListener {

    private val controller = context.lawnchairApp.smartspace
    private val prefs = Utilities.getLawnchairPrefs(context)

    private val clock: TextClock
    private val date: TextClock
    private val title: TextView
    private val titleWeather: View
    private val titleWeatherIcon: ImageView
    private val titleWeatherText: TextView
    private val subtitleRow: View
    private val subtitleContent: View
    private val subtitleIcon: ImageView
    private val subtitle: TextView
    private val subtitleWeather: View
    private val subtitleWeatherIcon: ImageView
    private val subtitleWeatherText: TextView

    private val textColor = Themes.getAttrColor(context, R.attr.workspaceTextColor)
    private val darkText = Themes.getAttrBoolean(context, R.attr.isWorkspaceDarkText)

    private var weather: WeatherData? = null
    private var card: CardData? = null
    private var performingSetup = false

    init {
        clipChildren = false
        LayoutInflater.from(context).inflate(R.layout.at_a_glance_content, this, true)
        clock = findViewById(R.id.glance_clock)
        date = findViewById(R.id.glance_date)
        title = findViewById(R.id.glance_title)
        titleWeather = findViewById(R.id.glance_title_weather)
        titleWeatherIcon = findViewById(R.id.glance_title_weather_icon)
        titleWeatherText = findViewById(R.id.glance_title_weather_text)
        subtitleRow = findViewById(R.id.glance_subtitle_row)
        subtitleContent = findViewById(R.id.glance_subtitle_content)
        subtitleIcon = findViewById(R.id.glance_subtitle_icon)
        subtitle = findViewById(R.id.glance_subtitle)
        subtitleWeather = findViewById(R.id.glance_subtitle_weather)
        subtitleWeatherIcon = findViewById(R.id.glance_subtitle_weather_icon)
        subtitleWeatherText = findViewById(R.id.glance_subtitle_weather_text)

        val fontManager = CustomFontManager.getInstance(context)
        listOf(clock, date, title, titleWeatherText, subtitle, subtitleWeatherText).forEach {
            fontManager.setCustomFont(it, CustomFontManager.FONT_SMARTSPACE_TEXT)
            if (!darkText) {
                // Keeps the text readable on bright wallpapers
                it.setShadowLayer(resources.displayMetrics.density * 3, 0f,
                        resources.displayMetrics.density, 0x40000000)
            }
        }
        subtitleIcon.imageTintList = ColorStateList.valueOf(textColor)

        clock.setOnClickListener(::openAlarms)
        date.setOnClickListener(::openCalendar)
        titleWeather.setOnClickListener(::openWeather)
        subtitleWeather.setOnClickListener(::openWeather)
        listOf(this, clock, date, title, titleWeather, subtitleContent, subtitleWeather)
                .forEach { it.setOnLongClickListener(this) }

        reloadCustomizations()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        controller.addListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        controller.removeListener(this)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Start aligned with the icons of the first column
        val idp = LauncherAppState.getIDP(context)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val iconSize = Utilities.pxFromDp(idp.iconSize, resources.displayMetrics)
        val cellWidth = width / Math.max(1, idp.numColumns)
        val inset = Math.max(0, (cellWidth - iconSize) / 2) - itemPadding
        val start = Math.max(0, inset)
        setPaddingRelative(start, paddingTop, start, paddingBottom)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private val itemPadding get() = resources.displayMetrics.density.times(4).toInt()

    /**
     * Applies the date and time settings.
     */
    fun reloadCustomizations() {
        val locale = Locale.getDefault()
        val showTime = prefs.smartspaceTime
        val timeAbove = showTime && prefs.smartspaceTimeAbove
        val is24h = prefs.smartspaceTime24H || DateFormat.is24HourFormat(context)
        val timePattern = DateFormat.getBestDateTimePattern(locale, if (is24h) "Hm" else "hm")
        val datePattern = DateFormat.getBestDateTimePattern(locale, "EEEMMMd")

        clock.visibility = if (timeAbove) View.VISIBLE else View.GONE
        clock.format12Hour = timePattern
        clock.format24Hour = timePattern

        val titlePattern = when {
            showTime && !timeAbove && prefs.smartspaceDate -> "$timePattern' · '$datePattern"
            showTime && !timeAbove -> timePattern
            else -> datePattern
        }
        date.format12Hour = titlePattern
        date.format24Hour = titlePattern
        bind()
    }

    override fun onDataUpdated(weather: WeatherData?, card: CardData?) {
        this.weather = weather
        this.card = if (controller.requiresSetup) {
            CardData(null, listOf(Line(context.getString(R.string.smartspace_setup_text))),
                     View.OnClickListener { startSetup() }, true)
        } else card
        bind()
    }

    private fun bind() {
        val card = card
        val weather = weather
        val doubleLine = card != null && card.isDoubleLine

        if (doubleLine) {
            // The event becomes the headline, the weather moves next to its details
            date.visibility = View.GONE
            title.visibility = View.VISIBLE
            title.text = card!!.title
            title.ellipsize = card.titleEllipsize
            title.setOnClickListener(card.onClickListener)
            bindWeather(null, titleWeather, titleWeatherIcon, titleWeatherText)
            bindSubtitle(card.subtitle, card)
            bindWeather(weather, subtitleWeather, subtitleWeatherIcon, subtitleWeatherText)
        } else {
            val showDate = prefs.smartspaceDate || prefs.smartspaceTime && !prefs.smartspaceTimeAbove
            date.visibility = if (showDate) View.VISIBLE else View.GONE
            title.visibility = View.GONE
            title.setOnClickListener(null)
            bindWeather(weather, titleWeather, titleWeatherIcon, titleWeatherText)
            bindWeather(null, subtitleWeather, subtitleWeatherIcon, subtitleWeatherText)
            bindSubtitle(card?.title, card)
        }
        // Keep the view tappable for the long press menu even without content
        visibility = View.VISIBLE
    }

    private fun bindSubtitle(text: CharSequence?, card: CardData?) {
        if (text.isNullOrEmpty() || card == null) {
            subtitleRow.visibility = View.GONE
            subtitleContent.setOnClickListener(null)
            return
        }
        subtitleRow.visibility = View.VISIBLE
        subtitle.text = text
        subtitle.ellipsize = if (card.isDoubleLine) card.subtitleEllipsize else card.titleEllipsize
        val icon = card.icon
        if (icon != null) {
            subtitleIcon.visibility = View.VISIBLE
            subtitleIcon.setImageBitmap(icon)
        } else {
            subtitleIcon.visibility = View.GONE
        }
        subtitleContent.setOnClickListener(card.onClickListener)
        subtitleContent.isClickable = card.onClickListener != null
        subtitleContent.isLongClickable = true
    }

    private fun bindWeather(weather: WeatherData?, container: View, icon: ImageView,
                            text: TextView) {
        if (weather == null) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE
        icon.setImageBitmap(weather.icon)
        text.text = weather.getTitle(prefs.weatherUnit)
        container.contentDescription = text.text
    }

    private fun startSetup() {
        if (performingSetup) return
        performingSetup = true
        controller.startSetup { performingSetup = false }
    }

    private fun openCalendar(v: View) {
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").also {
            ContentUris.appendId(it, System.currentTimeMillis())
        }.build()
        startActivity(v, Intent(Intent.ACTION_VIEW).setData(uri))
    }

    private fun openAlarms(v: View) {
        startActivity(v, Intent(AlarmClock.ACTION_SHOW_ALARMS))
    }

    private fun openWeather(v: View) {
        launcher() ?: return
        try {
            controller.openWeather(v)
        } catch (e: RuntimeException) {
            // No app or browser to show the forecast
        }
    }

    private fun startActivity(v: View, intent: Intent) {
        val launcher = launcher() ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            launcher.startActivitySafely(v, intent, null)
        } catch (e: ActivityNotFoundException) {
            // Nothing to open
        }
    }

    private fun launcher() = try {
        Launcher.getLauncher(context)
    } catch (e: ClassCastException) {
        // In the settings preview
        null
    }

    override fun onLongClick(v: View): Boolean {
        launcher() ?: return false
        val rect = RectF()
        val location = IntArray(2)
        v.getLocationInWindow(location)
        rect.set(location[0].toFloat(), location[1].toFloat(),
                 (location[0] + v.width).toFloat(), (location[1] + v.height).toFloat())
        openPopupMenu(v, rect, SmartspacePreferencesShortcut())
        return true
    }
}
