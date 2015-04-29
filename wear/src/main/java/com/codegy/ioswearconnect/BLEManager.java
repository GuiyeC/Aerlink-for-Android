package com.codegy.ioswearconnect;

import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Created by Guiye on 22/4/15.
 */
public class BLEManager {

    private static final String TAG_LOG = "BLEManager";

    private static final String DESCRIPTOR_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    private static final String SERVICE_BLANK = "00001111-0000-1000-8000-00805f9b34fb";

    public enum BLEManagerState {
        NoBluetooth,
        Disconnected,
        Scanning,
        Connecting,
        Preparing,
        Ready
    }

    public interface BLEManagerCallback {
        void onConnectionStateChange(BLEManagerState state);
        void onIncomingCall(NotificationData notificationData);
        void onCallEnded();
        void onNotificationReceived(NotificationData notificationData);
        void onNotificationCanceled(String notificationId);
        boolean shouldUpdateBatteryLevel();
        void onBatteryLevelChanged(int newBatteryLevel);
        void onMediaDataUpdated(byte[] packet, String attribute);
    }


    private Context mContext;
    private BLEManagerCallback mCallback;

    private PacketProcessor mPacketProcessor;

    private BluetoothLeScanner mScanner;
    private BluetoothGatt mBluetoothGatt;
    private BLEManagerState state = BLEManagerState.Disconnected;

    private boolean mSilentReconnect;

    private List<Command> pendingCommands = new ArrayList<>();
    private List<NotificationData> pendingNotifications = new ArrayList<>();

    private boolean moto360Fix;


    public BLEManager(Context context, BLEManagerCallback callback) {
        this.mContext = context;
        this.mCallback = callback;

        moto360Fix = Build.MODEL.equals("Moto 360");

        startScanner();
    }

    public BLEManagerState getState() {
        return state;
    }

    public void setState(BLEManagerState state) {
        if (state == this.state) {
            return;
        }

        this.state = state;

        if (!mSilentReconnect) {
            // Just reconnecting, don't show the user
            mCallback.onConnectionStateChange(state);
        }
    }


    public void tryConnectingAgain() {
        if (state == BLEManagerState.Disconnected) {
            mScansFailed = 0;
            mConnectionsFailed = 0;
            restartScanner();
        }
    }

    private void restartScanner() {
        reset();
        startScanner();
    }

    private void startScanner() {
        // Check if the scanner is alive already
        if (mScanner != null) {
            return;
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);

        // Checks if Bluetooth is supported on the device.
        if (bluetoothManager != null) {
            cancelScanningTimeoutTask();

            BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

            // If disabled -> enable bluetooth
            if (!bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.enable();
            }


            mScanner = bluetoothAdapter.getBluetoothLeScanner();

            // If bluetooth was disabled, the scanner may take longer
            if (mScanner != null) {
                ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();
                mScanner.startScan(null, settings, mScanCallback);

                Log.d(TAG_LOG, "Scanning started");

                setState(BLEManagerState.Scanning);

                scheduleScanningTimeoutTask();
            }
            else {
                final Handler handler = new Handler(mContext.getMainLooper());
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startScanner();

                        handler.removeCallbacksAndMessages(null);
                    }
                }, 2000);
            }
        }
        else {
            Log.d(TAG_LOG, "Bluetooth not supported");

            setState(BLEManagerState.NoBluetooth);
        }
    }

    private void stopScanner() {
        if (mScanner != null) {
            try {
                mScanner.stopScan(mScanCallback);
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            mScanner = null;

            Log.d(TAG_LOG, "Scanning stopped");
        }
    }

    public void close() {
        Log.d(TAG_LOG, "Close manager");

        reset();
        mNextCommandTask = null;
        mClearOldNotificationsTask = null;
        mMoto360FixTask = null;

        cancelScanningTimeoutTask();
        mScanningTimeoutTask = null;
        cancelConnectingTimeoutTask();
        mConnectingTimeoutTask = null;

        mSilentReconnect = false;

        state = BLEManagerState.Disconnected;
    }

    private void reset() {
        cancelNextCommandTask();
        cancelClearOldNotificationsTask();
        cancelMoto360FixTask();

        try {
            if (mScanner != null) {
                stopScanner();
            }

            if (mBluetoothGatt != null) {
                mBluetoothGatt.disconnect();
                mBluetoothGatt.close();
                mBluetoothGatt = null;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        mPacketProcessor = null;

        pendingCommands.clear();
        pendingNotifications.clear();
    }

    private List<ScanFilter> scanFilters() {
        ScanFilter blankFilter = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(SERVICE_BLANK)).build();
        ScanFilter bleUtilityFilter = new ScanFilter.Builder().setDeviceName("BLE Utility").build();
        List<ScanFilter> scanFilters = new ArrayList<>(2);
        scanFilters.add(blankFilter);
        scanFilters.add(bleUtilityFilter);

        return scanFilters;
    }

    private final ScanCallback mScanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, android.bluetooth.le.ScanResult result) {
            Log.d(TAG_LOG, "Scan Result: " + result.toString());

            BluetoothDevice device = result.getDevice();

            if (state == BLEManagerState.Scanning) {
                if (device != null && device.getName() != null && (device.getName().equals("codegy.BLEConnect") || device.getName().equals("Blank") || device.getName().equals("BLE Utility"))) {
                    cancelScanningTimeoutTask();
                    stopScanner();

                    mBluetoothGatt = device.connectGatt(mContext, false, mBluetoothGattCallback);

                    Log.i(TAG_LOG, "Connecting...: " + device.getName());

                    setState(BLEManagerState.Connecting);

                    scheduleConnectingTimeoutTask();
                }
            }
            else {
                if (state == BLEManagerState.Disconnected) {
                    mSilentReconnect = false;
                }

                cancelScanningTimeoutTask();
                stopScanner();
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

    };

    private final BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG_LOG, "onConnectionStateChange: " + status + " -> " + newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG_LOG, "Connected");

                setState(BLEManagerState.Preparing);

                gatt.discoverServices();
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.e(TAG_LOG, "Disconnected");

                cancelConnectingTimeoutTask();

                if (mBluetoothGatt != null) {
                    mBluetoothGatt.close();
                }

                if (!mSilentReconnect && state == BLEManagerState.Ready) {
                    Log.w(TAG_LOG, "Trying to reconnect");

                    mScansFailed = 0;
                    mSilentReconnect = true;
                    restartScanner();
                }
                else {
                    mSilentReconnect = false;

                    setState(BLEManagerState.Disconnected);
                }
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG_LOG, String.format("BluetoothGatt ReadRssi[%d]", rssi));
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG_LOG, "onServicesDiscovered: " + status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                subscribeCharacteristic(gatt, ServicesConstants.UUID_ANCS, ServicesConstants.CHARACTERISTIC_DATA_SOURCE);

                scheduleConnectingTimeoutTask();
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG_LOG, "Descriptor write successful: " + descriptor.getCharacteristic().getUuid().toString());

                cancelConnectingTimeoutTask();

                switch (descriptor.getCharacteristic().getUuid().toString()) {
                    case ServicesConstants.CHARACTERISTIC_DATA_SOURCE:
                        subscribeCharacteristic(gatt, ServicesConstants.UUID_ANCS, ServicesConstants.CHARACTERISTIC_NOTIFICATION_SOURCE);
                        scheduleConnectingTimeoutTask();
                        break;
                    case ServicesConstants.CHARACTERISTIC_NOTIFICATION_SOURCE:
                        subscribeCharacteristic(gatt, ServicesConstants.UUID_AMS, ServicesConstants.CHARACTERISTIC_REMOTE_COMMAND);
                        scheduleConnectingTimeoutTask();
                        break;
                    case ServicesConstants.CHARACTERISTIC_REMOTE_COMMAND:
                        subscribeCharacteristic(gatt, ServicesConstants.UUID_AMS, ServicesConstants.CHARACTERISTIC_ENTITY_UPDATE);
                        scheduleConnectingTimeoutTask();
                        break;
                    case ServicesConstants.CHARACTERISTIC_ENTITY_UPDATE:
                        subscribeCharacteristic(gatt, ServicesConstants.UUID_BAS, ServicesConstants.CHARACTERISTIC_BATTERY_LEVEL);
                        scheduleConnectingTimeoutTask();
                        break;
                    case ServicesConstants.CHARACTERISTIC_BATTERY_LEVEL:
                        Log.i(TAG_LOG, "Ready");
                        requestMediaUpdates();

                        cancelConnectingTimeoutTask();

                        setState(BLEManagerState.Ready);

                        mScansFailed = 0;
                        mConnectionsFailed = 0;
                        mSilentReconnect = false;
                        mConnectingTimeoutTask = null;
                        mScanningTimeoutTask = null;


                        if (moto360Fix) {
                            Log.i(TAG_LOG, "Scheduling Moto 360 fix");
                            mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);

                            scheduleMoto360FixTask();
                        }

                        break;
                }
            }
            else if (status == BluetoothGatt.GATT_WRITE_NOT_PERMITTED) {
                Log.e(TAG_LOG, "Status: write not permitted");

                cancelConnectingTimeoutTask();

                mScansFailed = 0;
                mConnectionsFailed = 0;
                mSilentReconnect = false;

                unpairDevice(gatt.getDevice());
                gatt.getDevice().createBond();

                // Check if bond is successful
                scheduleConnectingTimeoutTask();
            }
            else {
                subscribeCharacteristic(gatt, descriptor.getCharacteristic().getService().getUuid(), descriptor.getCharacteristic().getUuid().toString());
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            try {
                Command lastCommand = null;
                if (pendingCommands.size() > 0) {
                    lastCommand = pendingCommands.get(0);
                    pendingCommands.remove(0);
                }

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG_LOG, "Characteristic write successful: " + characteristic.getUuid().toString());

                    // If battery is still unknown try to get its value
                    if (mCallback.shouldUpdateBatteryLevel()) {
                        try {
                            gatt.readCharacteristic(gatt.getService(ServicesConstants.UUID_BAS).getCharacteristic(UUID.fromString(ServicesConstants.CHARACTERISTIC_BATTERY_LEVEL)));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    if (moto360Fix && characteristic.getUuid().toString().equals(ServicesConstants.CHARACTERISTIC_ENTITY_ATTRIBUTE)) {
                        try {
                            gatt.readCharacteristic(gatt.getService(ServicesConstants.UUID_AMS).getCharacteristic(UUID.fromString(ServicesConstants.CHARACTERISTIC_ENTITY_ATTRIBUTE)));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }


                    sendNextCommand();
                }
                else {
                    Log.w(TAG_LOG, "Characteristic write error: " + status + " :: " + characteristic.getUuid().toString());

                    if (lastCommand != null && lastCommand.shouldRetryAgain()) {
                        pendingCommands.add(lastCommand);
                    }

                    scheduleNextCommandTask();
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
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
                if (characteristic.getUuid().toString().equals(ServicesConstants.CHARACTERISTIC_BATTERY_LEVEL)) {
                    int batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    Log.d(TAG_LOG, "BAS    CHARACTERISTIC_BATTERY_LEVEL:: " + batteryLevel);
                    mCallback.onBatteryLevelChanged(batteryLevel);
                }
                else if (characteristic.getUuid().toString().equals(ServicesConstants.CHARACTERISTIC_ENTITY_ATTRIBUTE)) {
                    String mediaTitle = characteristic.getStringValue(0);
                    Log.d(TAG_LOG, "AMS    Title:: " + mediaTitle);
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            // Get notification packet from iOS
            byte[] packet = characteristic.getValue();

            switch (characteristic.getUuid().toString().toLowerCase()) {
                case ServicesConstants.CHARACTERISTIC_CURRENT_TIME:
                    Log.d(TAG_LOG, "CTS    CHARACTERISTIC_CURRENT_TIME:: " + characteristic.getStringValue(0));

                    break;
                case ServicesConstants.CHARACTERISTIC_BATTERY_LEVEL:
                    int batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    Log.d(TAG_LOG, "BAS    CHARACTERISTIC_BATTERY_LEVEL:: " + batteryLevel);
                    mCallback.onBatteryLevelChanged(batteryLevel);

                    break;
                case ServicesConstants.CHARACTERISTIC_ENTITY_UPDATE:
                case ServicesConstants.CHARACTERISTIC_ENTITY_ATTRIBUTE:
                    Log.d(TAG_LOG, "AMS    CHARACTERISTIC_ENTITY_UPDATE::");
                    mCallback.onMediaDataUpdated(packet, characteristic.getStringValue(3));
                    break;
                case ServicesConstants.CHARACTERISTIC_DATA_SOURCE:
                    if (mPacketProcessor == null && packet.length >= 5) {
                        byte[] notificationUID = new byte[] { packet[1], packet[2], packet[3], packet[4] };
                        int notificationIndex = -1;

                        for (int i = 0; i < pendingNotifications.size(); i++) {
                            NotificationData notificationData = pendingNotifications.get(i);

                            if (notificationData.compareUID(notificationUID)) {
                                notificationIndex = i;
                                break;
                            }
                        }

                        if (notificationIndex != -1) {
                            mPacketProcessor = new PacketProcessor(pendingNotifications.get(notificationIndex));
                            pendingNotifications.remove(notificationIndex);
                        }
                    }

                    if (mPacketProcessor != null) {
                        // Only remove callback if we are getting useful data
                        cancelClearOldNotificationsTask();

                        mPacketProcessor.process(packet);

                        if (mPacketProcessor.hasFinishedProcessing()) {
                            NotificationData notificationData = mPacketProcessor.getNotificationData();

                            if (notificationData != null) {
                                NotificationDataManager.updateData(notificationData);

                                if (notificationData.isIncomingCall()) {
                                    mCallback.onIncomingCall(notificationData);
                                }
                                else {
                                    mCallback.onNotificationReceived(notificationData);
                                }
                            }

                            mPacketProcessor = null;
                        }
                    }

                    if (pendingNotifications.size() > 0 || mPacketProcessor == null) {
                        // Clear notifications in case data never arrives
                        scheduleClearOldNotificationsTask();
                    }

                    break;
                case ServicesConstants.CHARACTERISTIC_NOTIFICATION_SOURCE:
                    try {
                        switch (packet[0]) {
                            case ServicesConstants.EventIDNotificationAdded:
                            case ServicesConstants.EventIDNotificationModified:
                                // Request attributes for the new notification
                                byte[] getAttributesPacket = new byte[] {
                                        ServicesConstants.CommandIDGetNotificationAttributes,

                                        // UID
                                        packet[4], packet[5], packet[6], packet[7],

                                        // App Identifier - NotificationAttributeIDAppIdentifier
                                        ServicesConstants.NotificationAttributeIDAppIdentifier,

                                        // Title - NotificationAttributeIDTitle
                                        // Followed by a 2-bytes max length parameter
                                        ServicesConstants.NotificationAttributeIDTitle,
                                        (byte) 0xff,
                                        (byte) 0xff,

                                        // Message - NotificationAttributeIDMessage
                                        // Followed by a 2-bytes max length parameter
                                        ServicesConstants.NotificationAttributeIDMessage,
                                        (byte) 0xff,
                                        (byte) 0xff,
                                };


                                NotificationData notificationData = new NotificationData(packet);
                                pendingNotifications.add(notificationData);


                                if (notificationData.hasPositiveAction()) {
                                    getAttributesPacket = PacketProcessor.concat(getAttributesPacket, new byte[] {
                                            // Positive Action Label - NotificationAttributeIDPositiveActionLabel
                                            ServicesConstants.NotificationAttributeIDPositiveActionLabel
                                    });
                                }
                                if (notificationData.hasNegativeAction()) {
                                    getAttributesPacket = PacketProcessor.concat(getAttributesPacket, new byte[] {
                                            // Negative Action Label - NotificationAttributeIDNegativeActionLabel
                                            ServicesConstants.NotificationAttributeIDNegativeActionLabel
                                    });
                                }

                                Command getAttributesCommand = new Command(ServicesConstants.UUID_ANCS, ServicesConstants.CHARACTERISTIC_CONTROL_POINT, getAttributesPacket);

                                pendingCommands.add(getAttributesCommand);
                                sendNextCommand();


                                // Clear notifications in case data never arrives
                                scheduleClearOldNotificationsTask();

                                break;
                            case ServicesConstants.EventIDNotificationRemoved:
                                if (packet[2] == 1) {
                                    // Call ended
                                    mCallback.onCallEnded();
                                }
                                else {
                                    // Cancel notification in watch
                                    String notificationId = new String(Arrays.copyOfRange(packet, 4, 8));
                                    mCallback.onNotificationCanceled(notificationId);
                                }

                                break;
                        }
                    }
                    catch(Exception e) {
                        Log.d(TAG_LOG, "error");
                        e.printStackTrace();
                    }

                    break;
            }
        }

    };

    public void addCommandToQueue(Command command) {
        pendingCommands.add(command);

        sendNextCommand();
    }

    private void sendNextCommand() {
        cancelNextCommandTask();

        if (state == BLEManagerState.Disconnected || pendingCommands.size() == 0) {
            return;
        }

        boolean result = false;
        Command command = pendingCommands.get(0);

        try {
            BluetoothGattService service = mBluetoothGatt.getService(command.getServiceUUID());

            if (service != null) {
                BluetoothGattCharacteristic mBluetoothGattCharacteristic = service.getCharacteristic(UUID.fromString(command.getCharacteristic()));

                if (mBluetoothGattCharacteristic != null) {
                    // not being used
                    // mBluetoothGattCharacteristic.setWriteType(command.getWriteType());

                    result = mBluetoothGattCharacteristic.setValue(command.getPacket());
                    Log.d(TAG_LOG, "Characteristic value set: " + result);

                    result = mBluetoothGatt.writeCharacteristic(mBluetoothGattCharacteristic);
                    Log.d(TAG_LOG, "Started writing command: " + result);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            result = false;
        }

        if (!result) {
            pendingCommands.remove(command);

            if (command.shouldRetryAgain()) {
                pendingCommands.add(command);
            }
        }

        if (pendingCommands.size() > 0) {
            scheduleNextCommandTask();
        }
    }

    private void requestMediaUpdates() {
        try {
            Command trackCommand = new Command(ServicesConstants.UUID_AMS, ServicesConstants.CHARACTERISTIC_ENTITY_UPDATE, new byte[] {
                    ServicesConstants.EntityIDTrack,
                    ServicesConstants.TrackAttributeIDTitle,
                    ServicesConstants.TrackAttributeIDArtist
            });

            pendingCommands.add(trackCommand);

            Command playerCommand = new Command(ServicesConstants.UUID_AMS, ServicesConstants.CHARACTERISTIC_ENTITY_UPDATE, new byte[] {
                    ServicesConstants.EntityIDPlayer,
                    ServicesConstants.PlayerAttributeIDPlaybackInfo
            });

            pendingCommands.add(playerCommand);

            sendNextCommand();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void subscribeCharacteristic(BluetoothGatt mBluetoothGatt, UUID serviceUUID, String characteristicUUIDString) {
        if (mBluetoothGatt == null || serviceUUID == null || characteristicUUIDString == null ) {
            return;
        }

        try {
            BluetoothGattService service = mBluetoothGatt.getService(serviceUUID);

            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(characteristicUUIDString));

                if (characteristic != null) {
                    mBluetoothGatt.setCharacteristicNotification(characteristic, true);

                    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(DESCRIPTOR_CONFIG));

                    if (descriptor != null) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        mBluetoothGatt.writeDescriptor(descriptor);
                    }

                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void unpairDevice(BluetoothDevice device) {
        Log.d(TAG_LOG, "Unpairing...");

        try {
            Method m = device.getClass().getMethod("removeBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void disableBluetooth() {
        try {
            // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
            // BluetoothAdapter through BluetoothManager.
            final BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);

            // Checks if Bluetooth is supported on the device.
            if (bluetoothManager != null) {
                BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

                if (bluetoothAdapter.isEnabled()) {
                    bluetoothAdapter.disable();

                    mDisabledBluetooth = true;

                    Log.d(TAG_LOG, "Disabling bluetooth");
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }


    // TASKS

    // Use so it's not reenabling bluetooth all the time
    private boolean mDisabledBluetooth = true;
    private int mScansFailed = 1;
    private ScheduledTask mScanningTimeoutTask;

    private void scheduleScanningTimeoutTask() {
        if (mScanningTimeoutTask == null) {
            mScanningTimeoutTask = new ScheduledTask(5000, mContext.getMainLooper(), new Runnable() {
                @Override
                public void run() {
                    Log.i(TAG_LOG, "Scanner timed out");

                    stopScanner();

                    mScansFailed++;

                    if (mScansFailed % 2 == 0) {
                        mSilentReconnect = false;
                        setState(BLEManagerState.Disconnected);
                    }
                    else {
                        if (!mDisabledBluetooth) {
                            disableBluetooth();
                        }
                        else {
                            mDisabledBluetooth = false;
                        }

                        startScanner();
                    }
                }
            });
        }
        else {
            mScanningTimeoutTask.cancel();
        }

        mScanningTimeoutTask.schedule();
    }

    private void cancelScanningTimeoutTask() {
        if (mScanningTimeoutTask != null) {
            mScanningTimeoutTask.cancel();
        }
    }


    private int mConnectionsFailed = 0;
    private ScheduledTask mConnectingTimeoutTask;

    private void scheduleConnectingTimeoutTask() {
        if (mConnectingTimeoutTask == null) {
            mConnectingTimeoutTask = new ScheduledTask(4000, mContext.getMainLooper(), new Runnable() {
                @Override
                public void run() {
                    if (state == BLEManagerState.Connecting || state == BLEManagerState.Preparing) {
                        Log.w(TAG_LOG, "Connecting timed out");

                        if (mBluetoothGatt != null && mBluetoothGatt.getDevice() != null) {
                            BluetoothDevice device = mBluetoothGatt.getDevice();
                            int bondState = device.getBondState();

                            // Don't do anything while bonding
                            if (bondState == BluetoothDevice.BOND_BONDING) {
                                Log.w(TAG_LOG, "Waiting for bond...");
                                mConnectionsFailed = 4;
                                scheduleConnectingTimeoutTask();
                            }
                            else {
                                mConnectionsFailed++;

                                // Don't unpair if it's reconnecting silently
                                if (bondState == BluetoothDevice.BOND_NONE || (mConnectionsFailed == 4 && !mSilentReconnect)) {
                                    mSilentReconnect = false;
                                    unpairDevice(device);
                                    device.createBond();

                                    // Check if bond is successful
                                    scheduleConnectingTimeoutTask();
                                }
                                else {
                                    try {
                                        mBluetoothGatt.disconnect();
                                        mBluetoothGatt.close();
                                        mBluetoothGatt = null;
                                    }
                                    catch (Exception e) {
                                        e.printStackTrace();
                                    }

                                    mScansFailed = 0;

                                    if (mConnectionsFailed == 2) {
                                        if (!mDisabledBluetooth) {
                                            disableBluetooth();
                                        }
                                        else {
                                            mDisabledBluetooth = false;
                                        }

                                        restartScanner();
                                    }
                                    else if (mConnectionsFailed == 6) {
                                        Log.w(TAG_LOG, "Giving up on connection");

                                        mConnectionsFailed = 0;
                                        stopScanner();

                                        mSilentReconnect = false;
                                        setState(BLEManagerState.Disconnected);
                                    }
                                    else {
                                        restartScanner();
                                    }
                                }
                            }
                        }
                        else {
                            Log.w(TAG_LOG, "Giving up on connection");

                            mScansFailed = 0;
                            mConnectionsFailed = 0;
                            stopScanner();

                            mSilentReconnect = false;
                            setState(BLEManagerState.Disconnected);
                        }
                    }
                }
            });
        }
        else {
            mConnectingTimeoutTask.cancel();
        }

        mConnectingTimeoutTask.schedule();
    }

    private void cancelConnectingTimeoutTask() {
        if (mConnectingTimeoutTask != null) {
            mConnectingTimeoutTask.cancel();
        }
    }


    private ScheduledTask mNextCommandTask;

    private void scheduleNextCommandTask() {
        if (state == BLEManagerState.Disconnected || pendingCommands.size() == 0) {
            cancelNextCommandTask();
            return;
        }

        long delay = 600 * pendingCommands.get(0).getRetryCount();

        if (mNextCommandTask == null) {
            mNextCommandTask = new ScheduledTask(delay, mContext.getMainLooper(), new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG_LOG, "Sending next command");
                    sendNextCommand();
                }
            });
        }
        else {
            mNextCommandTask.cancel();
            mNextCommandTask.setDelay(delay);
        }

        mNextCommandTask.schedule();
    }

    private void cancelNextCommandTask() {
        if (mNextCommandTask != null) {
            mNextCommandTask.cancel();
        }
    }


    private ScheduledTask mClearOldNotificationsTask;

    private void scheduleClearOldNotificationsTask() {
        if (mClearOldNotificationsTask == null) {
            mClearOldNotificationsTask = new ScheduledTask(700, mContext.getMainLooper(), new Runnable() {
                @Override
                public void run() {
                    Log.i(TAG_LOG, "Clear old notifications");
                    mPacketProcessor = null;
                    pendingNotifications.clear();
                }
            });
        }
        else {
            mClearOldNotificationsTask.cancel();
        }

        mClearOldNotificationsTask.schedule();
    }

    private void cancelClearOldNotificationsTask() {
        if (mClearOldNotificationsTask != null) {
            mClearOldNotificationsTask.cancel();
        }
    }


    private ScheduledTask mMoto360FixTask;

    private void scheduleMoto360FixTask() {
        if (state == BLEManagerState.Disconnected) {
            cancelMoto360FixTask();
            return;
        }

        if (mMoto360FixTask == null) {
            mMoto360FixTask = new ScheduledTask(250000, mContext.getMainLooper(), new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG_LOG, "Trying to keep connection alive");
                    mBluetoothGatt.readRemoteRssi();

                    // This command should have a response from the iOS device
                    Command attributeCommand = new Command(ServicesConstants.UUID_AMS, ServicesConstants.CHARACTERISTIC_ENTITY_ATTRIBUTE, new byte[]{
                            ServicesConstants.EntityIDTrack,
                            ServicesConstants.TrackAttributeIDTitle
                    });

                    addCommandToQueue(attributeCommand);

                    scheduleMoto360FixTask();
                }
            });
        }
        else {
            mMoto360FixTask.cancel();
        }

        mMoto360FixTask.schedule();
    }

    private void cancelMoto360FixTask() {
        if (mMoto360FixTask != null) {
            mMoto360FixTask.cancel();
        }
    }

}
