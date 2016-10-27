package com.codegy.aerlink.services.battery;

import android.app.Notification;
import android.app.PendingIntent;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

    public interface BatteryObserver {
        void onBatteryLevelChanged(int batteryLevel);
    }

    private static final String LOG_TAG = BatteryServiceHandler.class.getSimpleName();

    private static final int NOTIFICATION_BATTERY = 1002;
    private static final long SILENT_VIBRATION_PATTERN[] = { 200, 110 };

    private Context mContext;
    private ServiceUtils mServiceUtils;
    private BatteryObserver batteryObserver;

    private int batteryLevel = -1;
    private boolean showBattery = false;


    public BatteryServiceHandler(Context context, ServiceUtils serviceUtils) {
        this.mContext = context;
        this.mServiceUtils = serviceUtils;

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.IA_HIDE_BATTERY);
        context.registerReceiver(mBroadcastReceiver, intentFilter);
    }

    public void setBatteryObserver(BatteryObserver batteryObserver) {
        this.batteryObserver = batteryObserver;

        if (batteryObserver != null) {
            batteryObserver.onBatteryLevelChanged(batteryLevel);
        }
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
        int oldBatteryLevel = batteryLevel;
        batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        Log.d(LOG_TAG, "Battery level: " + batteryLevel);

        if (batteryObserver != null) {
            batteryObserver.onBatteryLevelChanged(batteryLevel);
        }

        if (batteryLevel > 20) {
            if (showBattery) {
                // When the battery reaches 20% hide the notification
                showBattery = false;

                mServiceUtils.cancelNotification(null, NOTIFICATION_BATTERY);
            }
        }
        else {
            boolean vibrate = false;

            if (oldBatteryLevel > batteryLevel && batteryLevel % 5 == 0) {
                // If the battery is running down, vibrate at 20, 15, 10 and 5
                vibrate = true;
                showBattery = true;
            }

            buildBatteryNotification(vibrate);
        }
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    private void buildBatteryNotification(boolean vibrate) {
        if (!showBattery || batteryLevel == -1) {
            return;
        }

        Bitmap background = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.bg_low_battery);

        // Build pending intent for when the user swipes the card away
        Intent deleteIntent = new Intent(Constants.IA_HIDE_BATTERY);
        PendingIntent deleteAction = PendingIntent.getBroadcast(mContext, 0, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.WearableExtender wearableExtender = new Notification.WearableExtender()
                .setBackground(background)
                .setContentIcon(R.drawable.nic_low_battery)
                .setHintHideIcon(true);

        Notification.Builder builder = new Notification.Builder(mContext)
                .setSmallIcon(R.drawable.nic_low_battery)
                .setDeleteIntent(deleteAction)
                .setContentTitle(mContext.getString(R.string.general_low_battery))
                .setContentText(batteryLevel + "%")
                .extend(wearableExtender)
                .setPriority(Notification.PRIORITY_DEFAULT);

        if (vibrate) {
            builder.setVibrate(SILENT_VIBRATION_PATTERN);
        }

        //notificationManager.cancel(NOTIFICATION_BATTERY);
        mServiceUtils.notify(null, NOTIFICATION_BATTERY, builder.build());
    }


    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            showBattery = false;
        }

    };

}
