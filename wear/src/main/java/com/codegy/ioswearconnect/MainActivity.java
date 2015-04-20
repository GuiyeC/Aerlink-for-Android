package com.codegy.ioswearconnect;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends Activity {

    private static final String TAG_LOG = "BLE_wear";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG_LOG, "-=-=-=-=-=-=-=-= onCreate -=-=-=-=-=-=-=-=-=");

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.d(TAG_LOG, "not supported ble");
            finish();
        }
        startService(new Intent(MainActivity.this, BLEService.class));
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        Log.d(TAG_LOG, "-=-=-=-=-=-=-=-= onDestroy -=-=-=-=-=-=-=-=-=");
        stopService(new Intent(MainActivity.this, BLEService.class));
    }
}
