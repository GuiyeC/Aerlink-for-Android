package com.codegy.aerlink.utils;

import android.app.ActivityManager;
import android.content.*;
import android.os.Bundle;
import android.os.IBinder;
import android.support.wearable.activity.WearableActivity;
import android.view.View;
import android.widget.TextView;
import com.codegy.aerlink.Constants;
import com.codegy.aerlink.MainService;

/**
 * Created by Guiye on 29/5/15.
 */
public class AerlinkActivity extends WearableActivity {

    private MainService service;
    private boolean mServiceBound = false;
    private boolean connected = false;


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


    /**
     * Update interface on this method, for example: check for connection
     */
    public void updateInterface() {
    }

    /***
     * Called on connection to device, get everything ready here, set callbacks of service handlers
     */
    public void onConnectedToDevice() {
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
            service = null;
            mServiceBound = false;

            onDisconnectedFromDevice();
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder serviceBinder) {
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
