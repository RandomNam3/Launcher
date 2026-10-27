package ch.deletescape.lawnchair.platform;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;

/**
 * Asks the user to confirm their identity with a fingerprint, the face or the screen lock.
 */
public final class BiometricCompat {

    public interface Callback {
        void onResult(boolean success);
    }

    private BiometricCompat() { }

    /**
     * Whether the device has a screen lock, without one there is nothing to confirm.
     */
    public static boolean isDeviceSecure(Context context) {
        if (Build.VERSION.SDK_INT < 23) return false;
        KeyguardManager keyguardManager = context.getSystemService(KeyguardManager.class);
        return keyguardManager != null && keyguardManager.isDeviceSecure();
    }

    /**
     * Whether {@link #authenticate} can show the system dialog, otherwise the screen lock has to
     * be confirmed with {@link KeyguardManager#createConfirmDeviceCredentialIntent}.
     */
    public static boolean supportsPrompt() {
        return Build.VERSION.SDK_INT >= 29;
    }

    /**
     * Shows the system dialog to confirm the identity. Requires {@link #supportsPrompt()}.
     */
    public static void authenticate(Activity activity, CharSequence title, CharSequence subtitle,
            Callback callback) {
        if (Build.VERSION.SDK_INT < 29) {
            callback.onResult(false);
            return;
        }
        BiometricPrompt.Builder builder = new BiometricPrompt.Builder(activity)
                .setTitle(title);
        if (subtitle != null) {
            builder.setSubtitle(subtitle);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK
                    | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        } else {
            builder.setDeviceCredentialAllowed(true);
        }
        BiometricPrompt prompt = builder.build();
        try {
            prompt.authenticate(new CancellationSignal(), activity.getMainExecutor(),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(
                                BiometricPrompt.AuthenticationResult result) {
                            callback.onResult(true);
                        }

                        @Override
                        public void onAuthenticationError(int errorCode,
                                CharSequence errString) {
                            callback.onResult(false);
                        }
                    });
        } catch (RuntimeException e) {
            // E.g. no screen lock set up after all
            callback.onResult(false);
        }
    }
}
