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

package ch.deletescape.lawnchair.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextUtils
import android.support.v4.graphics.ColorUtils
import android.util.AttributeSet
import android.view.View
import android.widget.Switch
import ch.deletescape.lawnchair.getColorEngineAccent
import com.android.launcher3.Utilities
import com.android.launcher3.anim.Interpolators
import com.android.launcher3.util.Themes

/**
 * Material 3 switch: a 52x32dp track, outlined when off, and a thumb that grows and shows a check
 * mark when the switch is on. Drawn completely by this view, the framework switch measures and
 * clips its drawables in its own way.
 */
open class M3Switch @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = android.R.attr.switchStyle)
    : Switch(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val trackWidth = 52 * density
    private val trackHeight = 32 * density
    private val outlineWidth = 2 * density

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2 * density
    }
    private val trackRect = RectF()
    private val checkPath = Path()

    private var accentColor = context.getColorEngineAccent()
    private val outlineColor = Themes.getAttrColor(context, android.R.attr.textColorSecondary)
    private val disabledAlpha = Themes.getAlpha(context, android.R.attr.disabledAlpha)

    // 0 = off, 1 = on
    private var position = if (isChecked) 1f else 0f
    private var animator: ValueAnimator? = null

    init {
        // Nothing of the framework switch is drawn
        thumbDrawable = null
        trackDrawable = null
        showText = false
    }

    fun setAccentColor(color: Int) {
        if (accentColor != color) {
            accentColor = color
            invalidate()
        }
    }

    override fun setChecked(checked: Boolean) {
        val changed = checked != isChecked
        super.setChecked(checked)
        val target = if (checked) 1f else 0f
        animator?.cancel()
        if (changed && isAttachedToWindow && isLaidOut) {
            animator = ValueAnimator.ofFloat(position, target).apply {
                duration = 250
                interpolator = Interpolators.DEACCEL_1_5
                addUpdateListener {
                    position = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            position = target
            invalidate()
        }
    }

    override fun jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState()
        animator?.cancel()
        position = if (isChecked) 1f else 0f
    }

    private val hasLabel get() = !TextUtils.isEmpty(text)
    private val labelGap = 16 * density
    private val isRtl get() = layoutDirection == View.LAYOUT_DIRECTION_RTL

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minHeight = (trackHeight + paddingTop + paddingBottom).toInt()
        if (hasLabel) {
            // Label on the start side, measured by TextView with the space for the track
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            setMeasuredDimension(measuredWidth,
                    Math.max(measuredHeight, View.resolveSize(minHeight, heightMeasureSpec)))
            return
        }
        val width = (trackWidth + paddingLeft + paddingRight).toInt()
        setMeasuredDimension(View.resolveSize(width, widthMeasureSpec),
                             View.resolveSize(minHeight, heightMeasureSpec))
    }

    private val trackSpace get() = if (hasLabel) (trackWidth + labelGap).toInt() else 0

    override fun getCompoundPaddingLeft() = paddingLeft + if (isRtl) trackSpace else 0

    override fun getCompoundPaddingRight() = paddingRight + if (isRtl) 0 else trackSpace

    override fun onDraw(canvas: Canvas) {
        if (hasLabel) {
            // Draws the label (the framework track and thumb are not set)
            super.onDraw(canvas)
        }
        val enabled = isEnabled
        val left = if (isRtl) paddingLeft.toFloat() else width - paddingRight - trackWidth
        val top = (height - trackHeight) / 2
        trackRect.set(left, top, left + trackWidth, top + trackHeight)
        val radius = trackHeight / 2

        val onColor = if (ColorUtils.calculateLuminance(accentColor) < 0.5) Color.WHITE
                else ColorUtils.blendARGB(accentColor, Color.BLACK, 0.75f)
        val offTrack = ColorUtils.setAlphaComponent(outlineColor, 0x1F)

        // Track
        paint.style = Paint.Style.FILL
        paint.color = alpha(ColorUtils.blendARGB(offTrack, accentColor, position), enabled)
        canvas.drawRoundRect(trackRect, radius, radius, paint)
        if (position < 1f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = outlineWidth
            paint.color = alpha(ColorUtils.setAlphaComponent(outlineColor,
                    (Color.alpha(outlineColor) * (1 - position)).toInt()), enabled)
            val inset = outlineWidth / 2
            trackRect.inset(inset, inset)
            canvas.drawRoundRect(trackRect, radius - inset, radius - inset, paint)
            trackRect.inset(-inset, -inset)
        }

        // Thumb, 16dp when off, 24dp when on, 28dp while pressed
        val pressedExtra = if (isPressed) 2 * density else 0f
        val thumbRadius = Utilities.mapRange(position, 8 * density, 12 * density) + pressedExtra
        val rtl = isRtl
        val startX = trackRect.left + radius
        val endX = trackRect.right - radius
        val cx = if (rtl) Utilities.mapRange(position, endX, startX)
                else Utilities.mapRange(position, startX, endX)
        val cy = trackRect.centerY()
        paint.style = Paint.Style.FILL
        paint.color = alpha(ColorUtils.blendARGB(outlineColor, onColor, position), enabled)
        canvas.drawCircle(cx, cy, thumbRadius, paint)

        // Check mark on the thumb
        if (position > 0.5f) {
            val size = 7 * density
            checkPath.reset()
            checkPath.moveTo(cx - size * 0.55f, cy + size * 0.02f)
            checkPath.lineTo(cx - size * 0.15f, cy + size * 0.42f)
            checkPath.lineTo(cx + size * 0.6f, cy - size * 0.38f)
            checkPaint.color = alpha(ColorUtils.setAlphaComponent(accentColor,
                    ((position - 0.5f) * 2 * 255).toInt()), enabled)
            canvas.drawPath(checkPath, checkPaint)
        }
    }

    private fun alpha(color: Int, enabled: Boolean): Int {
        if (enabled) return color
        return ColorUtils.setAlphaComponent(color, Color.alpha(color) * disabledAlpha / 255)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }
}
