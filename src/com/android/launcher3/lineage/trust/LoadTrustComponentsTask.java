/*
 * Copyright (C) 2019-2024 The LineageOS Project
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

import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;

import androidx.annotation.NonNull;

import com.android.launcher3.AppFilter;
import com.android.launcher3.lineage.trust.db.TrustComponent;
import com.android.launcher3.lineage.trust.db.TrustDatabaseHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LoadTrustComponentsTask extends AsyncTask<Void, Integer, List<TrustComponent>> {
    @NonNull
    private final Context mContext;
    @NonNull
    private TrustDatabaseHelper mDbHelper;

    @NonNull
    private PackageManager mPackageManager;

    @NonNull
    private AppFilter mAppFilter;

    @NonNull
    private Callback mCallback;

    LoadTrustComponentsTask(@NonNull Context context,
            @NonNull TrustDatabaseHelper dbHelper,
            @NonNull PackageManager packageManager,
            @NonNull AppFilter appFilter,
            @NonNull Callback callback) {
        mContext = context.getApplicationContext();
        mDbHelper = dbHelper;
        mPackageManager = packageManager;
        mAppFilter = appFilter;
        mCallback = callback;
    }

    @Override
    protected List<TrustComponent> doInBackground(Void... voids) {
        LauncherApps launcherApps = mContext.getSystemService(LauncherApps.class);
        if (launcherApps != null) {
            return loadFromLauncherApps(launcherApps);
        }
        return loadFromPackageManager();
    }

    @NonNull
    private List<TrustComponent> loadFromLauncherApps(@NonNull LauncherApps launcherApps) {
        Map<String, TrustComponent> byPackage = new HashMap<>();
        UserHandle currentUser = Process.myUserHandle();
        List<ActivityEntry> activities = new ArrayList<>();

        for (UserHandle user : launcherApps.getProfiles()) {
            for (LauncherActivityInfo info : launcherApps.getActivityList(null, user)) {
                activities.add(new ActivityEntry(info, user));
            }
        }

        int total = activities.size();
        for (int i = 0; i < total; i++) {
            if (isCancelled()) {
                break;
            }
            ActivityEntry entry = activities.get(i);
            LauncherActivityInfo info = entry.info;
            String pkgName = info.getComponentName().getPackageName();
            if (!mAppFilter.shouldShowApp(info.getComponentName())) {
                continue;
            }

            TrustComponent existing = byPackage.get(pkgName);
            if (existing != null && !entry.user.equals(currentUser)) {
                continue;
            }

            Drawable icon = info.getIcon(0);
            if (icon == null) {
                continue;
            }
            boolean isHidden = mDbHelper.isPackageHidden(pkgName);
            boolean isProtected = mDbHelper.isPackageProtected(pkgName);
            byPackage.put(pkgName, new TrustComponent(
                    pkgName,
                    mPackageManager.getUserBadgedIcon(icon, entry.user),
                    info.getLabel().toString(),
                    isHidden,
                    isProtected));

            if (total > 0) {
                publishProgress(Math.round(i * 100f / total));
            }
        }

        return sortComponents(byPackage);
    }

    @NonNull
    private List<TrustComponent> loadFromPackageManager() {
        List<TrustComponent> list = new ArrayList<>();
        Set<String> seenPackages = new HashSet<>();

        Intent filter = new Intent(Intent.ACTION_MAIN, null);
        filter.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> apps = mPackageManager.queryIntentActivities(filter,
                PackageManager.GET_META_DATA);

        int numPackages = apps.size();
        for (int i = 0; i < numPackages; i++) {
            if (isCancelled()) {
                break;
            }
            ResolveInfo app = apps.get(i);

            if (!mAppFilter.shouldShowApp(app.activityInfo.getComponentName())) {
                continue;
            }

            try {
                String pkgName = app.activityInfo.packageName;
                if (!seenPackages.add(pkgName)) {
                    continue;
                }
                String label = mPackageManager.getApplicationLabel(
                        mPackageManager.getApplicationInfo(pkgName,
                                PackageManager.GET_META_DATA)).toString();
                Drawable icon = app.loadIcon(mPackageManager);
                boolean isHidden = mDbHelper.isPackageHidden(pkgName);
                boolean isProtected = mDbHelper.isPackageProtected(pkgName);

                list.add(new TrustComponent(pkgName, icon, label, isHidden, isProtected));

                if (numPackages > 0) {
                    publishProgress(Math.round(i * 100f / numPackages));
                }
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Collections.sort(list, (a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));
        }
        return list;
    }

    @NonNull
    private List<TrustComponent> sortComponents(@NonNull Map<String, TrustComponent> byPackage) {
        List<TrustComponent> list = new ArrayList<>(byPackage.values());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Collections.sort(list, (a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));
        }
        return list;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        if (values.length > 0 && !isCancelled()) {
            mCallback.onLoadListProgress(values[0]);
        }
    }

    @Override
    protected void onPostExecute(List<TrustComponent> trustComponents) {
        if (!isCancelled()) {
            mCallback.onLoadCompleted(trustComponents);
        }
    }

    private static final class ActivityEntry {
        final LauncherActivityInfo info;
        final UserHandle user;

        ActivityEntry(LauncherActivityInfo info, UserHandle user) {
            this.info = info;
            this.user = user;
        }
    }

    interface Callback {
        void onLoadListProgress(int progress);

        void onLoadCompleted(List<TrustComponent> result);
    }
}
