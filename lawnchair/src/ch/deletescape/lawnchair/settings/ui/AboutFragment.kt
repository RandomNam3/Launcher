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

package ch.deletescape.lawnchair.settings.ui

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import ch.deletescape.lawnchair.applyAccent
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import java.nio.charset.StandardCharsets

/**
 * About screen. Replaces the attribouter library that was used by Lawnchair 2.
 */
class AboutFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_about, container, false)
        view.findViewById<TextView>(R.id.about_version).text =
                getString(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

        val links = view.findViewById<LinearLayout>(R.id.about_links)
        addRow(inflater, links, R.string.about_source_code, SOURCE_URL) { openUrl(SOURCE_URL) }
        addRow(inflater, links, R.string.about_based_on, LAWNCHAIR_URL) { openUrl(LAWNCHAIR_URL) }
        addRow(inflater, links, R.string.about_license, getString(R.string.about_license_summary)) {
            openUrl(LICENSE_URL)
        }
        addRow(inflater, links, R.string.about_third_party_licenses,
                getString(R.string.about_third_party_licenses_summary)) { showThirdPartyNotices() }
        return view
    }

    private fun addRow(inflater: LayoutInflater, parent: ViewGroup, title: Int, summary: String,
                       onClick: () -> Unit) {
        val row = inflater.inflate(R.layout.about_link_row, parent, false)
        row.findViewById<TextView>(android.R.id.title).setText(title)
        row.findViewById<TextView>(android.R.id.summary).text = summary
        row.setOnClickListener { onClick() }
        parent.addView(row)
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showThirdPartyNotices() {
        val context = context ?: return
        val text = try {
            context.resources.openRawResource(R.raw.third_party_notices).use {
                String(it.readBytes(), StandardCharsets.UTF_8)
            }
        } catch (e: Exception) {
            return
        }
        AlertDialog.Builder(context)
                .setTitle(R.string.about_third_party_licenses)
                .setMessage(text)
                .setPositiveButton(android.R.string.ok, null)
                .show()
                .applyAccent()
    }

    companion object {
        const val SOURCE_URL = "https://github.com/RandomNam3/Launcher"
        const val LAWNCHAIR_URL = "https://github.com/LawnchairLauncher/lawnchair"
        const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
    }
}
