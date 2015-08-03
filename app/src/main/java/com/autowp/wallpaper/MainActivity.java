package com.autowp.wallpaper;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity implements ServiceConnection, WallpaperSwitcherService.WallpaperSwitcherListener {
    private WallpaperSwitcherService mService;
    private boolean serviceIsBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        System.out.println("Resume");
        System.out.println(serviceIsBound);

        if (!serviceIsBound) {
            System.out.println("Bounding");
            Intent intent = new Intent(this, WallpaperSwitcherService.class);
            getApplicationContext().bindService(intent, this, Context.BIND_AUTO_CREATE);
            serviceIsBound = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (serviceIsBound) {
            if (mService != null) {
                System.out.println("removeEventListener");
                mService.removeEventListener(this);
            }

            getApplicationContext().unbindService(this);
            mService = null;
            serviceIsBound = false;
        }
    }

    public void updateWallpaper(View view) {
        Intent serviceIntent = new Intent(this, WallpaperSwitcherService.class);
        serviceIntent.setAction(WallpaperSwitcherService.ACTION_DO);
        startService(serviceIntent);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        System.out.println("onServiceConnected");
        WallpaperSwitcherService.LocalBinder binder = (WallpaperSwitcherService.LocalBinder) service;
        mService = binder.getService();

        System.out.println("addEventListener");
        mService.addEventListener(this);

        System.out.println("setEnabled");
        System.out.println(!mService.isProcessing());

        View updateNow = findViewById(R.id.update_now);
        updateNow.setEnabled(!mService.isProcessing());

        View progress = findViewById(R.id.progressBar);
        progress.setVisibility(mService.isProcessing() ? View.VISIBLE : View.INVISIBLE);

        TextView tvStatus = (TextView) findViewById(R.id.text_status);
        tvStatus.setText(mService.getStatusTest());
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        System.out.println("removeEventListener");
        mService.removeEventListener(this);
        mService = null;

        View updateNow = findViewById(R.id.update_now);
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

    @Override
    public void handleLoadingStateChanges(final boolean isLoading) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                View updateNow = findViewById(R.id.update_now);
                updateNow.setEnabled(!isLoading);

                View progress = findViewById(R.id.progressBar);
                progress.setVisibility(isLoading ? View.VISIBLE : View.INVISIBLE);
            }
        });
    }

}
