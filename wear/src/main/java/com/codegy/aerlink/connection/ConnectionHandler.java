package com.codegy.aerlink.connection;

import android.bluetooth.*;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import com.codegy.aerlink.battery.BASConstants;
import com.codegy.aerlink.currenttime.CTSConstants;
import com.codegy.aerlink.utils.ScheduledTask;

import java.util.*;

/**
 * Created by Guiye on 18/5/15.
 */
public class ConnectionHandler {

    private static final String LOG_TAG = ConnectionHandler.class.getSimpleName();

    private static final String DESCRIPTOR_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    

    private Context mContext;
    private ConnectionHandlerCallback mCallback;


    private BluetoothAdapter mBluetoothAdapter;


    private int mBondsFailed = 0;
    private int mConnectionsFailed = 0;

    private Command mCurrentCommand;
    private Queue<Command> pendingCommands = new LinkedList<>();
    private Queue<CharacteristicIdentifier> subscribeRequests;
    private Queue<CharacteristicIdentifier> readRequests;


    public ConnectionHandler(Context context, ConnectionHandlerCallback callback, BluetoothAdapter bluetoothAdapter) {
        this.mContext = context;
        this.mCallback = callback;
        this.mBluetoothAdapter = bluetoothAdapter;
    }


    public void close() {
        // Check if already closed
        if (mCallback == null) {
            return;
        }

        Log.i(LOG_TAG, "Close");

        reset();

        mNextCommandTask = null;
        mConnectingTimeoutTask = null;


        mCallback = null;
    }

    private void reset() {
        //setState(ConnectionState.Disconnected);

        cancelConnectingTimeoutTask();
        cancelNextCommandTask();


        try {
            if (mBluetoothGatt != null) {
                mBluetoothGatt.disconnect();
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


        mCallback.onConnectionStateChange(ConnectionState.Disconnected);
    }


    public void connectToDevice(final BluetoothDevice device) {
        if (mCallback.getState() == ConnectionState.Disconnected) {
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
                    mBluetoothGatt = device.connectGatt(mContext, true, mBluetoothGattCallback);
                }
            });

            Log.i(LOG_TAG, "Connecting...: " + device.getName());
            mCallback.onConnectionStateChange(ConnectionState.Connecting);

            //scheduleConnectingTimeoutTask();
        }
    }

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
                    //checkForBondedDevice();
                }
            }
            else {
                Log.wtf(LOG_TAG, "ON CONNECTION STATE CHANGED ERROR: " + status);

                BluetoothUtils.disableBluetooth(mBluetoothAdapter);
                //BluetoothUtils.resetBondedDevices(mBluetoothAdapter);
                reset();
                //checkForBondedDevice();
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

                BluetoothUtils.unpairDevice(gatt.getDevice());
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
            subscribeRequests = new LinkedList<>(requests);
        }

        subscribeNextRequest();
    }

    private void subscribeNextRequest() {
        Log.d(LOG_TAG, "Subscribe Requests: " + (subscribeRequests == null ? -1 :subscribeRequests.size()));
        if (subscribeRequests != null && subscribeRequests.size() > 0) {
            CharacteristicIdentifier characteristicIdentifier = subscribeRequests.remove();

            subscribeCharacteristic(mBluetoothGatt, characteristicIdentifier.getServiceUUID(), characteristicIdentifier.getCharacteristicUUID());

            scheduleConnectingTimeoutTask();
        }
        else {
            Log.i(LOG_TAG, "Ready");

            mCallback.onConnectionStateChange(ConnectionState.Ready);

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
            readRequests = new LinkedList<>();
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

        if (mCallback.getState() != ConnectionState.Ready || pendingCommands.size() == 0) {
            mCurrentCommand = null;
            return;
        }

        mCurrentCommand = pendingCommands.element();

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
            CharacteristicIdentifier characteristicIdentifier = readRequests.remove();

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
                Log.d(LOG_TAG, "Service available");
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(characteristicUUIDString));

                if (characteristic != null) {
                    Log.d(LOG_TAG, "Characteristic available");
                    bluetoothGatt.setCharacteristicNotification(characteristic, subscribe);

                    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(DESCRIPTOR_CONFIG));

                    if (descriptor != null) {
                        Log.d(LOG_TAG, "Descriptor available");
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        //descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                        bluetoothGatt.writeDescriptor(descriptor);
                        Log.d(LOG_TAG, "Started writing descriptor");
                    }

                }
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
                    if (mCallback.getState() == ConnectionState.Connecting) {
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

//                                reset();

                                if (mConnectionsFailed >= 10) {
                                    mConnectionsFailed = 0;

                                    // Last resort to prepare for a new connection
                                    // Reset bonded devices
                                    BluetoothUtils.resetBondedDevices(mBluetoothAdapter);
                                }

                                BluetoothUtils.disableBluetooth(mBluetoothAdapter);

                                reset();
//                                checkForBondedDevice();
                            }
                        }
                        else {
                            Log.w(LOG_TAG, "Start scanning again");

                            reset();
//                            checkForBondedDevice();
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
        if (mCallback.getState() != ConnectionState.Ready || pendingCommands.size() == 0) {
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
