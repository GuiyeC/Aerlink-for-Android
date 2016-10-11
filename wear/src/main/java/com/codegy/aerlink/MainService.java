package com.codegy.aerlink;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import com.codegy.aerlink.battery.BASConstants;
import com.codegy.aerlink.battery.BatteryServiceHandler;
import com.codegy.aerlink.cameraremote.CameraRemoteServiceHandler;
import com.codegy.aerlink.connection.*;
import com.codegy.aerlink.currenttime.CTSConstants;
import com.codegy.aerlink.currenttime.CurrentTimeServiceHandler;
import com.codegy.aerlink.media.AMSConstants;
import com.codegy.aerlink.media.MediaServiceHandler;
import com.codegy.aerlink.notifications.ANCSConstants;
import com.codegy.aerlink.notifications.NotificationServiceHandler;
import com.codegy.aerlink.reminders.ReminderServiceHandler;
import com.codegy.aerlink.utils.ServiceHandler;
import com.codegy.aerlink.utils.ServiceUtils;

import java.util.*;


public class MainService extends Service implements ServiceUtils, ConnectionHandlerCallback, DiscoveryHelper.DiscoveryCallback {

    private static final String LOG_TAG = MainService.class.getSimpleName();


    private IBinder mBinder = new ServiceBinder();

    private ConnectionState state = ConnectionState.Disconnected;

    private DiscoveryHelper discoveryHelper;
    private ConnectionHelper connectionHelper;
    private ConnectionHandler connectionHandler;
    private NotificationManager notificationManager;

    private boolean colorBackgrounds;

    private Map<Class, ServiceHandler> mServiceHandlers;


    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(LOG_TAG, "-=-=-=-=-=-=-=-=-=  Service created  =-=-=-=-=-=-=-=-=-");

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        colorBackgrounds = sp.getBoolean(Constants.SPK_COLOR_BACKGROUNDS, true);

        start();
    }

    @Override
    public void onDestroy() {
        Log.e(LOG_TAG, "xXxXxXxXxXxXxXxXxX Service destroyed XxXxXxXxXxXxXxXxXx");

        stop();

        getNotificationManager().cancelAll();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.v(LOG_TAG, "onBind");
        return mBinder;
    }


    private void start() {
        Log.v(LOG_TAG, "Starting...");
        // Just in case, try to close everything
        //stop();

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);


        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) || bluetoothManager == null) {
            setState(ConnectionState.NoBluetooth);
            return;
        }

        discoveryHelper = new DiscoveryHelper(this, this, bluetoothManager);
        connectionHandler = new ConnectionHandler(this, this, bluetoothManager.getAdapter());

        setState(ConnectionState.Disconnected);

        mServiceHandlers = new HashMap<>();

        Log.v(LOG_TAG, "Started");
    }

    private void stop() {
        Log.v(LOG_TAG, "Stopping...");

        try {
            if (mServiceHandlers != null) {
                for (ServiceHandler serviceHandler : mServiceHandlers.values()) {
                    serviceHandler.close();
                }

                mServiceHandlers.clear();
                mServiceHandlers = null;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        if (discoveryHelper != null) {
            discoveryHelper.stopDiscovery();
            discoveryHelper = null;
        }

        try {
            if (connectionHandler != null) {
                connectionHandler.close();
                connectionHandler = null;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        Log.v(LOG_TAG, "Stopped");
    }


    private void checkForBondedDevice() {
        Log.i(LOG_TAG, "Checking for previously bonded device");
    }

    public void setState(ConnectionState state) {
        this.state = state;

        switch (state) {
            case NoBluetooth:
                Log.wtf(LOG_TAG, "State: Bluetooth not supported");
                connectionHandler = null;

                stop();

                break;
            case Stopped:
                Log.i(LOG_TAG, "State: Stopped");
                break;
            case Ready:
                Log.i(LOG_TAG, "State: Ready");
                break;
            case Disconnected:
                Log.i(LOG_TAG, "State: Disconnected");

                // discoveryHelper.startDiscovery();

                BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                BluetoothDevice device = BluetoothUtils.getBondedDevice(bluetoothManager.getAdapter());

                if (device == null) {
                    Log.i(LOG_TAG, "Starting discovery");
                    discoveryHelper.startDiscovery();
                }
                else {
                    Log.i(LOG_TAG, "Connecting to previously bonded device");
                    connectionHandler.connectToDevice(device);
                }

                break;
            case Connecting:
                Log.i(LOG_TAG, "State: Connecting");
                break;
        }

        if (state != ConnectionState.Ready) {
            Intent stateIntent = new Intent(Constants.IA_SERVICE_NOT_READY);
            sendBroadcast(stateIntent);
        }
        else {
            Intent stateIntent = new Intent(Constants.IA_SERVICE_READY);
            sendBroadcast(stateIntent);
        }

        if (connectionHelper == null) {
            connectionHelper = new ConnectionHelper(this, this);
        }

        connectionHelper.showHelpForState(state);
    }

    public boolean isConnectionReady() {
        return state == ConnectionState.Ready;
    }

    public ServiceHandler getServiceHandler(Class serviceHandlerClass) {
        ServiceHandler serviceHandler = null;

        if (mServiceHandlers != null) {
            serviceHandler = mServiceHandlers.get(serviceHandlerClass);
        }

        return serviceHandler;
    }


    private NotificationManager getNotificationManager() {
        if (notificationManager == null) {
            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }

        return notificationManager;
    }

    @Override
    public void addCommandToQueue(Command command) {
        if (connectionHandler != null) {
            connectionHandler.addCommandToQueue(command);
        }
    }

    @Override
    public void notify(String tag, int id, Notification notification) {
        getNotificationManager().notify(tag, id, notification);
    }

    @Override
    public void cancelNotification(String tag, int id) {
        getNotificationManager().cancel(tag, id);
    }

    @Override
    public void vibrate(long[] pattern, int repeat) {
    }

    @Override
    public boolean getColorBackgrounds() {
        return colorBackgrounds;
    }

    @Override
    public ConnectionState getState() {
        return state;
    }

    @Override
    public void onConnectionStateChange(ConnectionState state) {
        setState(state);
    }

    @Override
    public void onReadyToSubscribe(BluetoothGatt bluetoothGatt) {
        Log.i(LOG_TAG, "Ready to Subscribe");
        for (ServiceHandler handler : mServiceHandlers.values()) {
            handler.reset();
        }


        BluetoothGattService notificationService = bluetoothGatt.getService(ANCSConstants.SERVICE_UUID);
        if (notificationService != null) {
            Log.i(LOG_TAG, "Notification Service available");
            if (!mServiceHandlers.containsKey(NotificationServiceHandler.class)) {
                mServiceHandlers.put(NotificationServiceHandler.class, new NotificationServiceHandler(this, this));
            }
        }

        BluetoothGattService mediaService = bluetoothGatt.getService(AMSConstants.SERVICE_UUID);
        if (mediaService != null) {
            Log.i(LOG_TAG, "Media Service available");
            if (!mServiceHandlers.containsKey(MediaServiceHandler.class)) {
                mServiceHandlers.put(MediaServiceHandler.class, new MediaServiceHandler(this, this));
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


            connectionHandler.addCommandToQueue(trackCommand);
            connectionHandler.addCommandToQueue(playerCommand);
        }
        BluetoothGattService batteryService = bluetoothGatt.getService(BASConstants.SERVICE_UUID);
        if (batteryService != null) {
            Log.i(LOG_TAG, "Battery Service available");
            if (!mServiceHandlers.containsKey(BatteryServiceHandler.class)) {
                mServiceHandlers.put(BatteryServiceHandler.class, new BatteryServiceHandler(this, this));
            }
        }

        // TODO: Sync time with iPhone

        BluetoothGattService currentTimeService = bluetoothGatt.getService(CTSConstants.SERVICE_UUID);
        if (currentTimeService != null) {
            Log.i(LOG_TAG, "Current Time Service available");
            if (!mServiceHandlers.containsKey(CurrentTimeServiceHandler.class)) {
                mServiceHandlers.put(CurrentTimeServiceHandler.class, new CurrentTimeServiceHandler(this, this));
            }
        }

        BluetoothGattService aerlinkService = bluetoothGatt.getService(ALSConstants.SERVICE_UUID);
        if (aerlinkService != null) {
            Log.i(LOG_TAG, "Aerlink Service available");
            if (!mServiceHandlers.containsKey(ReminderServiceHandler.class)) {
                mServiceHandlers.put(ReminderServiceHandler.class, new ReminderServiceHandler(this, this));
            }
            if (!mServiceHandlers.containsKey(CameraRemoteServiceHandler.class)) {
                mServiceHandlers.put(CameraRemoteServiceHandler.class, new CameraRemoteServiceHandler(this, this));
            }
        }

        List<CharacteristicIdentifier> requests = new ArrayList<>();

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

        connectionHandler.addSubscribeRequests(requests);
    }

    @Override
    public void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        if (mServiceHandlers != null) {
            for (ServiceHandler serviceHandler : mServiceHandlers.values()) {
                if (serviceHandler.canHandleCharacteristic(characteristic)) {
                    serviceHandler.handleCharacteristic(characteristic);
                    break;
                }
            }
        }
    }

    @Override
    public void onDeviceDiscovery(BluetoothDevice device) {
        Log.v(LOG_TAG, "Device discovered");

        connectionHandler.connectToDevice(device);
    }


    public class ServiceBinder extends Binder {
        public MainService getService() {
            return MainService.this;
        }
    }

}
