package com.codegy.aerlink.connection

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Handler
import android.os.Looper
import android.util.Log

class DiscoveryManager(var callback: Callback?, bluetoothManager: BluetoothManager) {

    interface Callback {
        fun onDeviceDiscovery(device: BluetoothDevice)
    }

    private var isScanning: Boolean = false
    private val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter
    private var scanner: BluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    private var scanSettings: ScanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
    private val allowedDevices: List<String> = listOf("Aerlink", "BLE Utility", "Blank")

    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!isScanning) {
                return
            }

            Log.d(LOG_TAG, "Scan Result: $result")

            val device = result.device ?: return
            val deviceName = device.name ?: return

            if (allowedDevices.contains(deviceName)) {
                callback?.onDeviceDiscovery(device)
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            Log.i(LOG_TAG, "Batch Scan Results: $results")
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.d(LOG_TAG, "Scan Failed: $errorCode")

            stopDiscovery()
            startDiscovery()
        }

    }

    fun startDiscovery() {
        // If disabled -> enable bluetooth
        if (!bluetoothAdapter.isEnabled) {
            stopDiscovery()

            bluetoothAdapter.enable()
            Log.wtf(LOG_TAG, "Bluetooth was disabled")

            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({ startDiscovery() }, RETRY_TIME)

            return
        }

        if (isScanning) {
            // Scanner is already running
            // Don't start anything
            return
        }

        try {
            scanner.startScan(null, scanSettings, scanCallback)
            isScanning = true

            Log.d(LOG_TAG, "Scanning started")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (!isScanning) {
            Log.d(LOG_TAG, "Scanning didn't start, will try again")

            // Scanning did not work, try again in a moment
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({ startDiscovery() }, RETRY_TIME)
        }
    }

    fun stopDiscovery() {
        isScanning = false

        synchronized(this) {
            try {
                scanner.stopScan(scanCallback)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            Log.d(LOG_TAG, "Scanning stopped")
        }
    }

    companion object {
        private val LOG_TAG = DiscoveryManager::class.java.simpleName
        private const val RETRY_TIME: Long = 3000
    }

}
