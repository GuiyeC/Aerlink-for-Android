package com.codegy.aerlink.reminders;

import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.codegy.aerlink.ALSConstants;
import com.codegy.aerlink.Constants;
import com.codegy.aerlink.connection.Command;
import com.codegy.aerlink.utils.PacketProcessor;
import com.codegy.aerlink.utils.ServiceHandler;
import com.codegy.aerlink.utils.ServiceUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by Guiye on 20/5/15.
 */
public class ReminderServiceHandler extends ServiceHandler {

    public interface RemindersCallback {
        void onRemindersUpdated(String remindersData);
    }

    private static final String LOG_TAG = ReminderServiceHandler.class.getSimpleName();

    private Context mContext;
    private ServiceUtils mServiceUtils;
    private RemindersCallback remindersCallback;

    private PacketProcessor mPacketProcessor;


    public ReminderServiceHandler(Context context, ServiceUtils serviceUtils) {
        this.mContext = context;
        this.mServiceUtils = serviceUtils;
    }


    public void requestDataUpdate() {
        Command reminderCommand = new Command(ALSConstants.SERVICE_UUID, ALSConstants.CHARACTERISTIC_REMINDERS_ACTION, new byte[] {
                (byte) 0x01
        });

        mServiceUtils.addCommandToQueue(reminderCommand);
    }

    public void setReminderCompleted(ReminderItem reminderItem) {
        Command reminderCommand = new Command(ALSConstants.SERVICE_UUID, ALSConstants.CHARACTERISTIC_REMINDERS_ACTION, new byte[] {
                (byte) 0x02,
                (byte) (reminderItem.isCompleted() ? 0x01 : 0x00),
                (byte) (reminderItem.getPosition() & 0xff),
                (byte) ((reminderItem.getPosition() >> 8) & 0xff)
        });

        mServiceUtils.addCommandToQueue(reminderCommand);
    }

    public void setRemindersCallback(RemindersCallback remindersCallback) {
        this.remindersCallback = remindersCallback;
    }


    @Override
    public void reset() {
        mPacketProcessor = null;
    }

    @Override
    public UUID getServiceUUID() {
        return ALSConstants.SERVICE_UUID;
    }

    @Override
    public List<String> getCharacteristicsToSubscribe() {
        List<String> characteristics = new ArrayList<>();

        characteristics.add(ALSConstants.CHARACTERISTIC_REMINDERS_DATA);

        return characteristics;
    }

    @Override
    public boolean canHandleCharacteristic(BluetoothGattCharacteristic characteristic) {
        String characteristicUUID = characteristic.getUuid().toString().toLowerCase();
        return characteristicUUID.equals(ALSConstants.CHARACTERISTIC_REMINDERS_DATA);
    }

    @Override
    public void handleCharacteristic(BluetoothGattCharacteristic characteristic) {
        byte[] packet = characteristic.getValue();

        if (mPacketProcessor == null) {
            mPacketProcessor = new PacketProcessor(packet);

            if (mPacketProcessor.getStatus() != 0x01) {
                mPacketProcessor = null;
            }
        }
        else {
            mPacketProcessor.process(packet);
        }

        if (mPacketProcessor != null && mPacketProcessor.isFinished()) {
            String remindersData = mPacketProcessor.getStringValue();

            if (remindersData != null) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
                sp.edit().putString(Constants.SPK_REMINDERS_DATA, remindersData).apply();

                if (remindersCallback != null) {
                    remindersCallback.onRemindersUpdated(remindersData);
                }
            }

            mPacketProcessor = null;
        }
    }

}
