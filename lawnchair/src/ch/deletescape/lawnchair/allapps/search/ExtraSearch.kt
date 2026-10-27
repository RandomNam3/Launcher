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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import ch.deletescape.lawnchair.lawnchairPrefs
import com.android.launcher3.R
import com.android.launcher3.Utilities
import java.text.DecimalFormatSymbols

/**
 * Results besides the apps for a search in the app drawer. Everything is found on the device,
 * nothing is sent to the internet.
 */
class ExtraSearch(private val context: Context) {

    private val settings = SettingsSearch(context)
    private val contacts = ContactsSearch(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var generation = 0

    /**
     * Searches in the background and delivers the results on the main thread, unless a newer
     * search was started in the meantime.
     */
    fun search(query: String, callback: (List<SearchResult>) -> Unit) {
        val current = ++generation
        if (query.isBlank()) {
            callback(emptyList())
            return
        }
        Utilities.THREAD_POOL_EXECUTOR.execute {
            val results = try {
                searchNow(query)
            } catch (e: RuntimeException) {
                emptyList<SearchResult>()
            }
            mainHandler.post {
                if (current == generation) callback(results)
            }
        }
    }

    fun cancel() {
        generation++
    }

    private fun searchNow(query: String): List<SearchResult> {
        val prefs = context.lawnchairPrefs
        val results = ArrayList<SearchResult>()
        if (prefs.searchCalculator) {
            Calculator.evaluate(query)?.let { results.add(calculation(it)) }
        }
        if (prefs.searchContacts) {
            results.addAll(contacts.search(query))
        }
        if (prefs.searchSettings) {
            results.addAll(settings.search(query))
        }
        return results
    }

    private fun calculation(value: Double): SearchResult {
        val separator = DecimalFormatSymbols.getInstance().decimalSeparator
        val text = Calculator.format(value, separator)
        return SearchResult(context.getDrawable(R.drawable.ic_search_calculator), "= $text",
                            context.getString(R.string.calculator_search_subtitle), true, true) { v ->
            val clipboard = v.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(text, text))
            Toast.makeText(v.context, R.string.calculator_search_copied, Toast.LENGTH_SHORT).show()
        }
    }
}
