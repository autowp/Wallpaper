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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;

/**
 * Created by Dmitry on 25.07.2015.
 */
public class WallpaperSwitcherService extends Service {

    public static final String ACTION_DO = "doSwitch";
    public static final String ACTION_ALARM_AND_DO = "startAlarmAndDoSwitch";

    private static final String URL_NEW_PICTURE = "https://en.wheelsage.org/api/picture/new-picture";
    private static final String URL_CAR_OF_DAY_PICTURE = "https://en.wheelsage.org/api/picture/car-of-day-picture";
    private static final String URL_RANDOM_PICTURE = "https://en.wheelsage.org/api/picture/random-picture";

    public static final String PREFERENCES_MAIN_MODE = "mode";
    private static final String PREFERENCES_URL = "url";
    public static final String PREFERENCES_PAGE_URL = "last_picture_page";
    public static final String PREFERENCES_NAME = "last_picture_name";

    public static final String MODE_DISABLED = "0";
    public static final String MODE_CAR_OF_DAY_PICTURE = "1";
    public static final String MODE_NEW_PICTURE = "2";
    public static final String MODE_RANDOM_PICTURE = "3";

    public static final int CHECK_PERIOD = 3600000;

    private String mStatusText = null;
    private boolean isProcessing = false;

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
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_DO:
                    doSwitch();
                    break;
                case ACTION_ALARM_AND_DO:
                    doSwitch();
                    startAlarm();
                    break;
            }
        }
        return Service.START_STICKY_COMPATIBILITY;
    }

    public interface WallpaperSwitcherListener {
        void handleStatusChanged(String status);

        void handleLoadingStateChanges(boolean isLoading);
    }

    private List<WallpaperSwitcherListener> mEventListeners =
            new ArrayList<>();

    public void onCreate(){
        super.onCreate();
        mSettings = getDefaultSharedPreferences(getApplicationContext());
        mWallpaperManager = WallpaperManager.getInstance(this);
        mPendingIntent = PendingIntent.getBroadcast(this, 0, getAlarmIntent(), 0);
    }

    private Intent getAlarmIntent() {
        return new Intent(this, AlarmReceiver.class);
    }

    public synchronized void addEventListener(WallpaperSwitcherListener listener) {
        mEventListeners.add(listener);
    }

    public synchronized void removeEventListener(WallpaperSwitcherListener listener){
        mEventListeners.remove(listener);
    }

    private synchronized void fireLoadingStateEvent() {
        for (WallpaperSwitcherListener handler: mEventListeners) {
            handler.handleLoadingStateChanges(isProcessing);
        }
    }

    private synchronized void fireStatusEvent() {
        for (WallpaperSwitcherListener handler: mEventListeners) {
            handler.handleStatusChanged(mStatusText);
        }
    }

    private class DownloadJsonTask extends AsyncTask<Void, Void, String> {
        String mJsonUrl;
        DisplayMetrics mDisplayMetrics;
        private DownloadJsonTask(DisplayMetrics displayMetrics, String url) {
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
                resetIsProcessing();
                return;
            }

            try {
                JSONObject dataJsonObj = new JSONObject(strJson);

                boolean status = dataJsonObj.getBoolean("status");

                if (! status) {
                    setStatusText(getString(com.autowp.wallpaper.R.string.error_json_downlod_failed));
                    resetIsProcessing();
                    return;
                }

                String url = dataJsonObj.getString("url");
                String name = dataJsonObj.getString("name");
                String pageUrl = dataJsonObj.getString("page");

                String oldImageUrl = mSettings.getString(PREFERENCES_URL, null);

                if (oldImageUrl != null && oldImageUrl.contentEquals(url)) {
                    setStatusText(getString(R.string.status_picture_same));
                    resetIsProcessing();
                    return;
                }

                new DownloadImageTask(mDisplayMetrics, url, name, pageUrl).execute();

            } catch (JSONException e) {
                setStatusText(String.format(getString(R.string.status_error), e.getMessage()));
                resetIsProcessing();
            }
        }
    }

    private class DownloadImageTask extends AsyncTask<Void, Void, Void> {
        private final String mName;
        private final String mPageUrl;
        String mImageUrl;
        DisplayMetrics mDisplayMetrics;
        private DownloadImageTask(DisplayMetrics displayMetrics, String url, String name, String pageUrl) {
            mDisplayMetrics = displayMetrics;
            mImageUrl = url;
            mName = name;
            mPageUrl = pageUrl;
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

                Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropWidth, cropHeight);

                Bitmap scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, displayWidth.intValue(), displayHeight.intValue(), true);

                setStatusText(getString(R.string.status_setting_wallpaper));

                mWallpaperManager.setBitmap(scaledBitmap);

                SharedPreferences.Editor editor = mSettings.edit();
                editor.putString(PREFERENCES_URL, mImageUrl);
                editor.putString(PREFERENCES_PAGE_URL, mPageUrl);
                editor.putString(PREFERENCES_NAME, mName);
                editor.apply();

                setStatusText(getString(R.string.status_complete));

                bitmap.recycle();
                croppedBitmap.recycle();

            } catch (Exception e) {
                setStatusText(String.format(getString(R.string.status_error), e.getMessage()));
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void a) {
            super.onPostExecute(a);

            resetIsProcessing();
        }
    }

    private void resetIsProcessing() {
        isProcessing = false;
        fireLoadingStateEvent();
    }

    private void setStatusText(final String status) {
        mStatusText = status;
        fireStatusEvent();
    }

    public String getStatusTest() {
        return mStatusText;
    }

    public synchronized void doSwitch() {
        if (isProcessing) {
            return;
        }

        boolean wifiOnly = mSettings.getBoolean("wifi_only", true);
        if (wifiOnly) {
            ConnectivityManager connectivityMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityMgr == null) {
                return;
            }
            NetworkInfo net = connectivityMgr.getActiveNetworkInfo();
            int netType = net.getType();
            if (netType != ConnectivityManager.TYPE_WIFI && netType != ConnectivityManager.TYPE_ETHERNET) {
                return;
            }
        }

        DisplayMetrics displayMetrics = new DisplayMetrics();

        int desiredWidth = mWallpaperManager.getDesiredMinimumWidth();
        int desiredHeight = mWallpaperManager.getDesiredMinimumHeight();

        if (desiredWidth > 0 && desiredHeight > 0) {
            displayMetrics.widthPixels = desiredWidth;
            displayMetrics.heightPixels = desiredHeight;
        } else {
            WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (windowManager != null) {
                Display dd = windowManager.getDefaultDisplay();
                if (dd != null) {
                    dd.getMetrics(displayMetrics);
                }
            }
        }

        switch (mSettings.getString(PREFERENCES_MAIN_MODE, MODE_DISABLED)) {
            case MODE_RANDOM_PICTURE:
                isProcessing = true;
                fireLoadingStateEvent();
                new DownloadJsonTask(displayMetrics, URL_RANDOM_PICTURE).execute();
                break;
            case MODE_NEW_PICTURE:
                isProcessing = true;
                fireLoadingStateEvent();
                new DownloadJsonTask(displayMetrics, URL_NEW_PICTURE).execute();
                break;

            case MODE_CAR_OF_DAY_PICTURE:
                isProcessing = true;
                fireLoadingStateEvent();
                new DownloadJsonTask(displayMetrics, URL_CAR_OF_DAY_PICTURE).execute();
                break;

            case MODE_DISABLED:
            default:
                break;
        }
    }

    public String getMode() {
        return mSettings.getString(PREFERENCES_MAIN_MODE, MODE_DISABLED);
    }

    public void stopAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(mPendingIntent);
        }
    }

    public void startAlarm() {
        int interval = -1;
        switch(getMode()) {
            case MODE_RANDOM_PICTURE:
            case MODE_NEW_PICTURE:
            case MODE_CAR_OF_DAY_PICTURE:
                interval = WallpaperSwitcherService.CHECK_PERIOD;
                break;
        }

        if (interval > 0) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), interval, mPendingIntent);
            }
        }
    }

    public boolean isProcessing() {
        return isProcessing;
    }
}
