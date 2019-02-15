package com.codegy.aerlink.service.currenttime

import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import com.codegy.aerlink.connection.Command
import com.codegy.aerlink.service.ServiceManager
import java.io.DataOutputStream
import java.io.IOException
import java.util.*

class CurrentTimeServiceManager: ServiceManager {

    private val currentTime: Calendar = Calendar.getInstance()

    override fun initialize(): List<Command>? {
        return listOf(
                Command(CTSContract.serviceUuid, CTSContract.currentTimeCharacteristicUuid)
        )
    }

    override fun close() {}

    override fun canHandleCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        return characteristic.uuid == CTSContract.currentTimeCharacteristicUuid
    }

    override fun handleCharacteristic(characteristic: BluetoothGattCharacteristic) {
        val year = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0) ?: return
        val month = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 2) ?: return
        val day = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 3) ?: return
        val hours = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 4) ?: return
        val minutes = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 5) ?: return
        val seconds = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 6) ?: return

        currentTime.set(year, month, day, hours, minutes, seconds)
        updateSystemTime()

        Log.d(LOG_TAG, "Current time: $currentTime")
    }

    private fun updateSystemTime() {
        val correctedTime = currentTime.clone() as Calendar
        // Add 3 seconds to compensate the delay of the update process
        correctedTime.add(Calendar.SECOND, 3)

        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            val command = "date -s ${String.format(Locale.getDefault(), "%d%02d%02d.%02d%02d%02d",
                    correctedTime.get(Calendar.YEAR),
                    correctedTime.get(Calendar.MONTH),
                    correctedTime.get(Calendar.DAY_OF_MONTH),
                    correctedTime.get(Calendar.HOUR_OF_DAY),
                    correctedTime.get(Calendar.MINUTE),
                    correctedTime.get(Calendar.SECOND)
            )}\n"

            Log.e("command", command)
            os.writeBytes(command)
            os.flush()
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor()
        } catch (e: InterruptedException) {
            Log.e(LOG_TAG, "Can't update current time")
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Can't update current time")
        }
    }

    companion object {
        private val LOG_TAG: String = CurrentTimeServiceManager::class.java.simpleName
    }

}