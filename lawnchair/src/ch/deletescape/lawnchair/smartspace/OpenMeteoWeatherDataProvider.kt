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

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.support.annotation.Keep
import android.util.Log
import ch.deletescape.lawnchair.BlankActivity
import ch.deletescape.lawnchair.LawnchairLauncher
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.checkLocationAccess
import ch.deletescape.lawnchair.smartspace.WeatherIconProvider.Companion.CONDITION_CLEAR
import ch.deletescape.lawnchair.smartspace.WeatherIconProvider.Companion.CONDITION_CLOUDS
import ch.deletescape.lawnchair.smartspace.WeatherIconProvider.Companion.CONDITION_FEW_CLOUDS
import ch.deletescape.lawnchair.smartspace.WeatherIconProvider.Companion.CONDITION_MIST
import ch.deletescape.lawnchair.smartspace.WeatherIconProvider.Companion.CONDITION_MOST_CLOUDS
import ch.deletescape.lawnchair.smartspace.WeatherIconProvider.Companion.CONDITION_RAIN
import ch.deletescape.lawnchair.smartspace.WeatherIconProvider.Companion.CONDITION_SHOWERS
import ch.deletescape.lawnchair.smartspace.WeatherIconProvider.Companion.CONDITION_SNOW
import ch.deletescape.lawnchair.smartspace.WeatherIconProvider.Companion.CONDITION_STORM
import ch.deletescape.lawnchair.smartspace.WeatherIconProvider.Companion.CONDITION_UNKNOWN
import ch.deletescape.lawnchair.util.Temperature
import ch.deletescape.lawnchair.util.net.Http
import com.android.launcher3.Utilities
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Current weather from Open-Meteo (https://open-meteo.com), a free weather service that needs no
 * account and no API key. The location is either the device location or a city that is looked up
 * with the Open-Meteo geocoding service.
 */
@Keep
class OpenMeteoWeatherDataProvider(controller: LawnchairSmartspaceController) :
        LawnchairSmartspaceController.PeriodicDataProvider(controller),
        LawnchairPreferences.OnPreferenceChangeListener {

    private val prefs = Utilities.getLawnchairPrefs(context)
    private val iconProvider by lazy { WeatherIconProvider(context) }
    private val devicePrefs = Utilities.getDevicePrefs(context)

    override val timeout = TimeUnit.MINUTES.toMillis(30)

    private val useDeviceLocation get() = isAutomatic(prefs.weatherCity)

    init {
        prefs.addOnPreferenceChangeListener(this, *WATCHED_PREFS)
    }

    override fun requiresSetup() = useDeviceLocation && !context.checkLocationAccess()

    override fun startSetup(onFinish: (Boolean) -> Unit) {
        if (!requiresSetup()) {
            onFinish(true)
            return
        }
        BlankActivity.requestPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION,
                LawnchairLauncher.REQUEST_PERMISSION_LOCATION_ACCESS) { granted ->
            onFinish(granted)
        }
    }

    override fun queryWeatherData(): LawnchairSmartspaceController.WeatherData? {
        val (latitude, longitude) = resolveLocation() ?: return null
        val fahrenheit = prefs.weatherUnit == Temperature.Unit.Fahrenheit
        val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${format(latitude)}&longitude=${format(longitude)}" +
                "&current=temperature_2m,weather_code,is_day" +
                "&temperature_unit=${if (fahrenheit) "fahrenheit" else "celsius"}"
        val current = try {
            Http.getJsonObject(url).getJSONObject("current")
        } catch (e: Exception) {
            Log.w(TAG, "Updating the weather failed", e)
            return null
        }
        val temperature = current.optDouble("temperature_2m", Double.NaN)
        if (temperature.isNaN()) return null
        val code = current.optInt("weather_code", -1)
        val isDay = current.optInt("is_day", 1) == 1
        return LawnchairSmartspaceController.WeatherData(
                iconProvider.getIcon(iconFor(code, isDay)),
                Temperature(temperature.roundToInt(),
                            if (fahrenheit) Temperature.Unit.Fahrenheit else Temperature.Unit.Celsius),
                forecastUrl = "https://duckduckgo.com/?q=" +
                        Http.encode("weather ${format(latitude)},${format(longitude)}"))
    }

    /**
     * Runs on the provider's background thread.
     */
    private fun resolveLocation(): Pair<Double, Double>? {
        if (useDeviceLocation) {
            val location = lastKnownLocation() ?: return null
            return location.latitude to location.longitude
        }
        val city = prefs.weatherCity.trim()
        val cacheKey = "open_meteo_city"
        if (devicePrefs.getString(cacheKey, null) == city) {
            val latitude = devicePrefs.getFloat("open_meteo_latitude", Float.NaN)
            val longitude = devicePrefs.getFloat("open_meteo_longitude", Float.NaN)
            if (!latitude.isNaN() && !longitude.isNaN()) {
                return latitude.toDouble() to longitude.toDouble()
            }
        }
        // "Lucerne, CH" searches for "Lucerne", the country code narrows the result down
        val parts = city.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val name = parts.firstOrNull() ?: return null
        val country = parts.getOrNull(1)?.takeIf { it.length == 2 }?.uppercase(Locale.ROOT)
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=${Http.encode(name)}" +
                "&count=10&format=json&language=${Locale.getDefault().language}"
        val results = try {
            Http.getJsonObject(url).optJSONArray("results")
        } catch (e: Exception) {
            Log.w(TAG, "Looking up the city failed", e)
            return null
        } ?: return null
        var match = results.optJSONObject(0) ?: return null
        if (country != null) {
            for (i in 0 until results.length()) {
                val result = results.optJSONObject(i) ?: continue
                if (result.optString("country_code").equals(country, ignoreCase = true)) {
                    match = result
                    break
                }
            }
        }
        val latitude = match.optDouble("latitude", Double.NaN)
        val longitude = match.optDouble("longitude", Double.NaN)
        if (latitude.isNaN() || longitude.isNaN()) return null
        devicePrefs.edit()
                .putString(cacheKey, city)
                .putFloat("open_meteo_latitude", latitude.toFloat())
                .putFloat("open_meteo_longitude", longitude.toFloat())
                .apply()
        return latitude to longitude
    }

    private fun lastKnownLocation(): Location? {
        if (!context.checkLocationAccess()) return null
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE)
                as? LocationManager ?: return null
        return try {
            locationManager.getProviders(true)
                    .mapNotNull { locationManager.getLastKnownLocation(it) }
                    .maxByOrNull { it.time }
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    override fun stopListening() {
        super.stopListening()
        prefs.removeOnPreferenceChangeListener(this, *WATCHED_PREFS)
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        if (!force) updateNow()
    }

    companion object {

        private const val TAG = "OpenMeteoWeather"
        private val WATCHED_PREFS = arrayOf("pref_weather_city", "pref_weather_units")

        const val AUTOMATIC = "##Auto"

        fun isAutomatic(city: String?) = city.isNullOrBlank() || city == AUTOMATIC

        private fun format(value: Double) = String.format(Locale.ROOT, "%.4f", value)

        /**
         * Maps a WMO weather code (as used by Open-Meteo) to the condition ids of the weather
         * icon set, which follow the OpenWeatherMap naming ("01d" = clear sky by day).
         */
        fun iconFor(code: Int, isDay: Boolean): String {
            val condition = when (code) {
                0 -> CONDITION_CLEAR
                1 -> CONDITION_FEW_CLOUDS
                2 -> CONDITION_CLOUDS
                3 -> CONDITION_MOST_CLOUDS
                45, 48 -> CONDITION_MIST
                51, 53, 55, 56, 57, 80, 81, 82 -> CONDITION_SHOWERS
                61, 63, 65, 66, 67 -> CONDITION_RAIN
                71, 73, 75, 77, 85, 86 -> CONDITION_SNOW
                95, 96, 99 -> CONDITION_STORM
                else -> return CONDITION_UNKNOWN
            }
            return condition + if (isDay) "d" else "n"
        }
    }
}
