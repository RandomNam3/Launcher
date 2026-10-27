package com.google.android.apps.nexuslauncher;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;

import com.android.launcher3.AppInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherCallbacks;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * Launcher callbacks: keeps the app suggestions up to date.
 */
public class NexusLauncher {
    private final Launcher mLauncher;
    final NexusLauncherCallbacks mCallbacks;

    public NexusLauncher(NexusLauncherActivity activity) {
        mLauncher = activity;
        mCallbacks = new NexusLauncherCallbacks();
        activity.setLauncherCallbacks(mCallbacks);
    }

    class NexusLauncherCallbacks implements LauncherCallbacks {

        private final Runnable mUpdatePredictionsIfResumed = this::updatePredictionsIfResumed;

        public void bindAllApplications(final ArrayList<AppInfo> list) {
            mLauncher.getUserEventDispatcher().updatePredictions();
        }

        public void dump(final String s, final FileDescriptor fileDescriptor,
                final PrintWriter printWriter, final String[] array) {
        }

        public void finishBindingItems(final boolean b) {
        }

        public boolean handleBackPressed() {
            return false;
        }

        public boolean hasCustomContentToLeft() {
            return false;
        }

        public boolean hasSettings() {
            return true;
        }

        public void onActivityResult(final int n, final int n2, final Intent intent) {
        }

        public void onAttachedToWindow() {
        }

        public void onCreate(final Bundle bundle) {
        }

        public void onDestroy() {
        }

        public void onDetachedFromWindow() {
        }

        @Override
        public void onHomeIntent(boolean internalStateHandled) {
        }

        public void onLauncherProviderChange() {
        }

        public void onPause() {
        }

        public void onRequestPermissionsResult(final int n, final String[] array, final int[] array2) {
        }

        public void onResume() {
            Handler handler = mLauncher.getDragLayer().getHandler();
            if (handler != null) {
                handler.removeCallbacks(mUpdatePredictionsIfResumed);
                Utilities.postAsyncCallback(handler, mUpdatePredictionsIfResumed);
            }
        }

        public void onSaveInstanceState(final Bundle bundle) {
        }

        public void onStart() {
        }

        public void onStop() {
        }

        public void onTrimMemory(int n) {
        }

        public boolean startSearch(String s, boolean b, Bundle bundle) {
            View gIcon = mLauncher.findViewById(R.id.g_icon);
            while (gIcon != null && !gIcon.isClickable()) {
                if (gIcon.getParent() instanceof View) {
                    gIcon = (View) gIcon.getParent();
                } else {
                    gIcon = null;
                }
            }
            return gIcon != null && gIcon.performClick();
        }

        private void updatePredictionsIfResumed() {
            if (mLauncher.hasBeenResumed()) {
                mLauncher.getUserEventDispatcher().updatePredictions();
            }
        }
    }
}
