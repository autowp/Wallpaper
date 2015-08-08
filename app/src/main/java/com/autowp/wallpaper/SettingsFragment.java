package com.autowp.wallpaper;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;

/**
 * Created by Dmitry on 03.08.2015.
 */
public class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        SharedPreferences settings = getDefaultSharedPreferences(getActivity().getApplicationContext());
        settings.registerOnSharedPreferenceChangeListener(this);


        Context context = getActivity().getApplicationContext();
        EditTextPreference versionPref = (EditTextPreference)findPreference("version");
        try {
            String version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            versionPref.setTitle(getString(R.string.version) + ": " + version);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        updateModeSummary();
        updateLastImage();

    }

    @Override
    public void onDestroy() {
        SharedPreferences settings = getDefaultSharedPreferences(getActivity().getApplicationContext());
        settings.unregisterOnSharedPreferenceChangeListener(this);

        super.onDestroy();
    }

    private void updateLastImage() {
        SharedPreferences sharedPreferences = getDefaultSharedPreferences(getActivity().getApplicationContext());
        Preference pref = findPreference(WallpaperSwitcherService.PREFENCES_NAME);

        String name = sharedPreferences.getString(WallpaperSwitcherService.PREFENCES_NAME, "");
        String url = sharedPreferences.getString(WallpaperSwitcherService.PREFENCES_PAGE_URL, "");

        if (name.length() > 0 && url.length() > 0) {
            pref.setSummary(name + "\n" + url);

            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            pref.setIntent(browserIntent);
        }
    }

    private void updateModeSummary() {
        SharedPreferences sharedPreferences = getDefaultSharedPreferences(getActivity().getApplicationContext());
        Preference connectionPref = findPreference(WallpaperSwitcherService.PREFENCES_MAIN_MODE);
        int value = Integer.parseInt(sharedPreferences.getString(
                WallpaperSwitcherService.PREFENCES_MAIN_MODE,
                WallpaperSwitcherService.MODE_DISABLED
        ));
        String[] entries = getResources().getStringArray(R.array.mode_entries);
        String valueStr = "";
        if (value < entries.length) {
            valueStr = entries[value];
        }

        connectionPref.setSummary(valueStr);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(WallpaperSwitcherService.PREFENCES_MAIN_MODE)) {

            updateModeSummary();

            Context context = getActivity().getApplicationContext();

            Intent serviceIntent = new Intent(context, WallpaperSwitcherService.class);
            serviceIntent.setAction(WallpaperSwitcherService.ACTION_DO);
            context.startService(serviceIntent);
        } else if (key.equals(WallpaperSwitcherService.PREFENCES_NAME)) {
            updateLastImage();
        }
    }
}
