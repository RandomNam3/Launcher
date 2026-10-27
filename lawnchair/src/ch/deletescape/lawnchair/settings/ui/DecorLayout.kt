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

package ch.deletescape.lawnchair.settings.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.os.Build
import android.annotation.TargetApi
import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.os.Environment
import android.support.design.widget.Snackbar
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import ch.deletescape.lawnchair.blur.BlurDrawable
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider
import ch.deletescape.lawnchair.getBooleanAttr
import ch.deletescape.lawnchair.getColorAttr
import ch.deletescape.lawnchair.getDimenAttr
import ch.deletescape.lawnchair.isVisible
import ch.deletescape.lawnchair.util.parents
import com.android.launcher3.Insettable
import com.android.launcher3.InsettableFrameLayout
import com.android.launcher3.R
import com.android.launcher3.Utilities
import java.io.File

@SuppressLint("ViewConstructor")
class DecorLayout(context: Context, private val window: Window) : InsettableFrameLayout(context, null),
        View.OnClickListener, BlurWallpaperProvider.Listener {

    private var tapCount = 0

    private val contentFrame: View
    private val actionBarContainer: View
    private val toolbar: View
    private val largeTitle: View
    private val toolbarShadow: View
    // Material 3: the top app bar has the colour of the page and gets a container colour as soon
    // as the content is scrolled under it
    private val toolbarColor = ColorDrawable(0)
    private var toolbarColorAnimator: ValueAnimator? = null
    internal var toolbarElevated = false
        set(value) {
            if (field != value) {
                field = value
                toolbarColorAnimator?.cancel()
                toolbarColorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), toolbarColor.color,
                        if (value) toolbarScrolledBackground else settingsBackground).apply {
                    duration = 200
                    addUpdateListener { setToolbarColor(it.animatedValue as Int) }
                    start()
                }
            }
        }

    private val shouldDrawBackground by lazy { context.getBooleanAttr(android.R.attr.windowShowWallpaper) }
    private val settingsBackground by lazy { context.getColorAttr(R.attr.settingsBackground) }
    private val toolbarScrolledBackground by lazy {
        context.getColorAttr(R.attr.settingsToolbarScrolledBackground)
    }

    private fun setToolbarColor(color: Int) {
        toolbarColor.color = color
        setStatusBarBackground(color)
    }

    var useLargeTitle: Boolean = false
        set(value) {
            field = value
            updateToolbar()
        }

    var hideToolbar: Boolean = false
        set(value) {
            field = value
            updateToolbar()
        }

    private val contentTop get() = when {
        hideToolbar -> 0
        useLargeTitle -> context.resources.getDimensionPixelSize(R.dimen.large_title_height)
        else -> context.getDimenAttr(R.attr.actionBarSize)
    }

    // Since Android 15 apps are always drawn edge-to-edge and Window.setStatusBarColor has no
    // effect anymore, so the status bar background is drawn by this view instead.
    private val drawStatusBarBackground = Build.VERSION.SDK_INT >= 35
    private val statusBarPaint = Paint()
    private var statusBarHeight = 0

    private fun setStatusBarBackground(color: Int) {
        if (drawStatusBarBackground) {
            statusBarPaint.color = color
            invalidate()
        } else {
            @Suppress("DEPRECATION")
            window.statusBarColor = color
        }
    }

    override fun setInsets(insets: Rect) {
        super.setInsets(insets)
        if (statusBarHeight != insets.top) {
            statusBarHeight = insets.top
            invalidate()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (drawStatusBarBackground && statusBarHeight > 0 && statusBarPaint.color != 0) {
            canvas.drawRect(0f, 0f, width.toFloat(), statusBarHeight.toFloat(), statusBarPaint)
        }
    }

    private fun updateToolbar() {
        largeTitle.visibility = if (useLargeTitle && !hideToolbar) View.VISIBLE else View.GONE
        toolbar.visibility = if (!useLargeTitle && !hideToolbar) View.VISIBLE else View.GONE
        // The status bar keeps its background also without toolbar
        actionBarContainer.background = if (hideToolbar) null else toolbarColor
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.decor_layout, this)

        contentFrame = findViewById(android.R.id.content)
        actionBarContainer = findViewById(R.id.action_bar_container)
        toolbar = findViewById(R.id.toolbar)
        largeTitle = findViewById(R.id.large_title)
        largeTitle.setOnClickListener(this)
        toolbarShadow = findViewById(R.id.toolbar_shadow)
        toolbarShadow.visibility = View.GONE
        actionBarContainer.background = toolbarColor
        setToolbarColor(settingsBackground)

        onEnabledChanged()

        if (shouldDrawBackground) {
            setWillNotDraw(false)
        }
    }

    override fun onEnabledChanged() {
        val enabled = BlurWallpaperProvider.isEnabled
        (background as? BlurDrawable)?.let {
            if (!enabled) {
                it.stopListening()
                background = null
            }
        } ?: if (enabled) {
            background = BlurWallpaperProvider.getInstance(context).createDrawable().apply {
                startListening()
            }
        } else Unit
    }

    override fun draw(canvas: Canvas) {
        if (shouldDrawBackground) canvas.drawColor(settingsBackground)
        super.draw(canvas)
    }

    override fun onClick(v: View?) {
        if (tapCount == 6 && allowDevOptions()) {
            Utilities.getLawnchairPrefs(context).developerOptionsEnabled = true
            Snackbar.make(
                    findViewById(R.id.content),
                    R.string.developer_options_enabled,
                    Snackbar.LENGTH_LONG).show()
            tapCount++
        } else if (tapCount < 6) {
            tapCount++
        }
    }

    private fun allowDevOptions(): Boolean {
        return try {
            File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), "Lawnchair/dev").exists()
        } catch (e: SecurityException) {
            false
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        BlurWallpaperProvider.getInstance(context).addListener(this)
        (background as BlurDrawable?)?.startListening()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        BlurWallpaperProvider.getInstance(context).removeListener(this)
        (background as BlurDrawable?)?.stopListening()
    }

    class ContentFrameLayout(context: Context, attrs: AttributeSet?) : InsettableFrameLayout(context, attrs) {

        private var decorLayout: DecorLayout? = null

        private val contentPath = Path()
        internal val dividerPath = Path()
        internal val backScrimPath = Path()
        internal val frontScrimPath = Path()

        private val selfRect = RectF()
        private val insetsRect = RectF()
        private val contentRect = RectF()

        private val dividerSize = Utilities.pxFromDp(1f, resources.displayMetrics).toFloat()

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            decorLayout = parents.first { it is DecorLayout } as DecorLayout
        }

        override fun setInsets(insets: Rect) {
            decorLayout?.also {
                setInsetsInternal(Rect(
                        insets.left,
                        insets.top + it.contentTop,
                        insets.right,
                        insets.bottom))
            } ?: setInsetsInternal(insets)
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            if (changed) {
                selfRect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
                computeClip()
            }
        }

        private fun setInsetsInternal(insets: Rect) {
            super.setInsets(insets)
            insetsRect.set(insets)
            computeClip()
        }

        private fun computeClip() {
            contentRect.set(
                    selfRect.left + insetsRect.left,
                    selfRect.top + insetsRect.top,
                    selfRect.right - insetsRect.right,
                    selfRect.bottom - insetsRect.bottom
            )

            dividerPath.reset()
            when {
                isNavBarToRightEdge() -> dividerPath.addRect(
                        contentRect.right,
                        selfRect.top,
                        contentRect.right + dividerSize,
                        selfRect.bottom,
                        Path.Direction.CW)
                isNavBarToLeftEdge() -> dividerPath.addRect(
                        contentRect.left - dividerSize,
                        selfRect.top,
                        contentRect.left,
                        selfRect.bottom,
                        Path.Direction.CW)
                else -> dividerPath.addRect(
                        selfRect.left,
                        contentRect.bottom,
                        selfRect.right,
                        contentRect.bottom + dividerSize,
                        Path.Direction.CW)
            }

            contentPath.reset()
            contentPath.addRect(contentRect, Path.Direction.CW)

            frontScrimPath.reset()
            frontScrimPath.addRect(selfRect, Path.Direction.CW)
            frontScrimPath.op(contentPath, Path.Op.DIFFERENCE)
            frontScrimPath.op(dividerPath, Path.Op.DIFFERENCE)

            backScrimPath.reset()
            backScrimPath.addRect(selfRect, Path.Direction.CW)
            backScrimPath.op(frontScrimPath, Path.Op.DIFFERENCE)

            invalidate()
        }

        private fun isNavBarToRightEdge(): Boolean {
            return insetsRect.bottom == 0f && insetsRect.right > 0
        }

        private fun isNavBarToLeftEdge(): Boolean {
            return insetsRect.bottom == 0f && insetsRect.left > 0
        }
    }

    class BackScrimView(context: Context, attrs: AttributeSet?) : View(context, attrs), Insettable {

        private val parent by lazy { parents.first { it is ContentFrameLayout } as ContentFrameLayout }

        override fun onFinishInflate() {
            super.onFinishInflate()
            background = background.mutate().apply { alpha = 230 }
        }

        override fun draw(canvas: Canvas) {
            val count = canvas.save()
            canvas.clipPath(parent.backScrimPath)
            super.draw(canvas)
            canvas.restoreToCount(count)
        }

        override fun setInsets(insets: Rect?) {
            // ignore this
        }
    }

    class FrontScrimView(context: Context, attrs: AttributeSet?) : View(context, attrs), Insettable {

        private val parent by lazy { parents.first { it is ContentFrameLayout } as ContentFrameLayout }

        override fun onFinishInflate() {
            super.onFinishInflate()
            background = background.mutate().apply { alpha = 230 }
        }

        override fun draw(canvas: Canvas) {
            val count = canvas.save()
            canvas.clipPath(parent.frontScrimPath)
            super.draw(canvas)
            canvas.restoreToCount(count)
            canvas.drawPath(parent.dividerPath, Paint().apply { color = 0x1f000000 })
        }

        override fun setInsets(insets: Rect?) {
            // ignore this
        }
    }

    class ToolbarElevationHelper constructor(private val scrollingView: View) : ViewTreeObserver.OnScrollChangedListener {

        private val decorLayout by lazy { scrollingView.parents.first { it is DecorLayout } as DecorLayout }

        init {
            scrollingView.viewTreeObserver.addOnScrollChangedListener(this)
        }

        override fun onScrollChanged() {
            decorLayout.toolbarElevated = scrollingView.canScrollVertically(-1)
        }

        fun destroy() {
            scrollingView.viewTreeObserver.removeOnScrollChangedListener(this)
        }
    }
}
