package com.codegy.aerlink.service.battery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothGattCharacteristic
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.codegy.aerlink.R
import com.codegy.aerlink.connection.Command
import com.codegy.aerlink.service.ServiceManager

class BatteryServiceManager(private val context: Context): ServiceManager {

    interface Observer {
        fun onBatteryLevelChanged(batteryLevel: Int)
    }

    private val notificationManager: NotificationManagerCompat by lazy { NotificationManagerCompat.from(context) }
    var observer: Observer? = null
        set(value) {
            field = value
            batteryLevel?.let {
                observer?.onBatteryLevelChanged(it)
            }
        }
    private var batteryLevel: Int? = null
        set(value) {
            if (value == field) return
            if (value != null) {
                handleBatteryChange(field, value)
            }
            field = value
        }
    private var showBattery = false
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            showBattery = false
        }
    }

    init {
        // Since android Oreo notification channel is needed.
        // Check if notification channel exists and if not create one
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            var notificationChannel = notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
            if (notificationChannel == null) {
                val channelDescription = "Aerlink Battery"
                val importance = NotificationManager.IMPORTANCE_HIGH
                notificationChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID, channelDescription, importance)
                notificationChannel.enableVibration(true)
// TODO:                notificationChannel.vibrationPattern = SILENT_VIBRATION_PATTERN
                notificationManager.createNotificationChannel(notificationChannel)
            }
        }

        val intentFilter = IntentFilter()
        intentFilter.addAction(IA_HIDE_BATTERY)
        context.registerReceiver(broadcastReceiver, intentFilter)
    }

    override fun initialize(): List<Command>? {
        return listOf(
                Command(BASContract.serviceUuid, BASContract.batteryLevelCharacteristicUuid)
        )
    }

    override fun close() {
        context.unregisterReceiver(broadcastReceiver)
    }

    override fun canHandleCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        return characteristic.uuid == BASContract.batteryLevelCharacteristicUuid
    }

    override fun handleCharacteristic(characteristic: BluetoothGattCharacteristic) {
        val previousLevel = batteryLevel
        batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
        Log.d(LOG_TAG, "Battery level update: Previous: $previousLevel New: $batteryLevel")
    }

    private fun handleBatteryChange(previousLevel: Int?, newLevel: Int) {
        observer?.onBatteryLevelChanged(newLevel)

        if (newLevel > 20) {
            if (showBattery) {
                // When the battery reaches 20% hide the notification
                showBattery = false
                notificationManager.cancel(null, NOTIFICATION_ID)
            }
        } else {
            var vibrate = false
            if (previousLevel != null && previousLevel > newLevel && newLevel % 5 == 0) {
                // If the battery is running down, vibrate at 20, 15, 10 and 5
                vibrate = true
                showBattery = true
            }

            buildBatteryNotification(newLevel, vibrate)
        }
    }

    private fun buildBatteryNotification(batteryLevel: Int, vibrate: Boolean) {
        if (!showBattery) {
            return
        }

        // Build pending intent for when the user swipes the card away
        val deleteIntent = Intent(IA_HIDE_BATTERY)
        val deleteAction = PendingIntent.getBroadcast(context, 0, deleteIntent, PendingIntent.FLAG_CANCEL_CURRENT)

        val background = BitmapFactory.decodeResource(context.resources, R.drawable.nic_low_battery)
        val wearableExtender = NotificationCompat.WearableExtender()
                .setBackground(background)
                .setContentIcon(R.drawable.nic_low_battery)
                .setHintHideIcon(true)

        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.nic_low_battery)
                .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.nic_low_battery))
                .setDeleteIntent(deleteAction)
                .setContentTitle(context.getString(R.string.general_low_battery))
                .setContentText("$batteryLevel%")
                .setColor(Color.RED)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .extend(wearableExtender)

        if (vibrate) {
            builder.setVibrate(SILENT_VIBRATION_PATTERN)
        }

        notificationManager.notify(null, NOTIFICATION_ID, builder.build())
    }

    companion object {
        private val LOG_TAG = BatteryServiceManager::class.java.simpleName
        private const val NOTIFICATION_ID: Int = 1002
        private const val NOTIFICATION_CHANNEL_ID: String = "com.codegy.aerlink.service.battery"
        private const val IA_HIDE_BATTERY: String = "com.codegy.aerlink.service.battery.IA_HIDE_BATTERY"
        private val SILENT_VIBRATION_PATTERN: LongArray = arrayOf<Long>(200, 110).toLongArray()
    }

}