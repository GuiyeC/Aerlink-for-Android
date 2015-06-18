package com.codegy.aerlink;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import com.codegy.aerlink.media.MediaServiceHandler;

public class MainActivity extends Activity {

    private static final String LOG_TAG = "Aerlink.MainActivity";

    MainService mService;
    boolean mServiceBound = false;

    private Switch mServiceSwitch;
    private Switch mColorBackgroundsSwitch;
    private Switch mBatteryUpdatesSwitch;
    private Switch mCompleteBatteryInfoSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(LOG_TAG, "-=-=-=-=-=-=-=-= onCreate MainActivity -=-=-=-=-=-=-=-=-=");

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.d(LOG_TAG, "not supported ble");
            finish();
        }

        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mServiceSwitch = (Switch) stub.findViewById(R.id.serviceSwitch);
                mColorBackgroundsSwitch = (Switch) stub.findViewById(R.id.colorBackgroundsSwitch);
                mBatteryUpdatesSwitch = (Switch) stub.findViewById(R.id.batteryUpdatesSwitch);
                mCompleteBatteryInfoSwitch = (Switch) stub.findViewById(R.id.completeBatteryInfoSwitch);


                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                final boolean colorBackgrounds = sp.getBoolean(Constants.SPK_COLOR_BACKGROUNDS, false);
                final boolean batteryUpdates = sp.getBoolean(Constants.SPK_BATTERY_UPDATES, true);
                final boolean completeBatteryInfo = sp.getBoolean(Constants.SPK_COMPLETE_BATTERY_INFO, false);
                final boolean serviceRunning = isServiceRunning();

                if (serviceRunning) {
                    Intent intent = new Intent(MainActivity.this, MainService.class);
                    bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
                }

                mServiceSwitch.setChecked(serviceRunning);
                mServiceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            startService(new Intent(MainActivity.this, MainService.class));

                            Intent intent = new Intent(MainActivity.this, MainService.class);
                            bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
                        } else {
                            stopService(new Intent(MainActivity.this, MainService.class));

                            try {
                                unbindService(mServiceConnection);
                            } catch (Exception e) {}

                            mServiceBound = false;
                        }
                    }
                });


                mColorBackgroundsSwitch.setChecked(colorBackgrounds);
                mColorBackgroundsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                        sp.edit().putBoolean(Constants.SPK_COLOR_BACKGROUNDS, isChecked).apply();

                        MainActivity.this.sendBroadcast(new Intent(Constants.IA_COLOR_BACKGROUNDS_CHANGED));
                    }
                });


                mBatteryUpdatesSwitch.setChecked(batteryUpdates);
                mBatteryUpdatesSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                        sp.edit().putBoolean(Constants.SPK_BATTERY_UPDATES, isChecked).apply();

                        MainActivity.this.sendBroadcast(new Intent(Constants.IA_BATTERY_UPDATES_CHANGED));
                    }
                });


                mBatteryUpdatesSwitch.setChecked(batteryUpdates);
                mBatteryUpdatesSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                        SharedPreferences.Editor editor = sp.edit();
                        editor.putBoolean(Constants.SPK_BATTERY_UPDATES, isChecked);

                        if (!isChecked) {
                            editor.putBoolean(Constants.SPK_COMPLETE_BATTERY_INFO, false);
                            mCompleteBatteryInfoSwitch.setChecked(false);
                        }

                        editor.apply();

                        MainActivity.this.sendBroadcast(new Intent(Constants.IA_BATTERY_UPDATES_CHANGED));
                    }
                });


                mCompleteBatteryInfoSwitch.setChecked(completeBatteryInfo);
                mCompleteBatteryInfoSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                        SharedPreferences.Editor editor = sp.edit();
                        editor.putBoolean(Constants.SPK_COMPLETE_BATTERY_INFO, isChecked);

                        if (isChecked) {
                            editor.putBoolean(Constants.SPK_BATTERY_UPDATES, true);
                            mBatteryUpdatesSwitch.setChecked(true);
                        }

                        editor.apply();

                        MainActivity.this.sendBroadcast(new Intent(Constants.IA_BATTERY_UPDATES_CHANGED));
                    }
                });


                TextView modelTextView = (TextView) stub.findViewById(R.id.modelTextView);
                modelTextView.setText(Build.MODEL);
            }
        });
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        Log.d(LOG_TAG, "-=-=-=-=-=-=-=-= onDestroy MainActivity -=-=-=-=-=-=-=-=-=");
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mServiceSwitch != null) {
            final boolean serviceRunning = isServiceRunning();
            mServiceSwitch.setChecked(serviceRunning);

            if (serviceRunning) {
                Intent intent = new Intent(MainActivity.this, MainService.class);
                bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mServiceBound) {
            try {
                unbindService(mServiceConnection);
            } catch (Exception e) {}
            mServiceBound = false;
        }
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (MainService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }


    public void startMedia(View view) {
        if (mService != null) {
            MediaServiceHandler serviceHandler = (MediaServiceHandler) mService.getServiceHandler(MediaServiceHandler.class);
            if (serviceHandler != null) {
                serviceHandler.sendPlay();
            }
        }
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceBound = false;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MainService.ServiceBinder myBinder = (MainService.ServiceBinder) service;
            mService = myBinder.getService();

            mServiceBound = true;
        }
    };
}
