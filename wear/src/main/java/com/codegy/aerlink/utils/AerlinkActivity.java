package com.codegy.aerlink.utils;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ActivityManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.ProgressSpinner;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import com.codegy.aerlink.Constants;
import com.codegy.aerlink.MainService;
import com.codegy.aerlink.R;
import com.codegy.aerlink.connection.ConnectionState;

/**
 * Created by Guiye on 29/5/15.
 */
public abstract class AerlinkActivity extends WearableActivity implements ServiceObserver {

    private static final String LOG_TAG = AerlinkActivity.class.getSimpleName();

    private final int PERMISSIONS_REQUEST_CODE = 1;

    protected MainService mService;
    protected boolean mServiceBound = false;
    protected ConnectionState state = ConnectionState.Disconnected;

    protected View mDisconnectedLayout;
    protected TextView mConnectionInfoTextView;
    protected boolean mLoading;
    protected View mLoadingLayout;
    protected ProgressSpinner mLoadingSpinner;
    protected boolean mShowingError;
    protected View mConnectionErrorLayout;


    public boolean isConnected() {
        return state == ConnectionState.Ready;
    }

    /***
     * Request for a specific service handler
     * @param serviceHandlerClass the class of the service handler requested
     * @return if the service is available this returns the correct service handler,
     * if not, it returns null
     */
    public ServiceHandler getServiceHandler(Class serviceHandlerClass) {
        ServiceHandler serviceHandler = null;

        if (mService != null) {
            serviceHandler = mService.getServiceHandler(serviceHandlerClass);
        }

        return serviceHandler;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (isServiceRunning()) {
            if (!mServiceBound){
                Intent intent = new Intent(this, MainService.class);
                bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
            }

            showErrorInterface(false);
        }
        else if (mServiceBound) {
            stopService();
        }

        updateInterface();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mService != null) {
            mService.removeObserver(this);
        }

        if (mServiceBound) {
            try {
                unbindService(serviceConnection);
            } catch (Exception ignored) {}
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
        } catch (Exception ignored) {}

        stopService(new Intent(this, MainService.class));

        mServiceBound = false;

        onConnectionStateChanged(ConnectionState.Disconnected);
    }

    /***
     * Called on connection to device, get everything ready here, set callbacks of service handlers
     */
    public abstract void onConnectedToDevice();

    /***
     * Called on disconnection from device, clear everything on this method related to the previously connected device
     */
    public void onDisconnectedFromDevice() {
        setLoading(false);

        showErrorInterface(false);
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
    public void updateInterface() {
        if (mDisconnectedLayout != null) {
            mDisconnectedLayout.setVisibility(isConnected() ? View.GONE : View.VISIBLE);
        }

        if (mConnectionInfoTextView != null) {
            switch (state) {
                case Ready:
                    mConnectionInfoTextView.setText(R.string.general_connected);
                    mConnectionInfoTextView.setTextColor(ContextCompat.getColor(this, R.color.connected));
                    break;
                case Connecting:
                    mConnectionInfoTextView.setText(R.string.general_connecting);
                    mConnectionInfoTextView.setTextColor(ContextCompat.getColor(this, R.color.connecting));
                    break;
                default:
                    mConnectionInfoTextView.setText(R.string.general_disconnected);
                    mConnectionInfoTextView.setTextColor(ContextCompat.getColor(this, R.color.disconnected));
                    break;
            }
        }
    }

    public void setLoading(boolean loading) {
        if (mLoading == loading) {
            return;
        }

        mLoading = loading;
        updateLoadingInterface();
    }

    public void updateLoadingInterface() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mLoadingLayout == null || mLoadingSpinner == null) {
                    return;
                }

                if (mLoading) {
                    mLoadingLayout.setVisibility(View.VISIBLE);
                    mLoadingSpinner.showWithAnimation();
                }
                else {
                    mLoadingSpinner.hideWithAnimation(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            mLoadingLayout.setVisibility(View.GONE);
                        }
                    });
                }
            }
        });
    }

    public void showErrorInterface(boolean showError) {
        if (mShowingError == showError) {
            return;
        }

        mShowingError = showError;
        updateErrorInterface();
    }

    public void updateErrorInterface() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mConnectionErrorLayout != null) {
                    mConnectionErrorLayout.setVisibility(mShowingError ? View.VISIBLE : View.GONE);
                }
            }
        });
    }

    public void restartConnectionAction(View view) {
        restartConnection();
        showErrorInterface(false);
        onConnectionStateChanged(ConnectionState.Disconnected);
    }

    public void restartConnection() {
        if (mService == null) {
            startService();
            return;
        }

        mService.restartConnection();
    }

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

    @Override
    public void onConnectionStateChanged(ConnectionState state) {
        if (this.state == state) {
            return;
        }
        if (state == ConnectionState.Ready) {
            onConnectedToDevice();
        }
        else if (this.state == ConnectionState.Ready) {
            onDisconnectedFromDevice();
        }

        this.state = state;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateInterface();
            }
        });
    }


    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(LOG_TAG, "Service disconnected");
            if (mService != null) {
                mService.removeObserver(AerlinkActivity.this);
            }

            mService = null;
            mServiceBound = false;

            onConnectionStateChanged(ConnectionState.Disconnected);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder serviceBinder) {
            Log.i(LOG_TAG, "Service connected");

            MainService.ServiceBinder binder = (MainService.ServiceBinder) serviceBinder;
            mService = binder.getService();

            mServiceBound = true;

            mService.addObserver(AerlinkActivity.this);
        }
    };

    private ScheduledTask mTimeOutTask;

    protected void scheduleTimeOutTask() {
        if (mTimeOutTask == null) {
            mTimeOutTask = new ScheduledTask(3000, getMainLooper(), new Runnable() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setLoading(false);

                            showErrorInterface(true);
                        }
                    });
                }
            });
        }
        else {
            mTimeOutTask.cancel();
        }

        mTimeOutTask.schedule();
    }

    protected void cancelTimeOutTask() {
        if (mTimeOutTask != null) {
            mTimeOutTask.cancel();
        }
    }

}
