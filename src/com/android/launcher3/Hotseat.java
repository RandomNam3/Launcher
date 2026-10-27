/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3;

import static com.android.launcher3.LauncherState.ALL_APPS;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import com.android.launcher3.util.Themes;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import ch.deletescape.lawnchair.LawnchairPreferences;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.logging.UserEventDispatcher.LogContainerProvider;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.userevent.nano.LauncherLogProto.ControlType;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;

public class Hotseat extends FrameLayout implements LogContainerProvider, Insettable {

    private final Launcher mLauncher;
    private CellLayout mContent;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mHasVerticalHotseat;

    public Hotseat(Context context) {
        this(context, null);
    }

    public Hotseat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Hotseat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLauncher = Launcher.getLauncher(context);
        mSurfacePaint.setColor(surfaceColor(Themes.getAttrColor(context, R.attr.m3SurfaceContainerHigh)));
    }

    // Material 3: floating rounded surface behind the dock icons and the search bar
    private final android.graphics.Paint mSurfacePaint =
            new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF mSurfaceRect = new android.graphics.RectF();
    private final Rect mInsets = new Rect();

    private static int surfaceColor(int color) {
        return android.support.v4.graphics.ColorUtils.setAlphaComponent(color, 0x99);
    }

    /**
     * The surface is its own view behind the icons, so it fades together with them when the
     * launcher state changes (e.g. the options).
     */
    private final View mSurface = new View(getContext()) {
        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            drawSurface(canvas);
        }
    };

    public View getSurface() {
        return mSurface;
    }

    private void drawSurface(android.graphics.Canvas canvas) {
        LawnchairPreferences prefs = Utilities.getLawnchairPrefs(getContext());
        if (!prefs.getDockFloatingSurface() || mHasVerticalHotseat || mContent == null) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float margin = 12 * density;
        float padding = 10 * density;
        float top = mContent.getTop() + mContent.getPaddingTop() - padding;
        float bottom = mContent.getBottom() - mContent.getPaddingBottom() + padding;
        View searchBox = findViewById(R.id.search_container_hotseat);
        if (searchBox != null && searchBox.getVisibility() == VISIBLE) {
            int shadowMargin = getResources().getDimensionPixelSize(R.dimen.qsb_shadow_margin);
            bottom = Math.max(bottom, searchBox.getBottom() + searchBox.getTranslationY()
                    - shadowMargin + padding);
        }
        bottom = Math.min(bottom, getHeight() - mInsets.bottom - 4 * density);
        mSurfaceRect.set(mInsets.left + margin, top, getWidth() - mInsets.right - margin, bottom);
        float radius = 28 * density;
        canvas.drawRoundRect(mSurfaceRect, radius, radius, mSurfacePaint);
    }

    public CellLayout getLayout() {
        return mContent;
    }

    /* Get the orientation invariant order of the item in the hotseat for persistence. */
    int getOrderInHotseat(int x, int y) {
        int xOrder = mHasVerticalHotseat ? (mContent.getCountY() - y - 1) : x;
        int yOrder = mHasVerticalHotseat ? x * mContent.getCountY() : y * mContent.getCountX();
        return xOrder + yOrder;
    }

    /* Get the orientation specific coordinates given an invariant order in the hotseat. */
    int getCellXFromOrder(int rank) {
        int size = mHasVerticalHotseat ? mContent.getCountY() : mContent.getCountX();
        return mHasVerticalHotseat ? rank / size : rank % size;
    }

    int getCellYFromOrder(int rank) {
        int size = mHasVerticalHotseat ? mContent.getCountY() : mContent.getCountX();
        return mHasVerticalHotseat ? (mContent.getCountY() - ((rank % size) + 1)) : rank / size;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        LawnchairPreferences prefs = Utilities.getLawnchairPrefs(getContext());
        if (prefs.getDockHide()) {
            setVisibility(GONE);
        } else if (prefs.getDockSearchBar()) {
            inflate(getContext(), R.layout.search_container_hotseat, this);
        } else {
            View v = this.findViewById(R.id.search_container_hotseat);
            if (v != null) {
                removeView(v);
            }
        }

        mContent = findViewById(R.id.layout);
        mSurface.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(mSurface, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // The search bar moves in its own onLayout
        mSurface.invalidate();
    }

    void resetLayout(boolean hasVerticalHotseat) {
        mContent.removeAllViewsInLayout();
        mHasVerticalHotseat = hasVerticalHotseat;
        InvariantDeviceProfile idp = mLauncher.getDeviceProfile().inv;
        int rows = Utilities.getLawnchairPrefs(mLauncher).getDockRowsCount();
        if (hasVerticalHotseat) {
            mContent.setGridSize(rows, idp.numHotseatIcons);
        } else {
            mContent.setGridSize(idp.numHotseatIcons, rows);
        }

        if (!FeatureFlags.NO_ALL_APPS_ICON) {
            // Add the Apps button
            Context context = getContext();
            DeviceProfile grid = mLauncher.getDeviceProfile();
            int allAppsButtonRank = grid.inv.getAllAppsButtonRank();

            LayoutInflater inflater = LayoutInflater.from(context);
            TextView allAppsButton = (TextView)
                    inflater.inflate(R.layout.all_apps_button, mContent, false);
            Drawable d = context.getResources().getDrawable(R.drawable.all_apps_button_icon);
            d.setBounds(0, 0, grid.hotseatIconSizePx, grid.hotseatIconSizePx);

            int scaleDownPx = getResources().getDimensionPixelSize(R.dimen.all_apps_button_scale_down);
            Rect bounds = d.getBounds();
            d.setBounds(bounds.left, bounds.top + scaleDownPx / 2, bounds.right - scaleDownPx,
                    bounds.bottom - scaleDownPx / 2);
            allAppsButton.setCompoundDrawables(null, d, null, null);

            allAppsButton.setContentDescription(context.getString(R.string.all_apps_button_label));
            if (mLauncher != null) {
                allAppsButton.setOnClickListener((v) -> {
                    if (!mLauncher.isInState(ALL_APPS)) {
                        mLauncher.getUserEventDispatcher().logActionOnControl(Action.Touch.TAP,
                                ControlType.ALL_APPS_BUTTON);
                        mLauncher.getStateManager().goToState(ALL_APPS);
                    }
                });
                allAppsButton.setOnFocusChangeListener(mLauncher.mFocusHandler);
            }

            // Note: We do this to ensure that the hotseat is always laid out in the orientation of
            // the hotseat in order regardless of which orientation they were added
            int x = getCellXFromOrder(allAppsButtonRank);
            int y = getCellYFromOrder(allAppsButtonRank);
            CellLayout.LayoutParams lp = new CellLayout.LayoutParams(x, y, 1, 1);
            lp.canReorder = false;
            mContent.addViewToCellLayout(allAppsButton, -1, allAppsButton.getId(), lp, true);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // We don't want any clicks to go through to the hotseat unless the workspace is in
        // the normal state or an accessible drag is in progress.
        return !mLauncher.getWorkspace().workspaceIconsCanBeDragged() &&
                !mLauncher.getAccessibilityDelegate().isInAccessibleDrag();
    }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, Target target, Target targetParent) {
        target.gridX = info.cellX;
        target.gridY = info.cellY;
        targetParent.containerType = ContainerType.HOTSEAT;
    }

    @Override
    public void setInsets(Rect insets) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        DeviceProfile grid = mLauncher.getDeviceProfile();

        if (grid.isVerticalBarLayout()) {
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            if (grid.isSeascape()) {
                lp.gravity = Gravity.LEFT;
                lp.width = grid.hotseatBarSizePx + insets.left;
            } else {
                lp.gravity = Gravity.RIGHT;
                lp.width = grid.hotseatBarSizePx + insets.right;
            }
        } else {
            lp.gravity = Gravity.BOTTOM;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = grid.hotseatBarSizePx + insets.bottom;
        }
        Rect padding = grid.getHotseatLayoutPadding();
        getLayout().setPadding(padding.left, padding.top, padding.right, padding.bottom);

        setLayoutParams(lp);
        mInsets.set(insets);
        InsettableFrameLayout.dispatchInsets(this, insets);
        mSurface.invalidate();
    }
}
