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

package ch.deletescape.lawnchair.smartspace

import android.content.Context
import android.location.Criteria
import android.location.LocationManager
import android.support.annotation.Keep
import android.util.Log
import android.widget.Toast
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.checkLocationAccess
import ch.deletescape.lawnchair.lawnchairApp
import ch.deletescape.lawnchair.util.Temperature
import com.android.launcher3.R
import com.android.launcher3.Utilities
import ch.deletescape.lawnchair.util.net.Http
import org.json.JSONObject
import kotlin.math.roundToInt

@Keep
@Suppress("DEPRECATION")
class OWMWeatherDataProvider(controller: LawnchairSmartspaceController) :
        LawnchairSmartspaceController.PeriodicDataProvider(controller), LawnchairPreferences.OnPreferenceChangeListener {

    private val prefs = Utilities.getLawnchairPrefs(context)
    private val iconProvider by lazy { WeatherIconProvider(context) }

    private val locationAccess get() = context.checkLocationAccess()
    private val locationManager: LocationManager? by lazy { if (locationAccess) {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager?
    } else null }

    init {
        prefs.addOnPreferenceChangeListener(this, "pref_weatherApiKey", "pref_weather_city", "pref_weather_units")
    }

    override fun updateData() {
        // TODO: Create a search/dropdown for cities, make Auto the default
        val query = if (prefs.weatherCity == "##Auto") {
            if (!locationAccess) {
                context.lawnchairApp.activityHandler.foregroundActivity?.let {
                    Utilities.requestLocationPermission(it)
                }
                return
            }
            val location = try {
                val locationProvider = locationManager?.getBestProvider(Criteria(), true)
                locationProvider?.let { locationManager?.getLastKnownLocation(it) }
            } catch (e: SecurityException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            } ?: return
            "lat=${location.latitude}&lon=${location.longitude}"
        } else {
            "q=${Http.encode(prefs.weatherCity)}"
        }
        val units = if (prefs.weatherUnit == Temperature.Unit.Fahrenheit) "imperial" else "metric"
        val url = "https://api.openweathermap.org/data/2.5/weather?$query&units=$units" +
                "&appid=${Http.encode(prefs.weatherApiKey)}"
        Http.async({ Http.getJsonObject(url) }) { json, error ->
            if (json != null) onSuccess(json) else onFailure(error)
        }
    }

    private fun onSuccess(json: JSONObject) {
        val temp = json.optJSONObject("main")?.optDouble("temp", Double.NaN) ?: return
        if (temp.isNaN()) return
        val icon = json.optJSONArray("weather")?.optJSONObject(0)?.optString("icon", null) ?: return
        updateData(LawnchairSmartspaceController.WeatherData(
                iconProvider.getIcon(icon),
                Temperature(
                        temp.roundToInt(),
                        if (prefs.weatherUnit != Temperature.Unit.Fahrenheit) Temperature.Unit.Celsius else Temperature.Unit.Fahrenheit
                ),
                "https://openweathermap.org/city/${json.optLong("id")}"
        ), null)
    }

    private fun onFailure(throwable: Throwable?) {
        Log.w("OWM", "Updating weather data failed", throwable)
        if (prefs.weatherApiKey == context.getString(R.string.default_owm_key)
                || (throwable as? Http.HttpException)?.code == 401) {
            Toast.makeText(context, R.string.owm_get_your_own_key, Toast.LENGTH_LONG).show()
        } else if (throwable != null) {
            Toast.makeText(context, R.string.weather_update_failed, Toast.LENGTH_LONG).show()
        }
        updateData(null, null)
    }

    override fun stopListening() {
        super.stopListening()
        prefs.removeOnPreferenceChangeListener(this, "pref_weatherApiKey", "pref_weather_city", "pref_weather_units")
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        if (key in arrayOf("pref_weatherApiKey", "pref_weather_city", "pref_weather_units")) {
            if (!force) updateNow()
        }
    }
}
