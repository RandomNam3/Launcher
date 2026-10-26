package com.google.android.apps.nexuslauncher.qsb;

import com.android.launcher3.Utilities;
import android.content.Intent;
import android.content.Context;
import android.content.BroadcastReceiver;

public class OPAStatusReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Receivers get data from other apps (or the system), never crash because of it
        try {
            handleReceive(context, intent);
        } catch (RuntimeException e) {
            android.util.Log.e("OPAStatusReceiver", "Unable to handle " + intent, e);
        }
    }

    private void handleReceive(Context context, Intent intent) {
        Utilities.getDevicePrefs(context).edit().putBoolean("opa_enabled", intent.getBooleanExtra("OPA_ENABLED", false)).apply();
    }
}
