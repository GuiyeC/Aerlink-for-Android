package com.codegy.aerlink;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v7.widget.CardView;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.widget.*;
import com.codegy.aerlink.battery.BatteryServiceHandler;
import com.codegy.aerlink.media.MediaServiceHandler;
import com.codegy.aerlink.utils.AerlinkActivity;
import org.w3c.dom.Text;

public class MainActivity extends AerlinkActivity {

    private static final String LOG_TAG = "Aerlink.MainActivity";

    private Switch mServiceSwitch;

    private LinearLayout mConnectionInfoLinearLayout;
    private TextView mConnectionInfoTextView;
    private ImageView mConnectionInfoImageView;
    private TextView mBatteryInfoTextView;

    private CardView mPlayMediaCardView;

//    private Switch mColorBackgroundsSwitch;
//    private Switch mBatteryUpdatesSwitch;
   // private Switch mCompleteBatteryInfoSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(LOG_TAG, "-=-=-=-=-=-=-=-= onCreate MainActivity -=-=-=-=-=-=-=-=-=");

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.d(LOG_TAG, "not supported ble");
            finish();
        }

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
                //   mColorBackgroundsSwitch = (Switch) stub.findViewById(R.id.colorBackgroundsSwitch);
                //   mBatteryUpdatesSwitch = (Switch) stub.findViewById(R.id.batteryUpdatesSwitch);
                //   mCompleteBatteryInfoSwitch = (Switch) stub.findViewById(R.id.completeBatteryInfoSwitch);


                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                final boolean colorBackgrounds = sp.getBoolean(Constants.SPK_COLOR_BACKGROUNDS, false);
                final boolean batteryUpdates = sp.getBoolean(Constants.SPK_BATTERY_UPDATES, true);
                final boolean completeBatteryInfo = sp.getBoolean(Constants.SPK_COMPLETE_BATTERY_INFO, false);
                final boolean serviceRunning = isServiceRunning();


                mConnectionInfoLinearLayout.setVisibility(serviceRunning ? View.VISIBLE : View.GONE);
                tryToConnect();

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

/*

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
                     //       mCompleteBatteryInfoSwitch.setChecked(false);
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
*/

//                TextView modelTextView = (TextView) stub.findViewById(R.id.modelTextView);
//                modelTextView.setText(Build.MODEL);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "-=-=-=-=-=-=-=-= onDestroy MainActivity -=-=-=-=-=-=-=-=-=");
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

            tryToConnect();
        }
    }

    public void startMedia(View view) {
        if (getService() != null) {
            MediaServiceHandler serviceHandler = (MediaServiceHandler) getService().getServiceHandler(MediaServiceHandler.class);
            if (serviceHandler != null) {
                serviceHandler.sendPlay();
            }
        }
    }

    private void updateBatteryLevel() {
        if (mBatteryInfoTextView != null && getService() != null) {
            BatteryServiceHandler serviceHandler = (BatteryServiceHandler) getService().getServiceHandler(BatteryServiceHandler.class);
            if (serviceHandler != null && serviceHandler.getBatteryLevel() != -1) {
                mBatteryInfoTextView.setText(serviceHandler.getBatteryLevel()+"%");
                mBatteryInfoTextView.setVisibility(View.VISIBLE);
            }
            else {
                mBatteryInfoTextView.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void tryToConnect() {
        if (mConnectionInfoTextView != null && mPlayMediaCardView != null) {
            boolean connected = false;

            if (getService() != null) {
                connected = getService().isConnectionReady();
            }

            mConnectionInfoTextView.setText(connected ? "Connected" : "Disconnected");
            mConnectionInfoTextView.setTextColor(getResources().getColor(connected ? R.color.green : R.color.red));
            mConnectionInfoImageView.setImageResource(connected ? R.drawable.status_connected : R.drawable.status_disconnected);
            mPlayMediaCardView.setVisibility(connected ? View.VISIBLE : View.GONE);

            updateBatteryLevel();
        }
    }

    @Override
    public void showDisconnected() {
        if (mConnectionInfoTextView != null && mPlayMediaCardView != null) {
            mConnectionInfoTextView.setText("Disconnected");
            mConnectionInfoTextView.setTextColor(getResources().getColor(R.color.red));
            mConnectionInfoImageView.setImageResource(R.drawable.status_disconnected);
            mBatteryInfoTextView.setVisibility(View.GONE);
            mPlayMediaCardView.setVisibility(View.GONE);
        }
    }
}
