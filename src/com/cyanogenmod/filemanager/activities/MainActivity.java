/*
 * Copyright (C) 2012 The CyanogenMod Project
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

package com.cyanogenmod.filemanager.activities;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.provider.Settings;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.cyanogenmod.filemanager.R;
import com.cyanogenmod.filemanager.activities.preferences.SettingsPreferences;
import com.cyanogenmod.filemanager.model.Bookmark;
import com.cyanogenmod.filemanager.model.FileSystemObject;
import com.cyanogenmod.filemanager.preferences.FileManagerSettings;
import com.cyanogenmod.filemanager.ui.ThemeManager;
import com.cyanogenmod.filemanager.ui.fragments.HomeFragment;
import com.cyanogenmod.filemanager.ui.fragments.NavigationFragment;
import com.cyanogenmod.filemanager.util.FileHelper;
import com.cyngn.uicommon.view.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The main navigation activity. This activity is the center of the application.
 * From this the user can navigate, search, make actions.<br/>
 * This activity is singleTop, so when it is displayed no other activities exists in
 * the stack.<br/>
 * This cause an issue with the saved instance of this class, because if another activity
 * is displayed, and the process is killed, MainActivity is started and the saved
 * instance gets corrupted.<br/>
 * For this reason the methods {link {@link Activity#onSaveInstanceState(Bundle)} and
 * {@link Activity#onRestoreInstanceState(Bundle)} are not implemented, and every time
 * the app is killed, is restarted from his initial state.
 */
public class MainActivity extends ActionBarActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static boolean DEBUG = false;

    // Bookmark list XML tags
    private static final String TAG_BOOKMARKS = "Bookmarks"; //$NON-NLS-1$
    private static final String TAG_BOOKMARK = "bookmark"; //$NON-NLS-1$

    private static final String STR_USB = "usb"; // $NON-NLS-1$

    /**
     * Intent code for request a search.
     */
    public static final int INTENT_REQUEST_SETTINGS = 20001;

    /**
     * Constant for extra information about selected search entry.
     */
    public static final String EXTRA_SEARCH_ENTRY_SELECTION =
            "extra_search_entry_selection"; //$NON-NLS-1$

    /**
     * Constant for extra information about last search data.
     */
    public static final String EXTRA_SEARCH_LAST_SEARCH_DATA =
            "extra_search_last_search_data"; //$NON-NLS-1$

    /**
     * Constant for extra information for request a navigation to the passed path.
     */
    public static final String EXTRA_NAVIGATE_TO =
            "extra_navigate_to"; //$NON-NLS-1$

    /**
     * Constant for extra information for request to add navigation to the history
     */
    public static final String EXTRA_ADD_TO_HISTORY =
            "extra_add_to_history"; //$NON-NLS-1$

    /**
     * Fragment types
     */
    private enum FragmentType {
        // Home fragment
        HOME,

        // Navigation fragment
        NAVIGATION,
    }

    static String MIME_TYPE_LOCALIZED_NAMES[];

    int[][] color_states = new int[][] {
            new int[] {android.R.attr.state_checked}, // checked
            new int[0] // default
    };

    // TODO: Replace with legitimate colors per item.
    int[] colors = new int[] {
            R.color.favorites_primary,
            Color.BLACK
    };

    private Toolbar mToolbar;
    private Fragment currentFragment;
    private DrawerLayout mDrawerLayout;
    private NavigationView mNavigationDrawer;

    private static final int REQUEST_CODE_STORAGE_PERMS = 321;
    private boolean hasPermissions() {
        int res = checkCallingOrSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return (res == PackageManager.PERMISSION_GRANTED);
    }

    private void requestNecessaryPermissions() {
        String[] permissions = new String[] {
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
        };
        requestPermissions(permissions, REQUEST_CODE_STORAGE_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grandResults) {
        boolean allowed = true;
        switch (requestCode) {
            case REQUEST_CODE_STORAGE_PERMS:
                for (int res : grandResults) {
                    allowed = allowed && (res == PackageManager.PERMISSION_GRANTED);
                }
                break;
            default:
                allowed = false;
                break;
        }
        if (allowed) {
            finishOnCreate();

        } else {
            if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                String text = getResources().getString(R.string.storage_permissions_denied);
                final ViewGroup viewGroup = (ViewGroup) ((ViewGroup) this
                        .findViewById(android.R.id.content)).getChildAt(0);
                if (viewGroup != null) {
                    Snackbar snackbar = Snackbar.make(viewGroup, text,
                            Snackbar.LENGTH_INDEFINITE, 3);
                    snackbar.setAction(android.R.string.ok, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            requestNecessaryPermissions();
                        }
                    });
                    snackbar.show();
                }
            } else {
                StringBuilder builder = new StringBuilder(getString(R.string
                        .storage_permissions_denied));
                builder.append("\n\n");
                builder.append(getString(R.string.storage_permissions_explanation));
                final ViewGroup viewGroup = (ViewGroup) ((ViewGroup) this
                        .findViewById(android.R.id.content)).getChildAt(0);
                if (viewGroup != null) {
                    Snackbar snackbar = Snackbar.make(viewGroup, builder.toString(),
                            Snackbar.LENGTH_INDEFINITE, 7);
                    snackbar.setAction(R.string.snackbar_settings, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startInstalledAppDetailsActivity(MainActivity.this);
                            finish();
                        }
                    });
                    snackbar.show();
                }
            }
        }
    }

    public static void startInstalledAppDetailsActivity(final Activity context) {
        if (context == null) {
            return;
        }
        final Intent i = new Intent();
        i.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.addCategory(Intent.CATEGORY_DEFAULT);
        i.setData(Uri.parse("package:" + context.getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        context.startActivity(i);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle state) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

         // Set the theme before setContentView
        ThemeManager.Theme theme = ThemeManager.getCurrentTheme(this);
        theme.setBaseThemeNoActionBar(this);

        //Set the main layout of the activity
        setContentView(R.layout.navigation);

        //Save state
        super.onCreate(state);

        if (!hasPermissions()) {
            requestNecessaryPermissions();
        } else {
            finishOnCreate();
        }
    }

    private void finishOnCreate() {
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mNavigationDrawer = (NavigationView) findViewById(R.id.navigation_view);
        mNavigationDrawer.setNavigationItemSelectedListener(this);
        ColorStateList colorStateList = new ColorStateList(color_states, colors);
        // TODO: Figure out why the following doesn't work correctly...
        mNavigationDrawer.setItemTextColor(colorStateList);
        mNavigationDrawer.setItemIconTintList(colorStateList);

        setCurrentFragment(FragmentType.HOME);

        //Initialize nfc adapter
        NfcAdapter mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter != null) {
            mNfcAdapter.setBeamPushUrisCallback(new NfcAdapter.CreateBeamUrisCallback() {
                @Override
                public android.net.Uri[] createBeamUris(android.nfc.NfcEvent event) {
                    if (currentFragment instanceof NavigationFragment) {
                        List<FileSystemObject> selectedFiles =
                                ((NavigationFragment)currentFragment).getCurrentNavigationView()
                                        .getSelectedFiles();
                        if (selectedFiles.size() > 0) {
                            List<android.net.Uri> fileUri = new ArrayList<Uri>();
                            for (FileSystemObject f : selectedFiles) {
                                //Beam ignores folders and system files
                                if (!FileHelper.isDirectory(f) && !FileHelper.isSystemFile(f)) {
                                    fileUri.add(Uri.fromFile(new File(f.getFullPath())));
                                }
                            }
                            if (fileUri.size() > 0) {
                                return fileUri.toArray(new android.net.Uri[fileUri.size()]);
                            }
                        }
                    }
                    return null;
                }
            }, this);
        }
    }

    private void setCurrentFragment(FragmentType fragmentType) {
        FragmentManager fragmentManager = getSupportFragmentManager();

        switch (fragmentType) {
            case NAVIGATION:
                currentFragment = new NavigationFragment();
                break;
            case HOME:
            default:
                // Default to HOME
                currentFragment = HomeFragment.newInstance();
                break;
        }

        fragmentManager.beginTransaction()
                .replace(R.id.navigation_fragment_container, currentFragment)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .addToBackStack(null)
                .commit();
    }

    public void addBookmark(Bookmark bookmark) {
        // stub
    }

    public void updateActiveDialog(Dialog dialog) {
        // stub
    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected void onNewIntent(Intent intent) {
        //stub
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onNavigationItemSelected(MenuItem menuItem) {
        int id = menuItem.getItemId();
        switch (id) {
            case R.id.navigation_item_home:
                if (DEBUG) Log.d(TAG, "onNavigationItemSelected::navigation_item_home");
                setCurrentFragment(FragmentType.HOME);
                break;
            case R.id.navigation_item_favorites:
                // TODO: Implement this path
                if (DEBUG) Log.d(TAG, "onNavigationItemSelected::navigation_item_favorites");
                break;
            case R.id.navigation_item_internal:
                if (DEBUG) Log.d(TAG, "onNavigationItemSelected::navigation_item_favorites");
                setCurrentFragment(FragmentType.NAVIGATION);
                break;
            case R.id.navigation_item_root_d:
                // TODO: Implement this path
                if (DEBUG) Log.d(TAG, "onNavigationItemSelected::navigation_item_root_d");
                break;
            case R.id.navigation_item_sd_card:
                // TODO: Implement this path
                if (DEBUG) Log.d(TAG, "onNavigationItemSelected::navigation_item_sd_card");
                break;
            case R.id.navigation_item_usb:
                // TODO: Implement this path
                if (DEBUG) Log.d(TAG, "onNavigationItemSelected::navigation_item_usb");
                break;
            case R.id.navigation_item_protected:
                // TODO: Implement this path
                if (DEBUG) Log.d(TAG, "onNavigationItemSelected::navigation_item_protected");
                break;
            case R.id.navigation_item_manage:
                // TODO: Implement this path
                if (DEBUG) Log.d(TAG, "onNavigationItemSelected::navigation_item_manage");
                break;
            case R.id.navigation_item_settings:
                if (DEBUG) Log.d(TAG, "onNavigationItemSelected::navigation_item_settings");
                openSettings();
                break;
            default:
                // TODO: Implement this path
                if (DEBUG) Log.d(TAG, "onNavigationItemSelected::default");
                setCurrentFragment(FragmentType.NAVIGATION); // Temporary...
                break;
        }
        mDrawerLayout.closeDrawers();
        return true;
    }

    /**
     * Method invoked when an action item is clicked.
     *
     * @param view The button pushed
     */
    public void onActionBarItemClick(android.view.View view) {

        if (currentFragment instanceof NavigationFragment) {

            switch (view.getId()) {
                //######################
                //Navigation Custom Title
                //######################
                case com.cyanogenmod.filemanager.R.id.ab_configuration:
                    //Show navigation view configuration toolbar
                    ((NavigationFragment)currentFragment)
                            .getCurrentNavigationView().getCustomTitle().showConfigurationView();
                    break;
                case com.cyanogenmod.filemanager.R.id.ab_close:
                    //Hide navigation view configuration toolbar
                    ((NavigationFragment)currentFragment)
                            .getCurrentNavigationView().getCustomTitle().hideConfigurationView();
                    break;

                //######################
                //Breadcrumb Actions
                //######################
                case com.cyanogenmod.filemanager.R.id.ab_filesystem_info:
                    //Show information of the filesystem
                    com.cyanogenmod.filemanager.model.MountPoint mp =
                            ((NavigationFragment)currentFragment)
                                    .getCurrentNavigationView().getBreadcrumb().getMountPointInfo();
                    com.cyanogenmod.filemanager.model.DiskUsage du =
                            ((NavigationFragment)currentFragment)
                                    .getCurrentNavigationView().getBreadcrumb().getDiskUsageInfo();
                    ((NavigationFragment)currentFragment).showMountPointInfo(mp, du);
                    break;

                //######################
                //Navigation view options
                //######################
                case com.cyanogenmod.filemanager.R.id.ab_sort_mode:
                    ((NavigationFragment)currentFragment).showSettingsPopUp(view,
                            java.util.Arrays.asList(
                                    new FileManagerSettings[]{
                                            FileManagerSettings.SETTINGS_SORT_MODE}));
                    break;
                case com.cyanogenmod.filemanager.R.id.ab_layout_mode:
                    ((NavigationFragment)currentFragment).showSettingsPopUp(view,
                            java.util.Arrays.asList(
                                    new FileManagerSettings[]{
                                            FileManagerSettings.SETTINGS_LAYOUT_MODE}));
                    break;
                case com.cyanogenmod.filemanager.R.id.ab_view_options:
                    // If we are in ChRooted mode, then don't show non-secure items
                    if (((NavigationFragment)currentFragment).mChRooted) {
                        ((NavigationFragment)currentFragment).showSettingsPopUp(view,
                                java.util.Arrays
                                        .asList(new FileManagerSettings[]{
                                                FileManagerSettings.SETTINGS_SHOW_DIRS_FIRST}));
                    } else {
                        ((NavigationFragment)currentFragment).showSettingsPopUp(view,
                                java.util.Arrays
                                        .asList(new FileManagerSettings[]{
                                                FileManagerSettings.SETTINGS_SHOW_DIRS_FIRST,
                                                FileManagerSettings.SETTINGS_SHOW_HIDDEN,
                                                FileManagerSettings.SETTINGS_SHOW_SYSTEM,
                                                FileManagerSettings.SETTINGS_SHOW_SYMLINKS}));
                    }

                    break;

                //######################
                //Selection Actions
                //######################
                case com.cyanogenmod.filemanager.R.id.ab_selection_done:
                    //Show information of the filesystem
                    ((NavigationFragment)currentFragment)
                            .getCurrentNavigationView().onDeselectAll();
                    break;

                //######################
                //Action Bar buttons
                //######################
                case com.cyanogenmod.filemanager.R.id.ab_actions:
                    ((NavigationFragment)currentFragment).openActionsDialog(
                            ((NavigationFragment)currentFragment)
                                    .getCurrentNavigationView().getCurrentDir(),
                            true);
                    break;

                case com.cyanogenmod.filemanager.R.id.ab_search:
                    ((NavigationFragment)currentFragment).openSearch();
                    break;

                default:
                    break;
            }
        }
    }

    /**
     * Method that opens the settings activity.
     *
     * @hide
     */
    void openSettings() {
        Intent settingsIntent = new Intent(MainActivity.this,
                SettingsPreferences.class);
        startActivityForResult(settingsIntent, INTENT_REQUEST_SETTINGS);
    }
}