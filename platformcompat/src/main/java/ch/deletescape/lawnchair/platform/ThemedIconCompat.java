package ch.deletescape.lawnchair.platform;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

/**
 * Themed icons (Android 13+): the monochrome layer of an adaptive icon, drawn in the colours of
 * the dynamic system palette.
 */
public final class ThemedIconCompat {

    private ThemedIconCompat() { }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= 33;
    }

    /**
     * Returns the background and foreground colour of themed icons, the same colours the system
     * launcher uses, or null if themed icons are not supported.
     */
    public static int[] getColors(Context context, boolean dark) {
        if (!isSupported()) return null;
        Resources res = context.getResources();
        if (dark) {
            return new int[] {
                    res.getColor(android.R.color.system_neutral1_800, null),
                    res.getColor(android.R.color.system_accent1_100, null)
            };
        } else {
            return new int[] {
                    res.getColor(android.R.color.system_accent1_100, null),
                    res.getColor(android.R.color.system_neutral2_700, null)
            };
        }
    }

    /**
     * Returns the themed version of the icon, or null if it has no monochrome layer.
     */
    public static Drawable createThemedIcon(Drawable icon, int background, int foreground) {
        if (!isSupported() || !(icon instanceof AdaptiveIconDrawable)) return null;
        Drawable monochrome = ((AdaptiveIconDrawable) icon).getMonochrome();
        if (monochrome == null) return null;
        Drawable.ConstantState state = monochrome.getConstantState();
        monochrome = (state != null ? state.newDrawable() : monochrome).mutate();
        monochrome.setTint(foreground);
        monochrome.setTintMode(PorterDuff.Mode.SRC_IN);
        return new AdaptiveIconDrawable(new ColorDrawable(background), monochrome);
    }
}
