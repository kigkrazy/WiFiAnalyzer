/*
 * WiFi Analyzer
 * Copyright (C) 2016  VREM Software Development <VREMSoftwareDevelopment@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package com.vrem.wifianalyzer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.util.Pair;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;

import com.vrem.wifianalyzer.navigation.NavigationMenu;
import com.vrem.wifianalyzer.navigation.NavigationMenuView;
import com.vrem.wifianalyzer.settings.Settings;
import com.vrem.wifianalyzer.settings.ThemeStyle;
import com.vrem.wifianalyzer.vendor.model.Database;
import com.vrem.wifianalyzer.vendor.model.VendorService;
import com.vrem.wifianalyzer.wifi.ConnectionView;
import com.vrem.wifianalyzer.wifi.band.WiFiBand;
import com.vrem.wifianalyzer.wifi.band.WiFiChannel;
import com.vrem.wifianalyzer.wifi.scanner.Scanner;
import com.vrem.wifianalyzer.wifi.scanner.Transformer;

import org.apache.commons.lang3.StringUtils;

import static android.support.design.widget.NavigationView.OnNavigationItemSelectedListener;

public class MainActivity extends AppCompatActivity implements OnSharedPreferenceChangeListener, OnNavigationItemSelectedListener {
    public static final String WI_FI_ANALYZER_BETA = "BETA";

    private ThemeStyle currentThemeStyle;
    private NavigationMenuView navigationMenuView;
    private String currentCountryCode;
    private ConnectionView connectionView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        initializeMainContext(this);

        Settings settings = MainContext.INSTANCE.getSettings();
        settings.initializeDefaultValues();
        setCurrentThemeStyle(settings.getThemeStyle());
        setTheme(getCurrentThemeStyle().themeAppCompatStyle());
        setWiFiChannelPairs();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        settings.registerOnSharedPreferenceChangeListener(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setOnClickListener(new WiFiBandToggle());
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        navigationMenuView = new NavigationMenuView(this, settings.getStartMenu());
        onNavigationItemSelected(navigationMenuView.getCurrentMenuItem());

        connectionView = new ConnectionView(this);
        Scanner scanner = MainContext.INSTANCE.getScanner();
        scanner.register(connectionView);
    }

    private void initializeMainContext(@NonNull Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        Handler handler = new Handler();
        Settings settings = new Settings(context);
        Configuration configuration = new Configuration(isLargeScreenLayout(), isDevelopment());

        MainContext mainContext = MainContext.INSTANCE;
        mainContext.setContext(context);
        mainContext.setConfiguration(configuration);
        mainContext.setResources(context.getResources());
        mainContext.setDatabase(new Database(context));
        mainContext.setSettings(settings);
        mainContext.setVendorService(new VendorService());
        mainContext.setLayoutInflater((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE));
        mainContext.setLogger(new Logger());
        mainContext.setScanner(new Scanner(wifiManager, handler, settings, new Transformer()));
    }

    private boolean isDevelopment() {
        return getPackageName().contains(WI_FI_ANALYZER_BETA);
    }

    private void setWiFiChannelPairs() {
        Settings settings = MainContext.INSTANCE.getSettings();
        String countryCode = settings.getCountryCode();
        if (!countryCode.equals(currentCountryCode)) {
            Pair<WiFiChannel, WiFiChannel> pair = WiFiBand.GHZ5.getWiFiChannels().getWiFiChannelPairFirst(countryCode);
            Configuration configuration = MainContext.INSTANCE.getConfiguration();
            configuration.setWiFiChannelPair(pair);
            currentCountryCode = countryCode;
        }
    }

    private boolean isLargeScreenLayout() {
        Resources resources = getResources();
        android.content.res.Configuration configuration = resources.getConfiguration();
        int screenLayoutSize = configuration.screenLayout & android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK;
        return screenLayoutSize == android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE ||
            screenLayoutSize == android.content.res.Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (shouldReload()) {
            reloadActivity();
        } else {
            setWiFiChannelPairs();
            Scanner scanner = MainContext.INSTANCE.getScanner();
            scanner.update();
            updateSubTitle();
        }
    }

    boolean shouldReload() {
        Settings settings = MainContext.INSTANCE.getSettings();
        ThemeStyle settingThemeStyle = settings.getThemeStyle();
        boolean result = !getCurrentThemeStyle().equals(settingThemeStyle);
        if (result) {
            setCurrentThemeStyle(settingThemeStyle);
        }
        return result;
    }

    private void reloadActivity() {
        finish();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem menuItem) {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        NavigationMenu navigationMenu = NavigationMenu.find(menuItem.getItemId());
        Fragment fragment = navigationMenu.getFragment();
        if (fragment == null) {
            startActivity(new Intent(this, navigationMenu.getActivity()));
        } else {
            navigationMenuView.setCurrentNavigationMenu(navigationMenu);
            getSupportFragmentManager().beginTransaction().replace(R.id.main_fragment, navigationMenu.getFragment()).commit();
            setTitle(menuItem.getTitle());
            updateSubTitle();
        }
        return true;
    }

    @Override
    protected void onPause() {
        Scanner scanner = MainContext.INSTANCE.getScanner();
        scanner.pause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Scanner scanner = MainContext.INSTANCE.getScanner();
        scanner.resume();
    }

    @Override
    protected void onDestroy() {
        Scanner scanner = MainContext.INSTANCE.getScanner();
        scanner.unregister(connectionView);
        super.onDestroy();
    }

    private void updateSubTitle() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            NavigationMenu navigationMenu = navigationMenuView.getCurrentNavigationMenu();
            Settings settings = MainContext.INSTANCE.getSettings();
            actionBar.setSubtitle(navigationMenu.isWiFiBandSwitchable() ? settings.getWiFiBand().getBand() : StringUtils.EMPTY);
        }
    }

    public NavigationMenuView getNavigationMenuView() {
        return navigationMenuView;
    }

    private class WiFiBandToggle implements OnClickListener {
        @Override
        public void onClick(View view) {
            if (navigationMenuView.getCurrentNavigationMenu().isWiFiBandSwitchable()) {
                Settings settings = MainContext.INSTANCE.getSettings();
                settings.toggleWiFiBand();
            }
        }
    }

    ThemeStyle getCurrentThemeStyle() {
        return currentThemeStyle;
    }

    void setCurrentThemeStyle(ThemeStyle currentThemeStyle) {
        this.currentThemeStyle = currentThemeStyle;
    }
}
