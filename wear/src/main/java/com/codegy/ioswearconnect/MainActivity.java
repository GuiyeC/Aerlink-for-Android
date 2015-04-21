package com.codegy.ioswearconnect;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final String TAG_LOG = "BLE_wear";

    private Switch mServiceSwitch;
    private Switch mColorBackgroundsSwitch;
    private Switch mBatteryUpdatesSwitch;
    private Switch mMoto360FixSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG_LOG, "-=-=-=-=-=-=-=-= onCreate MainActivity -=-=-=-=-=-=-=-=-=");

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.d(TAG_LOG, "not supported ble");
            finish();
        }

        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mServiceSwitch = (Switch) stub.findViewById(R.id.serviceSwitch);
                mColorBackgroundsSwitch = (Switch) stub.findViewById(R.id.colorBackgroundsSwitch);
                mBatteryUpdatesSwitch = (Switch) stub.findViewById(R.id.batteryUpdatesSwitch);
                mMoto360FixSwitch = (Switch) stub.findViewById(R.id.moto360FixSwitch);

                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                final boolean colorBackgrounds = sp.getBoolean(Constants.SPK_COLOR_BACKGROUNDS, false);
                final boolean batteryUpdates = sp.getBoolean(Constants.SPK_BATTERY_UPDATES, true);
                final boolean moto360Fix = sp.getBoolean(Constants.SPK_MOTO_360_FIX, false);
                final boolean serviceRunning = isServiceRunning();


                mServiceSwitch.setChecked(serviceRunning);
                mServiceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            startService(new Intent(MainActivity.this, BLEService.class));
                        } else {
                            stopService(new Intent(MainActivity.this, BLEService.class));
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


                if (mMoto360FixSwitch != null) {
                    mMoto360FixSwitch.setChecked(moto360Fix);
                    mMoto360FixSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                            sp.edit().putBoolean(Constants.SPK_MOTO_360_FIX, isChecked).apply();
                        }
                    });
                }

            }
        });
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        Log.d(TAG_LOG, "-=-=-=-=-=-=-=-= onDestroy MainActivity -=-=-=-=-=-=-=-=-=");
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mServiceSwitch != null) {
            final boolean serviceRunning = isServiceRunning();
            mServiceSwitch.setChecked(serviceRunning);
        }
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (BLEService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
