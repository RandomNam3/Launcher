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

package ch.deletescape.lawnchair.blur

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.launcher3.Utilities

/**
 * Stack blur (algorithm by Mario Klingemann) in plain Kotlin.
 *
 * Replaces the native hoko-blur library, whose prebuilt libraries were not aligned for devices
 * with 16 KB memory pages and which was the only native code of the app.
 */
object StackBlur {

    private const val TAG = "StackBlur"
    private const val MAX_RADIUS = 25

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Blurs [source] on a background thread and calls [callback] on the main thread with either
     * the blurred bitmap or the error. The source bitmap is never modified or recycled.
     */
    fun blurAsync(source: Bitmap, radius: Int, sampleFactor: Float, upscale: Boolean,
                  callback: (Bitmap?, Throwable?) -> Unit) {
        Utilities.THREAD_POOL_EXECUTOR.execute {
            val result = try {
                blur(source, radius, sampleFactor, upscale)
            } catch (t: Throwable) {
                // Includes OutOfMemoryError, the callers handle it
                Log.w(TAG, "Blur failed", t)
                mainHandler.post { callback(null, t) }
                return@execute
            }
            mainHandler.post { callback(result, null) }
        }
    }

    /**
     * Returns a new, blurred bitmap. [sampleFactor] > 1 blurs a downscaled copy (much faster),
     * with [upscale] the result is scaled back to the size of [source].
     */
    fun blur(source: Bitmap, radius: Int, sampleFactor: Float, upscale: Boolean): Bitmap {
        require(!source.isRecycled) { "Source bitmap is recycled" }
        val factor = if (sampleFactor < 1f) 1f else sampleFactor
        val width = Math.max(1, Math.round(source.width / factor))
        val height = Math.max(1, Math.round(source.height / factor))

        val scaled = if (width == source.width && height == source.height
                && source.config == Bitmap.Config.ARGB_8888) {
            source.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            Bitmap.createScaledBitmap(source, width, height, true).let {
                if (it.config == Bitmap.Config.ARGB_8888 && it.isMutable) it
                else it.copy(Bitmap.Config.ARGB_8888, true).also { copy ->
                    if (it !== source) it.recycle()
                }
            }
        }

        val r = Math.max(1, Math.min(radius, MAX_RADIUS))
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        blurPixels(pixels, width, height, r)
        scaled.setPixels(pixels, 0, width, 0, 0, width, height)

        if (!upscale || (width == source.width && height == source.height)) {
            return scaled
        }
        val result = Bitmap.createScaledBitmap(scaled, source.width, source.height, true)
        if (result !== scaled) scaled.recycle()
        return result
    }

    private fun blurPixels(pix: IntArray, w: Int, h: Int, radius: Int) {
        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        val a = IntArray(wh)
        val vmin = IntArray(Math.max(w, h))

        val divsum = ((div + 1) shr 1).let { it * it }
        val dv = IntArray(256 * divsum) { it / divsum }

        val stack = Array(div) { IntArray(4) }
        val r1 = radius + 1

        var yi = 0
        var yw = 0

        for (y in 0 until h) {
            var rinsum = 0; var ginsum = 0; var binsum = 0; var ainsum = 0
            var routsum = 0; var goutsum = 0; var boutsum = 0; var aoutsum = 0
            var rsum = 0; var gsum = 0; var bsum = 0; var asum = 0
            for (i in -radius..radius) {
                val p = pix[yi + Math.min(wm, Math.max(i, 0))]
                val sir = stack[i + radius]
                sir[0] = (p shr 16) and 0xff
                sir[1] = (p shr 8) and 0xff
                sir[2] = p and 0xff
                sir[3] = (p ushr 24) and 0xff
                val rbs = r1 - Math.abs(i)
                rsum += sir[0] * rbs; gsum += sir[1] * rbs; bsum += sir[2] * rbs; asum += sir[3] * rbs
                if (i > 0) {
                    rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]; ainsum += sir[3]
                } else {
                    routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]; aoutsum += sir[3]
                }
            }
            var stackpointer = radius

            for (x in 0 until w) {
                r[yi] = dv[rsum]; g[yi] = dv[gsum]; b[yi] = dv[bsum]; a[yi] = dv[asum]

                rsum -= routsum; gsum -= goutsum; bsum -= boutsum; asum -= aoutsum

                val stackstart = stackpointer - radius + div
                var sir = stack[stackstart % div]

                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]; aoutsum -= sir[3]

                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm)
                }
                val p = pix[yw + vmin[x]]

                sir[0] = (p shr 16) and 0xff
                sir[1] = (p shr 8) and 0xff
                sir[2] = p and 0xff
                sir[3] = (p ushr 24) and 0xff

                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]; ainsum += sir[3]

                rsum += rinsum; gsum += ginsum; bsum += binsum; asum += ainsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]; aoutsum += sir[3]

                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]; ainsum -= sir[3]

                yi++
            }
            yw += w
        }

        for (x in 0 until w) {
            var rinsum = 0; var ginsum = 0; var binsum = 0; var ainsum = 0
            var routsum = 0; var goutsum = 0; var boutsum = 0; var aoutsum = 0
            var rsum = 0; var gsum = 0; var bsum = 0; var asum = 0
            var yp = -radius * w
            for (i in -radius..radius) {
                yi = Math.max(0, yp) + x
                val sir = stack[i + radius]
                sir[0] = r[yi]; sir[1] = g[yi]; sir[2] = b[yi]; sir[3] = a[yi]
                val rbs = r1 - Math.abs(i)
                rsum += r[yi] * rbs; gsum += g[yi] * rbs; bsum += b[yi] * rbs; asum += a[yi] * rbs
                if (i > 0) {
                    rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]; ainsum += sir[3]
                } else {
                    routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]; aoutsum += sir[3]
                }
                if (i < hm) {
                    yp += w
                }
            }
            yi = x
            var stackpointer = radius
            for (y in 0 until h) {
                pix[yi] = (dv[asum] shl 24) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum; gsum -= goutsum; bsum -= boutsum; asum -= aoutsum

                val stackstart = stackpointer - radius + div
                var sir = stack[stackstart % div]

                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]; aoutsum -= sir[3]

                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w
                }
                val p = x + vmin[y]

                sir[0] = r[p]; sir[1] = g[p]; sir[2] = b[p]; sir[3] = a[p]

                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]; ainsum += sir[3]

                rsum += rinsum; gsum += ginsum; bsum += binsum; asum += ainsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]; aoutsum += sir[3]

                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]; ainsum -= sir[3]

                yi += w
            }
        }
    }
}
