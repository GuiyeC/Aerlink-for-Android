package com.codegy.ioswearconnect;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final String TAG_LOG = "BLE_wear";

    private Switch mServiceSwitch;

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
                mServiceSwitch.setChecked(isServiceRunning());

                mServiceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            startService(new Intent(MainActivity.this, BLEService.class));
                        }
                        else {
                            stopService(new Intent(MainActivity.this, BLEService.class));
                        }
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        Log.d(TAG_LOG, "-=-=-=-=-=-=-=-= onDestroy MainActivity -=-=-=-=-=-=-=-=-=");
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
