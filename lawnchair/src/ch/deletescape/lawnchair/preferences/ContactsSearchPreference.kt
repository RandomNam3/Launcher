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

import android.Manifest
import android.content.Context
import android.util.AttributeSet
import ch.deletescape.lawnchair.BlankActivity
import com.android.launcher3.Utilities

/**
 * Switch for the contact search. Asks for the contacts permission when it is switched on.
 */
class ContactsSearchPreference(context: Context, attrs: AttributeSet?) :
        StyledSwitchPreferenceCompat(context, attrs) {

    override fun onAttached() {
        super.onAttached()
        // The permission might have been revoked in the system settings
        if (isChecked && !hasPermission()) {
            isChecked = false
        }
    }

    override fun onClick() {
        if (!isChecked && !hasPermission()) {
            BlankActivity.requestPermission(context, Manifest.permission.READ_CONTACTS,
                    REQUEST_CODE) { granted ->
                if (granted && callChangeListener(true)) {
                    isChecked = true
                }
            }
            return
        }
        super.onClick()
    }

    private fun hasPermission() =
            Utilities.hasPermission(context, Manifest.permission.READ_CONTACTS)

    companion object {
        private const val REQUEST_CODE = 1060
    }
}
