package com.codegy.aerlink.service.aerlink.reminders

import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
import com.codegy.aerlink.connection.Command
import com.codegy.aerlink.service.ServiceManager
import com.codegy.aerlink.service.aerlink.reminders.model.ReminderCalendar
import com.codegy.aerlink.service.aerlink.reminders.model.ReminderItem
import com.codegy.aerlink.utils.CommandHandler
import com.codegy.aerlink.utils.PacketProcessor
import com.codegy.aerlink.utils.ScheduledTask
import org.json.JSONArray

class RemindersServiceManager(context: Context, private val commandHandler: CommandHandler): ServiceManager {
    interface CalendarsCallback {
        fun onDataTransferStarted()
        fun onError()
        fun onCalendarsUpdated(calendars: List<ReminderCalendar>)
    }

    interface RemindersCallback {
        fun onDataTransferStarted()
        fun onError()
        fun onRemindersUpdated(reminders: List<ReminderItem>)
    }

    var calendarsCallback: CalendarsCallback? = null
    var remindersCallback: RemindersCallback? = null
    private var selectedCalendar: String? = null
    private var packetProcessor: PacketProcessor? = null
    private val timeoutController: ScheduledTask = ScheduledTask(Looper.getMainLooper())
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    override fun initialize(): List<Command>? {
        return null
    }

    override fun close() {
        timeoutController.cancel()
        calendarsCallback = null
        remindersCallback = null
    }

    override fun canHandleCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        return characteristic.uuid == ARSContract.remindersDataCharacteristicUuid
    }

    override fun handleCharacteristic(characteristic: BluetoothGattCharacteristic) {
        Log.d(LOG_TAG, "handleCharacteristic :: Characteristic: $characteristic")
        timeoutController.cancel()
        val packet = characteristic.value

        var processor = packetProcessor
        if (processor != null) {
            processor.process(packet)
        } else {
            processor = PacketProcessor(packet)
            when (processor.action) {
                0x01 -> calendarsCallback?.onDataTransferStarted()
                0x02 -> remindersCallback?.onDataTransferStarted()
            }
            packetProcessor = processor
        }

        processor = packetProcessor
        if (processor?.isFinished == true) {
            when (processor.action) {
                0x01 -> processCalendarsData(processor)
                0x02 -> processRemindersData(processor)
            }
            packetProcessor = null
        }
    }

    fun requestCalendarsUpdate(forceUpdate: Boolean) {
        Log.d(LOG_TAG, "requestCalendarsUpdate :: Force update: $forceUpdate")
        val forceUpdateByte = (if (forceUpdate || fetchData(SPK_REMINDER_CALENDARS_DATA) == null) 0x01 else 0x00).toByte()
        val reminderCommand = Command(
                ARSContract.serviceUuid,
                ARSContract.remindersActionCharacteristicUuid,
                byteArrayOf(0x01.toByte(), forceUpdateByte)
        )
        reminderCommand.failureBlock = {
            calendarsCallback?.onError()
        }
        commandHandler.handleCommand(reminderCommand)
    }

    fun requestRemindersUpdate(forceUpdate: Boolean, calendarIdentifier: String) {
        selectedCalendar = calendarIdentifier
        val forceUpdateByte = (if (forceUpdate || fetchData(SPK_REMINDER_ITEMS_DATA + calendarIdentifier) == null) 0x01 else 0x00).toChar()
        val dataString = 0x02.toChar() + forceUpdateByte.toString() + calendarIdentifier
        val reminderCommand = Command(
                ARSContract.serviceUuid,
                ARSContract.remindersActionCharacteristicUuid,
                dataString.toByteArray()
        )
        reminderCommand.failureBlock = {
            remindersCallback?.onError()
        }
        commandHandler.handleCommand(reminderCommand)
    }

    fun setReminderCompleted(completed: Boolean, reminder: ReminderItem, calendarIdentifier: String, successBlock: (() -> Unit)) {
        val completedByte = (if (completed) 0x01 else 0x00).toChar()
        val dataString = 0x03.toChar() + completedByte.toString() + reminder.identifier
        val reminderCommand = Command(
                ARSContract.serviceUuid,
                ARSContract.remindersActionCharacteristicUuid,
                dataString.toByteArray()
        )
        reminderCommand.successBlock = {
            updateCachedData(calendarIdentifier, reminder)
            reminder.isCompleted = completed
            successBlock()
        }
        commandHandler.handleCommand(reminderCommand)
    }

    private fun updateCachedData(calendarIdentifier: String, reminder: ReminderItem) {
        var remindersData = fetchData(SPK_REMINDER_ITEMS_DATA + calendarIdentifier)
        if (remindersData != null) {
            val identifier = ",\"i\":\"" + reminder.identifier
            remindersData = if (reminder.isCompleted) {
                remindersData.replace("0$identifier", "1$identifier")
            } else {
                remindersData.replace("1$identifier", "0$identifier")
            }
            saveData(SPK_REMINDER_ITEMS_DATA + calendarIdentifier, remindersData)
        }
    }

    private fun fetchData(key: String): String? {
        return sharedPreferences.getString(key, null)
    }

    private fun saveData(key: String, data: String) {
        sharedPreferences.edit().putString(key, data).apply()
    }

    private fun processCalendarsData(packetProcessor: PacketProcessor) {
        Log.d(LOG_TAG, "processCalendarsData :: packetProcessor: $packetProcessor")
        when (packetProcessor.status) {
            0x00 -> Log.i(LOG_TAG, "Error with calendar data")
            0x01 -> {
                Log.i(LOG_TAG, "Calendar regular update")
                // Regular update
                packetProcessor.stringValue?.let {
                    saveData(SPK_REMINDER_CALENDARS_DATA, it)
                    convertCalendarsData(it)
                }
            }
            0x02 -> {
                Log.i(LOG_TAG, "Calendar cache update")
                // No update needed, cache is valid
                val cachedData = fetchData(SPK_REMINDER_CALENDARS_DATA)
                if (cachedData == null) {
                    // Force update
                    Log.i(LOG_TAG, "Calendar cache update failed, forcing update")
                    requestCalendarsUpdate(true)
                } else if (calendarsCallback != null) {
                    convertCalendarsData(cachedData)
                }
            }
        }
    }

    private fun convertCalendarsData(calendarsData: String?) {
        try {
            val jsonItems = JSONArray(calendarsData)
            val calendars = mutableListOf<ReminderCalendar>()
            for (i in 0 until jsonItems.length()) {
                val calendar = ReminderCalendar(jsonItems.getJSONObject(i))
                calendars.add(calendar)
            }
            calendarsCallback?.onCalendarsUpdated(calendars)
        } catch (e: Exception) {
            e.printStackTrace()
            calendarsCallback?.onCalendarsUpdated(listOf())
        }
    }

    private fun processRemindersData(packetProcessor: PacketProcessor) {
        Log.d(LOG_TAG, "processRemindersData :: packetProcessor: $packetProcessor")
        when (packetProcessor.status) {
            0x00 -> {
                selectedCalendar = null
                Log.i(LOG_TAG, "Error with reminders data")
            }
            0x01 -> {
                // Regular update
                packetProcessor.stringValue?.let {
                    val jsonItems = JSONArray(it)
                    val calendarIdentifier = jsonItems.getJSONObject(0).getString("i")
                    saveData(SPK_REMINDER_ITEMS_DATA + calendarIdentifier, it)
                    convertRemindersData(it)
                }
                selectedCalendar = null
            }
            0x02 -> {
                // No update needed, cache is valid
                val calendarIdentifier = selectedCalendar ?: return
                val cachedData = fetchData(SPK_REMINDER_ITEMS_DATA + calendarIdentifier)
                if (cachedData == null) {
                    // Force update
                    requestRemindersUpdate(true, calendarIdentifier)
                } else if (remindersCallback != null) {
                    selectedCalendar = null
                    convertRemindersData(cachedData)
                }
            }
        }
    }

    private fun convertRemindersData(remindersData: String?) {
        try {
            val jsonItems = JSONArray(remindersData)
            val calendarIdentifier = jsonItems.getJSONObject(0).getString("i")
            val selectedCalendar = selectedCalendar ?: return
            if (calendarIdentifier != selectedCalendar) {
                Log.d(LOG_TAG, "Calendar identifier does not match selected calendar: $calendarIdentifier $selectedCalendar")
                return
            }
            val items = mutableListOf<ReminderItem>()
            var uncompleted = 0
            for (i in 1 until jsonItems.length()) {
                val item = ReminderItem(jsonItems.getJSONObject(i))
                if (item.isCompleted) {
                    items.add(item)
                } else {
                    items.add(uncompleted, item)
                    uncompleted++
                }
            }
            remindersCallback?.onRemindersUpdated(items)
        } catch (e: Exception) {
            e.printStackTrace()
            remindersCallback?.onRemindersUpdated(listOf())
        }
    }

    companion object {
        private val LOG_TAG = RemindersServiceManager::class.java.simpleName
        private const val SPK_REMINDER_CALENDARS_DATA = "SPK_REMINDER_CALENDARS_DATA"
        private const val SPK_REMINDER_ITEMS_DATA = "SPK_REMINDER_ITEMS_DATA_"
    }
}