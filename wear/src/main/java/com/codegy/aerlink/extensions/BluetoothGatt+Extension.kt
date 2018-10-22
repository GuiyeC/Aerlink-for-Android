package com.codegy.aerlink.extensions

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.util.Log
import com.codegy.aerlink.connection.CharacteristicIdentifier
import java.util.*

fun BluetoothGatt.getCharacteristic(characteristicIdentifier: CharacteristicIdentifier): BluetoothGattCharacteristic? {
    return getCharacteristic(characteristicIdentifier.serviceUUID, characteristicIdentifier.characteristicUUID)
}

fun BluetoothGatt.getCharacteristic(serviceUUID: UUID, characteristicUUID: UUID): BluetoothGattCharacteristic? {
    val service = getService(serviceUUID)
    if (service == null) {
        Log.e(this.javaClass.simpleName, "Service unavailable: $serviceUUID")
        return null
    }

    val characteristic = service.getCharacteristic(characteristicUUID)
    if (characteristic == null) {
        Log.e(this.javaClass.simpleName, "Characteristic unavailable: $characteristicUUID")
        return null
    }

    return characteristic
}

fun BluetoothGatt.subscribeToCharacteristic(characteristicIdentifier: CharacteristicIdentifier): Boolean {
    val characteristic = getCharacteristic(characteristicIdentifier) ?: return false

    val notificationsEnabledLocally = setCharacteristicNotification(characteristic, true)
    if (!notificationsEnabledLocally) {
        Log.e(this.javaClass.simpleName, "Failed to enable notifications locally on ${characteristicIdentifier.characteristicUUID}")
        return false
    }

    // We write the configuration descriptor to enable notifications on the remote device
    val configDescriptorUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    val descriptor = characteristic.getDescriptor(configDescriptorUUID)
    if (descriptor == null) {
        Log.e(this.javaClass.simpleName, "Descriptor unavailable to write on ${characteristicIdentifier.characteristicUUID}")
        return false
    }

    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
//    descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
    val notificationsEnabledRemotely = writeDescriptor(descriptor)
    if (!notificationsEnabledRemotely) {
        Log.e(this.javaClass.simpleName, "Failed to enable notifications remotely on ${characteristicIdentifier.characteristicUUID}")
        return false
    }
    Log.d(this.javaClass.simpleName, "Started writing descriptor: ${characteristicIdentifier.characteristicUUID}")

    return true
}
