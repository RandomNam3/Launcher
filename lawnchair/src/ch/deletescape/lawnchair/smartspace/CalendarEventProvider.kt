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
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.support.annotation.Keep
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import ch.deletescape.lawnchair.BlankActivity
import ch.deletescape.lawnchair.smartspace.LawnchairSmartspaceController.CardData
import ch.deletescape.lawnchair.smartspace.LawnchairSmartspaceController.Line
import ch.deletescape.lawnchair.toBitmap
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.Utilities
import java.util.concurrent.TimeUnit

/**
 * Shows the next appointment of the day from the calendars on the device: events that are
 * running or start within the next hours. All-day events and declined events are skipped.
 */
@Keep
class CalendarEventProvider(controller: LawnchairSmartspaceController) :
        LawnchairSmartspaceController.PeriodicDataProvider(controller) {

    override val timeout = TimeUnit.MINUTES.toMillis(5)

    private val icon: Bitmap? by lazy {
        context.getDrawable(R.drawable.ic_smartspace_event)?.toBitmap()
    }

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (listening) updateNow()
        }
    }
    private var observing = false

    private val hasPermission
        get() = Utilities.hasPermission(context, Manifest.permission.READ_CALENDAR)

    override fun requiresSetup() = !hasPermission

    override fun startSetup(onFinish: (Boolean) -> Unit) {
        if (hasPermission) {
            onFinish(true)
            return
        }
        BlankActivity.requestPermission(context, Manifest.permission.READ_CALENDAR,
                REQUEST_CODE) { granted -> onFinish(granted) }
    }

    override fun startListening() {
        super.startListening()
        if (hasPermission && !observing) {
            try {
                context.contentResolver.registerContentObserver(
                        CalendarContract.Events.CONTENT_URI, true, observer)
                observing = true
            } catch (e: SecurityException) {
                Log.w(TAG, "Can't observe the calendar", e)
            }
        }
    }

    override fun stopListening() {
        super.stopListening()
        if (observing) {
            context.contentResolver.unregisterContentObserver(observer)
            observing = false
        }
    }

    override fun queryCardData(): CardData? {
        if (!hasPermission) return null
        val event = queryNextEvent() ?: return null
        val now = System.currentTimeMillis()
        val time = if (event.begin <= now) {
            context.getString(R.string.calendar_event_now_until, formatTime(event.end))
        } else {
            DateUtils.formatDateRange(context, event.begin, event.end,
                    DateUtils.FORMAT_SHOW_TIME)
        }
        val subtitle = listOfNotNull(time, event.location?.takeIf { it.isNotBlank() })
                .joinToString(" · ")
        return CardData(icon, listOf(Line(event.title), Line(subtitle)),
                        EventClickListener(event.id, event.begin, event.end), false)
    }

    private fun queryNextEvent(): Event? {
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now)
            ContentUris.appendId(it, now + LOOK_AHEAD)
        }.build()
        val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.EVENT_LOCATION)
        val selection = "${CalendarContract.Instances.ALL_DAY} = 0" +
                " AND ${CalendarContract.Instances.VISIBLE} = 1" +
                " AND ${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != " +
                CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED +
                " AND ${CalendarContract.Instances.END} > ?"
        return try {
            context.contentResolver.query(uri, projection, selection, arrayOf(now.toString()),
                    "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    val title = cursor.getString(1)
                    if (title.isNullOrBlank()) continue
                    return@use Event(cursor.getLong(0), title, cursor.getLong(2),
                                     cursor.getLong(3), cursor.getString(4))
                }
                null
            }
        } catch (e: SecurityException) {
            null
        } catch (e: RuntimeException) {
            Log.w(TAG, "Querying the calendar failed", e)
            null
        }
    }

    private fun formatTime(time: Long) = DateFormat.getTimeFormat(context).format(time)

    private data class Event(val id: Long, val title: String, val begin: Long, val end: Long,
                             val location: String?)

    private class EventClickListener(private val id: Long, private val begin: Long,
                                     private val end: Long) : View.OnClickListener {

        override fun onClick(v: View) {
            val intent = Intent(Intent.ACTION_VIEW)
                    .setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id))
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                Launcher.getLauncher(v.context).startActivitySafely(v, intent, null)
            } catch (e: ActivityNotFoundException) {
                // No calendar app
            }
        }
    }

    companion object {

        private const val TAG = "CalendarEventProvider"
        private const val REQUEST_CODE = 1040
        // Events of the next 12 hours, "today" in a launcher sense
        private val LOOK_AHEAD = TimeUnit.HOURS.toMillis(12)
    }
}
