package ch.deletescape.lawnchair.platform;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherUserInfo;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

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
