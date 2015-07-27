package com.autowp.wallpaper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by Dmitry on 25.07.2015.
 */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent arg1) {
        System.out.println("AlarmReceiver.onReceive");

        Intent serviceIntent = new Intent(context, WallpaperSwitcherService.class);
        serviceIntent.setAction("doSwitch");
        context.startService(serviceIntent);
    }
}
