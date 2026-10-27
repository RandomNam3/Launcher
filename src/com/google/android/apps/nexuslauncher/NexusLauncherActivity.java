package com.google.android.apps.nexuslauncher;

import com.android.launcher3.Launcher;

public class NexusLauncherActivity extends Launcher {
    private NexusLauncher mLauncher;

    public NexusLauncherActivity() {
        mLauncher = new NexusLauncher(this);
    }
}
