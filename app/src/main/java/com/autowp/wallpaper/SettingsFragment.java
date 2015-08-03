package com.autowp.wallpaper;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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

        updateModeSummary();
    }

    @Override
    public void onDestroy() {
        SharedPreferences settings = getDefaultSharedPreferences(getActivity().getApplicationContext());
        settings.unregisterOnSharedPreferenceChangeListener(this);

        super.onDestroy();
    }

    private void updateModeSummary() {
        SharedPreferences sharedPreferences = getDefaultSharedPreferences(getActivity().getApplicationContext());
        Preference connectionPref = findPreference(WallpaperSwitcherService.PREFENCES_MAIN_MODE);
        int value = Integer.parseInt(sharedPreferences.getString(WallpaperSwitcherService.PREFENCES_MAIN_MODE, "0"));
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
        }
    }
}
