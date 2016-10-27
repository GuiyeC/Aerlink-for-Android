package com.codegy.aerlink;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.CompoundButton;
import android.widget.Switch;

public class SettingsActivity extends Activity {

    private Switch mBatteryUpdatesSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);


        mBatteryUpdatesSwitch = (Switch) findViewById(R.id.batteryUpdatesSwitch);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);
        final boolean batteryUpdates = sp.getBoolean(Constants.SPK_BATTERY_UPDATES, true);

        mBatteryUpdatesSwitch.setChecked(batteryUpdates);
        mBatteryUpdatesSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);
                sp.edit().putBoolean(Constants.SPK_BATTERY_UPDATES, isChecked).apply();

                SettingsActivity.this.sendBroadcast(new Intent(Constants.IA_BATTERY_UPDATES_CHANGED));
            }
        });
    }
}
