package com.android.launcher3.util;

import android.content.ComponentName;
import android.content.Context;
import android.os.Process;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.ItemInfoWithIcon;
import com.android.launcher3.compat.UserManagerCompat;

/**
 * Matchers that distinguish between the main user, work profiles and the private space.
 */
public final class ProfileMatchers {

    private ProfileMatchers() { }

    /** Apps of a work profile, i.e. neither the main user nor the private space. */
    public static ItemInfoMatcher ofWorkProfile(Context context) {
        final UserManagerCompat um = UserManagerCompat.getInstance(context);
        return new ItemInfoMatcher() {
            @Override
            public boolean matches(ItemInfo info, ComponentName cn) {
                return um.isWorkProfile(info.user);
            }
        };
    }

    /** Everything that does not belong to the private space. */
    public static ItemInfoMatcher notPrivateProfile(Context context) {
        final UserManagerCompat um = UserManagerCompat.getInstance(context);
        return new ItemInfoMatcher() {
            @Override
            public boolean matches(ItemInfo info, ComponentName cn) {
                return !um.isPrivateProfile(info.user);
            }
        };
    }

    /** Apps of the private space, only while it is unlocked. */
    public static ItemInfoMatcher ofUnlockedPrivateProfile(Context context) {
        final UserManagerCompat um = UserManagerCompat.getInstance(context);
        return new ItemInfoMatcher() {
            @Override
            public boolean matches(ItemInfo info, ComponentName cn) {
                return um.isPrivateProfile(info.user) && !isQuietUser(info);
            }
        };
    }

    /** Returns whether the item must not be shown because it is part of a locked private space. */
    public static boolean isHiddenPrivateItem(Context context, ItemInfo info) {
        if (info == null || Process.myUserHandle().equals(info.user)) {
            return false;
        }
        UserManagerCompat um = UserManagerCompat.getInstance(context);
        return um.isPrivateProfile(info.user)
                && (isQuietUser(info) || um.isQuietModeEnabled(info.user));
    }

    private static boolean isQuietUser(ItemInfo info) {
        return info instanceof ItemInfoWithIcon && (((ItemInfoWithIcon) info).runtimeStatusFlags
                & ItemInfoWithIcon.FLAG_DISABLED_QUIET_USER) != 0;
    }
}
