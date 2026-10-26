package me.priyesh.chroma

import android.content.Context
import android.graphics.Color
import android.support.annotation.ColorInt
import android.support.v4.graphics.ColorUtils
import android.text.Editable
import android.util.DisplayMetrics
import android.view.WindowManager

fun screenDimensions(context: Context): DisplayMetrics {
  val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
  val metrics = DisplayMetrics()
  manager.defaultDisplay.getMetrics(metrics)
  return metrics
}

fun orientation(context: Context) = context.resources.configuration.orientation

infix fun Int.percentOf(n: Int): Int = (n * (this / 100.0)).toInt()

fun hue(color: Int): Int = hsv(color, 0)
fun saturation(color: Int): Int = hsv(color, 1, 100)
fun value(color: Int): Int = hsv(color, 2, 100)

private fun hsv(color: Int, index: Int, multiplier: Int = 1): Int {
  val hsv = FloatArray(3)
  Color.colorToHSV(color, hsv)
  return (hsv[index] * multiplier).toInt()
}

fun Any.toEditable(): Editable = Editable.Factory.getInstance().newEditable(toString())

@ColorInt
internal fun Context.getColorAttr(attr: Int): Int {
  val ta = obtainStyledAttributes(intArrayOf(attr))
  @ColorInt val colorAccent = ta.getColor(0, 0)
  ta.recycle()
  return colorAccent
}

internal fun ensureTextContrast(color: Int, bg: Int): Int {
  return findContrastColor(color, bg, 4.5)
}

internal fun findContrastColor(fg: Int, bg: Int, minRatio: Double): Int {
  if (ColorUtils.calculateContrast(fg, bg) >= minRatio) {
    return fg
  }

  val lab = DoubleArray(3)
  ColorUtils.colorToLAB(bg, lab)
  val bgL = lab[0]
  ColorUtils.colorToLAB(fg, lab)
  val fgL = lab[0]
  val isBgDark = bgL < 50

  var low = if (isBgDark) fgL else 0.0
  var high = if (isBgDark) 100.0 else fgL
  val a = lab[1]
  val b = lab[2]
  for (i in (0 until 15)) {
    if (high - low <= 0.00001) break
    val l = (low + high) / 2
    if (ColorUtils.calculateContrast(ColorUtils.LABToColor(l, a, b), bg) > minRatio) {
      if (isBgDark) high = l else low = l
    } else {
      if (isBgDark) low = l else high = l
    }
  }
  return ColorUtils.LABToColor(low, a, b)
}
