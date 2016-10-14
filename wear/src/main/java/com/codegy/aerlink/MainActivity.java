package com.codegy.aerlink;

import android.content.*;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.widget.*;
import com.codegy.aerlink.services.battery.BatteryServiceHandler;
import com.codegy.aerlink.services.media.MediaServiceHandler;
import com.codegy.aerlink.utils.AerlinkActivity;

public class MainActivity extends AerlinkActivity implements BatteryServiceHandler.BatteryObserver {

    private static final String LOG_TAG = "Aerlink.MainActivity";

    private Switch mServiceSwitch;

    private LinearLayout mConnectionInfoLinearLayout;
    private TextView mConnectionInfoTextView;
    private ImageView mConnectionInfoImageView;
    private TextView mBatteryInfoTextView;

    private CardView mPlayMediaCardView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(LOG_TAG, "-=-=-=-=-=-=-=-=-=  MainActivity created  =-=-=-=-=-=-=-=-=-");

        /*
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.d(LOG_TAG, "not supported ble");
           // finish();
        }
        */


        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mConnectionInfoLinearLayout = (LinearLayout) stub.findViewById(R.id.connectionInfoLinearLayout);
                mConnectionInfoTextView = (TextView) stub.findViewById(R.id.connectionInfoTextView);
                mConnectionInfoImageView = (ImageView) stub.findViewById(R.id.connectionInfoImageView);
                mBatteryInfoTextView = (TextView) stub.findViewById(R.id.batteryInfoTextView);

                mPlayMediaCardView = (CardView) stub.findViewById(R.id.playMediaCardView);

                mServiceSwitch = (Switch) stub.findViewById(R.id.serviceSwitch);


                final boolean serviceRunning = isServiceRunning();


                mConnectionInfoLinearLayout.setVisibility(serviceRunning ? View.VISIBLE : View.GONE);

                mServiceSwitch.setChecked(serviceRunning);
                mServiceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            mConnectionInfoLinearLayout.setVisibility(View.VISIBLE);

                            startService();
                        } else {
                            mConnectionInfoLinearLayout.setVisibility(View.GONE);

                            stopService();
                        }
                    }
                });


                updateInterface();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.e(LOG_TAG, "xXxXxXxXxXxXxXxXxX MainActivity destroyed XxXxXxXxXxXxXxXxXx");
    }

    @Override
    protected void onResume() {
        super.onResume();

        boolean serviceRunning = isServiceRunning();

        if (mServiceSwitch != null) {
            mServiceSwitch.setChecked(serviceRunning);
        }

        if (mConnectionInfoLinearLayout != null) {
            mConnectionInfoLinearLayout.setVisibility(serviceRunning ? View.VISIBLE : View.GONE);
        }
    }

    public void startMedia(View view) {
        MediaServiceHandler serviceHandler = (MediaServiceHandler) getServiceHandler(MediaServiceHandler.class);
        if (serviceHandler != null) {
            serviceHandler.sendPlay();
        }
    }


    @Override
     public void updateInterface() {
        boolean connected = isConnected();

        if (mConnectionInfoLinearLayout != null) {
            mConnectionInfoTextView.setText(connected ? "Connected" : "Disconnected");
            mConnectionInfoTextView.setTextColor(getResources().getColor(connected ? R.color.green : R.color.red));
        }

        if (mConnectionInfoImageView != null) {
            mConnectionInfoImageView.setImageResource(connected ? R.drawable.status_connected : R.drawable.status_disconnected);
        }

        if (mPlayMediaCardView != null) {
            mPlayMediaCardView.setVisibility(connected ? View.VISIBLE : View.GONE);
        }

        if (mBatteryInfoTextView != null) {
            if (connected) {
                BatteryServiceHandler serviceHandler = (BatteryServiceHandler) getServiceHandler(BatteryServiceHandler.class);

                if (serviceHandler != null) {
                    onBatteryLevelChanged(serviceHandler.getBatteryLevel());
                }
            }
            else {
                mBatteryInfoTextView.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onConnectedToDevice() {
        super.onConnectedToDevice();

        BatteryServiceHandler serviceHandler = (BatteryServiceHandler) getServiceHandler(BatteryServiceHandler.class);

        if (serviceHandler != null) {
            serviceHandler.setBatteryObserver(this);
        }
        else {
            onDisconnectedFromDevice();
        }
    }

    @Override
    public void onDisconnectedFromDevice() {
        super.onDisconnectedFromDevice();

        BatteryServiceHandler serviceHandler = (BatteryServiceHandler) getServiceHandler(BatteryServiceHandler.class);

        if (serviceHandler != null) {
            serviceHandler.setBatteryObserver(null);
        }
    }

    @Override
    public void onBatteryLevelChanged(final int batteryLevel) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mBatteryInfoTextView != null) {
                    if (batteryLevel != -1) {
                        mBatteryInfoTextView.setText(batteryLevel + "%");
                        mBatteryInfoTextView.setVisibility(View.VISIBLE);
                    } else {
                        mBatteryInfoTextView.setVisibility(View.GONE);
                    }
                }
            }
        });
    }


    public void goToSettings(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }
}
