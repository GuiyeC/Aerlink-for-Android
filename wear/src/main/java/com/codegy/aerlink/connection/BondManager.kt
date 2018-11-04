package com.codegy.aerlink.connection

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

class BondManager(private val device: BluetoothDevice, private val context: Context, var callback: Callback?) {

    interface Callback {
        fun onBondSuccessful(bondManager: BondManager, device: BluetoothDevice)
        fun onBondFailed(bondManager: BondManager, device: BluetoothDevice)
    }

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null || intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                return
            }

            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            if (this@BondManager.device != device) return
            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
            val prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)

            Log.d(LOG_TAG, "Bond state changed: state = $state prevState = $prevState")
            if (state == BluetoothDevice.BOND_BONDED && prevState == BluetoothDevice.BOND_BONDING) {
                Log.i(LOG_TAG, "Bond successful")
                callback?.onBondSuccessful(this@BondManager, device)
            } else if (state == BluetoothDevice.BOND_NONE && (prevState == BluetoothDevice.BOND_BONDING || prevState == BluetoothDevice.BOND_BONDED)) {
                Log.e(LOG_TAG, "Bond failed")
                callback?.onBondFailed(this@BondManager, device)
            }
        }
    }

    init {
        context.registerReceiver(bondReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
    }

    fun close() {
        callback = null
        context.unregisterReceiver(bondReceiver)
    }

    fun createBond() {
        device.createBond()
    }

    companion object {
        private val LOG_TAG = BondManager::class.java.simpleName
    }

}