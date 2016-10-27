package com.codegy.aerlink;

import android.content.*;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.View;
import android.widget.*;
import com.codegy.aerlink.connection.ConnectionState;
import com.codegy.aerlink.services.battery.BatteryServiceHandler;
import com.codegy.aerlink.services.media.MediaServiceHandler;
import com.codegy.aerlink.utils.AerlinkActivity;

public class MainActivity extends AerlinkActivity implements BatteryServiceHandler.BatteryObserver {

    private static final String LOG_TAG = "Aerlink.MainActivity";

    private Switch mServiceSwitch;

    private LinearLayout mConnectionInfoLinearLayout;
    private ImageView mConnectionInfoImageView;
    private TextView mBatteryInfoTextView;

    private CardView mPlayMediaCardView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.i(LOG_TAG, "-=-=-=-=-=-=-=-=-=  MainActivity created  =-=-=-=-=-=-=-=-=-");

        mConnectionInfoLinearLayout = (LinearLayout) findViewById(R.id.connectionInfoLinearLayout);
        mConnectionInfoTextView = (TextView) findViewById(R.id.connectionInfoTextView);
        mConnectionInfoImageView = (ImageView) findViewById(R.id.connectionInfoImageView);
        mBatteryInfoTextView = (TextView) findViewById(R.id.batteryInfoTextView);

        mPlayMediaCardView = (CardView) findViewById(R.id.playMediaCardView);
        mServiceSwitch = (Switch) findViewById(R.id.serviceSwitch);

        boolean serviceRunning = isServiceRunning();

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

    @Override
     public void updateInterface() {
        super.updateInterface();

        boolean connected = isConnected();

        if (mConnectionInfoImageView != null) {
            switch (state) {
                case Ready:
                    mConnectionInfoImageView.setImageResource(R.drawable.status_connected);
                    break;
                case Connecting:
                    mConnectionInfoImageView.setImageResource(R.drawable.status_connecting);
                    break;
                default:
                    mConnectionInfoImageView.setImageResource(R.drawable.status_disconnected);
                    break;
            }
        }

        if (mPlayMediaCardView != null) {
            mPlayMediaCardView.setVisibility(connected ? View.VISIBLE : View.GONE);
        }

        if (mBatteryInfoTextView != null) {
            if (connected) {
                BatteryServiceHandler serviceHandler = (BatteryServiceHandler) getServiceHandler(BatteryServiceHandler.class);

                if (serviceHandler != null) {
                    serviceHandler.setBatteryObserver(this);
                }
            }
            else {
                mBatteryInfoTextView.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onConnectedToDevice() {
        BatteryServiceHandler serviceHandler = (BatteryServiceHandler) getServiceHandler(BatteryServiceHandler.class);

        if (serviceHandler != null) {
            serviceHandler.setBatteryObserver(this);
        }
        else {
            onConnectionStateChanged(ConnectionState.Disconnected);
            restartConnection();
        }
    }

    @Override
    public void onDisconnectedFromDevice() {
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
                    if (batteryLevel > -1) {
                        mBatteryInfoTextView.setText(batteryLevel + "%");
                        mBatteryInfoTextView.setVisibility(View.VISIBLE);

                        if (batteryLevel > 20) {
                            mConnectionInfoTextView.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.connected));
                        }
                        else {
                            mConnectionInfoTextView.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.disconnected));
                        }
                    }
                    else {
                        mBatteryInfoTextView.setVisibility(View.GONE);
                    }
                }
            }
        });
    }


    public void startMedia(View view) {
        MediaServiceHandler serviceHandler = (MediaServiceHandler) getServiceHandler(MediaServiceHandler.class);

        if (serviceHandler != null) {
            serviceHandler.sendPlay();
        }
        else {
            onConnectionStateChanged(ConnectionState.Disconnected);
            restartConnection();
        }
    }

    public void goToSettings(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }
}
