/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.launcher3.compat;

import android.content.Context;
import ch.deletescape.lawnchair.platform.ProfileCompat;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import com.android.launcher3.util.LongArrayMap;
import java.util.ArrayList;
import java.util.List;

public class UserManagerCompatVL extends UserManagerCompat {
    private static final String USER_CREATION_TIME_KEY = "user_creation_time_";

    protected final UserManager mUserManager;
    private final PackageManager mPm;
    private final Context mContext;

    protected LongArrayMap<UserHandle> mUsers;
    // Create a separate reverse map as LongArrayMap.indexOfValue checks if objects are same
    // and not {@link Object#equals}
    protected ArrayMap<UserHandle, Long> mUserToSerialMap;
    // Users known to be (or not to be) the private space profile
    private final ArrayMap<UserHandle, Boolean> mPrivateProfileCache = new ArrayMap<>();

    UserManagerCompatVL(Context context) {
        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        mPm = context.getPackageManager();
        mContext = context;
    }

    @Override
    public long getSerialNumberForUser(UserHandle user) {
        synchronized (this) {
            if (mUserToSerialMap != null) {
                Long serial = mUserToSerialMap.get(user);
                return serial == null ? 0 : serial;
            }
        }
        return mUserManager.getSerialNumberForUser(user);
    }

    @Override
    public UserHandle getUserForSerialNumber(long serialNumber) {
        synchronized (this) {
            if (mUsers != null) {
                return mUsers.get(serialNumber);
            }
        }
        return mUserManager.getUserForSerialNumber(serialNumber);
    }

    @Override
    public boolean isQuietModeEnabled(UserHandle user) {
        return false;
    }

    @Override
    public boolean isUserUnlocked(UserHandle user) {
        return true;
    }

    @Override
    public boolean isDemoUser() {
        return false;
    }

    @Override
    public boolean requestQuietModeEnabled(boolean enableQuietMode, UserHandle user) {
        return false;
    }

    @Override
    public boolean isAnyProfileQuietModeEnabled() {
        return false;
    }

    @Override
    public boolean isPrivateProfile(UserHandle user) {
        if (!ProfileCompat.SUPPORTS_PRIVATE_SPACE || user == null
                || android.os.Process.myUserHandle().equals(user)) {
            return false;
        }
        synchronized (mPrivateProfileCache) {
            Boolean cached = mPrivateProfileCache.get(user);
            if (cached != null) {
                return cached;
            }
        }
        boolean isPrivate = ProfileCompat.isPrivateProfile(mContext, user);
        synchronized (mPrivateProfileCache) {
            mPrivateProfileCache.put(user, isPrivate);
        }
        return isPrivate;
    }

    @Override
    public UserHandle getPrivateProfile() {
        if (!ProfileCompat.SUPPORTS_PRIVATE_SPACE) {
            return null;
        }
        for (UserHandle user : getUserProfiles()) {
            if (isPrivateProfile(user)) {
                return user;
            }
        }
        return null;
    }

    @Override
    public void enableAndResetCache() {
        synchronized (mPrivateProfileCache) {
            mPrivateProfileCache.clear();
        }
        synchronized (this) {
            mUsers = new LongArrayMap<>();
            mUserToSerialMap = new ArrayMap<>();
            List<UserHandle> users = queryUserProfiles();
            for (UserHandle user : users) {
                long serial = mUserManager.getSerialNumberForUser(user);
                if (serial < 0) {
                    serial = ProfileCompat.getSerialNumber(mContext, user);
                }
                if (serial < 0) {
                    continue;
                }
                mUsers.put(serial, user);
                mUserToSerialMap.put(user, serial);
            }
        }
    }

    /**
     * All profiles of the user, including the private space, which UserManager does not report
     * since Android 15.
     */
    private List<UserHandle> queryUserProfiles() {
        List<UserHandle> users = mUserManager.getUserProfiles();
        List<UserHandle> result = users == null
                ? new ArrayList<>() : new ArrayList<>(users);
        List<UserHandle> launcherProfiles = ProfileCompat.getProfiles(mContext);
        if (launcherProfiles != null) {
            for (UserHandle user : launcherProfiles) {
                if (!result.contains(user)) {
                    result.add(user);
                }
            }
        }
        return result;
    }

    @Override
    public List<UserHandle> getUserProfiles() {
        synchronized (this) {
            if (mUsers != null) {
                return new ArrayList<>(mUserToSerialMap.keySet());
            }
        }

        return queryUserProfiles();
    }

    @Override
    public CharSequence getBadgedLabelForUser(CharSequence label, UserHandle user) {
        if (user == null) {
            return label;
        }
        return mPm.getUserBadgedLabel(label, user);
    }
}

