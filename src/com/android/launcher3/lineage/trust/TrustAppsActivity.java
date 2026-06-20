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

import static com.android.launcher3.lineage.trust.db.TrustComponent.Kind.HIDDEN;
import static com.android.launcher3.lineage.trust.db.TrustComponent.Kind.PROTECTED;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.AppFilter;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.lineage.LineageUtils;
import com.android.launcher3.lineage.trust.db.TrustComponent;
import com.android.launcher3.lineage.trust.db.TrustDatabaseHelper;

import java.util.List;

public class TrustAppsActivity extends Activity implements
        TrustAppsAdapter.Listener,
        LoadTrustComponentsTask.Callback,
        UpdateItemTask.UpdateCallback {

    private static final String KEY_TRUST_ONBOARDING = "pref_trust_onboarding";
    private static final String KEY_PROTECTED_INACTIVE_WARNING = "pref_protected_inactive_warning";
    private static final String STATE_AUTHENTICATED = "state_trust_authenticated";

    private RecyclerView mRecyclerView;
    private LinearLayout mLoadingView;
    private ProgressBar mProgressBar;

    private TrustDatabaseHelper mDbHelper;
    private TrustAppsAdapter mAdapter;
    private AppFilter mAppFilter;

    @Nullable
    private LoadTrustComponentsTask mLoadTask;
    @Nullable
    private UpdateItemTask mUpdateTask;

    private boolean mAuthenticated;
    private boolean mPendingAuth;

    @Override
    protected void onCreate(@Nullable Bundle savedInstance) {
        super.onCreate(savedInstance);
        TrustActivityIntents.applySecureWindow(this);
        mAuthenticated = savedInstance != null
                && savedInstance.getBoolean(STATE_AUTHENTICATED, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mAuthenticated && !mPendingAuth) {
            requestAuthentication();
        }
    }

    @Override
    protected void onDestroy() {
        if (mLoadTask != null) {
            mLoadTask.cancel(true);
            mLoadTask = null;
        }
        if (mUpdateTask != null) {
            mUpdateTask.cancel(true);
            mUpdateTask = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) {
            mAuthenticated = false;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_AUTHENTICATED, mAuthenticated);
    }

    private void requestAuthentication() {
        mPendingAuth = true;
        LineageUtils.showLockScreen(this, this, getString(R.string.trust_apps_auth_manager),
                () -> {
                    mPendingAuth = false;
                    mAuthenticated = true;
                    if (mRecyclerView == null) {
                        initializeContent();
                    }
                },
                () -> {
                    mPendingAuth = false;
                    finish();
                });
    }

    private void initializeContent() {
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        setupEdgeToEdge();
        setContentView(R.layout.activity_hidden_apps);
        mRecyclerView = findViewById(R.id.hidden_apps_list);
        mLoadingView = findViewById(R.id.hidden_apps_loading);
        mLoadingView.setVisibility(View.VISIBLE);
        mProgressBar = findViewById(R.id.hidden_apps_progress_bar);

        final boolean hasSecureKeyguard = LineageUtils.hasSecureKeyguard(this);
        mAdapter = new TrustAppsAdapter(this, hasSecureKeyguard);
        mDbHelper = TrustDatabaseHelper.getInstance(this);
        mAppFilter = new AppFilter(this);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.setAdapter(mAdapter);

        showOnBoarding(false);

        mLoadTask = new LoadTrustComponentsTask(this, mDbHelper, getPackageManager(), mAppFilter,
                this);
        mLoadTask.execute();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_trust_apps, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.menu_trust_help) {
            showOnBoarding(true);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onHiddenItemChanged(@NonNull TrustComponent component) {
        startUpdateTask(HIDDEN, component);
    }

    @Override
    public void onProtectedItemChanged(@NonNull TrustComponent component) {
        startUpdateTask(PROTECTED, component);
    }

    private void startUpdateTask(@NonNull TrustComponent.Kind kind,
            @NonNull TrustComponent component) {
        if (mUpdateTask != null) {
            mUpdateTask.cancel(true);
        }
        mUpdateTask = new UpdateItemTask(mDbHelper, this, kind);
        mUpdateTask.execute(component);
    }

    @Override
    public void onAppClicked(@NonNull TrustComponent component) {
        TrustLaunchHelper.runWithProtectedAuth(this, this,
                getString(R.string.trust_apps_manager_name),
                component.getPackageName(), () -> launchApp(component.getPackageName()));
    }

    private void launchApp(@NonNull String packageName) {
        LauncherApps launcherApps = getSystemService(LauncherApps.class);
        if (launcherApps == null) {
            return;
        }
        try {
            UserHandle currentUser = Process.myUserHandle();
            List<LauncherActivityInfo> activities =
                    launcherApps.getActivityList(packageName, currentUser);
            if (!activities.isEmpty()) {
                launcherApps.startMainActivity(
                        activities.get(0).getComponentName(), currentUser, null, null);
                return;
            }
            for (UserHandle user : launcherApps.getProfiles()) {
                if (user.equals(currentUser)) {
                    continue;
                }
                activities = launcherApps.getActivityList(packageName, user);
                if (!activities.isEmpty()) {
                    launcherApps.startMainActivity(
                            activities.get(0).getComponentName(), user, null, null);
                    return;
                }
            }
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onUpdated(boolean result) {
        if (!result) {
            Toast.makeText(this, R.string.trust_apps_update_failed, Toast.LENGTH_SHORT).show();
            reloadTrustList();
        }
        LauncherAppState.INSTANCE.get(this).getModel().reloadIfActive();
    }

    private void reloadTrustList() {
        if (mLoadTask != null) {
            mLoadTask.cancel(true);
        }
        if (mAppFilter == null || mDbHelper == null) {
            return;
        }
        mLoadingView.setVisibility(View.VISIBLE);
        mRecyclerView.setVisibility(View.GONE);
        mLoadTask = new LoadTrustComponentsTask(this, mDbHelper, getPackageManager(), mAppFilter,
                this);
        mLoadTask.execute();
    }

    @Override
    public void onLoadListProgress(int progress) {
        if (isFinishing() || mProgressBar == null) {
            return;
        }
        mProgressBar.setProgress(progress);
    }

    @Override
    public void onLoadCompleted(List<TrustComponent> result) {
        if (isFinishing() || mLoadingView == null || mRecyclerView == null || mAdapter == null) {
            return;
        }
        mLoadingView.setVisibility(View.GONE);
        mRecyclerView.setVisibility(View.VISIBLE);
        mAdapter.update(result);
        maybeShowProtectedInactiveWarning();
    }

    private void setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content),
                (v, windowInsets) -> {
                    Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                    // Apply the insets paddings to the view.
                    v.setPadding(insets.left, insets.top, insets.right, insets.bottom);

                    // Return CONSUMED if you don't want the window insets to keep being
                    // passed down to descendant views.
                    return WindowInsetsCompat.CONSUMED;
                });
    }

    private void showOnBoarding(boolean forceShow) {
        SharedPreferences preferenceManager = LauncherPrefs.getPrefs(this);
        if (!forceShow && preferenceManager.getBoolean(KEY_TRUST_ONBOARDING, false)) {
            return;
        }

        preferenceManager.edit()
                .putBoolean(KEY_TRUST_ONBOARDING, true)
                .apply();

        new AlertDialog.Builder(this)
                .setView(R.layout.dialog_trust_welcome)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /** Informs the user that protected apps are inactive without a device lock screen. */
    private void maybeShowProtectedInactiveWarning() {
        if (LineageUtils.hasSecureKeyguard(this) || !mDbHelper.hasAnyProtectedApp()) {
            return;
        }
        SharedPreferences prefs = LauncherPrefs.getPrefs(this);
        if (prefs.getBoolean(KEY_PROTECTED_INACTIVE_WARNING, false)) {
            return;
        }
        prefs.edit().putBoolean(KEY_PROTECTED_INACTIVE_WARNING, true).apply();
        new AlertDialog.Builder(this)
                .setMessage(R.string.launcher_lock_remove_warning)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
