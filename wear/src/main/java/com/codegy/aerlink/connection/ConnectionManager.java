package com.codegy.aerlink.connection;

import android.bluetooth.*;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import com.codegy.aerlink.connection.characteristic.CharacteristicIdentifier;
import com.codegy.aerlink.connection.characteristic.CharacteristicSubscriber;
import com.codegy.aerlink.connection.characteristic.CharacteristicSubscriberThread;
import com.codegy.aerlink.connection.command.Command;
import com.codegy.aerlink.connection.command.CommandHandler;
import com.codegy.aerlink.connection.command.CommandQueueThread;

import java.lang.reflect.Method;
import java.util.*;

import static android.content.ContentValues.TAG;

/**
 * Created by Guiye on 18/5/15.
 */
public class ConnectionManager implements CharacteristicSubscriber, CommandHandler {

    private static final String LOG_TAG = ConnectionManager.class.getSimpleName();

    private static final String DESCRIPTOR_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    public interface Callback {
        void onConnectionStateChange(ConnectionState state);
        void onReadyToSubscribe(BluetoothGatt bluetoothGatt);
        void onCharacteristicChanged(BluetoothGattCharacteristic characteristic);
    }

    private Context mContext;
    private Callback mCallback;
    private BluetoothDevice mDevice;

    private BluetoothAdapter mBluetoothAdapter;

    private int mBondsFailed = 0;
    private int mConnectionsFailed = 0;

    private CharacteristicSubscriberThread subscriberThread;
    private CommandQueueThread commandQueue;


    public ConnectionManager(Context context, Callback callback, BluetoothAdapter bluetoothAdapter) {
        this.mContext = context;
        this.mCallback = callback;
        this.mBluetoothAdapter = bluetoothAdapter;

        this.commandQueue = new CommandQueueThread(this);
        this.commandQueue.start();
    }


    public void close() {
        // Check if already closed
        if (mCallback == null) {
            return;
        }

        Log.i(LOG_TAG, "Close");
        mCallback = null;

        disconnectDevice();

        if (subscriberThread != null) {
            subscriberThread.kill();
            subscriberThread = null;
        }

        if (commandQueue != null) {
            commandQueue.kill();
            commandQueue = null;
        }
    }

    private void reset() {
        // Check if already closed
        if (mCallback == null) {
            return;
        }

        if (subscriberThread != null) {
            subscriberThread.kill();
            subscriberThread = null;
        }

        if (commandQueue != null) {
            commandQueue.clear();

            commandQueue.setReady(false);
        }

        // mCallback.onConnectionStateChange(ConnectionState.Disconnected);
    }

    public synchronized void connectToDevice(final BluetoothDevice device) {
        if (mBluetoothGatt != null) {
            return;
        }

        mDevice = device;

        if (subscriberThread == null) {
            subscriberThread = new CharacteristicSubscriberThread(this);
            subscriberThread.start();
        }
        else {
            subscriberThread.reset();
        }

        Handler handler = new Handler(mContext.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                mBluetoothGatt = device.connectGatt(mContext, false, mBluetoothGattCallback);

                Log.i(LOG_TAG, "Connecting...: " + device.getName());
                mCallback.onConnectionStateChange(ConnectionState.Connecting);
            }
        });
    }

    private synchronized void disconnectDevice() {
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
    }

    public synchronized void tryToReconnect() {
        if (mCallback == null) {
            return;
        }

        reset();
        disconnectDevice();

        if (mDevice == null) {
            mCallback.onConnectionStateChange(ConnectionState.Disconnected);
            return;
        }

        Log.w(LOG_TAG, "Reconnecting");
        connectToDevice(mDevice);
    }

    private boolean changeGattMtu(int mtu) {
        int retry = 5;
        boolean ok = false;
        while (!ok && retry > 0) {
            ok = mBluetoothGatt.requestMtu(mtu);
            retry--;
        }

        return ok;
    }

    private BluetoothGatt mBluetoothGatt;
    private final BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d(LOG_TAG, "onConnectionStateChange: " + status + " -> " + newState);


            if (status == BluetoothGatt.GATT_SUCCESS) {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        if (mCallback != null && gatt == mBluetoothGatt) {
                            gatt.discoverServices();

                            subscriberThread.setDiscovering();
                        }
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        Log.e(LOG_TAG, "Disconnected");
                        gatt.close();
                        Log.w(LOG_TAG, "Closed");
                        if (mCallback == null || gatt != mBluetoothGatt) {
                            return;
                        }

                        mBluetoothGatt = null;

                        tryToReconnect();
                        break;
                }
            }
            else {
                Log.wtf(LOG_TAG, "ON CONNECTION STATE CHANGED ERROR: " + status);

                gatt.close();
                Log.w(LOG_TAG, "Closed");
                if (mCallback == null || gatt != mBluetoothGatt) {
                    return;
                }

                mBluetoothGatt = null;

                // 8 means time out, don't restart bluetooth, just reconnect when possible
                if (status != 8) {
                    //BluetoothUtils.resetBondedDevices(mBluetoothAdapter);
                    mDevice = null;
                    BluetoothUtils.disableBluetooth(mBluetoothAdapter);
                    return;
                }

                tryToReconnect();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.d(LOG_TAG, "onServicesDiscovered: " + status);
            // Check if already closed
            if (mCallback == null || gatt != mBluetoothGatt) {
                return;
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (!changeGattMtu(512)) {
                    mCallback.onReadyToSubscribe(gatt);
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            // Check if already closed
            if (mCallback == null || gatt != mBluetoothGatt) {
                return;
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(LOG_TAG, "Descriptor write successful: " + descriptor.getCharacteristic().getUuid().toString());

                subscriberThread.remove();
            }
            else {
                BluetoothDevice device = gatt.getDevice();
                int bondState = device.getBondState();

                // Don't do anything while bonding
                if (bondState != BluetoothDevice.BOND_BONDED) {
                    Log.e(LOG_TAG, "Status: write not permitted");

                    BluetoothUtils.unpairDevice(gatt.getDevice());
                    mDevice = null;
                    tryToReconnect();
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            // Check if already closed
            if (mCallback == null || gatt != mBluetoothGatt) {
                return;
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(LOG_TAG, "Characteristic write successful: " + characteristic.getUuid().toString());

                commandQueue.remove();
            }
            else {
                Log.w(LOG_TAG, "Characteristic write error: " + status + " :: " + characteristic.getUuid().toString());

                commandQueue.moveToBack();
            }

            Log.d(LOG_TAG, "Characteristic value: " + new String(characteristic.getValue()));
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.d(LOG_TAG, "onCharacteristicRead status:: " + status);
            // Check if already closed
            if (mCallback == null || gatt != mBluetoothGatt) {
                return;
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                mCallback.onCharacteristicChanged(characteristic);
            }

            commandQueue.remove();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.d(LOG_TAG, "onCharacteristicChanged: " + characteristic.getUuid().toString());
            // Check if already closed
            if (mCallback == null || gatt != mBluetoothGatt) {
                return;
            }
            
            mCallback.onCharacteristicChanged(characteristic);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            Log.d(LOG_TAG, "onMtuChanged: " + mtu + " status: " + status);
            if (mCallback == null || gatt != mBluetoothGatt) {
                return;
            }

            if (status ==  BluetoothGatt.GATT_SUCCESS) {
                mCallback.onReadyToSubscribe(gatt);
            }
        }
    };

    private BluetoothGattCharacteristic findCharacteristic(UUID serviceUUID, UUID characteristicUUID) {
        if (mBluetoothGatt == null) {
            return null;
        }

        BluetoothGattService service = mBluetoothGatt.getService(serviceUUID);
        if (service == null) {
            Log.e(LOG_TAG, "Service unavailable");
            return null;
        }

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
        if (characteristic == null) {
            Log.e(LOG_TAG, "Characteristic unavailable");
            return null;
        }

        return characteristic;
    }

    // region Commands

    public void addCommandToQueue(Command command) {
        commandQueue.put(command);
    }

    @Override
    public void handleCommand(Command command) {
        if (mBluetoothGatt == null) {
            return;
        }

        final Command finalCommand = command;

        Handler handler = new Handler(mContext.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    BluetoothGattCharacteristic characteristic = findCharacteristic(finalCommand.getServiceUUID(), finalCommand.getCharacteristic());
                    if (characteristic == null) {
                        return;
                    }

                    if (finalCommand.isWriteCommand()) {
                        // not being used
                        // mBluetoothGattCharacteristic.setWriteType(command.getWriteType());

                        characteristic.setValue(finalCommand.getPacket());
                        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                        boolean result = mBluetoothGatt.writeCharacteristic(characteristic);
                        Log.d(LOG_TAG, "Started writing command: " + result);
                    }
                    else {
                        boolean result = mBluetoothGatt.readCharacteristic(characteristic);
                        Log.d(LOG_TAG, "Started reading command: " + result);
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // endregion


    //region Subscribe requests

    public void addSubscribeRequests(Queue<CharacteristicIdentifier> requests) {
        subscriberThread.setSubscribeRequests(requests);
    }

    @Override
    public void subscribeCharacteristic(CharacteristicIdentifier characteristicIdentifier) {
        if (mBluetoothGatt == null) {
            return;
        }

        final CharacteristicIdentifier finalCharacteristicIdentifier = characteristicIdentifier;

        Handler handler = new Handler(mContext.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    BluetoothGattCharacteristic characteristic = findCharacteristic(finalCharacteristicIdentifier.getServiceUUID(), finalCharacteristicIdentifier.getCharacteristicUUID());
                    if (characteristic == null) {
                        return;
                    }

                    mBluetoothGatt.setCharacteristicNotification(characteristic, true);

                    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(DESCRIPTOR_CONFIG));
                    if (descriptor == null) {
                        Log.e(LOG_TAG, "Descriptor unavailable to write");
                        return;
                    }

                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    //descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                    boolean result = mBluetoothGatt.writeDescriptor(descriptor);
                    Log.d(LOG_TAG, "Started writing descriptor: " + result);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    //endregion

    @Override
    public void onConnectionFailed() {
        Log.w(LOG_TAG, "Connecting timed out");
        if (mCallback == null) {
            return;
        }

        if (mBluetoothGatt != null && mBluetoothGatt.getDevice() != null) {
            BluetoothDevice device = mBluetoothGatt.getDevice();
            int bondState = device.getBondState();

            // Don't do anything while bonding
            if (bondState == BluetoothDevice.BOND_BONDING && mBondsFailed < 30) {
                Log.w(LOG_TAG, "Waiting for bond...");
                mBondsFailed++;

                subscriberThread.setConnecting();
            }
            else {
                mBondsFailed = 0;
                mConnectionsFailed++;

                BluetoothUtils.restartBluetooth(mBluetoothAdapter);

                if (mConnectionsFailed%3 == 0) {
                    mDevice = null;
                }

                tryToReconnect();
            }
        }
        else {
            Log.w(LOG_TAG, "Start scanning again");

            mDevice = null;

            tryToReconnect();
        }
    }

    @Override
    public void onSubscribingFailed() {
        Log.w(LOG_TAG, "Subscribing timed out");
        if (mCallback == null) {
            return;
        }

        if (mBluetoothGatt != null && mBluetoothGatt.getDevice() != null) {
            BluetoothDevice device = mBluetoothGatt.getDevice();
            int bondState = device.getBondState();

            // Don't do anything while bonding
            if (bondState == BluetoothDevice.BOND_BONDING && mBondsFailed < 30) {
                Log.w(LOG_TAG, "Waiting for bond...");
                mBondsFailed++;

                subscriberThread.setBonding();
            }
            else {
                mBondsFailed = 0;
                mConnectionsFailed++;

                if (mConnectionsFailed >= 10) {
                    mConnectionsFailed = 0;

                    // Last resort to prepare for a new connection
                    // Reset bonded devices
                    BluetoothUtils.resetBondedDevices(mBluetoothAdapter);
                }

                BluetoothUtils.restartBluetooth(mBluetoothAdapter);

                if (mConnectionsFailed%3 == 0) {
                    mDevice = null;
                }

                tryToReconnect();
            }
        }
        else {
            Log.w(LOG_TAG, "Start scanning again");

            mDevice = null;

            tryToReconnect();
        }
    }

    @Override
    public void onConnectionReady() {
        Log.i(LOG_TAG, "Ready");

        mCallback.onConnectionStateChange(ConnectionState.Ready);

        mBondsFailed = 0;
        mConnectionsFailed = 0;

        commandQueue.setReady(true);

        // We are done with this thread, we don't need it until disconnection
        subscriberThread = null;
    }

}
