package ch.deletescape.lawnchair.platform;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;

/**
 * Apps targeting Android 14 must declare whether a context registered receiver may receive
 * broadcasts from other apps, otherwise registering throws a SecurityException.
 */
public final class ReceiverCompat {

    private ReceiverCompat() { }

    /**
     * Registers a receiver that only receives system broadcasts and broadcasts sent by this app.
     */
    public static Intent registerNotExported(Context context, BroadcastReceiver receiver,
            IntentFilter filter) {
        return register(context, receiver, filter, null, null, false);
    }

    public static Intent registerNotExported(Context context, BroadcastReceiver receiver,
            IntentFilter filter, String permission, Handler handler) {
        return register(context, receiver, filter, permission, handler, false);
    }

    /**
     * Registers a receiver that also receives broadcasts from other apps.
     */
    public static Intent registerExported(Context context, BroadcastReceiver receiver,
            IntentFilter filter, String permission, Handler handler) {
        return register(context, receiver, filter, permission, handler, true);
    }

    public static Intent register(Context context, BroadcastReceiver receiver,
            IntentFilter filter, String permission, Handler handler, boolean exported) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            int flags = exported ? Context.RECEIVER_EXPORTED : Context.RECEIVER_NOT_EXPORTED;
            return context.registerReceiver(receiver, filter, permission, handler, flags);
        }
        return context.registerReceiver(receiver, filter, permission, handler);
    }
}
