package com.codegy.aerlink.connection;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;
import com.codegy.aerlink.services.aerlink.ALSConstants;
import com.codegy.aerlink.services.aerlink.UtilsServiceHandler;
import com.codegy.aerlink.services.battery.BASConstants;
import com.codegy.aerlink.services.battery.BatteryServiceHandler;
import com.codegy.aerlink.services.aerlink.cameraremote.CameraRemoteServiceHandler;
import com.codegy.aerlink.connection.characteristic.CharacteristicIdentifier;
import com.codegy.aerlink.connection.command.Command;
import com.codegy.aerlink.services.currenttime.CTSConstants;
import com.codegy.aerlink.services.currenttime.CurrentTimeServiceHandler;
import com.codegy.aerlink.services.media.AMSConstants;
import com.codegy.aerlink.services.media.MediaServiceHandler;
import com.codegy.aerlink.services.notifications.ANCSConstants;
import com.codegy.aerlink.services.notifications.NotificationServiceHandler;
import com.codegy.aerlink.services.aerlink.reminders.ReminderServiceHandler;
import com.codegy.aerlink.utils.ServiceHandler;
import com.codegy.aerlink.utils.ServiceUtils;

import java.util.*;

/**
 * Created by Guiye on 25/8/16.
 */
public class ServiceHandlerManager {

    private static final String LOG_TAG = ServiceHandlerManager.class.getSimpleName();

    private Context mContext;
    private ServiceUtils mServiceUtils;
    private Map<Class, ServiceHandler> mServiceHandlers;
    private boolean aerlinkAvailable = false;


    public ServiceHandlerManager(Context context, ServiceUtils serviceUtils) {
        this.mContext = context;
        this.mServiceUtils = serviceUtils;

        mServiceHandlers = new HashMap<>(6);
    }


    public ServiceHandler getServiceHandler(Class serviceHandlerClass) {
        return mServiceHandlers.get(serviceHandlerClass);
    }

    public void close() {
        for (ServiceHandler serviceHandler : mServiceHandlers.values()) {
            serviceHandler.close();
        }
    }

    public boolean isAerlinkAvailable() {
        return aerlinkAvailable;
    }

    public void setAerlinkAvailable(boolean aerlinkAvailable) {
        this.aerlinkAvailable = aerlinkAvailable;
    }

    public Queue<CharacteristicIdentifier> subscribeToServices(BluetoothGatt bluetoothGatt) {
        for (ServiceHandler handler : mServiceHandlers.values()) {
            handler.reset();
        }


        BluetoothGattService notificationService = bluetoothGatt.getService(ANCSConstants.SERVICE_UUID);
        if (notificationService != null) {
            Log.i(LOG_TAG, "Notification Service available");

            if (!mServiceHandlers.containsKey(NotificationServiceHandler.class)) {
                mServiceHandlers.put(NotificationServiceHandler.class, new NotificationServiceHandler(mContext, mServiceUtils));
            }
        }
        else {
            Log.e(LOG_TAG, "Notification Service unavailable");

            mServiceHandlers.remove(NotificationServiceHandler.class);
        }

        BluetoothGattService mediaService = bluetoothGatt.getService(AMSConstants.SERVICE_UUID);
        if (mediaService != null) {
            Log.i(LOG_TAG, "Media Service available");

            if (!mServiceHandlers.containsKey(MediaServiceHandler.class)) {
                mServiceHandlers.put(MediaServiceHandler.class, new MediaServiceHandler(mContext, mServiceUtils));
            }

            Command trackCommand = new Command(AMSConstants.SERVICE_UUID, AMSConstants.CHARACTERISTIC_ENTITY_UPDATE, new byte[] {
                    AMSConstants.EntityIDTrack,
                    AMSConstants.TrackAttributeIDTitle,
                    AMSConstants.TrackAttributeIDArtist
            });
            trackCommand.setImportance(Command.IMPORTANCE_MAX);

            Command playerCommand = new Command(AMSConstants.SERVICE_UUID, AMSConstants.CHARACTERISTIC_ENTITY_UPDATE, new byte[] {
                    AMSConstants.EntityIDPlayer,
                    AMSConstants.PlayerAttributeIDPlaybackInfo
            });
            playerCommand.setImportance(Command.IMPORTANCE_MAX);


            mServiceUtils.addCommandToQueue(trackCommand);
            mServiceUtils.addCommandToQueue(playerCommand);
        }
        else {
            Log.e(LOG_TAG, "Media Service unavailable");

            mServiceHandlers.remove(MediaServiceHandler.class);
        }

        BluetoothGattService batteryService = bluetoothGatt.getService(BASConstants.SERVICE_UUID);
        if (batteryService != null) {
            Log.i(LOG_TAG, "Battery Service available");

            if (!mServiceHandlers.containsKey(BatteryServiceHandler.class)) {
                mServiceHandlers.put(BatteryServiceHandler.class, new BatteryServiceHandler(mContext, mServiceUtils));
            }

            Command batteryCommand = new Command(BASConstants.SERVICE_UUID, BASConstants.CHARACTERISTIC_BATTERY_LEVEL);
            mServiceUtils.addCommandToQueue(batteryCommand);
        }
        else {
            Log.e(LOG_TAG, "Battery Service unavailable");

            mServiceHandlers.remove(CurrentTimeServiceHandler.class);
        }

        // TODO: Sync time with iPhone

        BluetoothGattService currentTimeService = bluetoothGatt.getService(CTSConstants.SERVICE_UUID);
        if (currentTimeService != null) {
            Log.i(LOG_TAG, "Current Time Service available");

            if (!mServiceHandlers.containsKey(CurrentTimeServiceHandler.class)) {
                mServiceHandlers.put(CurrentTimeServiceHandler.class, new CurrentTimeServiceHandler(mContext, mServiceUtils));
            }

            Command timeCommand = new Command(CTSConstants.SERVICE_UUID, CTSConstants.CHARACTERISTIC_CURRENT_TIME);
            mServiceUtils.addCommandToQueue(timeCommand);
        }
        else {
            Log.e(LOG_TAG, "Current Time Service unavailable");

            mServiceHandlers.remove(CurrentTimeServiceHandler.class);
        }

        BluetoothGattService aerlinkService = bluetoothGatt.getService(ALSConstants.SERVICE_UUID);
        if (aerlinkService != null && aerlinkService.getCharacteristics().size() >= 6) {
            Log.i(LOG_TAG, "Aerlink Service available");
            aerlinkAvailable = true;

            if (!mServiceHandlers.containsKey(ReminderServiceHandler.class)) {
                mServiceHandlers.put(ReminderServiceHandler.class, new ReminderServiceHandler(mContext, mServiceUtils));
            }
            if (!mServiceHandlers.containsKey(CameraRemoteServiceHandler.class)) {
                mServiceHandlers.put(CameraRemoteServiceHandler.class, new CameraRemoteServiceHandler(mContext, mServiceUtils));
            }
            if (!mServiceHandlers.containsKey(UtilsServiceHandler.class)) {
                mServiceHandlers.put(UtilsServiceHandler.class, new UtilsServiceHandler(mContext, mServiceUtils));
            }
        }
        else {
            Log.e(LOG_TAG, "Aerlink Service unavailable");
            aerlinkAvailable = false;

            mServiceHandlers.remove(ReminderServiceHandler.class);
            mServiceHandlers.remove(CameraRemoteServiceHandler.class);
            mServiceHandlers.remove(UtilsServiceHandler.class);
        }

        Queue<CharacteristicIdentifier> requests = new LinkedList<>();

        for (ServiceHandler serviceHandler : mServiceHandlers.values()) {
            UUID serviceUUID = serviceHandler.getServiceUUID();
            List<String> characteristics = serviceHandler.getCharacteristicsToSubscribe();

            if (serviceUUID != null && characteristics != null) {
                Log.i(LOG_TAG, "Adding characteristics: " + serviceUUID.toString());

                for (String characteristic : characteristics) {
                    requests.add(new CharacteristicIdentifier(serviceUUID, characteristic));
                }
            }
        }

        return requests;
    }

    public void handleCharacteristic(BluetoothGattCharacteristic characteristic) {
        for (ServiceHandler serviceHandler : mServiceHandlers.values()) {
            if (serviceHandler.canHandleCharacteristic(characteristic)) {
                serviceHandler.handleCharacteristic(characteristic);
                break;
            }
        }
    }

}
