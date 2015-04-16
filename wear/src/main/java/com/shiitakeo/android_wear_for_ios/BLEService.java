package com.shiitakeo.android_wear_for_ios;

import android.annotation.TargetApi;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.Notification;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.wearable.activity.ConfirmationActivity;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Created by shiitakeo on 15/03/15.
 */
public class BLEService extends Service {

    private static enum EventID {
        NotificationAdded,
        NotificationModified,
        NotificationRemoved
    }

    private static enum CommandID {
        GetNotificationAttributes,
        GetAppAttributes,
        PerformNotificationAction
    }

    private static enum NotificationAttributeID {
        AppIdentifier,
        Title,
        Subtitle,
        Message,
        MessageSize,
        Date,
        PositiveActionLabel,
        NegativeActionLabel
    }

    private static enum ActionID {
        Positive,
        Negative
    }

    private static enum RemoteCommandID {
        Play,
        Pause,
        TogglePlayPause,
        NextTrack,
        PreviousTrack,
        VolumeUp,
        VolumeDown,
        AdvanceRepeatMode,
        AdvanceShuffleMode,
        SkipForward,
        SkipBackward
    }

    // ANCS Profile
    private static final String UUID_ANCS = "7905f431-b5ce-4e99-a40f-4b1e122d00d0";
    private static final String CHARACTERISTIC_NOTIFICATION_SOURCE = "9fbf120d-6301-42d9-8c58-25e699a21dbd";
    private static final String CHARACTERISTIC_DATA_SOURCE =         "22eac6e9-24d6-4bb5-be44-b36ace7c7bfb";
    private static final String CHARACTERISTIC_CONTROL_POINT =       "69d1d8f3-45e1-49a8-9821-9bbdfdaad9d9";

    // AMS - Apple Media Service Profile
    private static final String UUID_AMS = "89D3502B-0F36-433A-8EF4-C502AD55F8DC";
    private static final String CHARACTERISTIC_REMOTE_COMMAND =   "9B3C81D8-57B1-4A8A-B8DF-0E56F7CA51C2";
    private static final String CHARACTERISTIC_ENTITY_UPDATE =    "2F7CABCE-808D-411F-9A0C-BB92BA96C102";
    private static final String CHARACTERISTIC_ENTITY_ATTRIBUTE = "C6B2F38C-23AB-46D8-A6AB-A3A870BBD5D7";



    // Battery Service
    private static final String UUID_BAS = "0000180F-0000-1000-8000-00805f9b34fb";

    private static final String DESCRIPTOR_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    private static final String SERVICE_BLANK = "00001111-0000-1000-8000-00805f9b34fb";

    // intent action
    public static final String INTENT_ACTION_POSITIVE = "com.shiitakeo.INTENT_ACTION_POSITIVE";
    public static final String INTENT_ACTION_NEGATIVE = "com.shiitakeo.INTENT_ACTION_NEGATIVE";
    public static final String INTENT_ACTION_DELETE = "com.shiitakeo.INTENT_ACTION_DELETE";

    private static final long SCREEN_TIME_OUT = 1000;
    private static final long VIBRATION_PATTERN[] = { 200, 100, 200, 100 };
    private static final long SILENT_VIBRATION_PATTERN[] = { 200, 110 };
    
    private static final String TAG_LOG = "BLE_wear";
    public static final String INTENT_EXTRA_UID = "INTENT_EXTRA_UID";

    private static final int API_LEVEL = Build.VERSION.SDK_INT;


    private NotificationManagerCompat notificationManager;
    private int notificationId = 0;
    private PacketProcessor packetProcessor;

    private Vibrator vibrator;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;
    
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private static boolean connected = false;
    private boolean reconnect = false;
    private int skipCount = 0;
    BLEScanCallback scanCallback = new BLEScanCallback();

    private boolean is_subscribed_characteristics = false;

    private BluetoothLeScanner bluetoothLeScanner;

    private BroadcastReceiver messageReceiver = new MessageReceiver();


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("Service", "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(INTENT_ACTION_POSITIVE);
        intentFilter.addAction(INTENT_ACTION_NEGATIVE);
        intentFilter.addAction(INTENT_ACTION_DELETE);
        registerReceiver(messageReceiver, intentFilter);

        notificationManager = NotificationManagerCompat.from(getApplicationContext());

        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        
        if (bluetoothAdapter != null) {
            bluetoothAdapter = null;
        }
        
        if (API_LEVEL >= 21 && bluetoothLeScanner != null) {
            Log.d(TAG_LOG, "status: ble reset");
            stopBLEScanner();
        }
        
        connected = false;
        is_subscribed_characteristics = false;


        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (bluetoothAdapter == null) {
            Log.d(TAG_LOG, "ble adapter is null");
            return super.onStartCommand(intent, flags, startId);
        }

        startBLEScanner();

        return super.onStartCommand(intent, flags, startId);
    }

    @TargetApi(21)
    private void startBLEScanner() {
        if (API_LEVEL >= 21) {
            Log.d(TAG_LOG, "start BLE scan @ lescan");

            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();
            bluetoothLeScanner.startScan(scanFilters(), settings, scanCallback);
        }
        else {
            Log.d(TAG_LOG, "start BLE scan @ BluetoothAdapter");
            bluetoothAdapter.startLeScan(le_scanCallback);
        }
    }

    @TargetApi(21)
    private void stopBLEScanner() {
        if (API_LEVEL >= 21) {
            bluetoothLeScanner.stopScan(scanCallback);
        }
        else {
            bluetoothAdapter.stopLeScan(le_scanCallback);
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG_LOG, "~~~~~~~~ service onDestroy");

        stopBLEScanner();
        
        connected =false;
        is_subscribed_characteristics = false;

        if (null != bluetoothGatt){
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        
        bluetoothAdapter = null;
        
        super.onDestroy();
    }
    
    private Vibrator getVibrator() {
        if (vibrator == null) {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        }
        
        return vibrator;
    }

    private PowerManager getPowerManager() {
        if (powerManager == null) {
            powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        }

        return powerManager;
    }

    private PowerManager.WakeLock getWakeLock() {
        if (wakeLock == null) {
            wakeLock = getPowerManager().newWakeLock((PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP), "iOS_WEAR_TAG");
        }

        return wakeLock;
    }

    public PacketProcessor getPacketProcessor() {
        if (packetProcessor == null) {
            packetProcessor = new PacketProcessor();
        }

        return packetProcessor;
    }

    private void wakeScreen() {
        if (!getWakeLock().isHeld()) {
            Log.d(TAG_LOG, "Waking Screen");
            getWakeLock().acquire(SCREEN_TIME_OUT);
        }
    }

    private void sendCommand(String serviceUUID, String characteristic, byte[] command) throws Exception {
        BluetoothGattService service = bluetoothGatt.getService(UUID.fromString(serviceUUID));

        if (service != null) {
            Log.d(TAG_LOG, "find service @ BR");
            BluetoothGattCharacteristic bluetoothGattCharacteristic = service.getCharacteristic(UUID.fromString(characteristic));

            if (bluetoothGattCharacteristic != null) {
                Log.d(TAG_LOG, "find chara @ BR");
                bluetoothGattCharacteristic.setValue(command);
                bluetoothGatt.writeCharacteristic(bluetoothGattCharacteristic);
            }
            else {
                Log.d(TAG_LOG, "cant find chara @ BR");
            }
        }
        else {
            Log.d(TAG_LOG, "cant find service @ BR");
        }
    }
    
    private void buildNotification(NotificationData notificationData) {
        Bitmap background;
        if (notificationData.getBackground() != -1) {
            background = BitmapFactory.decodeResource(getResources(), notificationData.getBackground());
        }
        else {
            background = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            background.eraseColor(notificationData.getBackgroundColor());
        }

        
        // Build pending intent for when the user swipes the card away
        Intent deleteIntent = new Intent(INTENT_ACTION_DELETE);
        deleteIntent.putExtra(INTENT_EXTRA_UID, notificationData.getUID());
        PendingIntent deleteAction = PendingIntent.getBroadcast(getApplicationContext(), notificationId, deleteIntent, PendingIntent.FLAG_ONE_SHOT);

        NotificationCompat.WearableExtender wearableExtender = new NotificationCompat.WearableExtender()
                .setBackground(background);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getApplicationContext())
                .setContentTitle(notificationData.getTitle())
                .setContentText(notificationData.getMessage())
                .setSmallIcon(notificationData.getAppIcon())
                .setContentInfo("7PM")
                .setSubText("test.hugo2@gmail.com")
                .setGroup(notificationData.getAppId())
                .setDeleteIntent(deleteAction)
                .extend(wearableExtender);
        
        // Build positive action intent only if available
        if (notificationData.getPositiveAction() != null) {
            Intent positiveIntent = new Intent(INTENT_ACTION_POSITIVE);
            positiveIntent.putExtra(INTENT_EXTRA_UID, notificationData.getUID());
            PendingIntent positiveAction = PendingIntent.getBroadcast(getApplicationContext(), notificationId, positiveIntent, PendingIntent.FLAG_ONE_SHOT);

            notificationBuilder.addAction(R.drawable.ic_action_accept, notificationData.getPositiveAction(), positiveAction);
        }
        // Build negative action intent only if available
        if (notificationData.getNegativeAction() != null) {
            Intent negativeIntent = new Intent(INTENT_ACTION_NEGATIVE);
            negativeIntent.putExtra(INTENT_EXTRA_UID, notificationData.getUID());
            PendingIntent negativeAction = PendingIntent.getBroadcast(getApplicationContext(), notificationId, negativeIntent, PendingIntent.FLAG_ONE_SHOT);

            notificationBuilder.addAction(R.drawable.ic_action_remove, notificationData.getNegativeAction(), negativeAction);
        }

        // Build and notify
        Notification notification = notificationBuilder.build();
        notificationManager.notify(notificationData.getUIDString(), 1000, notification);

        notificationId++;

        if (!notificationData.isSilent()) {
            getVibrator().vibrate(VIBRATION_PATTERN, -1);
            wakeScreen();
        }
        else {
            getVibrator().vibrate(SILENT_VIBRATION_PATTERN, -1);
        }
    }

    private void startCall(NotificationData notificationData) {
        Intent phoneIntent = new Intent(this, PhoneActivity.class);
        phoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        phoneIntent.putExtra(INTENT_EXTRA_UID, notificationData.getUID());
        phoneIntent.putExtra(PhoneActivity.EXTRA_TITLE, notificationData.getTitle());
        phoneIntent.putExtra(PhoneActivity.EXTRA_MESSAGE, notificationData.getMessage());

        startActivity(phoneIntent);
    }

    private List<ScanFilter> scanFilters() {
        // can't find ancs service
        return createScanFilter();
    }

    @TargetApi(21)
    private List<ScanFilter> createScanFilter() {
//        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(UUID_ANCS)).build();
        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(SERVICE_BLANK)).build();
        List<ScanFilter> list = new ArrayList<>(1);
        list.add(filter);

        return list;
    }
    

    @TargetApi(21)
    private class BLEScanCallback extends ScanCallback {
        
        @Override
        public void onScanResult(int callbackType, android.bluetooth.le.ScanResult result) {
            Log.i(TAG_LOG, "Scan Result: " + result.toString());
            BluetoothDevice device = result.getDevice();
            if (!connected) {
                Log.d(TAG_LOG, "is connect");
                if (device != null) {
                    Log.d(TAG_LOG, "device ");
                    if (!reconnect && device.getName() != null) {
                        Log.d(TAG_LOG, "getname ");
                        connected = true;
                        bluetoothGatt = result.getDevice().connectGatt(getApplicationContext(), false, bluetoothGattCallback);
                    } 
                    else if (reconnect && skipCount > 5 && device.getName() != null) {
                        Log.d(TAG_LOG, "reconnect:: ");
                        connected = true;
                        reconnect = false;
                        bluetoothGatt = result.getDevice().connectGatt(getApplicationContext(), false, bluetoothGattCallback);
                    } 
                    else {
                        Log.d(TAG_LOG, "skip:: ");
                        skipCount++;
                    }
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.i(TAG_LOG, "Batch Scan Results: " + results.toString());
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.d(TAG_LOG, "Scan Failed: " + errorCode);
        }
        
    }

    private BluetoothAdapter.LeScanCallback le_scanCallback = new BluetoothAdapter.LeScanCallback() {
        
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            Log.i(TAG_LOG, "onLeScan");
            if (!connected) {
                Log.d(TAG_LOG, "is connect");
                if (device != null) {
                    Log.d(TAG_LOG, "device ");
                    if (!reconnect && device.getName() != null) {
                        Log.d(TAG_LOG, "getname ");
                        connected = true;
                        bluetoothGatt = device.connectGatt(getApplicationContext(), false, bluetoothGattCallback);
                    }
                    else if (reconnect && skipCount > 5 && device.getName() != null) {
                        Log.d(TAG_LOG, "reconnect:: ");
                        connected = true;
                        reconnect = false;
                        bluetoothGatt = device.connectGatt(getApplicationContext(), false, bluetoothGattCallback);
                    }
                    else {
                        Log.d(TAG_LOG, "skip:: ");
                        skipCount++;
                    }
                }
            }
        }

    };

    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG_LOG, "onConnectionStateChange: " + status + " -> " + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // success, connect to gatt.
                // find service
                gatt.discoverServices();
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG_LOG, "onDisconnect: ");

                if (API_LEVEL >= 21 && bluetoothLeScanner != null) {
                    Log.d(TAG_LOG, "status: ble reset");
                    stopBLEScanner();
                }

                if (bluetoothGatt != null) {
                    bluetoothGatt.disconnect();
                    bluetoothGatt.close();
                    bluetoothGatt = null;
                }
                if (bluetoothAdapter != null) {
                    bluetoothAdapter = null;
                }

                connected = false;
                is_subscribed_characteristics = false;
                skipCount = 0;


                // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
                // BluetoothAdapter through BluetoothManager.
                final BluetoothManager bluetoothManager =
                        (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                bluetoothAdapter = bluetoothManager.getAdapter();

                // Checks if Bluetooth is supported on the device.
                if (bluetoothAdapter == null) {
                    Log.d(TAG_LOG, "ble adapter is null");
                    return;
                }

                reconnect = true;

                Log.d(TAG_LOG, "start BLE scan");
                startBLEScanner();

                //execute success animation
                Intent intent = new Intent(getApplicationContext(), ConfirmationActivity.class);
                intent.putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.FAILURE_ANIMATION);
                intent.putExtra(ConfirmationActivity.EXTRA_MESSAGE, "airplane mode on -> off, after restart app.");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG_LOG, "onServicesDiscovered received: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(UUID.fromString(UUID_ANCS));
                if (service == null) {
                    Log.d(TAG_LOG, "cant find service");
                }
                else {
                    Log.d(TAG_LOG, "find service");
                    Log.d(TAG_LOG, String.valueOf(bluetoothGatt.getServices()));

                    // subscribe data source characteristic
                    BluetoothGattCharacteristic data_characteristic = service.getCharacteristic(UUID.fromString(CHARACTERISTIC_DATA_SOURCE));

                    if (data_characteristic == null) {
                        Log.d(TAG_LOG, "cant find data source chara");
                    }
                    else {
                        Log.d(TAG_LOG, "find data source chara :: " + data_characteristic.getUuid());
                        Log.d(TAG_LOG, "set notify:: " + data_characteristic.getUuid());
                        bluetoothGatt.setCharacteristicNotification(data_characteristic, true);

                        BluetoothGattDescriptor descriptor = data_characteristic.getDescriptor(UUID.fromString(DESCRIPTOR_CONFIG));

                        if (descriptor == null) {
                            Log.d(TAG_LOG, " ** cant find desc :: " + descriptor.getUuid());
                        }
                        else {
                            Log.d(TAG_LOG, " ** find desc :: " + descriptor.getUuid());
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            bluetoothGatt.writeDescriptor(descriptor);

                            stopBLEScanner();
                        }
                    }
                }


                BluetoothGattService amsService = gatt.getService(UUID.fromString(UUID_AMS));
                if (amsService != null) {
                    BluetoothGattCharacteristic remoteCommandCharacteristic = amsService.getCharacteristic(UUID.fromString(CHARACTERISTIC_REMOTE_COMMAND));

                    if (remoteCommandCharacteristic != null) {
                        bluetoothGatt.setCharacteristicNotification(remoteCommandCharacteristic, true);

                        BluetoothGattDescriptor descriptor = remoteCommandCharacteristic.getDescriptor(UUID.fromString(DESCRIPTOR_CONFIG));

                        if (descriptor != null) {
                            Log.d(TAG_LOG, " ** find desc :: " + descriptor.getUuid());
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            bluetoothGatt.writeDescriptor(descriptor);
                        }
                    }

                    BluetoothGattCharacteristic entityUpdateCharacteristic = amsService.getCharacteristic(UUID.fromString(CHARACTERISTIC_ENTITY_UPDATE));

                    if (entityUpdateCharacteristic != null) {
                        bluetoothGatt.setCharacteristicNotification(entityUpdateCharacteristic, true);

                        BluetoothGattDescriptor descriptor = entityUpdateCharacteristic.getDescriptor(UUID.fromString(DESCRIPTOR_CONFIG));

                        if (descriptor != null) {
                            Log.d(TAG_LOG, " ** find desc :: " + descriptor.getUuid());
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            bluetoothGatt.writeDescriptor(descriptor);
                        }
                    }
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(TAG_LOG, " onDescriptorWrite:: " + status);
            // Notification source
            if (status == BluetoothGatt.GATT_SUCCESS){
                Log.d(TAG_LOG, "status: write success ");
                if (!is_subscribed_characteristics) {
                    //subscribe characteristic notification characteristic
                    BluetoothGattService service = gatt.getService(UUID.fromString(UUID_ANCS));
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(CHARACTERISTIC_NOTIFICATION_SOURCE));
//
                    if (characteristic == null) {
                        Log.d(TAG_LOG, " cant find chara");
                    }
                    else {
                        Log.d(TAG_LOG, " ** find chara :: " + characteristic.getUuid());
                        if (CHARACTERISTIC_NOTIFICATION_SOURCE.equals(characteristic.getUuid().toString())) {
                            Log.d(TAG_LOG, " set notify:: " + characteristic.getUuid());
                            bluetoothGatt.setCharacteristicNotification(characteristic, true);

                            BluetoothGattDescriptor notify_descriptor = characteristic.getDescriptor(
                                    UUID.fromString(DESCRIPTOR_CONFIG));
                            if (descriptor == null) {
                                Log.d(TAG_LOG, " ** not find desc :: " + notify_descriptor.getUuid());
                            }
                            else {
                                Log.d(TAG_LOG, " ** find desc :: " + notify_descriptor.getUuid());
                                notify_descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                bluetoothGatt.writeDescriptor(notify_descriptor);
                                is_subscribed_characteristics = true;
                            }
                        }
                    }

                    BluetoothGattService amsService = gatt.getService(UUID.fromString(UUID_AMS));
                    if (amsService != null) {
                        BluetoothGattCharacteristic remoteCommandCharacteristic = amsService.getCharacteristic(UUID.fromString(CHARACTERISTIC_REMOTE_COMMAND));

                        if (remoteCommandCharacteristic != null) {
                            bluetoothGatt.setCharacteristicNotification(remoteCommandCharacteristic, true);

                            BluetoothGattDescriptor remoteCommandDescriptor = remoteCommandCharacteristic.getDescriptor(UUID.fromString(DESCRIPTOR_CONFIG));

                            if (descriptor != null) {
                                Log.d(TAG_LOG, " ** find desc :: " + remoteCommandDescriptor.getUuid());
                                remoteCommandDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                bluetoothGatt.writeDescriptor(remoteCommandDescriptor);
                            }
                        }

                        BluetoothGattCharacteristic entityUpdateCharacteristic = amsService.getCharacteristic(UUID.fromString(CHARACTERISTIC_ENTITY_UPDATE));

                        if (entityUpdateCharacteristic != null) {
                            bluetoothGatt.setCharacteristicNotification(entityUpdateCharacteristic, true);

                            BluetoothGattDescriptor entityUpdateDescriptor = entityUpdateCharacteristic.getDescriptor(UUID.fromString(DESCRIPTOR_CONFIG));

                            if (descriptor != null) {
                                Log.d(TAG_LOG, " ** find desc :: " + entityUpdateDescriptor.getUuid());
                                entityUpdateDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                bluetoothGatt.writeDescriptor(entityUpdateDescriptor);
                            }
                        }
                    }
                }
                else {
                    //execute success animation
                    Intent intent = new Intent(getApplicationContext(), ConfirmationActivity.class);
                    intent.putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.SUCCESS_ANIMATION);
                    intent.putExtra(ConfirmationActivity.EXTRA_MESSAGE, "success");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
            }
            else if (status == BluetoothGatt.GATT_WRITE_NOT_PERMITTED) {
                Log.d(TAG_LOG, "status: write not permitted");
                //execute not permission animation
                Intent intent = new Intent(getApplicationContext(), ConfirmationActivity.class);
                intent.putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.FAILURE_ANIMATION);
                intent.putExtra(ConfirmationActivity.EXTRA_MESSAGE, "please re-authorization paring");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);

            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG_LOG, "onCharacteristicChanged:: " + characteristic.getUuid().toString());

            // Get notification packet from iOS
            byte[] packet = characteristic.getValue();

            switch (characteristic.getUuid().toString()) {
                case CHARACTERISTIC_ENTITY_UPDATE:
                case CHARACTERISTIC_REMOTE_COMMAND:
                case CHARACTERISTIC_ENTITY_ATTRIBUTE:
                    Log.d(TAG_LOG, "AMS    CHARACTERISTIC_ENTITY_UPDATE::");
                    break;
                case CHARACTERISTIC_DATA_SOURCE:
                    getPacketProcessor().process(packet);

                    if (packetProcessor.hasFinishedProcessing()) {
                        NotificationData notificationData = packetProcessor.getNotificationData();

                        NotificationDataManager.updateData(notificationData);

                        if (notificationData.isIncomingCall()) {
                            startCall(notificationData);
                        }
                        else {
                            buildNotification(notificationData);
                        }
                    }
                    
                    break;
                case CHARACTERISTIC_NOTIFICATION_SOURCE:
                    try {
                        switch (packet[0]) {
                            case (byte) 0x00: // NotificationAdded
                                int eventFlags = packet[1];
                                // boolean silent = (eventFlags & 1) != 0; // EventFlagSilent
                                // boolean important = (eventFlags & 2) != 0; // EventFlagImportant
                                boolean preExisting = (eventFlags & 4) != 0; // EventFlagPreExisting
                                // boolean bit3 = (eventFlags & 8) != 0; // EventFlagPositiveAction
                                // boolean bit4 = (eventFlags & 16) != 0; // EventFlagNegativeAction

                                // Don't show pre existing notifications
                                if (preExisting) {
                                    break;
                                }
                            case (byte) 0x01: // NotificationModified
                                // Prepare packet processor for new notification
                                getPacketProcessor().init(packet);

                                // Request attributes for the new notification
                                byte[] getAttributesCommand = {
                                        (byte) CommandID.GetNotificationAttributes.ordinal(),

                                        // UID
                                        packet[4], packet[5], packet[6], packet[7],

                                        // App Identifier - NotificationAttributeIDAppIdentifier
                                        (byte) NotificationAttributeID.AppIdentifier.ordinal(),

                                        // Title - NotificationAttributeIDTitle
                                        // Followed by a 2-bytes max length parameter
                                        (byte) NotificationAttributeID.Title.ordinal(),
                                        (byte) 0xff,
                                        (byte) 0xff,

                                        // Message - NotificationAttributeIDMessage
                                        // Followed by a 2-bytes max length parameter
                                        (byte) NotificationAttributeID.Message.ordinal(),
                                        (byte) 0xff,
                                        (byte) 0xff,

                                        // Positive Action Label - NotificationAttributeIDPositiveActionLabel
                                        (byte) NotificationAttributeID.PositiveActionLabel.ordinal(),

                                        // Negative Action Label - NotificationAttributeIDNegativeActionLabel
                                        (byte) NotificationAttributeID.NegativeActionLabel.ordinal()
                                };

                                sendCommand(UUID_ANCS, CHARACTERISTIC_CONTROL_POINT, getAttributesCommand);

                                break;
                            case (byte) 0x02: // NotificationRemoved

                                if (packet[2] == 1) {
                                    // Call ended
                                    sendBroadcast(new Intent(PhoneActivity.ACTION_END_CALL));
                                }
                                else {
                                    // Cancel notification in watch
                                    String notificationId = new String(Arrays.copyOfRange(packet, 4, 8));
                                    notificationManager.cancel(notificationId, 1000);
                                }

                                break;
                        }
                    }
                    catch(Exception e){
                        Log.d(TAG_LOG, "error");
                        e.printStackTrace();
                    }

                    break;
            }
        }
    };

    public class MessageReceiver extends BroadcastReceiver {
        
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // perform notification action: immediately
            // delete intent: after 7~8sec.
            if (action.equals(INTENT_ACTION_POSITIVE) | action.equals(INTENT_ACTION_NEGATIVE) | action.equals(INTENT_ACTION_DELETE)) {
                try {
                    byte[] UID = intent.getByteArrayExtra(INTENT_EXTRA_UID);
                    String notificationId = new String(UID);

                    if (!action.equals(INTENT_ACTION_DELETE)) {
                        // Dismiss notification
                        notificationManager.cancel(notificationId, 1000);
                    }
                    
                    byte actionId = (byte) ActionID.Positive.ordinal();
                    if (action.equals(INTENT_ACTION_NEGATIVE) | action.equals(INTENT_ACTION_DELETE)) {
                        actionId = (byte) ActionID.Negative.ordinal();
                    }
                    
                    // Perform user selected action
                    byte[] performActionCommand = {
                            (byte) CommandID.PerformNotificationAction.ordinal(),

                            // Notification UID
                            UID[0], UID[1], UID[2], UID[3],

                            // Action Id
                            actionId
                    };

                    sendCommand(UUID_AMS, CHARACTERISTIC_REMOTE_COMMAND, new byte[] { 0x2 } );
                    //sendCommand(UUID_ANCS, CHARACTERISTIC_CONTROL_POINT, performActionCommand);
                } 
                catch (Exception e) {
                    Log.d(TAG_LOG, "error");
                    e.printStackTrace();
                }
            }
        }
        
    }
}