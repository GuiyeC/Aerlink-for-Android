package com.codegy.aerlink.connection;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

/**
 * Created by Guiye on 19/5/15.
 */
public interface ConnectionHandlerCallback {

    void onConnectionStateChange(ConnectionHandler.ConnectionState state);
    void onReadyToSubscribe(BluetoothGatt bluetoothGatt);
    void onCharacteristicChanged(BluetoothGattCharacteristic characteristic);

}
