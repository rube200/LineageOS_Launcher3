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

import android.app.ActionBar;
import android.app.Activity;
import android.content.pm.LauncherApps;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.AppFilter;
import com.android.launcher3.R;
import com.android.launcher3.lineage.trust.db.TrustDatabaseHelper;

import java.util.List;

/**
 * Lists hidden launcher apps for launch outside the app drawer.
 * The list itself does not require authentication; protected apps still prompt on launch.
 */
public class HiddenAppsDrawerActivity extends Activity implements LoadHiddenAppsTask.Callback {

    private RecyclerView mRecyclerView;
    private LinearLayout mLoadingView;
    private ProgressBar mProgressBar;
    private TextView mEmptyView;
    private HiddenDrawerAppsAdapter mAdapter;
    @Nullable
    private LoadHiddenAppsTask mLoadTask;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TrustActivityIntents.applySecureWindow(this);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.hidden_apps_drawer_title);
        }

        setupEdgeToEdge();
        setContentView(R.layout.activity_hidden_apps_drawer);
        mRecyclerView = findViewById(R.id.hidden_drawer_apps_list);
        mLoadingView = findViewById(R.id.hidden_drawer_loading);
        mProgressBar = findViewById(R.id.hidden_drawer_progress_bar);
        mEmptyView = findViewById(R.id.hidden_drawer_empty);

        mAdapter = new HiddenDrawerAppsAdapter(this::launchHiddenApp);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.setAdapter(mAdapter);

        TrustDatabaseHelper dbHelper = TrustDatabaseHelper.getInstance(this);
        AppFilter appFilter = new AppFilter(this);
        mLoadTask = new LoadHiddenAppsTask(this, dbHelper, getPackageManager(), appFilter, this);
        mLoadTask.execute();
    }

    @Override
    protected void onDestroy() {
        if (mLoadTask != null) {
            mLoadTask.cancel(true);
            mLoadTask = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onLoadListProgress(int progress) {
        if (isFinishing()) {
            return;
        }
        mProgressBar.setProgress(progress);
    }

    @Override
    public void onLoadCompleted(List<HiddenDrawerItem> result) {
        if (isFinishing()) {
            return;
        }
        mLoadingView.setVisibility(View.GONE);
        mAdapter.setItems(result);
        if (result.isEmpty()) {
            mEmptyView.setVisibility(View.VISIBLE);
            mRecyclerView.setVisibility(View.GONE);
        } else {
            mEmptyView.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void launchHiddenApp(HiddenDrawerItem item) {
        TrustLaunchHelper.runWithProtectedAuth(this, this,
                getString(R.string.trust_apps_manager_name),
                item.getComponent().getPackageName(), () -> {
            LauncherApps launcherApps = getSystemService(LauncherApps.class);
            if (launcherApps == null) {
                return;
            }
            try {
                launcherApps.startMainActivity(
                        item.getComponent(), item.getUser(), null, null);
            } catch (SecurityException e) {
                Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content),
                (v, windowInsets) -> {
                    Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                    return WindowInsetsCompat.CONSUMED;
                });
    }
}
