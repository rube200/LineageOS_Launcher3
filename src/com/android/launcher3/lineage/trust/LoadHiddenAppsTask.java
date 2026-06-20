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
import com.android.launcher3.lineage.trust.db.TrustDatabaseHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Loads launcher activities whose package is marked hidden in the trust DB. */
class LoadHiddenAppsTask extends AsyncTask<Void, Integer, List<HiddenDrawerItem>> {

    @NonNull
    private final Context mContext;
    @NonNull
    private final TrustDatabaseHelper mDbHelper;
    @NonNull
    private final PackageManager mPackageManager;
    @NonNull
    private final AppFilter mAppFilter;
    @NonNull
    private final Callback mCallback;

    LoadHiddenAppsTask(
            @NonNull Context context,
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
    protected List<HiddenDrawerItem> doInBackground(Void... voids) {
        List<HiddenDrawerItem> list = new ArrayList<>();
        Set<String> seenEntries = new HashSet<>();

        LauncherApps launcherApps = mContext.getSystemService(LauncherApps.class);
        if (launcherApps != null) {
            List<ActivityEntry> activities = new ArrayList<>();
            for (UserHandle user : launcherApps.getProfiles()) {
                List<LauncherActivityInfo> userActivities = launcherApps.getActivityList(null, user);
                for (LauncherActivityInfo info : userActivities) {
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
                if (!mAppFilter.shouldShowApp(info.getComponentName())
                        || !mDbHelper.isPackageHidden(pkgName)) {
                    continue;
                }
                String entryKey = entry.user.hashCode() + ":" + pkgName;
                if (!seenEntries.add(entryKey)) {
                    continue;
                }
                Drawable icon = info.getIcon(0);
                if (icon == null) {
                    continue;
                }
                list.add(new HiddenDrawerItem(
                        info.getComponentName(),
                        entry.user,
                        info.getLabel().toString(),
                        mPackageManager.getUserBadgedIcon(icon, entry.user)));
                if (total > 0) {
                    publishProgress(Math.round(i * 100f / total));
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Collections.sort(list, (a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));
            }
            return list;
        }

        Intent filter = new Intent(Intent.ACTION_MAIN, null);
        filter.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> apps = mPackageManager.queryIntentActivities(filter,
                PackageManager.GET_META_DATA);

        int numPackages = apps.size();
        for (int i = 0; i < numPackages; i++) {
            ResolveInfo app = apps.get(i);
            if (!mAppFilter.shouldShowApp(app.activityInfo.getComponentName())) {
                continue;
            }

            try {
                String pkgName = app.activityInfo.packageName;
                if (!mDbHelper.isPackageHidden(pkgName)) {
                    continue;
                }
                UserHandle user = app.userHandle != null ? app.userHandle : Process.myUserHandle();
                String entryKey = user.hashCode() + ":" + pkgName;
                if (!seenEntries.add(entryKey)) {
                    continue;
                }
                String label = mPackageManager.getApplicationLabel(
                        mPackageManager.getApplicationInfo(pkgName,
                                PackageManager.GET_META_DATA)).toString();
                list.add(new HiddenDrawerItem(
                        app.activityInfo.getComponentName(),
                        user,
                        label,
                        app.loadIcon(mPackageManager)));
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

    private static final class ActivityEntry {
        final LauncherActivityInfo info;
        final UserHandle user;

        ActivityEntry(LauncherActivityInfo info, UserHandle user) {
            this.info = info;
            this.user = user;
        }
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        if (values.length > 0) {
            mCallback.onLoadListProgress(values[0]);
        }
    }

    @Override
    protected void onPostExecute(List<HiddenDrawerItem> items) {
        mCallback.onLoadCompleted(items);
    }

    interface Callback {
        void onLoadListProgress(int progress);

        void onLoadCompleted(List<HiddenDrawerItem> result);
    }
}
