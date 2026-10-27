package ch.deletescape.lawnchair.platform;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherUserInfo;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import java.util.List;

/**
 * Support for the private space profile introduced in Android 15.
 */
public final class ProfileCompat {

    private static final String TAG = "ProfileCompat";

    /** Intent.ACTION_PROFILE_ADDED, API 30 */
    public static final String ACTION_PROFILE_ADDED = "android.intent.action.PROFILE_ADDED";
    /** Intent.ACTION_PROFILE_REMOVED, API 30 */
    public static final String ACTION_PROFILE_REMOVED = "android.intent.action.PROFILE_REMOVED";
    /** Intent.ACTION_PROFILE_AVAILABLE, API 34 */
    public static final String ACTION_PROFILE_AVAILABLE = "android.intent.action.PROFILE_AVAILABLE";
    /** Intent.ACTION_PROFILE_UNAVAILABLE, API 34 */
    public static final String ACTION_PROFILE_UNAVAILABLE =
            "android.intent.action.PROFILE_UNAVAILABLE";

    public static final boolean SUPPORTS_PRIVATE_SPACE =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM;

    private ProfileCompat() { }

    /**
     * Returns the profiles the launcher can access. Since Android 15 the private space is only
     * reported by LauncherApps (to the default launcher holding ACCESS_HIDDEN_PROFILES),
     * UserManager hides it. Returns null on older versions or on errors.
     */
    public static List<UserHandle> getProfiles(Context context) {
        if (!SUPPORTS_PRIVATE_SPACE) {
            return null;
        }
        try {
            return context.getSystemService(LauncherApps.class).getProfiles();
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to query profiles", e);
            return null;
        }
    }

    /**
     * Returns the serial number of the profile as LauncherApps reports it, or -1.
     */
    public static long getSerialNumber(Context context, UserHandle user) {
        if (!SUPPORTS_PRIVATE_SPACE || user == null) {
            return -1;
        }
        try {
            LauncherUserInfo info = context.getSystemService(LauncherApps.class)
                    .getLauncherUserInfo(user);
            return info != null ? info.getUserSerialNumber() : -1;
        } catch (RuntimeException | LinkageError e) {
            Log.w(TAG, "Unable to query serial number of " + user, e);
            return -1;
        }
    }

    /**
     * Returns whether the given user is a private space profile.
     */
    public static boolean isPrivateProfile(Context context, UserHandle user) {
        if (!SUPPORTS_PRIVATE_SPACE || user == null) {
            return false;
        }
        try {
            LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
            LauncherUserInfo info = launcherApps.getLauncherUserInfo(user);
            return info != null
                    && UserManager.USER_TYPE_PROFILE_PRIVATE.equals(info.getUserType());
        } catch (RuntimeException | LinkageError e) {
            Log.w(TAG, "Unable to query user type of " + user, e);
            return false;
        }
    }
}
