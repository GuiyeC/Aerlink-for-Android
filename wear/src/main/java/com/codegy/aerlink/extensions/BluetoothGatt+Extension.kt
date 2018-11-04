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

// Status: https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/5738f83aeb59361a0a2eda2460113f6dc9194271/stack/include/gatt_api.h
fun BluetoothGatt.stringFromConnectionError(error: Int): String {
    when (error) {
        BluetoothGatt.GATT_SUCCESS -> return "SUCCESS"
        /** GATT CONN L2C FAILURE **/
        0x01 -> return "GATT CONN L2C FAILURE: General L2CAP failure"
        /** GATT_CONN_TIMEOUT **/
        0x08 -> return "GATT CONN TIMEOUT: Connection timeout"
        /** GATT CONN TERMINATE PEER USER **/
        0x13 -> return "GATT CONN TERMINATE PEER USER: Connection terminated by peer user"
        /** GATT CONN TERMINATE LOCAL HOST **/
        0x16 -> return "GATT CONN TERMINATE LOCAL HOST: Connection terminated by local host"
        /** GATT CONN FAIL ESTABLISH **/
        0x3E -> return "GATT CONN FAIL ESTABLISH: Connection fail to establish"
        /** GATT CONN LMP TIMEOUT **/
        0x22 -> return "GATT CONN LMP TIMEOUT: Connection fail for LMP, response timed-out"
        /** GATT CONN CANCEL **/
        0x0100 -> return "GATT CONN CANCEL: L2CAP connection cancelled"
        /** GATT ERROR **/
        0x0085 -> return "GATT ERROR"
        else -> return "UNKNOWN (0x${error.toString(16)})"
    }
}

fun BluetoothGatt.stringFromStatus(status: Int): String {
    when (status) {
        BluetoothGatt.GATT_SUCCESS -> return "SUCCESS"
        0x0001 -> return "GATT INVALID HANDLE"
        BluetoothGatt.GATT_READ_NOT_PERMITTED -> return "GATT READ NOT PERMITTED"
        BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> return "GATT WRITE NOT PERMITTED"
        0x0004 -> return "GATT INVALID PDU"
        BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION -> return "GATT INSUFFICIENT AUTHENTICATION: Insufficient authentication for a given operation"
        BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED -> return "GATT REQUEST NOT SUPPORTED: The given request is not supported"
        BluetoothGatt.GATT_INVALID_OFFSET -> return "GATT INVALID OFFSET: A read or write operation was requested with an invalid offset"
        0x0008 -> return "GATT INSUFFICIENT AUTHORIZATION"
        0x0009 -> return "GATT PREPARE Q FULL"
        0x000a -> return "GATT NOT FOUND"
        0x000b -> return "GATT NOT LONG"
        0x000c -> return "GATT INSUFFICIENT KEY SIZE"
        BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> return "GATT INVALID ATTRIBUTE LENGTH: A write operation exceeds the maximum length of the attribute"
        0x000e -> return "GATT ERR UNLIKELY"
        BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION -> return "GATT INSUFFICIENT ENCRYPTION: "
        0x0010 -> return "GATT UNSUPPORTED GRP TYPE"
        0x0011 -> return "GATT INSUFFICIENT RESOURCE"
        0x0080 -> return "GATT NO RESOURCES"
        0x0081 -> return "GATT INTERNAL ERROR"
        0x0082 -> return "GATT WRONG STATE"
        0x0083 -> return "GATT DB FULL"
        0x0084 -> return "GATT BUSY"
        0x0085 -> return "GATT ERROR"
        0x0086 -> return "GATT CMD STARTED"
        0x0087 -> return "GATT ILLEGAL PARAMETER"
        0x0088 -> return "GATT PENDING"
        0x0089 -> return "GATT AUTH FAIL"
        0x008a -> return "GATT MORE"
        0x008b -> return "GATT INVALID CFG"
        0x008c -> return "GATT SERVICE STARTED"
        0x008d -> return "GATT ENCRYPTED NO MITM"
        0x008e -> return "GATT NOT ENCRYPTED"
        BluetoothGatt.GATT_CONNECTION_CONGESTED -> return "GATT CONGESTED: A remote device connection is congested"
        BluetoothGatt.GATT_FAILURE -> return "GATT FAILURE: A GATT operation failed"
        else -> return "UNKNOWN (0x${status.toString(16)})"
    }
}
