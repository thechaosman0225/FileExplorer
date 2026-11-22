/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * This file is part of FileExplorer.
 *
 * FileExplorer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.micode.fileexplorer;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ActionMode;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;


public class FileExplorerTabActivity extends AppCompatActivity {
    private static final String INSTANCESTATE_TAB = "tab";
    private static final String CATEGORY_TAG = "file-explorer-category";
    private static final String LOCAL_TAG = "file-explorer-local";
    private static final int TAB_COUNT = 2;
    private static final int REQUEST_STORAGE_PERMISSIONS = 1;
    private static final String STORAGE_ACCESS_EXPLANATION_SHOWN = "storage_access_explanation_shown";
    private static final String LEGACY_STORAGE_PERMISSION_REQUESTED = "legacy_storage_permission_requested";

    private final Fragment[] mFragments = new Fragment[TAB_COUNT];
    private TabLayout mTabs;
    private int mCurrentTab = Util.CATEGORY_TAB_INDEX;
    private ActionMode mActionMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_pager);
        SystemBarInsets.apply(this, findViewById(android.R.id.content));

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mTabs = findViewById(R.id.tabs);
        mTabs.addTab(mTabs.newTab().setText(R.string.tab_categories));
        mTabs.addTab(mTabs.newTab().setText(R.string.tab_local));

        FragmentManager fragmentManager = getFragmentManager();
        mFragments[Util.CATEGORY_TAB_INDEX] = fragmentManager.findFragmentByTag(CATEGORY_TAG);
        mFragments[Util.SDCARD_TAB_INDEX] = fragmentManager.findFragmentByTag(LOCAL_TAG);
        if (mFragments[Util.CATEGORY_TAB_INDEX] == null) {
            mFragments[Util.CATEGORY_TAB_INDEX] = new FileCategoryActivity();
        }
        if (mFragments[Util.SDCARD_TAB_INDEX] == null) {
            mFragments[Util.SDCARD_TAB_INDEX] = new FileViewActivity();
        }

        int selectedTab = PreferenceManager.getDefaultSharedPreferences(this)
                .getInt(INSTANCESTATE_TAB, Util.CATEGORY_TAB_INDEX);
        if (selectedTab < 0 || selectedTab >= TAB_COUNT) {
            selectedTab = Util.CATEGORY_TAB_INDEX;
        }
        mCurrentTab = selectedTab;
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        if (!mFragments[Util.CATEGORY_TAB_INDEX].isAdded()) {
            transaction.add(R.id.fragment_container, mFragments[Util.CATEGORY_TAB_INDEX], CATEGORY_TAG);
        }
        if (!mFragments[Util.SDCARD_TAB_INDEX].isAdded()) {
            transaction.add(R.id.fragment_container, mFragments[Util.SDCARD_TAB_INDEX], LOCAL_TAG);
        }
        transaction.hide(mFragments[1 - selectedTab]).show(mFragments[selectedTab]).commit();
        mTabs.selectTab(mTabs.getTabAt(selectedTab));
        mTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Fragment fragment = mFragments[mCurrentTab];
                if (fragment instanceof IBackPressedListener
                        && ((IBackPressedListener) fragment).onBack()) {
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
        requestStorageAccessIfNeeded();
    }

    public static boolean hasFileSystemAccess(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestStorageAccessIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()
                    && !PreferenceManager.getDefaultSharedPreferences(this)
                    .getBoolean(STORAGE_ACCESS_EXPLANATION_SHOWN, false)) {
                PreferenceManager.getDefaultSharedPreferences(this).edit()
                        .putBoolean(STORAGE_ACCESS_EXPLANATION_SHOWN, true).apply();
                showAllFilesAccessExplanation();
            }
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasFileSystemAccess(this)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            if (!prefs.getBoolean(LEGACY_STORAGE_PERMISSION_REQUESTED, false)) {
                prefs.edit().putBoolean(LEGACY_STORAGE_PERMISSION_REQUESTED, true).apply();
                requestLegacyStoragePermissions();
            }
        }
    }

    private void showAllFilesAccessExplanation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.storage_access_title)
                .setMessage(R.string.storage_access_explanation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.storage_access_continue,
                        (dialog, which) -> openAllFilesAccessSettings())
                .show();
    }

    private void openAllFilesAccessSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        Intent appSettings = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        try {
            startActivity(appSettings);
        } catch (ActivityNotFoundException exception) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (ActivityNotFoundException fallbackException) {
                Toast.makeText(this, R.string.storage_access_required, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void requestLegacyStoragePermissions() {
        String[] permissions = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        ActivityCompat.requestPermissions(this, permissions, REQUEST_STORAGE_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_STORAGE_PERMISSIONS) {
            return;
        }
        if (!hasFileSystemAccess(this)) {
            Toast.makeText(this, R.string.storage_warning, Toast.LENGTH_LONG).show();
        }
        refreshStorageFragments();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStorageFragments();
        invalidateOptionsMenu();
    }

    private void refreshStorageFragments() {
        be.ppareit.swiftp.Util.resetScoped();
        if (mFragments[Util.SDCARD_TAB_INDEX] instanceof FileViewActivity) {
            ((FileViewActivity) mFragments[Util.SDCARD_TAB_INDEX]).onStoragePermissionChanged();
        }
        if (mFragments[Util.CATEGORY_TAB_INDEX] instanceof FileCategoryActivity) {
            ((FileCategoryActivity) mFragments[Util.CATEGORY_TAB_INDEX]).onStoragePermissionChanged();
        }
    }

    private void showTab(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= TAB_COUNT || tabIndex == mCurrentTab) {
            return;
        }
        if (tabIndex != Util.SDCARD_TAB_INDEX && mActionMode != null) {
            mActionMode.finish();
        }
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.hide(mFragments[mCurrentTab]).show(mFragments[tabIndex]).commit();
        mCurrentTab = tabIndex;
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putInt(INSTANCESTATE_TAB, mCurrentTab);
        editor.apply();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (mCurrentTab == Util.CATEGORY_TAB_INDEX) {
            FileCategoryActivity categoryFragment =
                    (FileCategoryActivity) mFragments[Util.CATEGORY_TAB_INDEX];
            if (categoryFragment.isHomePage()) {
                reInstantiateCategoryTab();
            } else {
                categoryFragment.setConfigurationChanged(true);
            }
        }
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_storage_access) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                showAllFilesAccessExplanation();
            } else {
                requestLegacyStoragePermissions();
            }
            return true;
        }
        if (item.getItemId() == R.id.action_ftp_server) {
            startActivity(new android.content.Intent(this, FTPServerActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new android.content.Intent(this, FileExplorerPreferenceActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_refresh) {
            Fragment currentFragment = mFragments[mCurrentTab];
            if (currentFragment instanceof FileCategoryActivity) {
                ((FileCategoryActivity) currentFragment).refresh();
            } else if (currentFragment instanceof FileViewActivity) {
                ((FileViewActivity) currentFragment).refresh();
            }
            return true;
        }
        if (item.getItemId() == R.id.action_exit) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem storageAccess = menu.findItem(R.id.action_storage_access);
        if (storageAccess != null) {
            storageAccess.setVisible(!hasFileSystemAccess(this));
        }
        return super.onPrepareOptionsMenu(menu);
    }

    public interface IBackPressedListener {
        boolean onBack();
    }

    public void setActionMode(ActionMode actionMode) {
        mActionMode = actionMode;
    }

    public ActionMode getActionMode() {
        return mActionMode;
    }

    public Fragment getFragment(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= TAB_COUNT) {
            throw new IllegalArgumentException("Unknown tab index: " + tabIndex);
        }
        return mFragments[tabIndex];
    }

    public void reInstantiateCategoryTab() {
        FileCategoryActivity categoryFragment = new FileCategoryActivity();
        FragmentTransaction transaction = getFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, categoryFragment, CATEGORY_TAG);
        if (mCurrentTab != Util.CATEGORY_TAB_INDEX) {
            transaction.hide(categoryFragment);
        }
        transaction.commit();
        mFragments[Util.CATEGORY_TAB_INDEX] = categoryFragment;
    }

    public void selectTab(int tabIndex) {
        if (tabIndex >= 0 && tabIndex < TAB_COUNT) {
            mTabs.selectTab(mTabs.getTabAt(tabIndex));
        }
    }

    public int getSelectedTab() {
        return mCurrentTab;
    }
}
