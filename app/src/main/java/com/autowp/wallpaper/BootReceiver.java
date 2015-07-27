package com.autowp.wallpaper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by Dmitry on 25.07.2015.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        System.out.println("BootReceiver.onReceive");

        Intent serviceIntent = new Intent(context, WallpaperSwitcherService.class);
        serviceIntent.setAction("startAlarmAndDoSwitch");
        context.startService(serviceIntent);
    }
}
