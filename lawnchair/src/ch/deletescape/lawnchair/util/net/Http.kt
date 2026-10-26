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

package ch.deletescape.lawnchair.util.net

import android.os.Handler
import android.os.Looper
import com.android.launcher3.Utilities
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

/**
 * Minimal HTTPS client, replaces Retrofit, OkHttp and Gson.
 *
 * Only https URLs are accepted, responses are limited in size and all errors are reported as
 * [HttpException] / [IOException] instead of being thrown on the main thread.
 */
object Http {

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    private const val USER_AGENT = "Launcher (Android)"

    private val mainHandler = Handler(Looper.getMainLooper())

    class HttpException(val code: Int, message: String, val body: String?) : IOException(message)

    /**
     * Performs a GET request on the calling thread. Must not be called on the main thread.
     */
    @Throws(IOException::class)
    fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val parsed = URL(url)
        if (parsed.protocol != "https") {
            throw IOException("Only https is allowed: $url")
        }
        val connection = parsed.openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json, text/plain, */*")
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }

            val code = connection.responseCode
            val charset = charsetOf(connection.contentType)
            if (code !in 200..299) {
                val body = try {
                    connection.errorStream?.use { readLimited(it, charset) }
                } catch (e: IOException) {
                    null
                }
                throw HttpException(code, "HTTP $code", body)
            }
            return connection.inputStream.use { readLimited(it, charset) }
        } finally {
            connection.disconnect()
        }
    }

    @Throws(IOException::class)
    fun getJsonObject(url: String, headers: Map<String, String> = emptyMap()): JSONObject {
        return try {
            JSONObject(get(url, headers))
        } catch (e: org.json.JSONException) {
            throw IOException("Invalid JSON from $url", e)
        }
    }

    @Throws(IOException::class)
    fun getJsonArray(url: String, headers: Map<String, String> = emptyMap()): JSONArray {
        return try {
            JSONArray(get(url, headers))
        } catch (e: org.json.JSONException) {
            throw IOException("Invalid JSON from $url", e)
        }
    }

    /**
     * Runs [request] on a background thread and delivers the result on the main thread.
     */
    fun <T> async(request: () -> T, callback: (T?, Exception?) -> Unit) {
        Utilities.THREAD_POOL_EXECUTOR.execute {
            try {
                val result = request()
                mainHandler.post { callback(result, null) }
            } catch (e: Exception) {
                mainHandler.post { callback(null, e) }
            }
        }
    }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun charsetOf(contentType: String?): Charset {
        val name = contentType?.split(';')
                ?.map { it.trim() }
                ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
                ?.substringAfter('=')
                ?.trim('"', ' ')
        return try {
            if (name.isNullOrEmpty()) StandardCharsets.UTF_8 else Charset.forName(name)
        } catch (e: Exception) {
            StandardCharsets.UTF_8
        }
    }

    @Throws(IOException::class)
    private fun readLimited(input: InputStream, charset: Charset): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_RESPONSE_BYTES) {
                throw IOException("Response too large")
            }
            out.write(buffer, 0, read)
        }
        return String(out.toByteArray(), charset)
    }
}
