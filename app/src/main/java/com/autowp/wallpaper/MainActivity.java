package com.autowp.wallpaper;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.RadioButton;
import android.widget.TextView;


public class MainActivity extends Activity implements ServiceConnection, WallpaperSwitcherService.WallpaperSwitcherListener {
    private WallpaperSwitcherService mService;
    private boolean serviceIsBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if (!serviceIsBound) {
            Intent intent = new Intent(this, WallpaperSwitcherService.class);
            System.out.println("MainActivity.bindService");
            bindService(intent, this, Context.BIND_AUTO_CREATE);
            serviceIsBound = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (serviceIsBound) {
            if (mService != null) {
                mService.removeEventListener(this);
            }

            this.unbindService(this);
            mService = null;
            serviceIsBound = false;
        }
    }

    public void updateWallpaper(View view) {
        System.out.println("updateWallpaper");
        mService.doSwitch();
    }

    public void onRadioButtonClicked(View view) {
        boolean checked = ((RadioButton) view).isChecked();

        if (checked) {
            mService.stopAlarm();

            SharedPreferences settings = getSharedPreferences(WallpaperSwitcherService.PREFENCES_MAIN, 0);
            settings.edit()
                .putInt(WallpaperSwitcherService.PREFENCES_MAIN_MODE, view.getId())
                .apply();

            View updateButton = findViewById(R.id.update_now);

            switch (view.getId()) {
                case R.id.radiogroup_mode_car_of_day_picture:
                    updateButton.setEnabled(true);
                    mService.setMode(WallpaperSwitcherService.MODE_CAR_OF_DAY_PICTURE);
                    break;
                case R.id.radiogroup_mode_new_picture:
                    updateButton.setEnabled(true);
                    mService.setMode(WallpaperSwitcherService.MODE_NEW_PICTURE);
                    break;
                case R.id.radiogroup_mode_random_picture:
                    updateButton.setEnabled(true);
                    mService.setMode(WallpaperSwitcherService.MODE_RANDOM_PICTURE);
                    break;
                case R.id.radiogroup_mode_disabled:
                default:
                    updateButton.setEnabled(false);
                    mService.setMode(WallpaperSwitcherService.MODE_DISABLED);
                    break;
            }
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        WallpaperSwitcherService.LocalBinder binder = (WallpaperSwitcherService.LocalBinder) service;
        mService = binder.getService();
        System.out.println("MainActivity.onServiceConnected");
        mService.addEventListener(this);

        View updateNow = findViewById(R.id.update_now);
        RadioButton radio1 = (RadioButton)findViewById(R.id.radiogroup_mode_disabled);
        RadioButton radio2 = (RadioButton)findViewById(R.id.radiogroup_mode_car_of_day_picture);
        RadioButton radio3 = (RadioButton)findViewById(R.id.radiogroup_mode_new_picture);
        RadioButton radio4 = (RadioButton)findViewById(R.id.radiogroup_mode_random_picture);

        radio1.setEnabled(true);
        radio2.setEnabled(true);
        radio3.setEnabled(true);
        radio4.setEnabled(true);

        switch (mService.getMode()) {
            case WallpaperSwitcherService.MODE_CAR_OF_DAY_PICTURE:
                radio2.setChecked(true);
                updateNow.setEnabled(true);
                break;
            case WallpaperSwitcherService.MODE_NEW_PICTURE:
                radio3.setChecked(true);
                updateNow.setEnabled(true);
                break;
            case WallpaperSwitcherService.MODE_RANDOM_PICTURE:
                radio4.setChecked(true);
                updateNow.setEnabled(true);
                break;
            case WallpaperSwitcherService.MODE_DISABLED:
            default:
                radio1.setChecked(true);
                updateNow.setEnabled(false);
                break;
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        System.out.println("MainActivity.onServiceDisconnected");
        mService.removeEventListener(this);
        mService = null;

        View updateNow = findViewById(R.id.update_now);
        RadioButton radio1 = (RadioButton)findViewById(R.id.radiogroup_mode_disabled);
        RadioButton radio2 = (RadioButton)findViewById(R.id.radiogroup_mode_car_of_day_picture);
        RadioButton radio3 = (RadioButton)findViewById(R.id.radiogroup_mode_new_picture);
        RadioButton radio4 = (RadioButton)findViewById(R.id.radiogroup_mode_random_picture);

        radio1.setEnabled(false);
        radio2.setEnabled(false);
        radio3.setEnabled(false);
        radio4.setEnabled(false);
        updateNow.setEnabled(false);
    }

    @Override
    public void handleStatusChanged(final String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tvStatus = (TextView) findViewById(R.id.text_status);
                tvStatus.setText(status);
            }
        });
    }
}
