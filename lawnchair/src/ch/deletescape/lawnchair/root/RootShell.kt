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

package ch.deletescape.lawnchair.root

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.launcher3.Utilities
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Minimal "su" shell, replaces the libsu library.
 */
object RootShell {

    private const val TAG = "RootShell"
    private const val TIMEOUT_SECONDS = 30L

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Whether root access is available and granted. Blocks, don't call on the main thread.
     */
    val isRootAvailable: Boolean by lazy {
        val output = StringBuilder()
        run(listOf("id"), output) && output.contains("uid=0")
    }

    /**
     * Runs [commands] as root on a background thread, [callback] is called on the main thread.
     */
    fun submit(commands: List<String>, callback: ((Boolean) -> Unit)? = null) {
        Utilities.THREAD_POOL_EXECUTOR.execute {
            val success = run(commands, null)
            if (callback != null) mainHandler.post { callback(success) }
        }
    }

    /**
     * Starts a long running root process (e.g. the root helper daemon) without waiting for it.
     */
    fun launch(commands: List<String>) {
        Utilities.THREAD_POOL_EXECUTOR.execute {
            try {
                val process = start(commands)
                drain(process.inputStream)
                drain(process.errorStream)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to launch root process", e)
            }
        }
    }

    private fun run(commands: List<String>, output: StringBuilder?): Boolean {
        return try {
            val process = start(commands)
            val out = Thread { readInto(process.inputStream, output) }.apply { start() }
            drain(process.errorStream)
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroy()
                return false
            }
            out.join(1000)
            process.exitValue() == 0
        } catch (e: IOException) {
            // No su binary
            false
        } catch (e: Exception) {
            Log.w(TAG, "Root command failed", e)
            false
        }
    }

    @Throws(IOException::class)
    private fun start(commands: List<String>): Process {
        val process = ProcessBuilder("su").start()
        process.outputStream.bufferedWriter().use { writer ->
            commands.forEach { writer.write(it); writer.write("\n") }
            writer.write("exit\n")
        }
        return process
    }

    private fun drain(input: InputStream) {
        Thread { readInto(input, null) }.apply { isDaemon = true; start() }
    }

    private fun readInto(input: InputStream, output: StringBuilder?) {
        try {
            input.bufferedReader().useLines { lines ->
                lines.forEach { line -> output?.let { synchronized(it) { it.append(line).append('\n') } } }
            }
        } catch (e: IOException) {
            // Process ended
        }
    }
}
