package com.android.launcher3.allapps;

import android.content.Context;
import android.os.AsyncTask;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.widget.Switch;

import ch.deletescape.lawnchair.LawnchairUtilsKt;
import ch.deletescape.lawnchair.colors.ColorEngine;
import com.android.launcher3.compat.UserManagerCompat;

/**
 * Locks and unlocks the private space (Android 15). Checked means unlocked.
 * Modelled after {@link WorkModeSwitch}, but only affects the private space profile.
 */
public class PrivateSpaceSwitch extends Switch {

    public PrivateSpaceSwitch(Context context) {
        super(context);
    }

    public PrivateSpaceSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PrivateSpaceSwitch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setChecked(boolean checked) {
        // No-op, do not change the checked state until the profile state broadcast is received.
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        int accent = ColorEngine.getInstance(getContext()).getAccent();
        LawnchairUtilsKt.applyColor(this, accent);
    }

    @Override
    public void toggle() {
        // Checked (unlocked) -> lock by enabling quiet mode and vice versa
        requestQuietModeAsync(isChecked());
    }

    private void setCheckedInternal(boolean checked) {
        super.setChecked(checked);
    }

    public void refresh() {
        UserManagerCompat userManager = UserManagerCompat.getInstance(getContext());
        setCheckedInternal(!userManager.isPrivateSpaceLocked());
        setEnabled(userManager.getPrivateProfile() != null);
    }

    private void requestQuietModeAsync(final boolean enableQuietMode) {
        final UserManagerCompat userManager = UserManagerCompat.getInstance(getContext());
        final UserHandle user = userManager.getPrivateProfile();
        if (user == null) {
            return;
        }
        new AsyncTask<Void, Void, Boolean>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                setEnabled(false);
            }

            @Override
            protected Boolean doInBackground(Void... voids) {
                // Returns false if the system has to ask for the credentials first
                return userManager.requestQuietModeEnabled(enableQuietMode, user);
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (!success) {
                    // Either cancelled or waiting for the credential prompt, the state will be
                    // refreshed by the profile availability broadcast.
                    setEnabled(true);
                }
            }
        }.execute();
    }
}
