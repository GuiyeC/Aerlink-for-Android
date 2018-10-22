package com.codegy.aerlink.extensions

import android.bluetooth.BluetoothDevice
import android.util.Log

fun BluetoothDevice.unpair() {
    Log.e(this.javaClass.simpleName, "$name: Unpairing...")

    try {
        this.javaClass.getMethod("removeBond").invoke(this)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}