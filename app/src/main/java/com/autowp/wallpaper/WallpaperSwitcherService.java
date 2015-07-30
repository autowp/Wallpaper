package com.autowp.wallpaper;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Dmitry on 25.07.2015.
 */
public class WallpaperSwitcherService extends Service {

    private static final String URL_NEW_PICTURE = "http://en.autowp.ru/api/picture/new-picture";
    private static final String URL_CAR_OF_DAY_PICTURE = "http://en.autowp.ru/api/picture/car-of-day-picture";
    private static final String URL_RANDOM_PICTURE = "http://en.autowp.ru/api/picture/random-picture";

    public static final String PREFENCES_MAIN = "main";
    public static final String PREFENCES_MAIN_MODE = "mode";
    private static final String PREFENCES_URL = "url";

    public static final int MODE_DISABLED = 0;
    public static final int MODE_CAR_OF_DAY_PICTURE = 1;
    public static final int MODE_NEW_PICTURE = 2;
    public static final int MODE_RANDOM_PICTURE = 3;

    public static final int CHECK_PERIOD = 3600000;

    private String mStatusText = null;

    private PendingIntent mPendingIntent;
    private SharedPreferences mSettings;
    private WallpaperManager mWallpaperManager;

    public class LocalBinder extends Binder {
        WallpaperSwitcherService getService() {
            return WallpaperSwitcherService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            System.out.println("onStartCommand " + intent.getAction());
            switch (intent.getAction()) {
                case "doSwitch":
                    doSwitch();
                    break;
                case "startAlarmAndDoSwitch":
                    doSwitch();
                    startAlarm();
                    break;
            }
        }
        return 0;
    }

    public interface WallpaperSwitcherListener {
        void handleStatusChanged(String status);
    }

    private List<WallpaperSwitcherListener> mWallpaperSwitcherListeners =
            new ArrayList<>();

    public void onCreate(){
        super.onCreate();
        mSettings = getSharedPreferences(PREFENCES_MAIN, 0);
        mWallpaperManager = WallpaperManager.getInstance(this);
        mPendingIntent = PendingIntent.getBroadcast(this, 0, getAlarmIntent(), 0);
    }

    private Intent getAlarmIntent() {
        return new Intent(this, AlarmReceiver.class);
    }

    public synchronized void addEventListener(WallpaperSwitcherListener listener) {
        mWallpaperSwitcherListeners.add(listener);
    }

    public synchronized void removeEventListener(WallpaperSwitcherListener listener){
        mWallpaperSwitcherListeners.remove(listener);
    }

    private synchronized void fireEvent()
    {
        Iterator<WallpaperSwitcherListener> i = mWallpaperSwitcherListeners.iterator();
        while(i.hasNext()) {
            i.next().handleStatusChanged(mStatusText);
        }
    }

    private class DownloadJsonTask extends AsyncTask<Void, Void, String> {
        String mJsonUrl;
        DisplayMetrics mDisplayMetrics;
        public DownloadJsonTask(DisplayMetrics displayMetrics, String url) {
            mDisplayMetrics = displayMetrics;
            mJsonUrl = url;
        }

        @Override
        protected String doInBackground(Void... params) {

            setStatusText(getString(R.string.status_download_picture_info));

            String resultJson = null;
            try {
                URL url = new URL(mJsonUrl);

                URLConnection urlConnection = url.openConnection();
                urlConnection.connect();

                InputStream inputStream = urlConnection.getInputStream();
                StringBuilder buffer = new StringBuilder();

                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line);
                }

                resultJson = buffer.toString();

            } catch (Exception e) {
                setStatusText(String.format(getString(R.string.status_error), e.getMessage()));
            }
            return resultJson;
        }

        @Override
        protected void onPostExecute(String strJson) {
            super.onPostExecute(strJson);

            if (strJson == null) {
                setStatusText(getString(com.autowp.wallpaper.R.string.error_json_downlod_failed));
                return;
            }

            try {
                JSONObject dataJsonObj = new JSONObject(strJson);

                boolean status = dataJsonObj.getBoolean("status");

                if (status) {
                    String url = dataJsonObj.getString("url");

                    String oldImageUrl = mSettings.getString(PREFENCES_URL, null);

                    if (oldImageUrl == null || !oldImageUrl.contentEquals(url)) {
                        new DownloadImageTask(mDisplayMetrics, url).execute();
                    } else {
                        setStatusText(getString(R.string.status_picture_same));
                    }


                }

            } catch (JSONException e) {
                setStatusText(String.format(getString(R.string.status_error), e.getMessage()));
            }
        }
    }

    private class DownloadImageTask extends AsyncTask<Void, Void, Void> {
        String mImageUrl;
        DisplayMetrics mDisplayMetrics;
        public DownloadImageTask(DisplayMetrics displayMetrics, String url) {
            mDisplayMetrics = displayMetrics;
            mImageUrl = url;
        }

        @Override
        protected Void doInBackground(Void... params) {
            setStatusText(String.format(getString(R.string.status_download_picture), mImageUrl));

            try {
                URL url = new URL(mImageUrl);
                URLConnection urlConnection = url.openConnection();
                urlConnection.connect();

                InputStream inputStream = urlConnection.getInputStream();

                setStatusText(getString(R.string.status_converting));

                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                Bitmap.createScaledBitmap(bitmap, 120, 120, false);

                Double srcWidth = Integer.valueOf(bitmap.getWidth()).doubleValue();
                Double srcHeight = Integer.valueOf(bitmap.getHeight()).doubleValue();

                Double displayWidth = Integer.valueOf(mDisplayMetrics.widthPixels).doubleValue();
                Double displayHeight = Integer.valueOf(mDisplayMetrics.heightPixels).doubleValue();

                Double displayRatio = displayWidth / displayHeight;
                Double bitmapRatio = srcWidth / srcHeight;

                int cropWidth, cropHeight, cropLeft, cropTop;

                if (displayRatio < bitmapRatio) {
                    cropWidth = (int)Math.round(srcHeight * displayRatio);
                    cropHeight = (int)Math.round(srcHeight);
                    cropLeft = (int)Math.round((srcWidth - cropWidth) / 2.0);
                    cropTop = 0;
                } else {
                    cropWidth = (int)Math.round(srcWidth);
                    cropHeight = (int)Math.round(srcWidth / displayRatio);
                    cropLeft = 0;
                    cropTop = (int)Math.round((srcHeight - cropHeight) / 2);
                }

                System.out.println(String.format("Crop to %d %d %d %d", cropLeft, cropTop, cropWidth, cropHeight));

                Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropWidth, cropHeight);
                bitmap.recycle();
                bitmap = null;

                System.out.println(String.format("Scale to %d %d", displayWidth.intValue(), displayHeight.intValue()));

                Bitmap scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, displayWidth.intValue(), displayHeight.intValue(), true);
                croppedBitmap.recycle();
                croppedBitmap = null;

                setStatusText(getString(R.string.status_setting_wallpaper));

                mWallpaperManager.setBitmap(scaledBitmap);

                SharedPreferences.Editor editor = mSettings.edit();
                editor.putString(PREFENCES_URL, mImageUrl);
                editor.apply();

                setStatusText(getString(R.string.status_complete));

            } catch (Exception e) {
                setStatusText(String.format(getString(R.string.status_error), e.getMessage()));
            }

            return null;
        }
    }

    private void setStatusText(final String status) {
        System.out.println(status);
        mStatusText = status;
        fireEvent();
    }

    public void doSwitch() {
        System.out.println("doSwitch");
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics displayMetrics = new DisplayMetrics();

        int desiredWidth = mWallpaperManager.getDesiredMinimumWidth();
        int desiredHeight = mWallpaperManager.getDesiredMinimumHeight();

        if (desiredWidth > 0 && desiredHeight > 0) {
            displayMetrics.widthPixels = desiredWidth;
            displayMetrics.heightPixels = desiredHeight;
        } else {
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        }

        switch (mSettings.getInt(PREFENCES_MAIN_MODE, 0)) {
            case MODE_RANDOM_PICTURE:
                new DownloadJsonTask(displayMetrics, URL_RANDOM_PICTURE).execute();
                break;
            case MODE_NEW_PICTURE:
                new DownloadJsonTask(displayMetrics, URL_NEW_PICTURE).execute();
                break;

            case MODE_CAR_OF_DAY_PICTURE:
                new DownloadJsonTask(displayMetrics, URL_CAR_OF_DAY_PICTURE).execute();
                break;

            case MODE_DISABLED:
            default:
                break;
        }
    }

    public int getMode() {
        return mSettings.getInt(PREFENCES_MAIN_MODE, 0);
    }

    public void setMode(int mode) {
        mSettings.edit().putInt(PREFENCES_MAIN_MODE, mode).apply();
        stopAlarm();
        startAlarm();
    }

    public void stopAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(mPendingIntent);
    }

    public void startAlarm() {
        System.out.println("Start alarm");
        int interval = -1;
        switch(getMode()) {
            case MODE_RANDOM_PICTURE:
            case MODE_NEW_PICTURE:
            case MODE_CAR_OF_DAY_PICTURE:
                interval = WallpaperSwitcherService.CHECK_PERIOD;
                break;
        }

        System.out.println("Interval " + interval);

        if (interval > 0) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            System.out.println("setInexactRepeating");
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), interval, mPendingIntent);
        }
    }
}
