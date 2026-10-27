/*
 * Copyright (C) 2018 The Android Open Source Project
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

package ch.deletescape.lawnchair.settings.ui;

import static ch.deletescape.lawnchair.settings.ui.SettingsActivity.EXTRA_FRAGMENT_ARG_KEY;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.support.annotation.VisibleForTesting;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceGroup;
import android.support.v7.preference.PreferenceGroupAdapter;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.PreferenceViewHolder;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import ch.deletescape.lawnchair.colors.ColorEngine;
import ch.deletescape.lawnchair.preferences.AdvancedPreferencesGroup;
import com.android.launcher3.R;

public class HighlightablePreferenceGroupAdapter extends PreferenceGroupAdapter {

    private static final String TAG = "HighlightableAdapter";
    @VisibleForTesting
    static final long DELAY_HIGHLIGHT_DURATION_MILLIS = 600L;
    private static final long HIGHLIGHT_DURATION = 15000L;
    private static final long HIGHLIGHT_FADE_OUT_DURATION = 500L;
    private static final long HIGHLIGHT_FADE_IN_DURATION = 200L;

    final int mInvisibleBackground;
    @VisibleForTesting
    final int mHighlightColor;
    @VisibleForTesting
    boolean mFadeInAnimated;

    private final int mNormalBackgroundRes;
    private final String mHighlightKey;
    private boolean mHighlightRequested;
    private int mHighlightPosition = RecyclerView.NO_POSITION;

    // Material 3 Expressive: the preferences of a category are shown as a group of cards
    private final boolean mCardsEnabled;
    private final int mCardColor;
    private final int mRippleColor;
    private final int mCardMargin;
    private final int mCardGap;
    private final float mCardRadiusLarge;
    private final float mCardRadiusSmall;


    /**
     * Tries to override initial expanded child count.
     * <p/>
     * Initial expanded child count will be ignored if: 1. fragment contains request to highlight a
     * particular row. 2. count value is invalid.
     */
    public static void adjustInitialExpandedChildCount(SettingsActivity.BaseFragment host) {
        if (host == null) {
            return;
        }
        final PreferenceScreen screen = host.getPreferenceScreen();
        if (screen == null) {
            return;
        }
        final Bundle arguments = host.getArguments();
        if (arguments != null) {
            final String highlightKey = arguments.getString(EXTRA_FRAGMENT_ARG_KEY);
            if (!TextUtils.isEmpty(highlightKey)) {
                // Has highlight row - expand everything
                screen.setInitialExpandedChildrenCount(Integer.MAX_VALUE);
                return;
            }
        }

        final int initialCount = host.getInitialExpandedChildCount();
        if (initialCount <= 0) {
            return;
        }
        screen.setInitialExpandedChildrenCount(initialCount);
    }

    public HighlightablePreferenceGroupAdapter(PreferenceGroup preferenceGroup, String key,
            boolean highlightRequested) {
        super(preferenceGroup);
        mHighlightKey = key;
        mHighlightRequested = highlightRequested;
        final Context context = preferenceGroup.getContext();
        final TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,
                outValue, true /* resolveRefs */);
        mNormalBackgroundRes = outValue.resourceId;
        context.getTheme().resolveAttribute(android.R.attr.windowBackground, outValue, true);
        mInvisibleBackground = ColorUtils
                .setAlphaComponent(ContextCompat.getColor(context, outValue.resourceId), 0);
        int accent = ColorEngine.getInstance(context).getAccent();
        mHighlightColor = ColorUtils.setAlphaComponent(accent, (int) (255 * 0.26));

        Integer cardColor = resolveColor(context, R.attr.settingsCardBackground);
        mCardsEnabled = cardColor != null;
        mCardColor = mCardsEnabled ? cardColor : 0;
        Integer rippleColor = resolveColor(context, android.R.attr.colorControlHighlight);
        mRippleColor = rippleColor != null ? rippleColor : 0x1F000000;
        Resources res = context.getResources();
        mCardMargin = res.getDimensionPixelSize(R.dimen.settings_card_margin);
        mCardGap = res.getDimensionPixelSize(R.dimen.settings_card_gap);
        mCardRadiusLarge = res.getDimension(R.dimen.settings_card_radius);
        mCardRadiusSmall = res.getDimension(R.dimen.settings_card_radius_inner);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        applyCard(holder, position);
        updateBackground(holder, position);
    }

    private static Integer resolveColor(Context context, int attr) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attr, value, true)) return null;
        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        if (value.resourceId != 0) {
            try {
                return ContextCompat.getColor(context, value.resourceId);
            } catch (Resources.NotFoundException e) {
                return null;
            }
        }
        return null;
    }

    private boolean isGroupBoundary(int position) {
        return position < 0 || position >= getItemCount()
                || getItem(position) instanceof PreferenceCategory;
    }

    /**
     * Places the preference in its card group: rounded corners at the start and the end of the
     * group, small corners and a small gap in between.
     */
    private void applyCard(PreferenceViewHolder holder, int position) {
        if (!mCardsEnabled) return;
        // Category titles have their own view type, their views are never reused for cards
        if (getItem(position) instanceof PreferenceCategory) return;
        View v = holder.itemView;
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (!(lp instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
        boolean first = isGroupBoundary(position - 1);
        boolean last = isGroupBoundary(position + 1);
        float top = first ? mCardRadiusLarge : mCardRadiusSmall;
        float bottom = last ? mCardRadiusLarge : mCardRadiusSmall;
        float[] radii = new float[] {top, top, top, top, bottom, bottom, bottom, bottom};
        mlp.setMargins(mCardMargin, first ? 0 : mCardGap, mCardMargin,
                position == getItemCount() - 1 ? mCardMargin : 0);
        v.setLayoutParams(mlp);
        v.setTag(R.id.preference_card_radii, radii);
        if (!Boolean.TRUE.equals(v.getTag(R.id.preference_highlighted))) {
            v.setBackground(createCardBackground(radii, mCardColor, true));
        }
    }

    private Drawable createCardBackground(float[] radii, int color, boolean ripple) {
        GradientDrawable card = new GradientDrawable();
        card.setCornerRadii(radii);
        card.setColor(color);
        return ripple ? new RippleDrawable(ColorStateList.valueOf(mRippleColor), card, null) : card;
    }

    private void setHighlightColor(View v, int color) {
        Object radii = v.getTag(R.id.preference_card_radii);
        if (mCardsEnabled && radii instanceof float[]) {
            v.setBackground(createCardBackground((float[]) radii,
                    ColorUtils.compositeColors(color, mCardColor), false));
        } else {
            v.setBackgroundColor(color);
        }
    }

    private void setNormalBackground(View v) {
        Object radii = v.getTag(R.id.preference_card_radii);
        if (mCardsEnabled && radii instanceof float[]) {
            v.setBackground(createCardBackground((float[]) radii, mCardColor, true));
        } else {
            v.setBackgroundResource(mNormalBackgroundRes);
        }
    }

    @VisibleForTesting
    void updateBackground(PreferenceViewHolder holder, int position) {
        View v = holder.itemView;
        if (position == mHighlightPosition) {
            // This position should be highlighted. If it's highlighted before - skip animation.
            addHighlightBackground(v, !mFadeInAnimated);
        } else if (Boolean.TRUE.equals(v.getTag(R.id.preference_highlighted))) {
            // View with highlight is reused for a view that should not have highlight
            removeHighlightBackground(v, false /* animate */);
        }
    }

    public void requestHighlight(View root, RecyclerView recyclerView) {
        if (mHighlightRequested || recyclerView == null || TextUtils.isEmpty(mHighlightKey)) {
            return;
        }
        int count = getItemCount();
        for (int i = 0; i < count; i++) {
            Preference pref = getItem(i);
            if (pref instanceof AdvancedPreferencesGroup) {
                AdvancedPreferencesGroup apg = (AdvancedPreferencesGroup) pref;
                if (apg.contains(mHighlightKey)) {
                    apg.setExpanded(true);
                }
            }
        }
        final int position = getPreferenceAdapterPosition(mHighlightKey);
        if (position < 0) {
            return;
        }
        root.postDelayed(() -> {
            mHighlightRequested = true;
            recyclerView.smoothScrollToPosition(position);
            mHighlightPosition = position;
            notifyItemChanged(position);
        }, DELAY_HIGHLIGHT_DURATION_MILLIS);
    }

    public boolean isHighlightRequested() {
        return mHighlightRequested;
    }

    @VisibleForTesting
    void requestRemoveHighlightDelayed(View v) {
        v.postDelayed(() -> {
            mHighlightPosition = RecyclerView.NO_POSITION;
            removeHighlightBackground(v, true /* animate */);
        }, HIGHLIGHT_DURATION);
    }

    private void addHighlightBackground(View v, boolean animate) {
        v.setTag(R.id.preference_highlighted, true);
        if (!animate) {
            setHighlightColor(v, mHighlightColor);
            Log.d(TAG, "AddHighlight: Not animation requested - setting highlight background");
            requestRemoveHighlightDelayed(v);
            return;
        }
        mFadeInAnimated = true;
        final int colorFrom = mInvisibleBackground;
        final int colorTo = mHighlightColor;
        final ValueAnimator fadeInLoop = ValueAnimator.ofObject(
                new ArgbEvaluator(), colorFrom, colorTo);
        fadeInLoop.setDuration(HIGHLIGHT_FADE_IN_DURATION);
        fadeInLoop.addUpdateListener(
                animator -> setHighlightColor(v, (int) animator.getAnimatedValue()));
        fadeInLoop.setRepeatMode(ValueAnimator.REVERSE);
        fadeInLoop.setRepeatCount(4);
        fadeInLoop.start();
        Log.d(TAG, "AddHighlight: starting fade in animation");
        requestRemoveHighlightDelayed(v);
    }

    private void removeHighlightBackground(View v, boolean animate) {
        if (!animate) {
            v.setTag(R.id.preference_highlighted, false);
            setNormalBackground(v);
            Log.d(TAG, "RemoveHighlight: No animation requested - setting normal background");
            return;
        }

        if (!Boolean.TRUE.equals(v.getTag(R.id.preference_highlighted))) {
            // Not highlighted, no-op
            Log.d(TAG, "RemoveHighlight: Not highlighted - skipping");
            return;
        }
        int colorFrom = mHighlightColor;
        int colorTo = mInvisibleBackground;

        v.setTag(R.id.preference_highlighted, false);
        final ValueAnimator colorAnimation = ValueAnimator.ofObject(
                new ArgbEvaluator(), colorFrom, colorTo);
        colorAnimation.setDuration(HIGHLIGHT_FADE_OUT_DURATION);
        colorAnimation.addUpdateListener(
                animator -> setHighlightColor(v, (int) animator.getAnimatedValue()));
        colorAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Animation complete - the background is now white. Change to mNormalBackgroundRes
                // so it is white and has ripple on touch.
                setNormalBackground(v);
            }
        });
        colorAnimation.start();
        Log.d(TAG, "Starting fade out animation");
    }
}
