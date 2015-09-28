package com.codegy.aerlink;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.wearable.view.WatchViewStub;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends Activity {

    private Switch mColorBackgroundsSwitch;
    private Switch mBatteryUpdatesSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mColorBackgroundsSwitch = (Switch) stub.findViewById(R.id.blackNotificationsSwitch);
                mBatteryUpdatesSwitch = (Switch) stub.findViewById(R.id.batteryUpdatesSwitch);


                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);
                final boolean colorBackgrounds = sp.getBoolean(Constants.SPK_COLOR_BACKGROUNDS, true);
                final boolean batteryUpdates = sp.getBoolean(Constants.SPK_BATTERY_UPDATES, true);


                mColorBackgroundsSwitch.setChecked(!colorBackgrounds);
                mColorBackgroundsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);
                        sp.edit().putBoolean(Constants.SPK_COLOR_BACKGROUNDS, !isChecked).apply();

                        SettingsActivity.this.sendBroadcast(new Intent(Constants.IA_COLOR_BACKGROUNDS_CHANGED));
                    }
                });


                mBatteryUpdatesSwitch.setChecked(batteryUpdates);
                mBatteryUpdatesSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);
                        sp.edit().putBoolean(Constants.SPK_BATTERY_UPDATES, isChecked).apply();

                        SettingsActivity.this.sendBroadcast(new Intent(Constants.IA_BATTERY_UPDATES_CHANGED));
                    }
                });


                //TextView modelTextView = (TextView) stub.findViewById(R.id.modelTextView);
                //modelTextView.setText(Build.MODEL);
            }
        });
    }
}
