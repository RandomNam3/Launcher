/*
 *     Copyright (C) 2019 Lawnchair Team.
 *
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

package ch.deletescape.lawnchair.adaptive

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.android.launcher3.R

class IconShapeCustomizeView(context: Context, attrs: AttributeSet?) :
        LinearLayout(context, attrs) {

    private val previousShape = IconShapeManager.getInstance(context).iconShape
    private var topLeft = previousShape.topLeft
        set(value) {
            if (field != value) {
                field = value
                rebuildShape()
            }
        }
    private var topRight = previousShape.topRight
        set(value) {
            if (field != value) {
                field = value
                rebuildShape()
            }
        }
    private var bottomLeft = previousShape.bottomLeft
        set(value) {
            if (field != value) {
                field = value
                rebuildShape()
            }
        }
    private var bottomRight = previousShape.bottomRight
        set(value) {
            if (field != value) {
                field = value
                rebuildShape()
            }
        }
    var currentShape = previousShape
        set(value) {
            field = value
            previewDrawable.iconShape = value
        }
    private val previewDrawable = IconShapeDrawable(currentShape)

    override fun onFinishInflate() {
        super.onFinishInflate()
        shapePreview.setImageDrawable(previewDrawable)
        (topLeftRow as IconShapeCornerRow)
                .init(R.string.icon_shape_top_left, topLeft) { topLeft = it }
        (topRightRow as IconShapeCornerRow)
                .init(R.string.icon_shape_top_right, topRight) { topRight = it }
        (bottomLeftRow as IconShapeCornerRow)
                .init(R.string.icon_shape_bottom_left, bottomLeft) { bottomLeft = it }
        (bottomRightRow as IconShapeCornerRow)
                .init(R.string.icon_shape_bottom_right, bottomRight) { bottomRight = it }
    }

    private fun rebuildShape() {
        currentShape = IconShape(topLeft, topRight, bottomLeft, bottomRight)
    }
}

// Replacements for the removed kotlin-android-extensions synthetic view properties
private val android.view.View.bottomLeftRow: android.view.View get() = findViewById(com.android.launcher3.R.id.bottomLeftRow)
private val android.view.View.bottomRightRow: android.view.View get() = findViewById(com.android.launcher3.R.id.bottomRightRow)
private val android.view.View.shapePreview: android.widget.ImageView get() = findViewById(com.android.launcher3.R.id.shapePreview)
private val android.view.View.topLeftRow: android.view.View get() = findViewById(com.android.launcher3.R.id.topLeftRow)
private val android.view.View.topRightRow: android.view.View get() = findViewById(com.android.launcher3.R.id.topRightRow)
