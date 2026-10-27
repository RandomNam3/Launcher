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

package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.support.v7.preference.EditTextPreference
import android.util.AttributeSet
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.smartspace.OpenMeteoWeatherDataProvider
import com.android.launcher3.R
import com.android.launcher3.Utilities

/**
 * City for the Open-Meteo weather. An empty value uses the device location.
 */
class WeatherCityPreference(context: Context, attrs: AttributeSet?) :
        EditTextPreference(context, attrs), LawnchairPreferences.OnPreferenceChangeListener {

    private val prefs = Utilities.getLawnchairPrefs(context)

    override fun onAttached() {
        super.onAttached()
        updateSummary()
        prefs.addOnPreferenceChangeListener("pref_smartspace_widget_provider", this)
    }

    override fun onDetached() {
        super.onDetached()
        prefs.removeOnPreferenceChangeListener("pref_smartspace_widget_provider", this)
    }

    override fun getText(): String? {
        val city = prefs.weatherCity
        return if (OpenMeteoWeatherDataProvider.isAutomatic(city)) "" else city
    }

    override fun setText(text: String?) {
        val value = text?.trim()
        prefs.weatherCity = if (value.isNullOrEmpty()) OpenMeteoWeatherDataProvider.AUTOMATIC else value
        updateSummary()
        notifyChanged()
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        isVisible = prefs.weatherProvider == OpenMeteoWeatherDataProvider::class.java.name
    }

    private fun updateSummary() {
        val city = prefs.weatherCity
        summary = if (OpenMeteoWeatherDataProvider.isAutomatic(city)) {
            context.getString(R.string.pref_weather_city_automatic)
        } else city
    }
}
