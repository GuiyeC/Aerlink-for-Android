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

    private TextView infoTextView;


    public void setInfoTextView(TextView infoTextView) {
        this.infoTextView = infoTextView;
    }

    public MainService getService() {
        return service;
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

        if (!isServiceRunning()) {
            startService(new Intent(this, MainService.class));
        }

        if (!mServiceBound) {
            Intent intent = new Intent(this, MainService.class);
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mBroadcastReceiver);


        if (mServiceBound) {
            disconnect();

            unbindService(serviceConnection);
            mServiceBound = false;

            showDisconnected();
        }
    }

    public void tryToConnect() { }

    public void disconnect() { }

    public void showDisconnected() {
        showInfoText("Disconnected.\nConnect using \"Aerlink\" on your iOS device.");
    }

    public void showInfoText(final String infoText) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (infoTextView != null) {
                    infoTextView.setText(infoText);
                    infoTextView.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    public void hideInfoTextView() {
        if (infoTextView == null || infoTextView.getVisibility() == View.GONE) {
            return;
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                infoTextView.setVisibility(View.GONE);
            }
        });
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceBound = false;
            
            disconnect();

            showDisconnected();
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MainService.ServiceBinder binder = (MainService.ServiceBinder) service;
            AerlinkActivity.this.service = binder.getService();

            tryToConnect();

            mServiceBound = true;
        }
    };

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(Constants.IA_SERVICE_READY)) {
                tryToConnect();
            }
            else {
                showDisconnected();
            }
        }

    };


    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (MainService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
