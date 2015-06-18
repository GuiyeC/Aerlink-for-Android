package com.codegy.aerlink.connection;

import android.bluetooth.*;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import com.codegy.aerlink.utils.ScheduledTask;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by Guiye on 18/5/15.
 */
public class ConnectionHandler {

    private static final String LOG_TAG = ConnectionHandler.class.getSimpleName();

    private static final String DESCRIPTOR_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";


    public enum ConnectionState {
        NoBluetooth,
        Disconnected,
        Scanning,
        Connecting,
        Pairing,
        Preparing,
        Ready
    }
    

    private Context mContext;
    private ConnectionHandlerCallback mCallback;

    private BluetoothLeScanner mScanner;
    private BluetoothGatt mBluetoothGatt;
    private ConnectionState state;

    private String mDeviceMac;
    private boolean mSilentReconnect = false;
    // Use so it's not reenabling bluetooth all the time
    private boolean mDisabledBluetooth = false;
    private int mScansFailed = 0;
    private int mBondsFailed = 0;
    private int mConnectionsFailed = 0;

    private Command mCurrentCommand;
    private List<Command> pendingCommands = new ArrayList<>();
    private List<SubscribeRequest> subscribeRequests;


    public ConnectionHandler(Context context, ConnectionHandlerCallback callback) {
        this.mContext = context;
        this.mCallback = callback;

        startScanner();
    }

    public ConnectionState getState() {
        return state;
    }

    public void setState(ConnectionState state) {
        if (state == this.state) {
            return;
        }

        this.state = state;

        if (!mSilentReconnect) {
            // Just reconnecting, don't show the user
            mCallback.onConnectionStateChange(state);
        }
    }


    private void restartScanner() {
        reset();
        startScanner();
    }

    private void startScanner() {
        // Check if the scanner is alive already
        if (state == ConnectionState.Disconnected || mScanner != null) {
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
                mDisabledBluetooth = true;
                bluetoothAdapter.enable();
            }


            mScanner = bluetoothAdapter.getBluetoothLeScanner();

            // If bluetooth was disabled, the scanner may take longer
            if (mScanner != null) {
                ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();
                mScanner.startScan(null, settings, mScanCallback);

                Log.d(LOG_TAG, "Scanning started");

                setState(ConnectionState.Scanning);

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
            Log.d(LOG_TAG, "Bluetooth not supported");

            setState(ConnectionState.NoBluetooth);
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

            Log.d(LOG_TAG, "Scanning stopped");
        }
    }

    public void close() {
        if (mCallback == null && state == ConnectionState.Disconnected) {
            return;
        }

        Log.d(LOG_TAG, "Close");

        reset();

        mNextCommandTask = null;
        mScanningTimeoutTask = null;
        mConnectingTimeoutTask = null;

        mSilentReconnect = false;

        setState(ConnectionState.Disconnected);

        mCallback = null;
    }

    private void reset() {
        state = null;

        cancelScanningTimeoutTask();
        cancelConnectingTimeoutTask();
        cancelNextCommandTask();

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

            mBluetoothGatt = null;
        }

        mCurrentCommand = null;
        pendingCommands.clear();
    }

    private final ScanCallback mScanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, android.bluetooth.le.ScanResult result) {
            Log.d(LOG_TAG, "Scan Result: " + result.toString());

            if (state == ConnectionState.Scanning) {
                BluetoothDevice device = result.getDevice();
                String deviceName = device != null ? device.getName() : null;

                if (deviceName != null && ((mDeviceMac != null && device.getAddress().equals(mDeviceMac)) || deviceName.equals("Aerlink") || deviceName.equals("BLE Utility") || deviceName.equals("Blank"))) {
                    cancelScanningTimeoutTask();
                    stopScanner();

                    mBluetoothGatt = device.connectGatt(mContext, false, mBluetoothGattCallback);

                    Log.i(LOG_TAG, "Connecting...: " + device.getName());
                    setState(ConnectionState.Connecting);

                    scheduleConnectingTimeoutTask();
                }
            }
            else {
                cancelScanningTimeoutTask();
                stopScanner();
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.i(LOG_TAG, "Batch Scan Results: " + results.toString());
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.d(LOG_TAG, "Scan Failed: " + errorCode);
        }

    };

    private final BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(LOG_TAG, "onConnectionStateChange: " + status + " -> " + newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(LOG_TAG, "Connected");

                setState(ConnectionState.Preparing);

                gatt.discoverServices();

                scheduleConnectingTimeoutTask();
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.e(LOG_TAG, "Disconnected");

                if (!mSilentReconnect && state == ConnectionState.Ready) {
                    Log.w(LOG_TAG, "Trying to reconnect");

                    mScansFailed = 0;
                    mSilentReconnect = true;
                    restartScanner();
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(LOG_TAG, "onServicesDiscovered: " + status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                scheduleConnectingTimeoutTask();

                mCallback.onReadyToSubscribe(gatt);
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            Log.i(LOG_TAG, "MTU Changed: " + mtu);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            cancelConnectingTimeoutTask();

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(LOG_TAG, "Descriptor write successful: " + descriptor.getCharacteristic().getUuid().toString());

                subscribeNextRequest();
            }
            else {
                Log.e(LOG_TAG, "Status: write not permitted");

                mScansFailed = 0;
                mConnectionsFailed = 0;
                mSilentReconnect = false;

                unpairDevice(gatt.getDevice());
                gatt.getDevice().createBond();

                // Check if bond is successful
                scheduleConnectingTimeoutTask();
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            try {
                Command lastCommand = new Command(characteristic.getService().getUuid(), characteristic.getUuid().toString(), characteristic.getValue());
                boolean found = false;

                for (Command command : pendingCommands) {
                    if (command.equals(lastCommand)) {
                        lastCommand = command;
                        found = true;
                        break;
                    }
                }

                /*
                if (!found && lastCommand.getServiceUUID().equals(mCurrentCommand.getServiceUUID()) && lastCommand.getCharacteristic().equals(mCurrentCommand.getCharacteristic())) {
                    found = true;
                    lastCommand = mCurrentCommand;
                    mCurrentCommand = null;
                }
                */

                if (found) {
                    pendingCommands.remove(lastCommand);
                }

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(LOG_TAG, "Characteristic write successful: " + characteristic.getUuid().toString());
                    Log.d(LOG_TAG, "Characteristic value: " + new String(characteristic.getValue()));

                    /*
                    if (characteristic.getUuid().toString().equals(ServicesConstants.CHARACTERISTIC_ENTITY_ATTRIBUTE)) {
                        try {
                            if (lastCommand != null && lastCommand.getPacket() != null) {
                                mRequestingMediaAttribute = lastCommand.getPacket()[1];
                                gatt.readCharacteristic(gatt.getService(ServicesConstants.UUID_AMS).getCharacteristic(UUID.fromString(ServicesConstants.CHARACTERISTIC_ENTITY_ATTRIBUTE)));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    */

                    sendNextCommand();
                }
                else {
                    Log.w(LOG_TAG, "Characteristic write error: " + status + " :: " + characteristic.getUuid().toString());
                    Log.d(LOG_TAG, "Characteristic value: " + new String(characteristic.getValue()));

                    if (found && lastCommand.shouldRetryAgain()) {
                        pendingCommands.add(lastCommand);
                    }

                    scheduleNextCommandTask();
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        /*
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.d(LOG_TAG, "onCharacteristicRead status:: " + status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.getUuid().toString().equals(ServicesConstants.CHARACTERISTIC_BATTERY_LEVEL)) {
                    int batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    Log.d(LOG_TAG, "BAS    CHARACTERISTIC_BATTERY_LEVEL:: " + batteryLevel);
                    mCallback.onBatteryLevelChanged(batteryLevel);
                }
                else if (characteristic.getUuid().toString().equals(ServicesConstants.CHARACTERISTIC_ENTITY_ATTRIBUTE)) {
                    String mediaAttribute = characteristic.getStringValue(0);
                    Log.d(LOG_TAG, "AMS    Attribute:: " + mediaAttribute);

                    if (mRequestingMediaAttribute == ServicesConstants.TrackAttributeIDArtist) {
                        mCallback.onMediaArtistUpdated(mediaAttribute);
                    }
                    else if (mRequestingMediaAttribute == ServicesConstants.TrackAttributeIDTitle) {
                        mCallback.onMediaTitleUpdated(mediaAttribute);
                    }

                    mRequestingMediaAttribute = -1;
                }
                else {
                    String value = characteristic.getStringValue(0);
                    Log.d(LOG_TAG, "READ    Value:: " + value);
                }
            }
        }
        */

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(LOG_TAG, "onCharacteristicChanged: " + characteristic.getUuid().toString());
            
            mCallback.onCharacteristicChanged(characteristic);
        }
    };

    public void addSubscribeRequests(List<SubscribeRequest> requests) {
        if (requests != null && requests.size() > 0) {
            subscribeRequests = new ArrayList<>(requests);
        }

        subscribeNextRequest();
    }

    private void subscribeNextRequest() {
        if (subscribeRequests != null && subscribeRequests.size() > 0) {
            SubscribeRequest subscribeRequest = subscribeRequests.get(0);
            subscribeRequests.remove(0);

            subscribeCharacteristic(mBluetoothGatt, subscribeRequest.getServiceUUID(), subscribeRequest.getCharacteristicUUID());

            scheduleConnectingTimeoutTask();
        }
        else {
            Log.i(LOG_TAG, "Ready");
            //requestMediaUpdates();

            setState(ConnectionState.Ready);

            cancelScanningTimeoutTask();
            cancelConnectingTimeoutTask();

            subscribeRequests = null;

            mScansFailed = 0;
            mBondsFailed = 0;
            mConnectionsFailed = 0;
            mSilentReconnect = false;
            mDisabledBluetooth = true;
            mConnectingTimeoutTask = null;
            mScanningTimeoutTask = null;

            mDeviceMac = mBluetoothGatt.getDevice().getAddress();

            sendNextCommand();
        }
    }

    public void addCommandToQueue(Command command) {
        pendingCommands.add(command);

        sendNextCommand();
    }

    private void sendNextCommand() {
        cancelNextCommandTask();

        if (state != ConnectionState.Ready || pendingCommands.size() == 0) {
            return;
        }

        Command command = pendingCommands.get(0);

        try {
            BluetoothGattService service = mBluetoothGatt.getService(command.getServiceUUID());

            if (service != null) {
                BluetoothGattCharacteristic mBluetoothGattCharacteristic = service.getCharacteristic(UUID.fromString(command.getCharacteristic()));

                if (mBluetoothGattCharacteristic != null) {
                    // not being used
                    // mBluetoothGattCharacteristic.setWriteType(command.getWriteType());

                    mBluetoothGattCharacteristic.setValue(command.getPacket());
                    boolean result = mBluetoothGatt.writeCharacteristic(mBluetoothGattCharacteristic);
                    Log.d(LOG_TAG, "Started writing command: " + result);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        if (pendingCommands.size() > 0) {
            scheduleNextCommandTask();
        }
    }

    private void subscribeCharacteristic(BluetoothGatt bluetoothGatt, UUID serviceUUID, String characteristicUUIDString) {
        setCharacteristicNotification(bluetoothGatt, serviceUUID, characteristicUUIDString, true);
    }

    private void unsubscribeCharacteristic(BluetoothGatt bluetoothGatt, UUID serviceUUID, String characteristicUUIDString) {
        setCharacteristicNotification(bluetoothGatt, serviceUUID, characteristicUUIDString, false);
    }

    private void setCharacteristicNotification(BluetoothGatt bluetoothGatt, UUID serviceUUID, String characteristicUUIDString, boolean subscribe) {
        if (bluetoothGatt == null || serviceUUID == null || characteristicUUIDString == null ) {
            return;
        }

        try {
            BluetoothGattService service = bluetoothGatt.getService(serviceUUID);

            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(characteristicUUIDString));

                if (characteristic != null) {
                    bluetoothGatt.setCharacteristicNotification(characteristic, subscribe);

                    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(DESCRIPTOR_CONFIG));

                    if (descriptor != null) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        bluetoothGatt.writeDescriptor(descriptor);
                    }

                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void unpairDevice(BluetoothDevice device) {
        Log.d(LOG_TAG, "Unpairing...");

        try {
            Method m = device.getClass().getMethod("removeBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        mSilentReconnect = false;
        setState(ConnectionState.Pairing);
    }

    private void disableBluetooth() {
        if (!mDisabledBluetooth) {
            try {
                // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
                // BluetoothAdapter through BluetoothManager.
                final BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);

                // Checks if Bluetooth is supported on the device.
                if (bluetoothManager != null) {
                    BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

                    if (bluetoothAdapter.isEnabled()) {
                        bluetoothAdapter.disable();

                        Log.d(LOG_TAG, "Disabling bluetooth");
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        else {
            mDisabledBluetooth = false;
        }
    }


    // TASKS

    private ScheduledTask mScanningTimeoutTask;

    private void scheduleScanningTimeoutTask() {
        if (mScanningTimeoutTask == null) {
            mScanningTimeoutTask = new ScheduledTask(5000, mContext.getMainLooper(), new Runnable() {
                @Override
                public void run() {
                    Log.i(LOG_TAG, "Scanner timed out");

                    stopScanner();

                    mScansFailed++;

                    if (mScansFailed > 2) {
                        close(); // Kill everything, let the user try again when they want
                    }
                    else {
                        if (mScansFailed == 1) {
                            // Moto 360 needs Bluetooth to be reenabled to reconnect
                            disableBluetooth();
                        }

                        restartScanner();
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


    private ScheduledTask mConnectingTimeoutTask;

    private void scheduleConnectingTimeoutTask() {
        if (mConnectingTimeoutTask == null) {
            mConnectingTimeoutTask = new ScheduledTask(4000, mContext.getMainLooper(), new Runnable() {
                @Override
                public void run() {
                    if (state == ConnectionState.Connecting || state == ConnectionState.Pairing || state == ConnectionState.Preparing) {
                        Log.w(LOG_TAG, "Connecting timed out");

                        if (mBluetoothGatt != null && mBluetoothGatt.getDevice() != null) {
                            BluetoothDevice device = mBluetoothGatt.getDevice();
                            int bondState = device.getBondState();

                            // Don't do anything while bonding
                            if (bondState == BluetoothDevice.BOND_BONDING && mBondsFailed < 30) {
                                Log.w(LOG_TAG, "Waiting for bond...");
                                mBondsFailed++;

                                mConnectionsFailed = 4;
                                scheduleConnectingTimeoutTask();
                            }
                            else {
                                mConnectionsFailed++;

                                // Don't unpair if it's reconnecting silently
                                if (bondState == BluetoothDevice.BOND_NONE || (mConnectionsFailed == 4 && !mSilentReconnect)) {
                                    if (mBondsFailed > 0) {
                                        close();
                                    }
                                    else {
                                        mSilentReconnect = false;
                                        unpairDevice(device);
                                        device.createBond();

                                        // Check if bond is successful
                                        scheduleConnectingTimeoutTask();
                                    }
                                }
                                else {
                                    mBondsFailed = 0;
                                    mScansFailed = 0;

                                    if (mConnectionsFailed >= 6) {
                                        Log.w(LOG_TAG, "Giving up on connection");

                                        close();
                                    }
                                    else {
                                        if (mConnectionsFailed == 2) {
                                            disableBluetooth();
                                        }

                                        restartScanner();
                                    }
                                }
                            }
                        }
                        else {
                            Log.w(LOG_TAG, "Giving up on connection");

                            close();
                        }
                    }
                }
            });
        } else {
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
        if (state != ConnectionState.Ready || pendingCommands.size() == 0) {
            cancelNextCommandTask();
            return;
        }

        if (mNextCommandTask == null) {
            mNextCommandTask = new ScheduledTask(1600, mContext.getMainLooper(), new Runnable() {
                @Override
                public void run() {
                    Log.d(LOG_TAG, "Sending next command");
                    sendNextCommand();
                }
            });
        }
        else {
            mNextCommandTask.cancel();
        }

        mNextCommandTask.schedule();
    }

    private void cancelNextCommandTask() {
        if (mNextCommandTask != null) {
            mNextCommandTask.cancel();
        }
    }
    
}
