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

package ch.deletescape.lawnchair.predictions

import ch.deletescape.lawnchair.platform.ReceiverCompat

import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.view.View
import ch.deletescape.lawnchair.runOnMainThread
import ch.deletescape.lawnchair.settings.ui.SettingsActivity
import com.android.launcher3.*
import com.android.launcher3.util.ComponentKey
import com.google.android.apps.nexuslauncher.CustomAppPredictor
import com.google.android.apps.nexuslauncher.allapps.PredictionsFloatingHeader
import com.android.launcher3.util.ComponentKeyMapper
import java.util.concurrent.TimeUnit

/**
 * App suggestions in the drawer, ranked by how often apps were opened from it. Shortly after
 * headphones are connected, the apps opened in that situation come first.
 */
open class LawnchairEventPredictor(private val context: Context): CustomAppPredictor(context) {

    private val prefs by lazy { Utilities.getLawnchairPrefs(context) }
    private val packageManager by lazy { context.packageManager }

    private val handlerThread by lazy { HandlerThread("event-predictor").apply { start() }}
    private val handler by lazy { Handler(handlerThread.looper) }

    private val devicePrefs = Utilities.getDevicePrefs(context)
    private val appsList = CountRankedArrayPreference(devicePrefs, "recent_app_launches", 250)
    private val phonesList = CountRankedArrayPreference(devicePrefs, "plugged_app_launches", 20)

    /**
     * Time at which headphones have been plugged in / connected. 0 if disconnected, -1 before initialized
     */
    private var phonesConnectedAt = -1L
        set(value) {
            field = value
            if (value != -1L) {
                phonesLaunches = 0
                updatePredictions()
                if (value != 0L) {
                    // Ensure temporary predictions get removed again after
                    handler.postDelayed(this::updatePredictions, DURATION_RECENTLY)
                }
            }
        }
    /**
     * Whether headphones have just been plugged in / connected (in the last two minutes)
     * TODO: Is two minutes appropriate or do we want to increase this?
     */
    private val phonesJustConnected get() = phonesConnectedAt > 0 && SystemClock.uptimeMillis() in phonesConnectedAt until phonesConnectedAt + DURATION_RECENTLY
    /**
     * Whether or not the current app launch is relevant for headphone suggestions or not
     */
    private val relevantForPhones get() = phonesLaunches < 2 && phonesJustConnected
    /**
     * Number of launches recorded since headphones were connected
     */
    private var phonesLaunches = 0
    private val phonesStateChangeReceiver by lazy {
        object : BroadcastReceiver() {
            private var firstReceive = true

            override fun onReceive(context: Context, intent: Intent) {
                if (!firstReceive) {
                    phonesConnectedAt = when (intent.action) {
                        Intent.ACTION_HEADSET_PLUG -> {
                            when (intent.getIntExtra("state", -1)) {
                                1 -> SystemClock.currentThreadTimeMillis()
                                else -> 0
                            }
                        }
                        BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                            when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)) {
                                2 -> SystemClock.currentThreadTimeMillis()
                                else -> 0
                            }
                        }
                        else -> 0
                    }
                }
                firstReceive = false
            }
        }
    }

    init {
        if (isPredictorEnabled) {
            setupBroadcastReceiver()
        }
    }

    private fun setupBroadcastReceiver() {
        ReceiverCompat.registerNotExported(context, phonesStateChangeReceiver,
                IntentFilter(Intent.ACTION_HEADSET_PLUG).apply {
                    addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                }, null, handler)
    }

    private fun tearDownBroadcastReceiver() {
        try {
            context.unregisterReceiver(phonesStateChangeReceiver)
        } catch(ignored: Exception) {
            // there is apparently no way to reliably check if a receiver is actually registered and
            // an exception is thrown when trying to unregister one that never was
        }
    }

    override fun updatePredictions() {
        super.updatePredictions()
        runOnMainThread {
            val header = LauncherAppState.getInstanceNoCreate()?.launcher?.appsView
                    ?.floatingHeaderView as? PredictionsFloatingHeader ?: return@runOnMainThread
            header.setPredictedApps(isPredictorEnabled, predictions)
        }
    }

    override fun logAppLaunch(v: View?, intent: Intent?, user: UserHandle?) {
        super.logAppLaunch(v, intent, user)
        logAppLaunchImpl(v, intent, user ?: Process.myUserHandle())
    }

    private fun logAppLaunchImpl(v: View?, intent: Intent?, user: UserHandle) {
        if (isPredictorEnabled) {
            if (intent?.component != null && mAppFilter.shouldShowApp(intent.component, user)) {
                clearRemovedComponents()

                // Launches from everywhere count, the home screen and the dock as well
                val key = ComponentKey(intent.component, user).toString()
                appsList.add(key)
                if (relevantForPhones) {
                    phonesList.add(key)
                    phonesLaunches++
                }
                updatePredictions()
            }
        }
    }

    // TODO: There must be a better, more elegant way to concatenate these lists
    override fun getPredictions(): MutableList<ComponentKeyMapper<AppInfo>> {
        return if (isPredictorEnabled) {
            clearRemovedComponents()
            val user = Process.myUserHandle()
            val appList = if (phonesJustConnected) phonesList.getRanked().take(MAX_HEADPHONE_SUGGESTIONS).toMutableList() else mutableListOf()
            appList.addAll(appsList.getRanked().filterNot { appList.contains(it) }.take(MAX_PREDICTIONS - appList.size))
            val fullList = appList.map { getComponentFromString(it) }
                    .filterNot { isHiddenApp(context, it.key) }.toMutableList()
            if (fullList.size < MAX_PREDICTIONS) {
                fullList.addAll(
                        PLACE_HOLDERS.mapNotNull { packageManager.getLaunchIntentForPackage(it)?.component }
                                .map { ComponentKeyMapper<AppInfo>(ComponentKey(it, user)) }
                )
            }
            fullList.take(MAX_PREDICTIONS).toMutableList()
        } else mutableListOf()
    }

    // TODO: Extension function?
    private fun clearRemovedComponents() {
        appsList.removeAll {
            val component = getComponentFromString(it).key?.componentName ?: return@removeAll true
            try {
                packageManager.getActivityInfo(component, 0)
                false
            } catch (ignored: PackageManager.NameNotFoundException) {
                val intent = packageManager.getLaunchIntentForPackage(component.packageName)
                if (intent != null) {
                    val componentInfo = intent.component
                    if (componentInfo != null) {
                        val key = ComponentKey(componentInfo, Process.myUserHandle())
                        appsList.replace(it, key.toString())
                        return@removeAll false
                    }
                }
                true
            }
        }
        phonesList.removeAll {
            val component = getComponentFromString(it).key?.componentName ?: return@removeAll true
            try {
                packageManager.getActivityInfo(component, 0)
                false
            } catch (ignored: PackageManager.NameNotFoundException) {
                val intent = packageManager.getLaunchIntentForPackage(component.packageName)
                if (intent != null) {
                    val componentInfo = intent.component
                    if (componentInfo != null) {
                        val key = ComponentKey(componentInfo, Process.myUserHandle())
                        phonesList.replace(it, key.toString())
                        return@removeAll false
                    }
                }
                true
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == SettingsActivity.SHOW_PREDICTIONS_PREF) {
            if (!isPredictorEnabled) {
                appsList.clear()
                tearDownBroadcastReceiver()
            } else {
                setupBroadcastReceiver()
            }
            updatePredictions()
        } else if (key == "pref_hidden_prediction_set") {
            updatePredictions()
        }
    }

    /**
     * A ranked list with roll over to get/store currently relevant events and rank them by occurence
     */
    inner class CountRankedArrayPreference(private val prefs: SharedPreferences, private val key: String, private val maxSize: Int = -1, private val delimiter: String = ";") {
        private var list = load()

        fun getRanked() : Set<String> = list.distinct().sortedBy { value -> list.count { it == value } }.reversed().toSet()

        fun add(string: String) {
            list.add(0, string)
            if (maxSize >= 0 && list.size > maxSize) {
                list = list.take(maxSize).toMutableList()
            }
            save()
        }

        fun clear() {
            list.clear()
            prefs.edit().remove(key).apply()
        }
        fun removeAll(filter: (String) -> Boolean) = list.removeAll(filter)
        fun replace(filter: String, replacement: String) {
            list = list.map { if (it == filter) replacement else it }.toMutableList()
        }
        fun contains(element: String) = list.contains(element)

        private fun load() = (prefs.getString(key, "") ?: "").split(delimiter)
                .filter { it.isNotEmpty() }.toMutableList()
        private fun save() {
            val strValue = list.joinToString(delimiter)
            prefs.edit().putString(key, strValue).apply()
        }
    }

    companion object {
        // TODO: Increase to two?
        const val MAX_HEADPHONE_SUGGESTIONS = 1
        // Our definition of "Recently"
        @JvmStatic
        val DURATION_RECENTLY = TimeUnit.MINUTES.toMillis(2)
    }
}