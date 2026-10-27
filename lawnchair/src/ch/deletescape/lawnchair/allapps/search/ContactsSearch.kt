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

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.ContactsContract
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory
import android.util.Log
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.Utilities

/**
 * Finds contacts by their name. Needs the contacts permission, which is asked for when the
 * contact search is switched on in the settings.
 */
class ContactsSearch(private val context: Context) {

    val hasPermission get() = Utilities.hasPermission(context, Manifest.permission.READ_CONTACTS)

    fun search(query: String, limit: Int = 3): List<SearchResult> {
        if (!hasPermission || query.isBlank()) return emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query.trim()))
        val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
        val results = ArrayList<SearchResult>()
        val fallbackIcon = context.getDrawable(R.drawable.ic_search_contact)
        val subtitle = context.getString(R.string.contacts_search_subtitle)
        try {
            context.contentResolver.query(uri, projection, null, null,
                    "${ContactsContract.Contacts.TIMES_CONTACTED} DESC")?.use { cursor ->
                while (cursor.moveToNext() && results.size < limit) {
                    val id = cursor.getLong(0)
                    val lookupKey = cursor.getString(1) ?: continue
                    val name = cursor.getString(2) ?: continue
                    val photo = cursor.getString(3)?.let { loadPhoto(Uri.parse(it)) }
                    val contactUri = ContactsContract.Contacts.getLookupUri(id, lookupKey)
                    results.add(SearchResult(photo ?: fallbackIcon, name, subtitle, photo == null,
                                             false) { v ->
                        Launcher.getLauncher(v.context).startActivitySafely(v,
                                Intent(Intent.ACTION_VIEW, contactUri)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null)
                    })
                }
            }
        } catch (e: RuntimeException) {
            // E.g. the permission was revoked meanwhile
            Log.w("ContactsSearch", "Searching the contacts failed", e)
        }
        return results
    }

    private fun loadPhoto(uri: Uri): Drawable? {
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                val bitmap: Bitmap = BitmapFactory.decodeStream(it) ?: return null
                RoundedBitmapDrawableFactory.create(context.resources, bitmap).apply {
                    isCircular = true
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
