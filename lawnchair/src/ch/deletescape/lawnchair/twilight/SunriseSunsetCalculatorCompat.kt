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

package ch.deletescape.lawnchair.twilight

import java.util.*
import kotlin.math.*

/**
 * Official sunrise and sunset times (zenith 90°50') using the algorithm from the
 * "Almanac for Computers" (1990), the same one the previously used SunriseSunsetCalculator
 * library implements. Falls back to 6:00 / 20:00 without a location or during polar day/night.
 */
class SunriseSunsetCalculatorCompat(private val latitude: Double?, private val longitude: Double?,
                                    private val timeZone: TimeZone) {

    fun getOfficialSunriseCalendarForDate(calendar: Calendar): Calendar {
        return compute(calendar, true) ?: cloneWithHour(calendar, 6)
    }

    fun getOfficialSunsetCalendarForDate(calendar: Calendar): Calendar {
        return compute(calendar, false) ?: cloneWithHour(calendar, 20)
    }

    private fun compute(date: Calendar, sunrise: Boolean): Calendar? {
        val lat = latitude ?: return null
        val lon = longitude ?: return null
        if (lat.isNaN() || lon.isNaN()) return null

        val local = Calendar.getInstance(timeZone).apply {
            clear()
            set(date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DATE), 0, 0, 0)
        }
        val dayOfYear = local.get(Calendar.DAY_OF_YEAR)
        val lngHour = lon / 15.0
        val t = dayOfYear + ((if (sunrise) 6.0 else 18.0) - lngHour) / 24.0

        val meanAnomaly = 0.9856 * t - 3.289
        val trueLongitude = normalize(meanAnomaly + 1.916 * sinDeg(meanAnomaly)
                + 0.020 * sinDeg(2 * meanAnomaly) + 282.634, 360.0)

        var rightAscension = normalize(Math.toDegrees(atan(0.91764 * tanDeg(trueLongitude))), 360.0)
        val lQuadrant = floor(trueLongitude / 90.0) * 90.0
        val raQuadrant = floor(rightAscension / 90.0) * 90.0
        rightAscension = (rightAscension + (lQuadrant - raQuadrant)) / 15.0

        val sinDec = 0.39782 * sinDeg(trueLongitude)
        val cosDec = cos(asin(sinDec))
        val cosLocalHour = (cosDeg(ZENITH) - sinDec * sinDeg(lat)) / (cosDec * cosDeg(lat))
        if (cosLocalHour > 1 || cosLocalHour < -1 || cosLocalHour.isNaN()) {
            // The sun never rises or never sets on this day
            return null
        }
        val localHourAngle = if (sunrise) {
            360.0 - Math.toDegrees(acos(cosLocalHour))
        } else {
            Math.toDegrees(acos(cosLocalHour))
        } / 15.0

        val localMeanTime = localHourAngle + rightAscension - 0.06571 * t - 6.622
        val utcHours = normalize(localMeanTime - lngHour, 24.0)
        val offsetHours = timeZone.getOffset(local.timeInMillis) / 3_600_000.0
        val localHours = normalize(utcHours + offsetHours, 24.0)

        local.add(Calendar.MINUTE, (localHours * 60).roundToInt())
        return local
    }

    private fun cloneWithHour(src: Calendar, hour: Int): Calendar {
        return Calendar.getInstance(src.timeZone).apply {
            set(src.get(Calendar.YEAR), src.get(Calendar.MONTH), src.get(Calendar.DATE), hour, 0)
        }
    }

    companion object {
        private const val ZENITH = 90.8333

        private fun sinDeg(deg: Double) = sin(Math.toRadians(deg))
        private fun cosDeg(deg: Double) = cos(Math.toRadians(deg))
        private fun tanDeg(deg: Double) = tan(Math.toRadians(deg))

        private fun normalize(value: Double, max: Double): Double {
            var v = value % max
            if (v < 0) v += max
            return v
        }
    }
}
