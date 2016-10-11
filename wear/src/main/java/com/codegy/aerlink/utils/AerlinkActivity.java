package com.codegy.aerlink.utils;

import android.Manifest;
import android.app.ActivityManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import com.codegy.aerlink.Constants;
import com.codegy.aerlink.MainService;
import com.codegy.aerlink.connection.ConnectionHandler;

/**
 * Created by Guiye on 29/5/15.
 */
public abstract class AerlinkActivity extends WearableActivity {

    private static final String LOG_TAG = AerlinkActivity.class.getSimpleName();

    private MainService service;
    private boolean mServiceBound = false;
    private boolean connected = false;

    private final int PERMISSIONS_REQUEST_CODE = 1;

    public MainService getService() {
        return service;
    }

    public boolean isConnected() {
        return connected;
    }

    /***
     * Request for a specific service handler
     * @param serviceHandlerClass the class of the service handler requested
     * @return if the service is available this returns the correct service handler,
     * if not, it returns null
     */
    public ServiceHandler getServiceHandler(Class serviceHandlerClass) {
        ServiceHandler serviceHandler = null;

        if (getService() != null) {
            serviceHandler = getService().getServiceHandler(serviceHandlerClass);
        }

        return serviceHandler;
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.IA_SERVICE_READY);
        intentFilter.addAction(Constants.IA_SERVICE_NOT_READY);
        registerReceiver(mBroadcastReceiver, intentFilter);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (isServiceRunning()) {
            if (!mServiceBound){
                Intent intent = new Intent(this, MainService.class);
                bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
            }
        }
        else if (mServiceBound) {
            stopService();
        }

        updateInterface();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            unregisterReceiver(mBroadcastReceiver);
        } catch (Exception e) {}

        if (mServiceBound) {
            try {
                unbindService(serviceConnection);
            } catch (Exception e) {}
        }
    }

    public void startService() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{ Manifest.permission.ACCESS_COARSE_LOCATION },
                    PERMISSIONS_REQUEST_CODE);
            return;
        }

        startService(new Intent(this, MainService.class));

        Intent intent = new Intent(this, MainService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public void stopService() {
        try {
            unbindService(serviceConnection);
        } catch (Exception e) {}

        stopService(new Intent(this, MainService.class));

        mServiceBound = false;
        connected = false;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean success = true;

            if (grantResults.length == permissions.length) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        success = false;
                        break;
                    }
                }
            }
            else {
                success = false;
            }

            if (success) {
                startService();
            }
            else {
                stopService();
            }
        }
    }

    /**
     * Update interface on this method, for example: check for connection
     */
    public abstract void updateInterface();

    /***
     * Called on connection to device, get everything ready here, set callbacks of service handlers
     */
    public void onConnectedToDevice() {
        Log.i(LOG_TAG, "Connected");
        connected = true;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateInterface();
            }
        });
    }

    /***
     * Called on disconnection from device, clear everything on this method related to the previously connected device
     */
    public void onDisconnectedFromDevice() {
        Log.i(LOG_TAG, "Disconnected");
        connected = false;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateInterface();
            }
        });
    }

    // TEMP
    /*
    public void onServiceUnavailable() {
        onDisconnectedFromDevice();
    }
    */


    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(LOG_TAG, "Service disconnected");

            service = null;
            mServiceBound = false;

            onDisconnectedFromDevice();
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder serviceBinder) {
            Log.i(LOG_TAG, "Service connected");

            MainService.ServiceBinder binder = (MainService.ServiceBinder) serviceBinder;
            service = binder.getService();

            mServiceBound = true;

            // Bound to service, check if its connected to device
            if (service.isConnectionReady()) {
                onConnectedToDevice();
            }
        }
    };


    /***
     * Receives notifications related to the connection to the iOS device
     */
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(Constants.IA_SERVICE_READY)) {
                onConnectedToDevice();
            }
            else {
                onDisconnectedFromDevice();
            }
        }

    };


    /***
     * Check if Aerlink's main service is running
     * @return boolean indicating if the service is running or not
     */
    public boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (MainService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
