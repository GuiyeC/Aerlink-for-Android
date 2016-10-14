package com.codegy.aerlink.services.currenttime;

import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.util.Log;
import com.codegy.aerlink.utils.ServiceHandler;
import com.codegy.aerlink.utils.ServiceUtils;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Created by Guiye on 4/9/15.
 */
public class CurrentTimeServiceHandler extends ServiceHandler {

    private static final String LOG_TAG = CurrentTimeServiceHandler.class.getSimpleName();

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

        updateSystemTime();

        Log.d(LOG_TAG, "Current time: " + currentTime.toString());
    }

    private void updateSystemTime() {
        if (currentTime == null) {
            return;
        }

        Calendar correctedTime = (Calendar) currentTime.clone();

        // Add 3 seconds to compensate the delay of the update process
        correctedTime.add(Calendar.SECOND, 3);

        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());

            String command = "date -s " + String.format(Locale.getDefault(), "%d%02d%02d.%02d%02d%02d",
                    correctedTime.get(Calendar.YEAR),
                    correctedTime.get(Calendar.MONTH),
                    correctedTime.get(Calendar.DAY_OF_MONTH),
                    correctedTime.get(Calendar.HOUR_OF_DAY),
                    correctedTime.get(Calendar.MINUTE),
                    correctedTime.get(Calendar.SECOND)
            ) + "\n";

            Log.e("command",command);
            os.writeBytes(command);
            os.flush();
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
        }
        catch (InterruptedException | IOException e) {
            Log.e(LOG_TAG, "Can't update current time");
        }
    }

    public Calendar getCurrentTime() {
        return currentTime;
    }

}
