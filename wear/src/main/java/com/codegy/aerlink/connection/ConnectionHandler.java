package com.codegy.aerlink.connection;

import android.bluetooth.*;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import com.codegy.aerlink.battery.BASConstants;
import com.codegy.aerlink.currenttime.CTSConstants;
import com.codegy.aerlink.utils.ScheduledTask;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Created by Guiye on 18/5/15.
 */
public class ConnectionHandler implements DiscoveryHelper.DiscoveryCallback {

    private static final String LOG_TAG = ConnectionHandler.class.getSimpleName();

    private static final String DESCRIPTOR_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";


    public enum ConnectionState {
        NoBluetooth,
        Disconnected,
        Connecting,
        Ready
    }
    

    private Context mContext;
    private ConnectionHandlerCallback mCallback;

    private DiscoveryHelper mDiscoveryHelper;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;

    private ConnectionState state;

    private int mBondsFailed = 0;
    private int mConnectionsFailed = 0;

    private Command mCurrentCommand;
    private List<Command> pendingCommands = new ArrayList<>();
    private List<CharacteristicIdentifier> subscribeRequests;
    private List<CharacteristicIdentifier> readRequests;


    public ConnectionHandler(Context context, ConnectionHandlerCallback callback) {
        this.mContext = context;
        this.mCallback = callback;

        mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothManager != null) {
            setState(ConnectionState.Disconnected);

            mBluetoothAdapter = mBluetoothManager.getAdapter();

            if (mBluetoothGattServer == null) {
                mBluetoothGattServer = mBluetoothManager.openGattServer(mContext, mGattServerCallback);
            }

            // Start by checking for bonded device
            checkForBondedDevice();
        }
        else {
            Log.w(LOG_TAG, "Bluetooth not supported");

            setState(ConnectionState.NoBluetooth);
        }
    }


    public ConnectionState getState() {
        return state;
    }

    public void setState(ConnectionState state) {
        if (state == this.state) {
            return;
        }

        this.state = state;

        mCallback.onConnectionStateChange(state);
    }


    private void checkForBondedDevice() {
        Log.i(LOG_TAG, "Checking for previously bonded device");

        if (mDiscoveryHelper == null) {
            mDiscoveryHelper = new DiscoveryHelper(mContext, this);
        }
/*
        if (getBondedDevice() == null) {
            // No previously bonded device, start bonding activity
*/
            mDiscoveryHelper.startScanningAndAdvertising();
            /*
        }
        else {
            mDiscoveryHelper.startScanningAndAdvertising();
        }
        */
    }



    public void close() {
        // Check if already closed
        if (mCallback == null && state == null) {
            return;
        }

        Log.i(LOG_TAG, "Close");

        reset();

        mNextCommandTask = null;
        mConnectingTimeoutTask = null;

        if (mDiscoveryHelper != null) {
            mDiscoveryHelper.close();
            mDiscoveryHelper = null;
        }

        state = null;

        mCallback = null;
    }

    private void reset() {
        setState(ConnectionState.Disconnected);

        cancelConnectingTimeoutTask();
        cancelNextCommandTask();

        mDiscoveryHelper.stopScanningAndAdvertising();

        try {
            if (mBluetoothGatt != null) {
                mBluetoothGatt.close();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            mBluetoothGatt = null;
        }

        mCurrentCommand = null;

        if (pendingCommands != null) {
            pendingCommands.clear();
        }

        if (subscribeRequests != null) {
            subscribeRequests.clear();
        }

        readRequests = null;
    }


    @Override
    public void connectToDevice(final BluetoothDevice device) {
        if (mDiscoveryHelper != null) {
            mDiscoveryHelper.stopScanningAndAdvertising();
        }

        if (state == ConnectionState.Disconnected) {
            try {
                if (mBluetoothGatt != null) {
                    mBluetoothGatt.close();
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            Handler handler = new Handler(mContext.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    mBluetoothGatt = device.connectGatt(mContext, false, mBluetoothGattCallback);
                }
            });

            Log.i(LOG_TAG, "Connecting...: " + device.getName());
            setState(ConnectionState.Connecting);

            scheduleConnectingTimeoutTask();
        }
    }

    private BluetoothGattServer mBluetoothGattServer;
    private final BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, final int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    Log.i(LOG_TAG, "Connected to device: " + device.getAddress());

                    connectToDevice(device);
                }
                else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Log.v(LOG_TAG, "Disconnected from device");
                }
            }
            else {
                Log.e(LOG_TAG, "Error when connecting: " + status);
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded,
                                                 int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite,
                    responseNeeded, offset, value);
            Log.v(LOG_TAG, "Characteristic Write request: " + Arrays.toString(value));
            if (responseNeeded) {
                mBluetoothGattServer.sendResponse(device, requestId, 0,
            /* No need to respond with an offset */ 0,
            /* No need to respond with a value */ null);
            }
        }
    };

    private BluetoothGatt mBluetoothGatt;
    private final BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
            Log.d(LOG_TAG, "onConnectionStateChange: " + status + " -> " + newState);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(LOG_TAG, "Connected");

                    gatt.discoverServices();

                    scheduleConnectingTimeoutTask();
                }
                else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.e(LOG_TAG, "Disconnected");
                    Log.w(LOG_TAG, "Trying to reconnect");

                    // TODO: check if the disconnection is started by the user
                    reset();
                    checkForBondedDevice();
                }
            }
            else {
                Log.wtf(LOG_TAG, "ON CONNECTION STATE CHANGED ERROR: " + status);

                reset();
                checkForBondedDevice();
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

                mConnectionsFailed = 0;

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
                if (mCurrentCommand != null) {
                    pendingCommands.remove(mCurrentCommand);
                }

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(LOG_TAG, "Characteristic write successful: " + characteristic.getUuid().toString());

                    mCurrentCommand = null;

                    sendNextCommand();
                }
                else {
                    Log.w(LOG_TAG, "Characteristic write error: " + status + " :: " + characteristic.getUuid().toString());

                    if (mCurrentCommand != null && mCurrentCommand.shouldRetryAgain()) {
                        pendingCommands.add(mCurrentCommand);
                    }

                    mCurrentCommand = null;

                    scheduleNextCommandTask();
                }

                Log.d(LOG_TAG, "Characteristic value: " + new String(characteristic.getValue()));
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.d(LOG_TAG, "onCharacteristicRead status:: " + status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                mCallback.onCharacteristicChanged(characteristic);
            }

            readNextCharacteristic();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(LOG_TAG, "onCharacteristicChanged: " + characteristic.getUuid().toString());
            
            mCallback.onCharacteristicChanged(characteristic);
        }
    };

    public void addSubscribeRequests(List<CharacteristicIdentifier> requests) {
        if (requests != null && requests.size() > 0) {
            subscribeRequests = new ArrayList<>(requests);
        }

        subscribeNextRequest();
    }

    private void subscribeNextRequest() {
        if (subscribeRequests != null && subscribeRequests.size() > 0) {
            CharacteristicIdentifier characteristicIdentifier = subscribeRequests.get(0);
            subscribeRequests.remove(0);

            subscribeCharacteristic(mBluetoothGatt, characteristicIdentifier.getServiceUUID(), characteristicIdentifier.getCharacteristicUUID());

            scheduleConnectingTimeoutTask();
        }
        else {
            Log.i(LOG_TAG, "Ready");

            setState(ConnectionState.Ready);

            cancelConnectingTimeoutTask();
            mConnectingTimeoutTask = null;

            subscribeRequests = null;

            mBondsFailed = 0;
            mConnectionsFailed = 0;

            addCharacteristicReadRequest(new CharacteristicIdentifier(BASConstants.SERVICE_UUID, BASConstants.CHARACTERISTIC_BATTERY_LEVEL));
            addCharacteristicReadRequest(new CharacteristicIdentifier(CTSConstants.SERVICE_UUID, CTSConstants.CHARACTERISTIC_CURRENT_TIME));
            readNextCharacteristic();

            sendNextCommand();
        }
    }

    public void addCharacteristicReadRequest(CharacteristicIdentifier characteristicIdentifier) {
        if (readRequests == null) {
            readRequests = new ArrayList<>();
        }

        readRequests.add(characteristicIdentifier);
    }

    public void addCommandToQueue(Command command) {
        pendingCommands.add(command);

        if (mCurrentCommand == null) {
            sendNextCommand();
        }
    }

    private void sendNextCommand() {
        cancelNextCommandTask();

        if (state != ConnectionState.Ready || pendingCommands.size() == 0) {
            mCurrentCommand = null;
            return;
        }

        mCurrentCommand = pendingCommands.get(0);

        try {
            BluetoothGattService service = mBluetoothGatt.getService(mCurrentCommand.getServiceUUID());

            if (service != null) {
                BluetoothGattCharacteristic bluetoothGattCharacteristic = service.getCharacteristic(UUID.fromString(mCurrentCommand.getCharacteristic()));

                if (bluetoothGattCharacteristic != null) {
                    // not being used
                    // mBluetoothGattCharacteristic.setWriteType(command.getWriteType());

                    bluetoothGattCharacteristic.setValue(mCurrentCommand.getPacket());
                    boolean result = mBluetoothGatt.writeCharacteristic(bluetoothGattCharacteristic);
                    Log.d(LOG_TAG, "Started writing command: " + result);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();

            // If something failed there is no current command being sent
            mCurrentCommand = null;
        }

        if (pendingCommands.size() > 0) {
            scheduleNextCommandTask();
        }
    }

    private void readNextCharacteristic() {
        if (readRequests != null && readRequests.size() > 0) {
            CharacteristicIdentifier characteristicIdentifier = readRequests.get(0);
            readRequests.remove(0);

            try {
                BluetoothGattService service = mBluetoothGatt.getService(characteristicIdentifier.getServiceUUID());

                if (service != null) {
                    BluetoothGattCharacteristic bluetoothGattCharacteristic = service.getCharacteristic(UUID.fromString(characteristicIdentifier.getCharacteristicUUID()));

                    if (bluetoothGattCharacteristic != null) {
                        boolean result = mBluetoothGatt.readCharacteristic(bluetoothGattCharacteristic);
                        Log.d(LOG_TAG, "Started reading characteristic: " + result);
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
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


    /**
     * Last resort to prepare for a new connection
     *
     * Reset bonded devices
     * Switch bluetooth off and on
     */
    public void connectionHardReset() {
        resetBondedDevices();

        try {
            if (mBluetoothAdapter != null) {
                if (mBluetoothAdapter.isEnabled()) {
                    mBluetoothAdapter.disable();

                    Log.d(LOG_TAG, "Bluetooth disabled");
                }

                mBluetoothAdapter.enable();

                Log.d(LOG_TAG, "Bluetooth enabled");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }


    private BluetoothDevice getBondedDevice() {
        BluetoothDevice bondedDevice = null;

        if (mBluetoothAdapter != null) {
            try {
                Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
                if (devices != null) {
                    for (BluetoothDevice device : devices) {
                        String deviceName = device.getName();
                        if (deviceName != null && (deviceName.equals("Aerlink") || deviceName.equals("BLE Utility") || deviceName.equals("Blank"))) {
                            bondedDevice = device;
                            break;
                        }
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        return bondedDevice;
    }

    private void resetBondedDevices() {
        if (mBluetoothAdapter != null) {
            try {
                Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
                if (devices != null) {
                    for (BluetoothDevice device : devices) {
                        String deviceName = device.getName();
                        if (deviceName != null && (deviceName.equals("Aerlink") || deviceName.equals("BLE Utility") || deviceName.equals("Blank"))) {
                            unpairDevice(device);
                        }
                    }
                }

                Log.d(LOG_TAG, "Bonded devices reset");
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void unpairDevice(BluetoothDevice device) {
        Log.d(LOG_TAG, device.getName() + ": Unpairing...");

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
            if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
                mBluetoothAdapter.disable();

                Log.d(LOG_TAG, "Disabling bluetooth");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }


    // TASKS

    private ScheduledTask mConnectingTimeoutTask;

    private void scheduleConnectingTimeoutTask() {
        if (mConnectingTimeoutTask == null) {
            mConnectingTimeoutTask = new ScheduledTask(5000, mContext.getMainLooper(), new Runnable() {
                @Override
                public void run() {
                    if (state == ConnectionState.Connecting) {
                        Log.w(LOG_TAG, "Connecting timed out");

                        if (mBluetoothGatt != null && mBluetoothGatt.getDevice() != null) {
                            BluetoothDevice device = mBluetoothGatt.getDevice();
                            int bondState = device.getBondState();

                            // Don't do anything while bonding
                            if (bondState == BluetoothDevice.BOND_BONDING && mBondsFailed < 30) {
                                Log.w(LOG_TAG, "Waiting for bond...");
                                mBondsFailed++;

                                // If the bond is not successful do a connection hard reset
                                mConnectionsFailed = 10;

                                // Check again in 5 seconds
                                scheduleConnectingTimeoutTask();
                            }
                            else {
                                mBondsFailed = 0;
                                mConnectionsFailed++;

                                reset();

                                if (mConnectionsFailed < 10) {
                                    disableBluetooth();
                                }
                                else {
                                    mConnectionsFailed = 0;

                                    connectionHardReset();
                                }

                                checkForBondedDevice();
                            }
                        }
                        else {
                            Log.w(LOG_TAG, "Start scanning again");

                            reset();
                            checkForBondedDevice();
                        }
                    }
                    else {
                        mBondsFailed = 0;
                        mConnectionsFailed = 0;
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
        if (state != ConnectionState.Ready || pendingCommands.size() == 0) {
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
