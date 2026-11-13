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
import android.content.Context;
import android.content.Intent;
import android.view.WindowManager;

import androidx.annotation.NonNull;

public final class TrustActivityIntents {

    private static final int PRIVATE_TRUST_FLAGS =
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_NO_HISTORY;

    private TrustActivityIntents() {}

    @NonNull
    public static Intent hiddenAppsDrawer(@NonNull Context context) {
        return new Intent(context, HiddenAppsDrawerActivity.class).addFlags(PRIVATE_TRUST_FLAGS);
    }

    @NonNull
    public static Intent trustAppsManager(@NonNull Context context) {
        return new Intent(context, TrustAppsActivity.class).addFlags(PRIVATE_TRUST_FLAGS);
    }

    /** Prevents hidden-app UI from appearing in recents thumbnails or screenshots. */
    public static void applySecureWindow(@NonNull Activity activity) {
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
}
