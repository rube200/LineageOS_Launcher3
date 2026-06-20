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

import android.content.ComponentName;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;

import androidx.annotation.NonNull;

/** One row in the hidden-apps drawer (launcher surface for hidden packages). */
public final class HiddenDrawerItem {

    @NonNull
    private final ComponentName mComponent;
    @NonNull
    private final UserHandle mUser;
    @NonNull
    private final String mLabel;
    @NonNull
    private final Drawable mIcon;

    public HiddenDrawerItem(
            @NonNull ComponentName component,
            @NonNull UserHandle user,
            @NonNull String label,
            @NonNull Drawable icon) {
        mComponent = component;
        mUser = user;
        mLabel = label;
        mIcon = icon;
    }

    @NonNull
    public ComponentName getComponent() {
        return mComponent;
    }

    @NonNull
    public UserHandle getUser() {
        return mUser;
    }

    @NonNull
    public String getLabel() {
        return mLabel;
    }

    @NonNull
    public Drawable getIcon() {
        return mIcon;
    }
}
