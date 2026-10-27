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

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import com.android.launcher3.Launcher
import com.android.launcher3.R
import java.text.Normalizer
import java.util.Locale

/**
 * Finds pages of the system settings by their name and a few keywords.
 */
class SettingsSearch(private val context: Context) {

    private class Page(val title: Int, val keywords: Int, val action: String)

    private val pages = listOf(
            Page(R.string.settings_search_wifi, R.string.settings_search_wifi_keywords, Settings.ACTION_WIFI_SETTINGS),
            Page(R.string.settings_search_bluetooth, R.string.settings_search_bluetooth_keywords, Settings.ACTION_BLUETOOTH_SETTINGS),
            Page(R.string.settings_search_network, R.string.settings_search_network_keywords, Settings.ACTION_WIRELESS_SETTINGS),
            Page(R.string.settings_search_display, R.string.settings_search_display_keywords, Settings.ACTION_DISPLAY_SETTINGS),
            Page(R.string.settings_search_sound, R.string.settings_search_sound_keywords, Settings.ACTION_SOUND_SETTINGS),
            Page(R.string.settings_search_battery, R.string.settings_search_battery_keywords, Intent.ACTION_POWER_USAGE_SUMMARY),
            Page(R.string.settings_search_apps, R.string.settings_search_apps_keywords, Settings.ACTION_APPLICATION_SETTINGS),
            Page(R.string.settings_search_notifications, R.string.settings_search_notifications_keywords, "android.settings.NOTIFICATION_SETTINGS"),
            Page(R.string.settings_search_storage, R.string.settings_search_storage_keywords, Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
            Page(R.string.settings_search_location, R.string.settings_search_location_keywords, Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            Page(R.string.settings_search_security, R.string.settings_search_security_keywords, Settings.ACTION_SECURITY_SETTINGS),
            Page(R.string.settings_search_privacy, R.string.settings_search_privacy_keywords, Settings.ACTION_PRIVACY_SETTINGS),
            Page(R.string.settings_search_accounts, R.string.settings_search_accounts_keywords, Settings.ACTION_SYNC_SETTINGS),
            Page(R.string.settings_search_date, R.string.settings_search_date_keywords, Settings.ACTION_DATE_SETTINGS),
            Page(R.string.settings_search_language, R.string.settings_search_language_keywords, Settings.ACTION_LOCALE_SETTINGS),
            Page(R.string.settings_search_accessibility, R.string.settings_search_accessibility_keywords, Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Page(R.string.settings_search_developer, R.string.settings_search_developer_keywords, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            Page(R.string.settings_search_about, R.string.settings_search_about_keywords, Settings.ACTION_DEVICE_INFO_SETTINGS),
            Page(R.string.settings_search_airplane, R.string.settings_search_airplane_keywords, Settings.ACTION_AIRPLANE_MODE_SETTINGS),
            Page(R.string.settings_search_nfc, R.string.settings_search_nfc_keywords, Settings.ACTION_NFC_SETTINGS),
            Page(R.string.settings_search_data, R.string.settings_search_data_keywords, Settings.ACTION_DATA_ROAMING_SETTINGS),
            Page(R.string.settings_search_wallpaper, R.string.settings_search_wallpaper_keywords, Intent.ACTION_SET_WALLPAPER),
            Page(R.string.settings_search_settings, R.string.settings_search_settings_keywords, Settings.ACTION_SETTINGS))

    fun search(query: String, limit: Int = 3): List<SearchResult> {
        val needle = normalize(query)
        if (needle.length < 2) return emptyList()
        val icon = context.getDrawable(R.drawable.ic_search_settings)
        val subtitle = context.getString(R.string.settings_search_subtitle)
        return pages.asSequence()
                .mapNotNull { page ->
                    val title = context.getString(page.title)
                    val score = score(needle, normalize(title),
                                      normalize(context.getString(page.keywords)))
                    if (score > 0) Triple(page, title, score) else null
                }
                .sortedByDescending { it.third }
                .filter { isAvailable(it.first.action) }
                .take(limit)
                .map { (page, title, _) ->
                    SearchResult(icon, title, subtitle, true, false) { v ->
                        Launcher.getLauncher(v.context).startActivitySafely(v,
                                Intent(page.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null)
                    }
                }
                .toList()
    }

    private fun isAvailable(action: String) = context.packageManager.resolveActivity(
            Intent(action), PackageManager.MATCH_DEFAULT_ONLY) != null

    private fun score(needle: String, title: String, keywords: String): Int = when {
        title.startsWith(needle) -> 3
        title.split(' ').any { it.startsWith(needle) } -> 2
        keywords.split(',').any { keyword ->
            keyword.trim().let { it.startsWith(needle) || it.split(' ').any { w -> w.startsWith(needle) } }
        } -> 1
        else -> 0
    }

    companion object {

        fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace("\\p{Mn}+".toRegex(), "")
                .lowercase(Locale.getDefault())
                .trim()
    }
}
