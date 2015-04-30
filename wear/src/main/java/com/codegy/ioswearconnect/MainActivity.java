package com.codegy.ioswearconnect;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
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
    private Switch mCompleteBatteryInfoSwitch;

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
                mCompleteBatteryInfoSwitch = (Switch) stub.findViewById(R.id.completeBatteryInfoSwitch);


                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                final boolean colorBackgrounds = sp.getBoolean(Constants.SPK_COLOR_BACKGROUNDS, false);
                final boolean batteryUpdates = sp.getBoolean(Constants.SPK_BATTERY_UPDATES, true);
                final boolean completeBatteryInfo = sp.getBoolean(Constants.SPK_COMPLETE_BATTERY_INFO, false);
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
