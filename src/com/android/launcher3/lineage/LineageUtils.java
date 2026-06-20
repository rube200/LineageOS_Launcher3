package com.android.launcher3.lineage;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.biometrics.BiometricManager.Authenticators;
import android.hardware.biometrics.BiometricPrompt;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.R;

public class LineageUtils {

    /**
     * Shows authentication screen to confirm credentials (pin, pattern or password) for the current
     * user of the device.
     *
     * @param cancelRunnable run when the user dismisses the prompt without authenticating
     */
    public static void showLockScreen(
            @NonNull Context context,
            @Nullable Activity activityHost,
            @NonNull String title,
            @NonNull Runnable successRunnable,
            @Nullable Runnable cancelRunnable) {
        showLockScreenInternal(context, activityHost, title, successRunnable, cancelRunnable,
                true /* allowWhenNoKeyguard */);
    }

    /** Device auth for protected app launches; does not run {@code onAuthenticated} without a lock. */
    public static void showLockScreenForProtectedLaunch(
            @NonNull Context context,
            @Nullable Activity activityHost,
            @NonNull String title,
            @NonNull Runnable onAuthenticated) {
        showLockScreenInternal(context, activityHost, title, onAuthenticated,
                null /* cancelRunnable */, false /* allowWhenNoKeyguard */);
    }

    private static void showLockScreenInternal(
            @NonNull Context context,
            @Nullable Activity activityHost,
            @NonNull String title,
            @NonNull Runnable successRunnable,
            @Nullable Runnable cancelRunnable,
            boolean allowWhenNoKeyguard) {
        if (hasSecureKeyguard(context)) {
            final Handler mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(() -> {
                final Context promptContext = activityHost != null ? activityHost : context;
                final BiometricPrompt.AuthenticationCallback authenticationCallback =
                        new BiometricPrompt.AuthenticationCallback() {
                            @Override
                            public void onAuthenticationSucceeded(
                                        BiometricPrompt.AuthenticationResult result) {
                                successRunnable.run();
                            }

                            @Override
                            public void onAuthenticationError(int errorCode,
                                    CharSequence errString) {
                                if (cancelRunnable != null) {
                                    cancelRunnable.run();
                                }
                            }
                };

                final BiometricPrompt bp = new BiometricPrompt.Builder(promptContext)
                        .setTitle(title)
                        .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG |
                                                  Authenticators.DEVICE_CREDENTIAL)
                        .build();

                bp.authenticate(new CancellationSignal(),
                        runnable -> mainHandler.post(runnable),
                        authenticationCallback);
            });
        } else if (allowWhenNoKeyguard) {
            Toast.makeText(context, R.string.trust_apps_no_lock_error, Toast.LENGTH_LONG)
                .show();
            successRunnable.run();
        } else {
            Toast.makeText(context, R.string.trust_apps_no_lock_error, Toast.LENGTH_LONG)
                .show();
            if (cancelRunnable != null) {
                cancelRunnable.run();
            }
        }
    }

    public static boolean hasSecureKeyguard(Context context) {
        final KeyguardManager keyguardManager = context.getSystemService(KeyguardManager.class);
        return keyguardManager != null && keyguardManager.isKeyguardSecure();
    }

}
