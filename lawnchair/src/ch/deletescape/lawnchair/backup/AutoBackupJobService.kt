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
package ch.deletescape.lawnchair.backup

import android.app.job.JobParameters
import android.app.job.JobService
import com.android.launcher3.Utilities

/**
 * Creates the automatic backup when it is due.
 */
class AutoBackupJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        if (!AutoBackup.isDue(this)) return false
        Utilities.THREAD_POOL_EXECUTOR.execute {
            // When it fails, the next periodic run tries again
            AutoBackup.backupNow(this)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}
