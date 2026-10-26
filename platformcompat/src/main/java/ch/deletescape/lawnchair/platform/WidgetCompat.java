package ch.deletescape.lawnchair.platform;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.util.SizeF;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;

/**
 * App widget features of Android 12 and newer.
 */
public final class WidgetCompat {

    private static final String TAG = "WidgetCompat";

    /** AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL, API 31 */
    private static final int WIDGET_FEATURE_CONFIGURATION_OPTIONAL = 4;

    private WidgetCompat() { }

    private static boolean atLeastS() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    /** Default width in cells requested by the widget, 0 if not specified. */
    public static int getTargetCellWidth(AppWidgetProviderInfo info) {
        return atLeastS() ? info.targetCellWidth : 0;
    }

    /** Default height in cells requested by the widget, 0 if not specified. */
    public static int getTargetCellHeight(AppWidgetProviderInfo info) {
        return atLeastS() ? info.targetCellHeight : 0;
    }

    /**
     * Whether the configuration activity may be skipped when the widget is added, it can still
     * be opened later with "Reconfigure".
     */
    public static boolean isConfigurationOptional(AppWidgetProviderInfo info) {
        return atLeastS() && (info.widgetFeatures & WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0
                && (info.widgetFeatures & AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0;
    }

    /**
     * Tells the widget the exact sizes it is shown at (portrait and landscape, in dp), so that
     * widgets with responsive layouts can pick the right one.
     */
    public static void updateAppWidgetSize(AppWidgetHostView view, int portraitWidthDp,
            int portraitHeightDp, int landscapeWidthDp, int landscapeHeightDp) {
        if (!atLeastS()) {
            return;
        }
        try {
            List<SizeF> sizes = new ArrayList<>(2);
            sizes.add(new SizeF(portraitWidthDp, portraitHeightDp));
            sizes.add(new SizeF(landscapeWidthDp, landscapeHeightDp));
            view.updateAppWidgetSize(null, sizes);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to update widget sizes", e);
        }
    }

    /**
     * Clips the widget to the system's rounded widget corners, if the widget marks its
     * background with @android:id/background (Android 12 widget guidelines).
     */
    public static void enforceRoundedCorners(final AppWidgetHostView hostView) {
        if (!atLeastS()) {
            return;
        }
        View background = findBackground(hostView);
        if (background == null || background.getWidth() == 0 || background.getHeight() == 0) {
            if (hostView.getClipToOutline()) {
                hostView.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                hostView.setClipToOutline(false);
            }
            return;
        }
        final Rect bounds = new Rect(0, 0, background.getWidth(), background.getHeight());
        View v = background;
        while (v != hostView && v.getParent() instanceof View) {
            bounds.offset(v.getLeft(), v.getTop());
            v = (View) v.getParent();
        }
        final float radius = getSystemWidgetRadius(hostView.getContext());
        if (radius <= 0) {
            return;
        }
        hostView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(bounds, radius);
            }
        });
        hostView.setClipToOutline(true);
        hostView.invalidateOutline();
    }

    private static float getSystemWidgetRadius(Context context) {
        try {
            return context.getResources().getDimension(
                    android.R.dimen.system_app_widget_background_radius);
        } catch (Resources.NotFoundException e) {
            return 0;
        }
    }

    private static View findBackground(View view) {
        if (view.getId() == android.R.id.background) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findBackground(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Renders the widget preview of Android 12+ widgets: the generated preview (Android 15) or
     * the preview layout. Must be called on the main thread. Returns null if there is none.
     */
    public static Bitmap renderPreview(Context context, AppWidgetProviderInfo info,
            int width, int height) {
        if (!atLeastS() || width <= 0 || height <= 0) {
            return null;
        }
        RemoteViews views = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
                && (info.generatedPreviewCategories
                        & AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0) {
            try {
                views = AppWidgetManager.getInstance(context).getWidgetPreview(info.provider,
                        info.getProfile(), AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
            } catch (RuntimeException e) {
                Log.w(TAG, "Unable to load generated preview of " + info.provider, e);
            }
        }
        if (views == null && info.previewLayout != 0) {
            views = new RemoteViews(info.provider.getPackageName(), info.previewLayout);
        }
        if (views == null) {
            return null;
        }
        try {
            FrameLayout parent = new FrameLayout(context);
            View view = views.apply(context, parent);
            parent.addView(view, new FrameLayout.LayoutParams(width, height));
            int w = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
            int h = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);
            parent.measure(w, h);
            parent.layout(0, 0, width, height);
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            float radius = getSystemWidgetRadius(context);
            if (radius > 0) {
                android.graphics.Path clip = new android.graphics.Path();
                clip.addRoundRect(0, 0, width, height, radius, radius,
                        android.graphics.Path.Direction.CW);
                canvas.clipPath(clip);
            }
            parent.draw(canvas);
            return bitmap;
        } catch (RuntimeException | OutOfMemoryError e) {
            // Widget previews come from other apps, never let them crash us
            Log.w(TAG, "Unable to render preview of " + info.provider, e);
            return null;
        }
    }
}
