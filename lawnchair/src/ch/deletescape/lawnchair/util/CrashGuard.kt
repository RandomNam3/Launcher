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

package ch.deletescape.lawnchair.util

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import ch.deletescape.lawnchair.lawnchairApp
import com.android.launcher3.R

/**
 * Keeps the launcher running when a single feature fails.
 *
 * An exception in a click handler, an animation, a posted task or a background thread is logged
 * and written to a bug report, and the launcher goes on. Only failures after which the app would
 * be left in a broken state still end the process (Android then restarts the launcher cleanly):
 * running out of memory, errors while Android creates, starts or stops an activity or service,
 * and the same failure repeating in quick succession.
 */
object CrashGuard {

    private const val TAG = "CrashGuard"
    private const val REPEAT_WINDOW_MS = 10_000L
    private const val MAX_REPEATS = 3

    private lateinit var context: Context
    private val recentFailures = ArrayList<Long>()
    private var lastToast = 0L
    private var installed = false

    /**
     * Installs the guard on the main thread. Call once from Application.onCreate.
     */
    fun install(context: Context) {
        if (installed) return
        installed = true
        this.context = context.applicationContext
        guardLooper(Looper.getMainLooper())
    }

    /**
     * Runs the message loop of [looper] so that an exception in one message does not end the
     * thread. Has to be called before the looper processes anything that matters.
     */
    @JvmStatic
    fun guardLooper(looper: Looper) {
        // The test runtime drives the loopers itself
        if (Build.FINGERPRINT == "robolectric") return
        Handler(looper).postAtFrontOfQueue {
            while (true) {
                try {
                    Looper.loop()
                    // The looper was quit
                    return@postAtFrontOfQueue
                } catch (e: Throwable) {
                    if (!survive(Thread.currentThread(), e)) {
                        throw e
                    }
                }
            }
        }
    }

    /**
     * Decides whether the app can go on after [e] was thrown on [thread], and reports it if so.
     * Used by the uncaught exception handler for threads without a guarded looper.
     */
    @JvmStatic
    fun survive(thread: Thread, e: Throwable): Boolean {
        if (!::context.isInitialized || isFatal(thread, e) || repeatsTooOften()) {
            return false
        }
        Log.e(TAG, "Recovered from an exception on ${thread.name}", e)
        report(e)
        if (thread === Looper.getMainLooper().thread && isDrawingFailure(e)) {
            // The views might be in an inconsistent state after a failed layout or draw pass,
            // build the screen again
            Handler(Looper.getMainLooper()).post {
                try {
                    context.lawnchairApp.activityHandler.foregroundActivity?.recreate()
                } catch (t: Throwable) {
                    Log.e(TAG, "Unable to recreate the activity", t)
                }
            }
        }
        return true
    }

    private fun isFatal(thread: Thread, e: Throwable): Boolean {
        if (e is OutOfMemoryError) return true
        if (thread !== Looper.getMainLooper().thread) return false
        // Errors while Android creates, starts, stops or destroys a component leave the component
        // half initialized, and the system waits for a result that never comes
        return causes(e).any { cause ->
            cause.stackTrace.any { frame ->
                frame.className.startsWith("android.app.servertransaction.") ||
                        (frame.className == "android.app.ActivityThread" &&
                                frame.methodName in LIFECYCLE_METHODS)
            }
        }
    }

    private fun isDrawingFailure(e: Throwable) = causes(e).any { cause ->
        cause.stackTrace.any { it.className == "android.view.ViewRootImpl" }
    }

    private fun causes(e: Throwable) = generateSequence(e) { it.cause?.takeIf { c -> c !== it } }
            .take(10)

    @Synchronized
    private fun repeatsTooOften(): Boolean {
        val now = SystemClock.elapsedRealtime()
        recentFailures.removeAll { now - it > REPEAT_WINDOW_MS }
        recentFailures.add(now)
        return recentFailures.size > MAX_REPEATS
    }

    private fun report(e: Throwable) {
        try {
            context.lawnchairApp.bugReporter.writeReport("Recovered exception", e)
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to write the bug report", t)
        }
        Handler(Looper.getMainLooper()).post {
            val now = SystemClock.elapsedRealtime()
            if (now - lastToast > REPEAT_WINDOW_MS) {
                lastToast = now
                try {
                    Toast.makeText(context, R.string.crash_guard_message, Toast.LENGTH_SHORT).show()
                } catch (t: Throwable) {
                    // Not important
                }
            }
        }
    }

    private val LIFECYCLE_METHODS = setOf(
            "handleBindApplication", "performLaunchActivity", "handleLaunchActivity",
            "performResumeActivity", "handleResumeActivity", "performPauseActivity",
            "handlePauseActivity", "performStopActivity", "performStopActivityInner",
            "handleStopActivity", "callActivityOnStop", "performDestroyActivity",
            "handleDestroyActivity", "handleRelaunchActivity", "handleCreateService",
            "handleServiceArgs", "handleStopService", "handleBindService", "handleUnbindService",
            "handleReceiver", "installProvider")
}
