package com.google.android.apps.nexuslauncher.allapps;

import com.android.launcher3.AppInfo;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.animation.Interpolator;
import ch.deletescape.lawnchair.LawnchairPreferences;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsContainerView.AdapterHolder;
import com.android.launcher3.allapps.FloatingHeaderView;
import com.android.launcher3.anim.PropertySetter;
import ch.deletescape.lawnchair.settings.ui.SettingsActivity;
import com.android.launcher3.util.ComponentKeyMapper;
import com.google.android.apps.nexuslauncher.allapps.PredictionRowView.DividerType;
import java.util.List;

@TargetApi(26)
public class PredictionsFloatingHeader extends FloatingHeaderView implements Insettable {
    private static final FloatProperty<PredictionsFloatingHeader> CONTENT_ALPHA = new FloatProperty<PredictionsFloatingHeader>("contentAlpha") {
        public void setValue(PredictionsFloatingHeader predictionsFloatingHeader, float f) {
            predictionsFloatingHeader.setContentAlpha(f);
        }

        public Float get(PredictionsFloatingHeader predictionsFloatingHeader) {
            return predictionsFloatingHeader.mContentAlpha;
        }
    };
    private float mContentAlpha;
    private final int mHeaderTopPadding;
    private boolean mIsCollapsed;
    private boolean mIsVerticalLayout;
    private PredictionRowView mPredictionRowView;
    private boolean mShowAllAppsLabel;

    public PredictionsFloatingHeader(Context context) {
        this(context, null);
    }

    public PredictionsFloatingHeader(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mContentAlpha = 1.0f;
        mHeaderTopPadding = context.getResources().getDimensionPixelSize(R.dimen.all_apps_header_top_padding);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mPredictionRowView = findViewById(R.id.prediction_row);
        updateShowAllAppsLabel();
    }

    @Override
    public void setup(AdapterHolder[] adapterHolderArr, boolean z) {
        mPredictionRowView.setup(this, arePredictionsEnabled());
        mTabsHidden = z;
        updateExpectedHeight();
        super.setup(adapterHolderArr, z);
    }

    private void updateExpectedHeight() {
        boolean useAllAppsLabel = mShowAllAppsLabel && mTabsHidden;
        // Material 3: no divider line between the suggestions and the apps
        DividerType dividerType = useAllAppsLabel ? DividerType.ALL_APPS_LABEL : DividerType.NONE;
        mPredictionRowView.setDividerType(dividerType, false);
        mMaxTranslation = mPredictionRowView.getExpectedHeight();
    }

    @Override
    public int getMaxTranslation() {
        if (mMaxTranslation == 0 && mTabsHidden) {
            return getResources().getDimensionPixelSize(R.dimen.all_apps_search_bar_bottom_padding);
        }
        if (mMaxTranslation <= 0 || !mTabsHidden) {
            return mMaxTranslation;
        }
        return mMaxTranslation + getPaddingTop();
    }

    public PredictionRowView getPredictionRowView() {
        return mPredictionRowView;
    }

    @Override
    public void setInsets(Rect rect) {
        DeviceProfile deviceProfile = Launcher.getLauncher(getContext()).getDeviceProfile();
        int i = deviceProfile.desiredWorkspaceLeftRightMarginPx + deviceProfile.cellLayoutPaddingLeftRightPx;
        mPredictionRowView.setPadding(i, mPredictionRowView.getPaddingTop(), i, mPredictionRowView.getPaddingBottom());
        mIsVerticalLayout = deviceProfile.isVerticalBarLayout();
    }

    public void headerChanged() {
        int i = mMaxTranslation;
        updateExpectedHeight();
        if (mMaxTranslation != i) {
            Launcher.getLauncher(getContext()).getAppsView().setupHeader();
        }
    }

    @Override
    protected void applyScroll(int uncappedY, int currentY) {
        if (uncappedY < currentY - mHeaderTopPadding) {
            mPredictionRowView.setScrolledOut(true);
            return;
        }
        LawnchairPreferences prefs = Utilities.getLawnchairPrefs(getContext());
        float translationY = uncappedY;
        if (!prefs.getDockSearchBar() || prefs.getDockHide()) {
            int qsbHeight = getResources().getDimensionPixelSize(R.dimen.qsb_widget_height);
            translationY -= mHeaderTopPadding;
            translationY += qsbHeight / 2;
        }
        mPredictionRowView.setScrolledOut(false);
        mPredictionRowView.setScrollTranslation(translationY);
    }

    @Override
    public void setContentVisibility(boolean hasHeader, boolean hasContent, PropertySetter propertySetter, Interpolator interpolator) {
        if (hasHeader && !hasContent && mIsCollapsed) {
            Launcher.getLauncher(getContext()).getAppsView().getSearchUiManager().resetSearch();
        }
        allowTouchForwarding(hasContent);
        propertySetter.setFloat(this, CONTENT_ALPHA, hasContent ? 1.0f : 0.0f, interpolator);
        mPredictionRowView.setContentVisibility(hasHeader, hasContent, propertySetter, interpolator);
    }

    public void updateShowAllAppsLabel() {
        setShowAllAppsLabel(Utilities.ATLEAST_MARSHMALLOW && Utilities.getLawnchairPrefs(getContext()).getShowAllAppsLabel());
    }

    public void setShowAllAppsLabel(boolean show) {
        if (mShowAllAppsLabel != show) {
            mShowAllAppsLabel = show;
            headerChanged();
        }
    }

    private void setContentAlpha(float alpha) {
        mContentAlpha = alpha;
        mTabLayout.setAlpha(alpha);
    }

    public boolean hasVisibleContent() {
        return arePredictionsEnabled();
    }

    private boolean arePredictionsEnabled() {
        return Utilities.getPrefs(getContext()).getBoolean(SettingsActivity.SHOW_PREDICTIONS_PREF, true);
    }

    public void setCollapsed(boolean collapsed) {
        if (collapsed != mIsCollapsed) {
            mIsCollapsed = collapsed;
            mPredictionRowView.setCollapsed(collapsed);
            headerChanged();
        }
    }

    public void setPredictedApps(boolean z, List<ComponentKeyMapper<AppInfo>> list) {
        mPredictionRowView.setPredictedApps(z, list);
    }
}
