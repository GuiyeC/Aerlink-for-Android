package com.codegy.aerlink.service

import android.bluetooth.BluetoothGattCharacteristic
import com.codegy.aerlink.connection.Command

interface ServiceManager {
    fun initialize(): List<Command>?
    fun canHandleCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean
    fun handleCharacteristic(characteristic: BluetoothGattCharacteristic)
    fun close()
}