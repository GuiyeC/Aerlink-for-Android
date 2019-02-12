package com.codegy.aerlink.service.battery

import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import com.codegy.aerlink.connection.Command
import com.codegy.aerlink.service.ServiceManager

class BatteryServiceManager(private val context: Context): ServiceManager {

    interface Observer {
        fun onBatteryLevelChanged(batteryLevel: Int)
    }

    var observer: Observer? = null
    var batteryLevel: Int? = null
        set(value) {
            if (value == field) return
            if (value != null) {
                handleBatteryChange(field, value)
                observer?.onBatteryLevelChanged(value)
            }

            field = value
        }

    override fun initialize(): List<Command>? {
        return listOf(
                Command(BASContract.serviceUuid, BASContract.batteryLevelCharacteristicUuid)
        )
    }

    override fun close() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun canHandleCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        return characteristic.uuid == BASContract.batteryLevelCharacteristicUuid
    }

    override fun handleCharacteristic(characteristic: BluetoothGattCharacteristic) {
        val previousLevel = batteryLevel
        batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
        Log.d(LOG_TAG, "Battery level update: Previous: $previousLevel New: $batteryLevel")
    }

    fun handleBatteryChange(previousLevel: Int?, newLevel: Int) {
//        if (newLevel > 20) {
//            if (showBattery) {
//                // When the battery reaches 20% hide the notification
//                showBattery = false
//
//                mServiceUtils.cancelNotification(null, NOTIFICATION_BATTERY)
//            }
//        } else {
//            var vibrate = false
//            if (previousLevel != null && previousLevel > newLevel && newLevel % 5 == 0) {
//                // If the battery is running down, vibrate at 20, 15, 10 and 5
//                vibrate = true
//                showBattery = true
//            }
//
//            buildBatteryNotification(vibrate)
//        }
    }

    companion object {
        private val LOG_TAG = BatteryServiceManager::class.java.simpleName
    }

}