package com.codegy.ioswearconnect;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.graphics.*;
import android.media.MediaMetadata;
import android.media.VolumeProvider;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.*;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.wearable.activity.ConfirmationActivity;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Created by codegy on 15/03/15.
 */
public class BLEService extends Service {

    private enum EventID {
        NotificationAdded,
        NotificationModified,
        NotificationRemoved
    }

    private enum CommandID {
        GetNotificationAttributes,
        GetAppAttributes,
        PerformNotificationAction
    }

    private enum NotificationAttributeID {
        AppIdentifier,
        Title,
        Subtitle,
        Message,
        MessageSize,
        Date,
        PositiveActionLabel,
        NegativeActionLabel
    }

    private enum ActionID {
        Positive,
        Negative
    }

    private enum RemoteCommandID {
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

    private enum EntityID {
        Player,
        Queue,
        Track
    }

    private enum TrackAttributeID {
        Artist,
        Album,
        Title,
        Duration
    }

    private enum PlayerAttributeID {
        Name,
        PlaybackInfo,
        Volume
    }


    // ANCS Profile
    private static final UUID UUID_ANCS = UUID.fromString("7905f431-b5ce-4e99-a40f-4b1e122d00d0");
    private static final String CHARACTERISTIC_NOTIFICATION_SOURCE = "9fbf120d-6301-42d9-8c58-25e699a21dbd";
    private static final String CHARACTERISTIC_DATA_SOURCE =         "22eac6e9-24d6-4bb5-be44-b36ace7c7bfb";
    private static final String CHARACTERISTIC_CONTROL_POINT =       "69d1d8f3-45e1-49a8-9821-9bbdfdaad9d9";

    // AMS - Apple Media Service Profile
    private static final UUID UUID_AMS = UUID.fromString("89d3502b-0f36-433a-8ef4-c502ad55f8dc");
    private static final String CHARACTERISTIC_REMOTE_COMMAND =   "9b3c81d8-57b1-4a8a-b8df-0e56f7ca51c2";
    public static final String CHARACTERISTIC_ENTITY_UPDATE =    "2f7cabce-808d-411f-9a0c-bb92ba96c102";
    private static final String CHARACTERISTIC_ENTITY_ATTRIBUTE = "c6b2f38c-23ab-46d8-a6ab-a3a870bbd5d7";

    // Battery Service
    private static final UUID UUID_BAS = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
    private static final String CHARACTERISTIC_BATTERY_LEVEL = "00002a19-0000-1000-8000-00805f9b34fb";

    // Current Time Service
    private static final UUID UUID_CTS = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb");
    private static final String CHARACTERISTIC_CURRENT_TIME = "00002a2b-0000-1000-8000-00805f9b34fb";

    private static final String DESCRIPTOR_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    private static final String SERVICE_BLANK = "00001111-0000-1000-8000-00805f9b34fb";


    public static final int NOTIFICATION_REGULAR = 1000;
    public static final int NOTIFICATION_MEDIA = 1001;
    public static final int NOTIFICATION_BATTERY = 1002;
    public static final int NOTIFICATION_HELP = 2000;

    private static final long SCREEN_TIME_OUT = 1000;
    private static final long VIBRATION_PATTERN[] = { 200, 100, 200, 100 };
    private static final long SILENT_VIBRATION_PATTERN[] = { 200, 110 };
    
    private static final String TAG_LOG = "BLE_wear";
    public static final String INTENT_EXTRA_UID = "INTENT_EXTRA_UID";



    private NotificationManagerCompat notificationManager;
    private int notificationId = 0;
    private PacketProcessor packetProcessor;

    private Vibrator vibrator;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;
    
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private String deviceAddress;
    private static boolean connected = false;
    private boolean reconnect = false;
    private int skipCount = 0;
    BLEScanCallback scanCallback = new BLEScanCallback();

    private List<String> characteristicsSubscribed = new ArrayList<>();
    private List<Command> pendingCommands = new ArrayList<>();
    private boolean writingCommand = false;

    private BluetoothLeScanner bluetoothLeScanner;

    private BroadcastReceiver messageReceiver = new MessageReceiver();

    private MediaSession mSession;
    private boolean mediaPlaying;
    private boolean mediaHidden = true;
    private String mediaTitle;
    private String mediaArtist;

    private int batteryLevel;
    private boolean batteryUpdates;
    private boolean colorBackgrounds;
    private boolean moto360Fix;


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("Service", "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(BLEService.this);
        batteryUpdates = sp.getBoolean(Constants.SPK_BATTERY_UPDATES, true);
        colorBackgrounds = sp.getBoolean(Constants.SPK_COLOR_BACKGROUNDS, false);
        moto360Fix = sp.getBoolean(Constants.SPK_MOTO_360_FIX, false);


        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.IA_POSITIVE);
        intentFilter.addAction(Constants.IA_NEGATIVE);
        intentFilter.addAction(Constants.IA_DELETE);
        intentFilter.addAction(Constants.IA_HIDE_MEDIA);
        intentFilter.addAction(Constants.IA_BATTERY_UPDATES_CHANGED);
        intentFilter.addAction(Constants.IA_COLOR_BACKGROUNDS_CHANGED);
        registerReceiver(messageReceiver, intentFilter);

        notificationManager = NotificationManagerCompat.from(getApplicationContext());

        buildHelpNotification();

        initMediaSessions();

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

    @Override
    public void onDestroy() {
        Log.d(TAG_LOG, "~~~~~~~~ service onDestroy");

        try {
            unregisterReceiver(messageReceiver);

            if (mSession != null) {
                mSession.release();
            }

            reset();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        
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


    private void reset() {
        notificationManager.cancelAll();

        try {
            if (bluetoothLeScanner != null) {
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
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        connected = false;
        reconnect = false;
        skipCount = 0;

        writingCommand = false;
        pendingCommands.clear();
        characteristicsSubscribed.clear();

        mediaPlaying = false;
        mediaHidden = true;
        mediaArtist = null;
        mediaTitle = null;

        batteryLevel = -1;
    }

    private void moto360Fix() {
        if (!connected) {
            // Nothing we can do
            return;
        }


        Thread thread = new Thread() {
            public void run() {
                Looper.prepare();

                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG_LOG, "Trying to keep connection alive");

                        bluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);

                        // This command should have a response from the iOS device
                        Command attributeCommand = new Command(UUID_AMS, CHARACTERISTIC_ENTITY_ATTRIBUTE, new byte[] {
                                (byte) EntityID.Track.ordinal(),
                                (byte) TrackAttributeID.Title.ordinal()
                        });
                        pendingCommands.add(attributeCommand);

                        sendCommand();

                        moto360Fix();

                        handler.removeCallbacks(this);
                        Looper.myLooper().quit();
                    }
                }, 250000);

                Looper.loop();
            }
        };
        thread.start();
    }

    private void startBLEScanner() {
        Log.d(TAG_LOG, "start BLE scan @ lescan");
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();
        bluetoothLeScanner.startScan(scanFilters(), settings, scanCallback);
    }

    private void stopBLEScanner() {
        bluetoothLeScanner.stopScan(scanCallback);
        bluetoothLeScanner = null;
    }

    private void wakeScreen() {
        if (!getWakeLock().isHeld()) {
            Log.d(TAG_LOG, "Waking Screen");
            getWakeLock().acquire(SCREEN_TIME_OUT);
        }
    }

    private void sendCommand() {
        if (!connected || characteristicsSubscribed.size() < 5 || writingCommand || pendingCommands.size() == 0) {
            return;
        }

        Command command = pendingCommands.get(0);

        try {
            BluetoothGattService service = bluetoothGatt.getService(command.getServiceUUID());

            if (service != null) {
                BluetoothGattCharacteristic bluetoothGattCharacteristic = service.getCharacteristic(UUID.fromString(command.getCharacteristic()));

                if (bluetoothGattCharacteristic != null) {
                    bluetoothGattCharacteristic.setValue(command.getPacket());
                    writingCommand = bluetoothGatt.writeCharacteristic(bluetoothGattCharacteristic);
                    Log.d(TAG_LOG, "Started writing command: " + writingCommand);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            writingCommand = false;
        }

        if (!writingCommand) {
            pendingCommands.remove(command);

            if (command.shouldRetryAgain()) {
                pendingCommands.add(command);
            }

            if (pendingCommands.size() > 0) {
                Log.d(TAG_LOG, "Will try again");
                Thread thread = new Thread() {
                    public void run() {
                        Looper.prepare();

                        final Handler handler = new Handler();
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG_LOG, "Trying to send command again");
                                sendCommand();

                                handler.removeCallbacks(this);
                                Looper.myLooper().quit();
                            }
                        }, 500);

                        Looper.loop();
                    }
                };
                thread.start();
            }
        }
    }
    
    private void buildNotification(NotificationData notificationData) {
        Bitmap background;
        if (notificationData.getBackground() != -1) {
            background = BitmapFactory.decodeResource(getResources(), notificationData.getBackground());
        }
        else {
            background = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            if (colorBackgrounds) {
                background.eraseColor(notificationData.getBackgroundColor());
            }
            else {
                background.eraseColor(0);
            }
        }

        
        // Build pending intent for when the user swipes the card away
        Intent deleteIntent = new Intent(Constants.IA_DELETE);
        deleteIntent.putExtra(INTENT_EXTRA_UID, notificationData.getUID());
        PendingIntent deleteAction = PendingIntent.getBroadcast(getApplicationContext(), notificationId, deleteIntent, PendingIntent.FLAG_ONE_SHOT);

        NotificationCompat.WearableExtender wearableExtender = new NotificationCompat.WearableExtender()
                .setBackground(background);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getApplicationContext())
                .setContentTitle(notificationData.getTitle())
                .setContentText(notificationData.getMessage())
                .setSmallIcon(notificationData.getAppIcon())
                .setGroup(notificationData.getAppId())
                .setDeleteIntent(deleteAction)
                .setPriority(Notification.PRIORITY_HIGH)
                .extend(wearableExtender);
        
        // Build positive action intent only if available
        if (notificationData.getPositiveAction() != null) {
            Intent positiveIntent = new Intent(Constants.IA_POSITIVE);
            positiveIntent.putExtra(INTENT_EXTRA_UID, notificationData.getUID());
            PendingIntent positiveAction = PendingIntent.getBroadcast(getApplicationContext(), notificationId, positiveIntent, PendingIntent.FLAG_ONE_SHOT);

            notificationBuilder.addAction(R.drawable.ic_action_accept, notificationData.getPositiveAction(), positiveAction);
        }
        // Build negative action intent only if available
        if (notificationData.getNegativeAction() != null) {
            Intent negativeIntent = new Intent(Constants.IA_NEGATIVE);
            negativeIntent.putExtra(INTENT_EXTRA_UID, notificationData.getUID());
            PendingIntent negativeAction = PendingIntent.getBroadcast(getApplicationContext(), notificationId, negativeIntent, PendingIntent.FLAG_ONE_SHOT);

            notificationBuilder.addAction(R.drawable.ic_action_remove, notificationData.getNegativeAction(), negativeAction);
        }

        // Build and notify
        Notification notification = notificationBuilder.build();
        notificationManager.notify(notificationData.getUIDString(), NOTIFICATION_REGULAR, notification);

        notificationId++;


        if (!notificationData.isPreExisting()) {
            if (!notificationData.isSilent()) {
                getVibrator().vibrate(VIBRATION_PATTERN, -1);
                wakeScreen();
            }
            else {
                getVibrator().vibrate(SILENT_VIBRATION_PATTERN, -1);
            }
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

    private void requestMediaUpdates() {
        try {
            Command trackCommand = new Command(UUID_AMS, CHARACTERISTIC_ENTITY_UPDATE, new byte[] {
                    (byte) EntityID.Track.ordinal(),
                    (byte) TrackAttributeID.Title.ordinal(),
                    (byte) TrackAttributeID.Artist.ordinal()
            });

            pendingCommands.add(trackCommand);

            Command playerCommand = new Command(UUID_AMS, CHARACTERISTIC_ENTITY_UPDATE, new byte[] {
                    (byte) EntityID.Player.ordinal(),
                    (byte) PlayerAttributeID.PlaybackInfo.ordinal()
            });

            pendingCommands.add(playerCommand);

            sendCommand();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<ScanFilter> scanFilters() {
        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(SERVICE_BLANK)).build();
        List<ScanFilter> list = new ArrayList<>(1);
        list.add(filter);

        return list;
    }

    private void processCallBack(BluetoothDevice device) {
        if (!connected) {
            if (device != null) {
                if ((!reconnect || skipCount > 5) && device.getName() != null) {
                    stopBLEScanner();

                    Log.d(TAG_LOG, "Connecting...: " + device.getName());
                    skipCount = 0;
                    connected = true;
                    deviceAddress = device.getAddress();
                    bluetoothGatt = device.connectGatt(getApplicationContext(), false, bluetoothGattCallback);
                }
                else {
                    Log.d(TAG_LOG, "skip:: ");
                    skipCount++;
                }
            }
        }
        else {
            stopBLEScanner();
        }
    }

    private class BLEScanCallback extends ScanCallback {
        
        @Override
        public void onScanResult(int callbackType, android.bluetooth.le.ScanResult result) {
            Log.i(TAG_LOG, "Scan Result: " + result.toString());
            BluetoothDevice device = result.getDevice();
            processCallBack(device);
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


    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG_LOG, "onConnectionStateChange: " + status + " -> " + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // success, connect to gatt.
                // find service
                connected = true;

                gatt.discoverServices();

                if (moto360Fix) {
                    moto360Fix();
                }
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG_LOG, "onDisconnect: ");

                reset();

                buildHelpNotification();


                reconnect = true;

                // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
                // BluetoothAdapter through BluetoothManager.
                final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                bluetoothAdapter = bluetoothManager.getAdapter();

                // Checks if Bluetooth is supported on the device.
                if (bluetoothAdapter == null) {
                    Log.d(TAG_LOG, "ble adapter is null");
                    return;
                }

                Log.d(TAG_LOG, "start BLE scan");
                startBLEScanner();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG_LOG, "onServicesDiscovered received: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                subscribeCharacteristic(gatt.getService(UUID_ANCS), CHARACTERISTIC_DATA_SOURCE);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(TAG_LOG, " onDescriptorWrite:: " + status);
            // Notification source
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG_LOG, "status: write success: " + descriptor.getCharacteristic().getUuid().toString());
                characteristicsSubscribed.add(descriptor.getCharacteristic().getUuid().toString());
                    switch (descriptor.getCharacteristic().getUuid().toString()) {
                        case CHARACTERISTIC_DATA_SOURCE:
                            subscribeCharacteristic(gatt.getService(UUID_ANCS), CHARACTERISTIC_NOTIFICATION_SOURCE);
                            break;
                        case CHARACTERISTIC_NOTIFICATION_SOURCE:
                            subscribeCharacteristic(gatt.getService(UUID_AMS), CHARACTERISTIC_REMOTE_COMMAND);
                            break;
                        case CHARACTERISTIC_REMOTE_COMMAND:
                            subscribeCharacteristic(gatt.getService(UUID_AMS), CHARACTERISTIC_ENTITY_UPDATE);
                            break;
                        case CHARACTERISTIC_ENTITY_UPDATE:
                            subscribeCharacteristic(gatt.getService(UUID_BAS), CHARACTERISTIC_BATTERY_LEVEL);
                            break;
                        case CHARACTERISTIC_BATTERY_LEVEL:
                            subscribeCharacteristic(gatt.getService(UUID_CTS), CHARACTERISTIC_CURRENT_TIME);
                            break;
                        case CHARACTERISTIC_CURRENT_TIME:
                            requestMediaUpdates();

                            buildHelpNotification();

                            break;
                    }
            }
            else if (status == BluetoothGatt.GATT_WRITE_NOT_PERMITTED) {
                Log.d(TAG_LOG, "status: write not permitted");

                unpairDevice(gatt.getDevice());
                gatt.disconnect();
            }
        }

        private void unpairDevice(BluetoothDevice device) {
            Log.d(TAG_LOG, "Unpairing...");
            try {
                Method m = device.getClass().getMethod("removeBond", (Class[]) null);
                m.invoke(device, (Object[]) null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d(TAG_LOG, "onCharacteristicWrite:: " + characteristic.getUuid().toString());

            writingCommand = false;
            Command lastCommand = pendingCommands.get(0);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                // If battery is still unknown try to get its value
                if (batteryUpdates && batteryLevel == -1) {
                    try {
                        gatt.readCharacteristic(gatt.getService(UUID_BAS).getCharacteristic(UUID.fromString(CHARACTERISTIC_BATTERY_LEVEL)));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (moto360Fix && characteristic.getUuid().toString().equals(CHARACTERISTIC_ENTITY_ATTRIBUTE)) {
                    try {
                        gatt.readCharacteristic(gatt.getService(UUID_AMS).getCharacteristic(UUID.fromString(CHARACTERISTIC_ENTITY_ATTRIBUTE)));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                pendingCommands.remove(0);
            }
            else {
                pendingCommands.remove(0);

                if (lastCommand.shouldRetryAgain()) {
                    pendingCommands.add(lastCommand);
                }
            }

            sendCommand();
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.d(TAG_LOG, "onCharacteristicRead status:: " + status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                /*
                String newAttribute = characteristic.getStringValue(0);
                Log.d(TAG_LOG, "onCharacteristicRead value:: " + newAttribute);
                boolean containsOldTitle = newAttribute.indexOf(mediaTitle) == 0;
                boolean containsOldArtist = newAttribute.indexOf(mediaArtist) == 0;

                if (containsOldTitle && !containsOldArtist) {
                    mediaTitle = characteristic.getStringValue(0);

                    updateMetadata();
                }
                else if (!containsOldTitle && containsOldArtist) {
                    mediaArtist = characteristic.getStringValue(0);

                    updateMetadata();
                }
                */
                if (characteristic.getUuid().toString().equals(CHARACTERISTIC_BATTERY_LEVEL)) {
                    batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    Log.d(TAG_LOG, "BAS    CHARACTERISTIC_BATTERY_LEVEL:: " + batteryLevel);
                    buildBatteryNotification();
                }
                else if (characteristic.getUuid().toString().equals(CHARACTERISTIC_ENTITY_ATTRIBUTE)) {
                    String mediaTitle = characteristic.getStringValue(0);
                    Log.d(TAG_LOG, "AMS    Title:: " + mediaTitle);
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG_LOG, "onCharacteristicChanged:: " + characteristic.getUuid().toString());

            // Get notification packet from iOS
            byte[] packet = characteristic.getValue();

            switch (characteristic.getUuid().toString().toLowerCase()) {
                case CHARACTERISTIC_CURRENT_TIME:
                    Log.d(TAG_LOG, "CTS    CHARACTERISTIC_CURRENT_TIME:: " + characteristic.getStringValue(0));

                    break;
                case CHARACTERISTIC_BATTERY_LEVEL:
                    batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    Log.d(TAG_LOG, "BAS    CHARACTERISTIC_BATTERY_LEVEL:: " + batteryLevel);
                    buildBatteryNotification();

                    break;
                case CHARACTERISTIC_ENTITY_UPDATE:
                case CHARACTERISTIC_ENTITY_ATTRIBUTE:
                    Log.d(TAG_LOG, "AMS    CHARACTERISTIC_ENTITY_UPDATE::");
                    processMediaPacket(packet, characteristic.getStringValue(3));
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
                            case (byte) 0x01: // NotificationModified
                                // Prepare packet processor for new notification
                                getPacketProcessor().init(packet);

                                // Request attributes for the new notification
                                byte[] getAttributesPacket = new byte[] {
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
                                };

                                NotificationData notificationData = getPacketProcessor().getNotificationData();

                                if (notificationData.hasPositiveAction()) {
                                    getAttributesPacket = PacketProcessor.concat(getAttributesPacket, new byte[] {
                                            // Positive Action Label - NotificationAttributeIDPositiveActionLabel
                                            (byte) NotificationAttributeID.PositiveActionLabel.ordinal()
                                    });
                                }
                                if (notificationData.hasNegativeAction()) {
                                    getAttributesPacket = PacketProcessor.concat(getAttributesPacket, new byte[] {
                                        // Negative Action Label - NotificationAttributeIDNegativeActionLabel
                                        (byte) NotificationAttributeID.NegativeActionLabel.ordinal()
                                });
                            }

                                Command getAttributesCommand = new Command(UUID_ANCS, CHARACTERISTIC_CONTROL_POINT, getAttributesPacket);
                                pendingCommands.add(getAttributesCommand);

                                sendCommand();

                                break;
                            case (byte) 0x02: // NotificationRemoved

                                if (packet[2] == 1) {
                                    // Call ended
                                    sendBroadcast(new Intent(PhoneActivity.ACTION_END_CALL));
                                }
                                else {
                                    // Cancel notification in watch
                                    String notificationId = new String(Arrays.copyOfRange(packet, 4, 8));
                                    notificationManager.cancel(notificationId, NOTIFICATION_REGULAR);
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

    public void subscribeCharacteristic(BluetoothGattService service, String uuidString) {
        if (service == null || uuidString == null || characteristicsSubscribed.contains(uuidString)) {
            return;
        }

        try {
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(uuidString));

            if (characteristic != null) {
                bluetoothGatt.setCharacteristicNotification(characteristic, true);

                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(DESCRIPTOR_CONFIG));

                if (descriptor != null) {
                    Log.d(TAG_LOG, " ** find desc :: " + descriptor.getUuid());
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    bluetoothGatt.writeDescriptor(descriptor);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class MessageReceiver extends BroadcastReceiver {
        
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // perform notification action: immediately
            // delete intent: after 7~8sec.
            if (action.equals(Constants.IA_POSITIVE) | action.equals(Constants.IA_NEGATIVE) | action.equals(Constants.IA_DELETE)) {
                try {
                    byte[] UID = intent.getByteArrayExtra(INTENT_EXTRA_UID);
                    String notificationId = new String(UID);

                    if (!action.equals(Constants.IA_DELETE)) {
                        // Dismiss notification
                        notificationManager.cancel(notificationId, NOTIFICATION_REGULAR);
                    }
                    
                    byte actionId = (byte) ActionID.Positive.ordinal();
                    if (action.equals(Constants.IA_NEGATIVE) | action.equals(Constants.IA_DELETE)) {
                        actionId = (byte) ActionID.Negative.ordinal();
                    }
                    
                    // Perform user selected action
                    byte[] performActionPacket = {
                            (byte) CommandID.PerformNotificationAction.ordinal(),

                            // Notification UID
                            UID[0], UID[1], UID[2], UID[3],

                            // Action Id
                            actionId
                    };

                    Command performActionCommand = new Command(UUID_ANCS, CHARACTERISTIC_CONTROL_POINT, performActionPacket);
                    pendingCommands.add(performActionCommand);

                    sendCommand();
                } 
                catch (Exception e) {
                    Log.d(TAG_LOG, "error");
                    e.printStackTrace();
                }
            }
            else if (action.equals(Constants.IA_HIDE_MEDIA)) {
                mediaHidden = true;

                if (mediaPlaying) {
                    buildMediaNotification();
                }
            }
            else if (action.equals(Constants.IA_BATTERY_UPDATES_CHANGED)) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(BLEService.this);
                batteryUpdates = sp.getBoolean(Constants.SPK_BATTERY_UPDATES, true);

                if (batteryUpdates) {
                    buildBatteryNotification();
                }
                else {
                    notificationManager.cancel(NOTIFICATION_BATTERY);
                }
            }
            else if (action.equals(Constants.IA_COLOR_BACKGROUNDS_CHANGED)) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(BLEService.this);
                colorBackgrounds = sp.getBoolean(Constants.SPK_COLOR_BACKGROUNDS, false);

                if (!mediaHidden) {
                    buildMediaNotification();
                }
            }
        }
        
    }

    private void buildHelpNotification() {
        Bitmap background = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        background.eraseColor(0);

        Notification.WearableExtender wearableExtender = new Notification.WearableExtender()
                .setBackground(background);

        if (!connected) {
            wearableExtender.addPage(new Notification.Builder(this)
                    .setContentTitle(getString(R.string.help))
                    .setContentText(getString(R.string.help_how_to))
                    .build());
        }

        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(connected ? R.string.help_title_connected : R.string.help_title_searching))
                .setContentText(getString(connected ? R.string.help_subtitle_connected : R.string.help_subtitle_searching))
                .setPriority(Notification.PRIORITY_MAX)
                .setOngoing(!connected)
                .extend(wearableExtender);

        notificationManager.notify(NOTIFICATION_HELP, builder.build());
    }

    private void buildBatteryNotification() {
        if (!batteryUpdates || batteryLevel == -1) {
            return;
        }

        Bitmap background = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        background.eraseColor(0);

        int batteryIcon;

        if (batteryLevel > 85) {
            batteryIcon = R.drawable.ic_battery_5;
        }
        else if (batteryLevel > 65) {
            batteryIcon = R.drawable.ic_battery_4;
        }
        else if (batteryLevel > 45) {
            batteryIcon = R.drawable.ic_battery_3;
        }
        else if (batteryLevel > 25) {
            batteryIcon = R.drawable.ic_battery_2;
        }
        else {
            batteryIcon = R.drawable.ic_battery_1;
        }


        Notification.WearableExtender wearableExtender = new Notification.WearableExtender()
                .setBackground(background);

        Notification.Builder builder = new Notification.Builder( this )
                .setSmallIcon(batteryIcon)
                .setContentTitle(getString(R.string.battery_level))
                .setContentText(batteryLevel + "%")
                .extend(wearableExtender)
                .setPriority(Notification.PRIORITY_MIN);

        notificationManager.notify(NOTIFICATION_BATTERY, builder.build());
    }

    private void buildMediaNotification() {
        mediaHidden = false;

        Bitmap background = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        background.eraseColor(Color.rgb(230, 16, 71));

        // Build pending intent for when the user swipes the card away
        Intent deleteIntent = new Intent(Constants.IA_HIDE_MEDIA);
        PendingIntent deleteAction = PendingIntent.getBroadcast(getApplicationContext(), 0, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.MediaStyle style = new Notification.MediaStyle()
                .setMediaSession(mSession.getSessionToken());

        Notification.WearableExtender wearableExtender = new Notification.WearableExtender()
                .setBackground(background)
                .setHintHideIcon(true);

        Notification.Builder builder = new Notification.Builder( this )
                .setSmallIcon(R.drawable.ic_music)
                 .setDeleteIntent(deleteAction)
                .setStyle(style)
                .extend(wearableExtender)
                .setPriority(Notification.PRIORITY_LOW);

        notificationManager.notify(NOTIFICATION_MEDIA, builder.build());

        boolean result = bluetoothGatt.readCharacteristic(bluetoothGatt.getService(UUID_BAS).getCharacteristic(UUID.fromString(CHARACTERISTIC_BATTERY_LEVEL)));
        Log.d(TAG_LOG, "Read battery: " + result);
    }

    private void initMediaSessions() {
        mSession = new MediaSession(getApplicationContext(), "iOS_Wear_session");
        mSession.setActive(true);
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS | MediaSession.FLAG_HANDLES_MEDIA_BUTTONS);
        mSession.setPlaybackToRemote(new VolumeProvider(VolumeProvider.VOLUME_CONTROL_RELATIVE, 50, 100) {

            @Override
            public void onAdjustVolume(int direction) {
                super.onAdjustVolume(direction);

                try {
                    Command volumeCommand;
                    if (direction == 1) {
                        volumeCommand = new Command(UUID_AMS, CHARACTERISTIC_REMOTE_COMMAND, new byte[] {
                                (byte) RemoteCommandID.VolumeUp.ordinal()
                        });
                    }
                    else {
                        volumeCommand = new Command(UUID_AMS, CHARACTERISTIC_REMOTE_COMMAND, new byte[] {
                                (byte) RemoteCommandID.VolumeDown.ordinal()
                        });
                    }
                    pendingCommands.add(volumeCommand);

                    sendCommand();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        });

        mSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                super.onPlay();
                Log.e(TAG_LOG, "onPlay");

                Command remoteCommand = new Command(UUID_AMS, CHARACTERISTIC_REMOTE_COMMAND, new byte[] {
                        (byte) RemoteCommandID.TogglePlayPause.ordinal()
                });
                pendingCommands.add(remoteCommand);

                sendCommand();
            }

            @Override
            public void onPause() {
                super.onPause();
                Log.e(TAG_LOG, "onPause");

                Command remoteCommand = new Command(UUID_AMS, CHARACTERISTIC_REMOTE_COMMAND, new byte[] {
                        (byte) RemoteCommandID.TogglePlayPause.ordinal()
                });
                pendingCommands.add(remoteCommand);

                sendCommand();
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                Log.e(TAG_LOG, "onSkipToNext");

                Command remoteCommand = new Command(UUID_AMS, CHARACTERISTIC_REMOTE_COMMAND, new byte[] {
                        (byte) RemoteCommandID.NextTrack.ordinal()
                });
                pendingCommands.add(remoteCommand);

                sendCommand();
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                Log.e(TAG_LOG, "onSkipToPrevious");

                Command remoteCommand = new Command(UUID_AMS, CHARACTERISTIC_REMOTE_COMMAND, new byte[] {
                        (byte) RemoteCommandID.PreviousTrack.ordinal()
                });
                pendingCommands.add(remoteCommand);

                sendCommand();
            }

            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                return super.onMediaButtonEvent(mediaButtonIntent);
            }

        });

        long position = PlaybackState.PLAYBACK_POSITION_UNKNOWN;
        PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                .setActions(getAvailableActions());
        stateBuilder.setState(mediaPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, position, 1.0f);
        mSession.setPlaybackState(stateBuilder.build());
    }

    private long getAvailableActions() {
        long actions = PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | PlaybackState.ACTION_SKIP_TO_NEXT;

        /*
        if (mCurrentIndexOnQueue > 0) {
            actions |= PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        }
        if (mCurrentIndexOnQueue < mPlayingQueue.size() - 1) {
            actions |= PlaybackState.ACTION_SKIP_TO_NEXT;
        }
        */

        return actions;
    }

    private void processMediaPacket(byte[] packet, String attribute) {
        try {
            if (packet != null) {
                Log.d(TAG_LOG, "AMS ATTRIBUTE: " + attribute);

                switch (packet[0]) {
                    case 0:
                        switch (packet[1]) {
                            case 1:
                                mediaPlaying = !attribute.substring(0, 1).equals("0");
                                break;
                        }

                        long position = PlaybackState.PLAYBACK_POSITION_UNKNOWN;
                        PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                                .setActions(getAvailableActions());
                        stateBuilder.setState(mediaPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, position, 1.0f);
                        mSession.setPlaybackState(stateBuilder.build());

                        if (mediaPlaying || !mediaHidden) {
                            buildMediaNotification();
                        }
                        break;
                    case 2:
                        boolean truncated = (packet[2] & 1) != 0;

                        switch (packet[1]) {
                            case 0:
                                mediaArtist = attribute;

                                if (truncated) {
                                    mediaArtist += "...";
                                    /*
                                    Command attributeCommand = new Command(UUID_AMS, CHARACTERISTIC_ENTITY_ATTRIBUTE, new byte[] {
                                            (byte) EntityID.Track.ordinal(),
                                            (byte) TrackAttributeID.Artist.ordinal()
                                    });
                                    pendingCommands.add(attributeCommand);

                                    sendCommand();
                                    */
                                }
                                break;
                            case 2:
                                mediaTitle = attribute;

                                if (truncated) {
                                    mediaTitle += "...";
                                    /*
                                    Command attributeCommand = new Command(UUID_AMS, CHARACTERISTIC_ENTITY_ATTRIBUTE, new byte[] {
                                            (byte) EntityID.Track.ordinal(),
                                            (byte) TrackAttributeID.Title.ordinal()
                                    });
                                    pendingCommands.add(attributeCommand);

                                    sendCommand();
                                    */
                                }
                                break;
                        }

                        updateMetadata();
                        break;
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateMetadata() {
        MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder();

        if (mediaTitle == null && mediaArtist == null) {
            metadataBuilder.putString(MediaMetadata.METADATA_KEY_ARTIST, "No info");
        }
        else {
            // And at minimum the title and artist for legacy support
            metadataBuilder.putString(MediaMetadata.METADATA_KEY_TITLE, mediaTitle);
            metadataBuilder.putString(MediaMetadata.METADATA_KEY_ARTIST, mediaArtist);
        }

        // Add any other fields you have for your data as well
        mSession.setMetadata(metadataBuilder.build());

        if (mediaPlaying || !mediaHidden) {
            buildMediaNotification();
        }
    }
}