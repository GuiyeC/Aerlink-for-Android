package com.codegy.aerlink.currenttime;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.Log;
import com.codegy.aerlink.Constants;
import com.codegy.aerlink.R;
import com.codegy.aerlink.utils.ServiceHandler;
import com.codegy.aerlink.utils.ServiceUtils;

import java.util.*;

/**
 * Created by Guiye on 4/9/15.
 */
public class CurrentTimeServiceHandler extends ServiceHandler {

    private static final String LOG_TAG = CurrentTimeServiceHandler.class.getSimpleName();

    public static final int NOTIFICATION_CURRENT_TIME = 1003;

    private Context mContext;
    private ServiceUtils mServiceUtils;

    private Calendar currentTime;


    public CurrentTimeServiceHandler(Context context, ServiceUtils serviceUtils) {
        this.mContext = context;
        this.mServiceUtils = serviceUtils;
    }


    @Override
    public void close() {
        currentTime = null;
    }

    @Override
    public UUID getServiceUUID() {
        return CTSConstants.SERVICE_UUID;
    }

    @Override
    public List<String> getCharacteristicsToSubscribe() {
        List<String> characteristics = new ArrayList<>();

        characteristics.add(CTSConstants.CHARACTERISTIC_CURRENT_TIME);

        return characteristics;
    }

    @Override
    public boolean canHandleCharacteristic(BluetoothGattCharacteristic characteristic) {
        String characteristicUUID = characteristic.getUuid().toString().toLowerCase();
        return characteristicUUID.equals(CTSConstants.CHARACTERISTIC_CURRENT_TIME);
    }

    @Override
    public void handleCharacteristic(BluetoothGattCharacteristic characteristic) {
        int year = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
        int month = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 2);
        int day = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 3);
        int hours = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 4);
        int minutes = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 5);
        int seconds = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 6);

        if (currentTime == null) {
            currentTime = Calendar.getInstance();
        }
        currentTime.set(year, month, day, hours, minutes, seconds);


        try {
            AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
            am.setTime(currentTime.getTimeInMillis());
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        Log.d(LOG_TAG, "Current time: " + currentTime.toString());

       // buildCurrentTimeNotification();
    }

    public Calendar getCurrentTime() {
        return currentTime;
    }

    private void buildCurrentTimeNotification() {
        if (currentTime == null) {
            return;
        }

        Bitmap background = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        background.eraseColor(0);


        // Build pending intent for when the user swipes the card away
        Intent deleteIntent = new Intent(Constants.IA_HIDE_BATTERY);
        PendingIntent deleteAction = PendingIntent.getBroadcast(mContext, 0, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.WearableExtender wearableExtender = new Notification.WearableExtender()
                .setBackground(background);

        Notification.Builder builder = new Notification.Builder(mContext)
                .setSmallIcon(R.drawable.nic_notification)
                .setDeleteIntent(deleteAction)
                .setContentTitle("Time")
                .setContentText(String.format("%d:%02d", currentTime.get(Calendar.HOUR), currentTime.get(Calendar.MINUTE)))
                .extend(wearableExtender)
                .setPriority(Notification.PRIORITY_MIN);

        //notificationManager.cancel(NOTIFICATION_BATTERY);
        mServiceUtils.notify(null, NOTIFICATION_CURRENT_TIME, builder.build());
    }

}
