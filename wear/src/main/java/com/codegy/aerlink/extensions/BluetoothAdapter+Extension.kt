package com.codegy.aerlink.extensions

import android.bluetooth.BluetoothAdapter
import android.util.Log

fun BluetoothAdapter.resetBondedDevices() {
    try {
        val devices = bondedDevices ?: return
        for (device in devices) {
            val deviceName = device.name
            if (deviceName == "Aerlink" || deviceName == "BLE Utility" || deviceName == "Blank") {
                device.unpair()
            }
        }

        Log.d(this.javaClass.simpleName, "Bonded devices reset")
    } catch (e: Exception) {
        e.printStackTrace()
    }
}