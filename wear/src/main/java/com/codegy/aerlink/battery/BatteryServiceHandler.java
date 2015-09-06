package com.codegy.aerlink.battery;

import android.app.Notification;
import android.app.PendingIntent;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.*;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;
import android.util.Log;
import com.codegy.aerlink.Constants;
import com.codegy.aerlink.R;
import com.codegy.aerlink.utils.ServiceHandler;
import com.codegy.aerlink.utils.ServiceUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by Guiye on 19/5/15.
 */
public class BatteryServiceHandler extends ServiceHandler {

    private static final String LOG_TAG = BatteryServiceHandler.class.getSimpleName();

    public static final int NOTIFICATION_BATTERY = 1002;
    private static final long SILENT_VIBRATION_PATTERN[] = { 200, 110 };

    private Context mContext;
    private ServiceUtils mServiceUtils;

    private int batteryLevel = -1;
    private boolean batteryUpdates;
    private boolean batteryHidden = false;


    public BatteryServiceHandler(Context context, ServiceUtils serviceUtils) {
        this.mContext = context;
        this.mServiceUtils = serviceUtils;

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        batteryUpdates = sp.getBoolean(Constants.SPK_BATTERY_UPDATES, true);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.IA_HIDE_BATTERY);
        intentFilter.addAction(Constants.IA_BATTERY_UPDATES_CHANGED);
        context.registerReceiver(mBroadcastReceiver, intentFilter);
    }


    @Override
    public void close() {
        batteryLevel = -1;

        mContext.unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    public UUID getServiceUUID() {
        return BASConstants.SERVICE_UUID;
    }

    @Override
    public List<String> getCharacteristicsToSubscribe() {
        List<String> characteristics = new ArrayList<>();

        characteristics.add(BASConstants.CHARACTERISTIC_BATTERY_LEVEL);

        return characteristics;
    }

    @Override
    public boolean canHandleCharacteristic(BluetoothGattCharacteristic characteristic) {
        String characteristicUUID = characteristic.getUuid().toString().toLowerCase();
        return characteristicUUID.equals(BASConstants.CHARACTERISTIC_BATTERY_LEVEL);
    }

    @Override
    public void handleCharacteristic(BluetoothGattCharacteristic characteristic) {
        int newBatteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        Log.d(LOG_TAG, "Battery level: " + newBatteryLevel);

        // If the battery is running down, vibrate at 20, 15, 10 and 5
        if (batteryLevel > newBatteryLevel && newBatteryLevel <= 20 && newBatteryLevel % 5 == 0) {
            mServiceUtils.vibrate(SILENT_VIBRATION_PATTERN, -1);
        }

        batteryLevel = newBatteryLevel;

        if ((batteryLevel <= 25 && batteryLevel % 5 == 0) || batteryLevel % 20 == 0) {
            batteryHidden = false;
        }

        buildBatteryNotification();
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    private void buildBatteryNotification() {
        if (!batteryUpdates || batteryHidden || batteryLevel == -1) {
            return;
        }

        Bitmap background = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        background.eraseColor(0);

        int batteryIcon;

        if (batteryLevel > 85) {
            batteryIcon = R.drawable.nic_battery_5;
        }
        else if (batteryLevel > 65) {
            batteryIcon = R.drawable.nic_battery_4;
        }
        else if (batteryLevel > 45) {
            batteryIcon = R.drawable.nic_battery_3;
        }
        else if (batteryLevel > 25) {
            batteryIcon = R.drawable.nic_battery_2;
        }
        else {
            batteryIcon = R.drawable.nic_battery_1;
        }

        // Build pending intent for when the user swipes the card away
        Intent deleteIntent = new Intent(Constants.IA_HIDE_BATTERY);
        PendingIntent deleteAction = PendingIntent.getBroadcast(mContext, 0, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.WearableExtender wearableExtender = new Notification.WearableExtender()
                .setBackground(background);

        Notification.Builder builder = new Notification.Builder(mContext)
                .setSmallIcon(batteryIcon)
                .setDeleteIntent(deleteAction)
                .setContentTitle(mContext.getString(R.string.battery_level))
                .setContentText(batteryLevel + "%")
                .extend(wearableExtender)
                .setPriority(Notification.PRIORITY_MIN);

        //notificationManager.cancel(NOTIFICATION_BATTERY);
        mServiceUtils.notify(null, NOTIFICATION_BATTERY, builder.build());
    }


    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(Constants.IA_HIDE_BATTERY)) {
                batteryHidden = true;
            }
            else if (action.equals(Constants.IA_BATTERY_UPDATES_CHANGED)) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                batteryUpdates = sp.getBoolean(Constants.SPK_BATTERY_UPDATES, true);

                if (batteryUpdates) {
                    buildBatteryNotification();
                }
                else {
                    mServiceUtils.cancelNotification(null, NOTIFICATION_BATTERY);
                }
            }
        }

    };

}
