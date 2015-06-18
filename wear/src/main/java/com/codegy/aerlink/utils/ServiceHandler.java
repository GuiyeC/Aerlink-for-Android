package com.codegy.aerlink.utils;

import android.bluetooth.BluetoothGattCharacteristic;

import java.util.List;
import java.util.UUID;

/**
 * Created by Guiye on 19/5/15.
 */
public abstract class ServiceHandler {

    public void close() {}
    public void reset() {}
    public UUID getServiceUUID() { return null; }
    public List<String> getCharacteristicsToSubscribe() { return null; }
    public boolean canHandleCharacteristic(BluetoothGattCharacteristic characteristic) { return false; }
    public void handleCharacteristic(BluetoothGattCharacteristic characteristic) {}
}
