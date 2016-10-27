package com.codegy.aerlink.services.aerlink.reminders;

import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import com.codegy.aerlink.services.aerlink.ALSConstants;
import com.codegy.aerlink.Constants;
import com.codegy.aerlink.connection.command.Command;
import com.codegy.aerlink.utils.PacketProcessor;
import com.codegy.aerlink.utils.ServiceHandler;
import com.codegy.aerlink.utils.ServiceUtils;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by Guiye on 20/5/15.
 */
public class ReminderServiceHandler extends ServiceHandler {

    public interface CalendarsCallback {
        void onDataTransferStarted();
        void onCalendarsUpdated(List<ReminderCalendar>  calendars);
    }

    public interface RemindersCallback {
        void onDataTransferStarted();
        void onRemindersUpdated(List<ReminderItem> reminders);
    }

    private static final String LOG_TAG = ReminderServiceHandler.class.getSimpleName();

    private Context mContext;
    private ServiceUtils mServiceUtils;
    private CalendarsCallback calendarsCallback;
    private RemindersCallback remindersCallback;

    private PacketProcessor mPacketProcessor;


    public ReminderServiceHandler(Context context, ServiceUtils serviceUtils) {
        this.mContext = context;
        this.mServiceUtils = serviceUtils;
    }


    public void setSelectedCalendar(ReminderCalendar calendar) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        sp.edit().putString(Constants.SPK_REMINDER_SELECTED_CALENDAR, calendar.getIdentifier()).apply();
    }

    public void requestCalendarsUpdate(boolean forceUpdate, Runnable failure) {
        if (!forceUpdate) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
            String cacheData = sp.getString(Constants.SPK_REMINDER_CALENDARS_DATA, null);
            if (cacheData == null) {
                // Force update
                forceUpdate = true;
            }
        }

        byte forceUpdateByte = (byte)(forceUpdate ? 0x01 : 0x00);
        Command reminderCommand = new Command(ALSConstants.SERVICE_UUID, ALSConstants.CHARACTERISTIC_REMINDERS_ACTION, new byte[] {
                (byte) 0x01,
                forceUpdateByte
        });
        reminderCommand.setFailureBlock(failure);

        mServiceUtils.addCommandToQueue(reminderCommand);
    }

    public void requestRemindersUpdate(boolean forceUpdate, Runnable failure) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        String selectedCalendar = sp.getString(Constants.SPK_REMINDER_SELECTED_CALENDAR, null);

        if (selectedCalendar == null) {
            // Error
            return;
        }
        if (!forceUpdate) {
            String cacheData = sp.getString(Constants.SPK_REMINDER_ITEMS_DATA + selectedCalendar, null);
            if (cacheData == null) {
                // Force update
                forceUpdate = true;
            }
        }

        char forceUpdateByte = (char)(forceUpdate ? 0x01 : 0x00);

        String dataString = (char)0x02 + Character.toString(forceUpdateByte) + selectedCalendar;
        Command reminderCommand = new Command(ALSConstants.SERVICE_UUID, ALSConstants.CHARACTERISTIC_REMINDERS_ACTION, dataString.getBytes());
        reminderCommand.setFailureBlock(failure);

        mServiceUtils.addCommandToQueue(reminderCommand);
    }

    public void setReminderCompleted(final ReminderItem reminderItem, Runnable success, Runnable failure) {
        final char completed = (char)(reminderItem.isCompleted() ? 0x01 : 0x00);
        String dataString = (char)0x03 + Character.toString(completed) + reminderItem.getIdentifier();
        Command reminderCommand = new Command(ALSConstants.SERVICE_UUID, ALSConstants.CHARACTERISTIC_REMINDERS_ACTION, dataString.getBytes());
        reminderCommand.setSuccessBlock(success);
        reminderCommand.setFailureBlock(failure);

        mServiceUtils.addCommandToQueue(reminderCommand);
    }

    public void updateCachedData(String calendarIdentifier, ReminderItem item) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        String remindersData = sp.getString(Constants.SPK_REMINDER_ITEMS_DATA + calendarIdentifier, null);
        if (remindersData != null) {
            String identifier = ",\"i\":\"" + item.getIdentifier();
            if (item.isCompleted()) {
                remindersData = remindersData.replace("0"+identifier, "1"+identifier);
            }
            else {
                remindersData = remindersData.replace("1"+identifier, "0"+identifier);
            }

            sp.edit().putString(Constants.SPK_REMINDER_ITEMS_DATA + calendarIdentifier, remindersData).apply();
        }
    }

    public void setRemindersCallback(RemindersCallback remindersCallback) {
        this.remindersCallback = remindersCallback;
    }

    public void setCalendarsCallback(CalendarsCallback calendarsCallback) {
        this.calendarsCallback = calendarsCallback;
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

            if (!mPacketProcessor.isFinished()) {
                switch (mPacketProcessor.getAction()) {
                    case 0x01:
                        if (calendarsCallback != null) {
                            calendarsCallback.onDataTransferStarted();
                        }
                        break;
                    case 0x02:
                        if (remindersCallback != null) {
                            remindersCallback.onDataTransferStarted();
                        }
                        break;
                }
            }
        }
        else {
            mPacketProcessor.process(packet);
        }

        if (mPacketProcessor == null || !mPacketProcessor.isFinished()) {
            return;
        }
        switch (mPacketProcessor.getAction()) {
            case 0x01:
                processCalendarsData(mPacketProcessor);
                break;
            case 0x02:
                processRemindersData(mPacketProcessor);
                break;
        }

        mPacketProcessor = null;
    }

    private void processCalendarsData(PacketProcessor packetProcessor) {
        switch (packetProcessor.getStatus()) {
            case 0x00:
                Log.i(LOG_TAG, "Error with calendar data");
                // Error
                break;
            case 0x01:
                Log.i(LOG_TAG, "Calendar regular update");
                // Regular update
                String calendarsData = packetProcessor.getStringValue();

                if (calendarsData != null) {
                    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
                    sp.edit().putString(Constants.SPK_REMINDER_CALENDARS_DATA, calendarsData).apply();

                    if (calendarsCallback != null) {
                        convertCalendarsData(calendarsData);
                    }
                }
                break;
            case 0x02:
                Log.i(LOG_TAG, "Calendar cache update");
                // No update needed, cache is valid
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
                String cacheData = sp.getString(Constants.SPK_REMINDER_CALENDARS_DATA, null);
                if (cacheData == null) {
                    // Force update
                    Log.i(LOG_TAG, "Calendar cache update failed, forcing update");
                    requestCalendarsUpdate(true, null);
                }
                else if (calendarsCallback != null) {
                    convertCalendarsData(cacheData);
                }
        }
    }

    private void convertCalendarsData(String calendarsData) {
        try {
            JSONArray jsonItems = new JSONArray(calendarsData);

            List<ReminderCalendar> calendars = new ArrayList<>(jsonItems.length());

            for (int i = 0; i < jsonItems.length(); i++) {
                ReminderCalendar calendar = new ReminderCalendar(jsonItems.getJSONObject(i).get("t").toString(), jsonItems.getJSONObject(i).get("i").toString(), jsonItems.getJSONObject(i).get("c").toString());

                calendars.add(calendar);
            }

            if (calendarsCallback != null) {
                calendarsCallback.onCalendarsUpdated(calendars);
            }
        }
        catch (Exception e) {
            e.printStackTrace();

            if (calendarsCallback != null) {
                calendarsCallback.onCalendarsUpdated(null);
            }
        }
    }

    private void processRemindersData(PacketProcessor packetProcessor) {
        switch (packetProcessor.getStatus()) {
            case 0x00:
                // Error
                break;
            case 0x01: {
                // Regular update
                String remindersData = packetProcessor.getStringValue();

                if (remindersData != null) {
                    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
                    String selectedCalendar = sp.getString(Constants.SPK_REMINDER_SELECTED_CALENDAR, null);

                    if (selectedCalendar == null) {
                        // Error
                        return;
                    }

                    sp.edit().putString(Constants.SPK_REMINDER_ITEMS_DATA + selectedCalendar, remindersData).apply();

                    if (remindersCallback != null) {
                        convertRemindersData(remindersData, selectedCalendar);
                    }
                }
                break;
            }
            case 0x02: {
                // No update needed, cache is valid
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
                String selectedCalendar = sp.getString(Constants.SPK_REMINDER_SELECTED_CALENDAR, null);

                if (selectedCalendar == null) {
                    // Error
                    return;
                }

                String cacheData = sp.getString(Constants.SPK_REMINDER_ITEMS_DATA + selectedCalendar, null);
                if (cacheData == null) {
                    // Force update
                    requestRemindersUpdate(true, null);
                }
                else if (remindersCallback != null) {
                    convertRemindersData(cacheData, selectedCalendar);
                }
            }
        }
    }

    private void convertRemindersData(String remindersData, String selectedCalendar) {
        try {
            JSONArray jsonItems = new JSONArray(remindersData);
            String calendarIdentifier = jsonItems.getJSONObject(0).getString("i");
            if (calendarIdentifier.equals(selectedCalendar)) {
                return;
            }

            List<ReminderItem> items = new ArrayList<>(jsonItems.length());

            int uncompleted = 0;
            for (int i = 1; i < jsonItems.length(); i++) {
                ReminderItem item = new ReminderItem(jsonItems.getJSONObject(i).getInt("c"), jsonItems.getJSONObject(i).getString("t"), jsonItems.getJSONObject(i).getString("i"));

                if (item.isCompleted()) {
                    items.add(item);
                }
                else {
                    items.add(uncompleted, item);
                    uncompleted++;
                }
            }

            if (remindersCallback != null) {
                remindersCallback.onRemindersUpdated(items);
            }
        }
        catch (Exception e) {
            e.printStackTrace();

            if (remindersCallback != null) {
                remindersCallback.onRemindersUpdated(null);
            }
        }
    }

}
