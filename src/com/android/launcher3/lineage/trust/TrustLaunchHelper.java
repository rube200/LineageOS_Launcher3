/*
 * Copyright (C) 2026 The LineageOS Project
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
package com.android.launcher3.lineage.trust;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.lineage.LineageUtils;
import com.android.launcher3.lineage.trust.db.TrustDatabaseHelper;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.RunnableList;
import com.android.systemui.shared.recents.model.Task;

import java.util.List;

/** Shared helpers for Trebuchet trust / protected-app launch checks and auth gating. */
public final class TrustLaunchHelper {

    private TrustLaunchHelper() {}

    @NonNull
    private static String resolveAuthTitle(
            @NonNull Context context,
            @Nullable ItemInfo item,
            @Nullable Intent intent,
            @NonNull String fallbackTitle) {
        return resolveAuthTitleForPackage(context, resolveLaunchPackage(item, intent), fallbackTitle);
    }

    @NonNull
    private static String resolveAuthTitleForItems(
            @NonNull Context context,
            @NonNull List<? extends ItemInfo> items,
            @NonNull String fallbackTitle) {
        for (ItemInfo item : items) {
            if (isProtectedLaunch(context, item)) {
                return resolveAuthTitle(context, item, item != null ? item.getIntent() : null,
                        fallbackTitle);
            }
        }
        return fallbackTitle;
    }

    @NonNull
    private static String resolveAuthTitleForTaskKey(
            @NonNull Context context,
            @Nullable Task.TaskKey taskKey,
            @NonNull String fallbackTitle) {
        if (taskKey == null) {
            return fallbackTitle;
        }
        return resolveAuthTitleForPackage(context, taskKey.getPackageName(), fallbackTitle);
    }

    @NonNull
    private static String resolveAuthTitleForTaskKeys(
            @NonNull Context context,
            @Nullable Task.TaskKey[] taskKeys,
            @NonNull String fallbackTitle) {
        if (taskKeys == null) {
            return fallbackTitle;
        }
        for (Task.TaskKey key : taskKeys) {
            if (isProtectedLaunch(context, key)) {
                return resolveAuthTitleForTaskKey(context, key, fallbackTitle);
            }
        }
        return fallbackTitle;
    }

    @NonNull
    private static String resolveAuthTitleForPackage(
            @NonNull Context context,
            @Nullable String pkg,
            @NonNull String fallbackTitle) {
        if (pkg == null) {
            return fallbackTitle;
        }
        try {
            PackageManager pm = context.getPackageManager();
            String label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
            return context.getString(R.string.trust_apps_auth_open_app, label);
        } catch (Exception ignored) {
            return fallbackTitle;
        }
    }

    /** @return whether {@code item} targets a package marked protected in the trust DB. */
    private static boolean isProtectedLaunch(@NonNull Context context, @Nullable ItemInfo item) {
        return isProtectedLaunch(context, item, item != null ? item.getIntent() : null);
    }

    /**
     * @return whether this launch should require device auth before starting.
     * Blocking is inactive when the device has no secure keyguard.
     */
    public static boolean isProtectedLaunch(
            @NonNull Context context, @Nullable ItemInfo item, @Nullable Intent intent) {
        if (!LineageUtils.hasSecureKeyguard(context)) {
            return false;
        }
        String pkg = resolveLaunchPackage(item, intent);
        return pkg != null && TrustDatabaseHelper.getInstance(context).isPackageProtected(pkg);
    }

    /** True when any item in the list is protected (one auth covers the whole launch). */
    private static boolean isAnyProtectedLaunch(
            @NonNull Context context, @NonNull List<? extends ItemInfo> items) {
        for (ItemInfo item : items) {
            if (isProtectedLaunch(context, item)) {
                return true;
            }
        }
        return false;
    }

    /** Runs {@code launch} after device auth when the resolved target is protected. */
    public static void runWithProtectedAuth(
            @NonNull Context context,
            @Nullable Activity activityHost,
            @NonNull String authTitle,
            @Nullable ItemInfo item,
            @Nullable Intent intent,
            @NonNull Runnable launch) {
        if (isProtectedLaunch(context, item, intent)) {
            authThenRun(context, activityHost, resolveAuthTitle(context, item, intent, authTitle),
                    launch);
        } else {
            launch.run();
        }
    }

    /** Runs {@code launch} after device auth when {@code packageName} is protected. */
    public static void runWithProtectedAuth(
            @NonNull Context context,
            @Nullable Activity activityHost,
            @NonNull String authTitle,
            @NonNull String packageName,
            @NonNull Runnable launch) {
        if (!LineageUtils.hasSecureKeyguard(context)
                || !TrustDatabaseHelper.getInstance(context).isPackageProtected(packageName)) {
            launch.run();
            return;
        }
        authThenRun(context, activityHost,
                resolveAuthTitle(context, null, new Intent().setPackage(packageName), authTitle),
                launch);
    }

    /** Runs {@code launch} once if any item in the list is protected. */
    public static void runWithProtectedAuthForAny(
            @NonNull Context context,
            @Nullable Activity activityHost,
            @NonNull String authTitle,
            @NonNull List<? extends ItemInfo> items,
            @NonNull Runnable launch) {
        if (isAnyProtectedLaunch(context, items)) {
            authThenRun(context, activityHost,
                    resolveAuthTitleForItems(context, items, authTitle), launch);
        } else {
            launch.run();
        }
    }

    /** @return whether a Recents {@link Task.TaskKey} targets a protected package. */
    public static boolean isProtectedLaunch(
            @NonNull Context context, @Nullable Task.TaskKey taskKey) {
        if (!LineageUtils.hasSecureKeyguard(context) || taskKey == null) {
            return false;
        }
        String pkg = taskKey.getPackageName();
        return pkg != null && TrustDatabaseHelper.getInstance(context).isPackageProtected(pkg);
    }

    /** Runs {@code launch} after device auth when the Recents task is protected. */
    public static void runWithProtectedAuthForTask(
            @NonNull Context context,
            @Nullable Activity activityHost,
            @NonNull String authTitle,
            @Nullable Task.TaskKey taskKey,
            @NonNull Runnable launch) {
        if (isProtectedLaunch(context, taskKey)) {
            authThenRun(context, activityHost,
                    resolveAuthTitleForTaskKey(context, taskKey, authTitle), launch);
        } else {
            launch.run();
        }
    }

    /** @return true if any of the given Recents tasks is protected. */
    public static boolean isAnyProtectedLaunchForTaskKeys(
            @NonNull Context context, @Nullable Task.TaskKey... taskKeys) {
        if (taskKeys == null) {
            return false;
        }
        for (Task.TaskKey key : taskKeys) {
            if (isProtectedLaunch(context, key)) {
                return true;
            }
        }
        return false;
    }

    /** Runs {@code launch} once if any of the given Recents tasks is protected. */
    public static void runWithProtectedAuthForAnyTaskKey(
            @NonNull Context context,
            @Nullable Activity activityHost,
            @NonNull String authTitle,
            @NonNull Runnable launch,
            @Nullable Task.TaskKey... taskKeys) {
        if (isAnyProtectedLaunchForTaskKeys(context, taskKeys)) {
            authThenRun(context, activityHost,
                    resolveAuthTitleForTaskKeys(context, taskKeys, authTitle), launch);
        } else {
            launch.run();
        }
    }

    /** Resolves a user-facing auth title for a protected launch intent. */
    @NonNull
    public static String getProtectedLaunchAuthTitle(
            @NonNull Context context,
            @Nullable Intent intent,
            @NonNull String fallbackTitle) {
        return resolveAuthTitle(context, null, intent, fallbackTitle);
    }

    /**
     * Protected-app gate for {@link com.android.launcher3.views.ActivityContext#startActivitySafely}.
     *
     * @return a RunnableList for the launch; when auth is required it completes after auth + launch
     */
    @Nullable
    public static RunnableList startActivitySafelyWithProtectedGate(
            @NonNull Context context,
            @Nullable Activity activityHost,
            @NonNull String authTitle,
            @Nullable View v,
            @NonNull Intent intent,
            @Nullable ItemInfo item,
            @NonNull StartActivitySafelyCallback callback) {
        if (!isProtectedLaunch(context, item, intent)) {
            return callback.start(v, intent, item);
        }
        RunnableList pending = new RunnableList();
        authThenRun(
                context,
                activityHost,
                resolveAuthTitle(context, item, intent, authTitle),
                () -> {
                    RunnableList result = callback.start(v, intent, item);
                    if (result != null) {
                        result.add(pending::executeAllAndDestroy);
                    } else {
                        pending.executeAllAndDestroy();
                    }
                });
        return pending;
    }

    /** Shows device auth for a protected launch; does not run the callback without a secure lock. */
    public static void authThenRun(
            @NonNull Context context,
            @Nullable Activity activityHost,
            @NonNull String authTitle,
            @NonNull Runnable onAuthenticated) {
        LineageUtils.showLockScreenForProtectedLaunch(
                context, activityHost, authTitle, onAuthenticated);
    }

    @Nullable
    private static String resolveLaunchPackage(
            @Nullable ItemInfo item, @Nullable Intent intent) {
        if (item != null) {
            String targetPackage = item.getTargetPackage();
            if (targetPackage != null) {
                return targetPackage;
            }
        }
        if (intent != null) {
            ComponentName component = intent.getComponent();
            if (component != null) {
                return component.getPackageName();
            }
            return intent.getPackage();
        }
        return null;
    }

    /** Called by {@link #startActivitySafelyWithProtectedGate} to perform the actual launch. */
    public interface StartActivitySafelyCallback {
        RunnableList start(View v, Intent intent, ItemInfo item);
    }
}
