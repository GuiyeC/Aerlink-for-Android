package com.codegy.aerlink.service.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothGattCharacteristic
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.codegy.aerlink.R
import com.codegy.aerlink.connection.Command
import com.codegy.aerlink.service.ServiceManager
import com.codegy.aerlink.service.notifications.model.NotificationAttribute
import com.codegy.aerlink.service.notifications.model.NotificationDataReader
import com.codegy.aerlink.service.notifications.model.NotificationEvent
import com.codegy.aerlink.utils.CommandHandler
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class NotificationServiceManager(private val context: Context, private val commandHandler: CommandHandler): ServiceManager {

    private val notificationManager: NotificationManagerCompat by lazy { NotificationManagerCompat.from(context) }
    private var notificationDataReader: NotificationDataReader? = null
    private val eventQueue: Queue<NotificationEvent> = ConcurrentLinkedQueue()
    private val notificationCache: MutableMap<String, Notification> = mutableMapOf()
    private var ready = false

    override fun initialize(): List<Command>? {
        ready = true
        requestNextNotificationData()
        return null
    }

    override fun close() {

    }

    override fun canHandleCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        return ANCSContract.characteristicsToSubscribe.find { it.characteristicUUID == characteristic.uuid } != null
    }

    override fun handleCharacteristic(characteristic: BluetoothGattCharacteristic) {
        val packet = characteristic.value

        when (characteristic.uuid) {
            ANCSContract.notificationSourceCharacteristicUuid -> {
                handleNotificationSourcePacket(packet)
            }
            ANCSContract.dataSourceCharacteristicUuid -> {
                handleDataSourcePacket(packet)
            }
        }
    }

    private fun handleNotificationSourcePacket(packet: ByteArray) {
        Log.d(LOG_TAG, "Notification Source Packet")

        val event = NotificationEvent(packet)
        eventQueue.removeAll { it.uid.contentEquals(event.uid) }

        eventQueue.add(event)
        requestNextNotificationData()
    }

    private fun handleDataSourcePacket(packet: ByteArray) {
        Log.d(LOG_TAG, "Notification Data Packet")
        notificationDataReader?.let {
            // TODO: check uid
            it.readPacket(packet)
            if (it.isFinished) {
                handleNotificationEvent(it.event, it.attributes)
                notificationDataReader = null
                requestNextNotificationData()
            }
        }
    }

    private fun requestNextNotificationData() {
        if (!ready || notificationDataReader != null || eventQueue.isEmpty()) {
            return
        }
        val event = eventQueue.poll()
        Log.d(LOG_TAG, "Next event: $event")
        when (event.type) {
            NotificationEvent.Type.Added -> {
                val attributesToRead = mutableListOf(
                        NotificationAttribute.AppIdentifier,
                        NotificationAttribute.Title,
                        NotificationAttribute.Subtitle,
                        NotificationAttribute.Message
                )
                if (event.hasPositiveAction) {
                    attributesToRead.add(NotificationAttribute.PositiveActionLabel)
                }
                if (event.hasNegativeAction) {
                    attributesToRead.add(NotificationAttribute.NegativeActionLabel)
                }
                notificationDataReader = NotificationDataReader(event, attributesToRead)

                val command = buildNotificationAttributesCommand(event.uid, attributesToRead)
                commandHandler.handleCommand(command)
            }
            NotificationEvent.Type.Modified -> {
                val attributesToRead = mutableListOf(
                        NotificationAttribute.Title,
                        NotificationAttribute.Subtitle,
                        NotificationAttribute.Message
                )
                if (event.hasPositiveAction) {
                    attributesToRead.add(NotificationAttribute.PositiveActionLabel)
                }
                if (event.hasNegativeAction) {
                    attributesToRead.add(NotificationAttribute.NegativeActionLabel)
                }
                notificationDataReader = NotificationDataReader(event, attributesToRead)

                val command = buildNotificationAttributesCommand(event.uid, attributesToRead)
                commandHandler.handleCommand(command)
            }
            NotificationEvent.Type.Removed -> {
                notificationManager.cancel(event.uidString, NOTIFICATION_ID)
            }
            NotificationEvent.Type.Reserved -> {
//                Log.wtf()
            }
        }
    }

    private fun buildNotificationAttributesCommand(uid: ByteArray, attributes: List<NotificationAttribute>): Command {
        var packetSize = 5 + attributes.size
        for (attribute in attributes) {
            if (attribute.needsLengthParameter()) {
                packetSize += 2
            }
        }
        val packet = ByteArray(packetSize)
        packet[0] = 0 // CommandIDGetNotificationAttributes
        packet[1] = uid[0]
        packet[2] = uid[1]
        packet[3] = uid[2]
        packet[4] = uid[3]
        var index = 5
        for (attribute in attributes) {
            packet[index] = attribute.value
            index += 1
            if (attribute.needsLengthParameter()) {
                packet[index] = 0xFF.toByte()
                index += 1
                packet[index] = 0xFF.toByte()
                index += 1
            }
        }

        return Command(ANCSContract.serviceUuid, ANCSContract.controlPointCharacteristicUuid, packet)
    }

    private fun handleNotificationEvent(event: NotificationEvent, attributes: Map<NotificationAttribute, String>) {
        Log.d(LOG_TAG, "Event: $event")
        Log.d(LOG_TAG, "Attributes: $attributes")
        when (event.type) {
            NotificationEvent.Type.Added,
            NotificationEvent.Type.Modified -> {
                val notificationBuilder = Notification.Builder(context)

                attributes[NotificationAttribute.Title]?.let {
                    notificationBuilder.setContentTitle(it)
                }
                attributes[NotificationAttribute.Title]?.let {
                    val subtitle = attributes[NotificationAttribute.Subtitle] ?: ""
                    notificationBuilder.setContentTitle(subtitle + "\n" + it)
                }
                notificationBuilder.setSmallIcon(R.drawable.nic_notification)
                attributes[NotificationAttribute.AppIdentifier]?.let {
                    notificationBuilder.setGroup(it)
                }
//                        .setContentTitle(notificationData.getTitle())
//                        .setContentText(notificationData.getMessage())
//                        .setSmallIcon(notificationData.getAppIcon())
//                        .setGroup(notificationData.getAppId())
//                        .setDeleteIntent(deleteAction)
//                        .setPriority(Notification.PRIORITY_MAX)

                val background = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                background.eraseColor(Color.BLUE)
                val wearableExtender = Notification.WearableExtender()
                        .setBackground(background)
                notificationBuilder.extend(wearableExtender)

                notificationManager.notify(event.uidString, NOTIFICATION_ID, notificationBuilder.build())
            }
            NotificationEvent.Type.Removed -> {
                notificationManager.cancel(event.uidString, NOTIFICATION_ID)
            }
            NotificationEvent.Type.Reserved -> TODO()
        }
    }

    companion object {
        private val LOG_TAG = NotificationServiceManager::class.java.simpleName
    }

}