package ch.deletescape.lawnchair;

import android.content.Context;

import com.google.android.apps.nexuslauncher.PredictionUiStateManager;

/**
 * Java bridge to {@link PredictionUiStateManager}, which is only available as a prebuilt
 * library. The Kotlin compiler cannot resolve its supertypes because they are declared in this
 * module's own Java sources.
 */
public final class PredictionClientSwitcher {

    private PredictionClientSwitcher() { }

    public static void switchToOverview(Context context) {
        PredictionUiStateManager.getInstance(context)
                .switchClient(PredictionUiStateManager.Client.OVERVIEW);
    }

    public static void switchToHome(Context context) {
        PredictionUiStateManager.getInstance(context)
                .switchClient(PredictionUiStateManager.Client.HOME);
    }
}
