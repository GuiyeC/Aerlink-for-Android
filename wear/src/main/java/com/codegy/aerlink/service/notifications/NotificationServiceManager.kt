package com.codegy.aerlink.service.notifications

import android.app.Notification
import android.app.PendingIntent
import android.bluetooth.BluetoothGattCharacteristic
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.codegy.aerlink.R
import com.codegy.aerlink.connection.Command
import com.codegy.aerlink.extensions.NotificationChannelImportance
import com.codegy.aerlink.extensions.createChannelIfNeeded
import com.codegy.aerlink.service.ServiceManager
import com.codegy.aerlink.service.media.MediaServiceManager
import com.codegy.aerlink.service.notifications.model.NotificationAttribute
import com.codegy.aerlink.service.notifications.model.NotificationDataReader
import com.codegy.aerlink.service.notifications.model.NotificationEvent
import com.codegy.aerlink.ui.PhoneActivity
import com.codegy.aerlink.utils.CommandHandler
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class NotificationServiceManager(private val context: Context, private val commandHandler: CommandHandler): ServiceManager {
    private val notificationManager: NotificationManagerCompat by lazy { NotificationManagerCompat.from(context) }
    private var notificationDataReader: NotificationDataReader? = null
    private val eventQueue: Queue<NotificationEvent> = ConcurrentLinkedQueue()
    private var ready = false
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val uid = intent.getByteArrayExtra(IE_NOTIFICATION_UID) ?: return
            handleLocalAction(action, uid)
        }
    }

    init {
        val channelDescription = context.getString(R.string.notifications_notification_channel)
        val importance = NotificationChannelImportance.Default
        notificationManager.createChannelIfNeeded(NOTIFICATION_CHANNEL_ID, channelDescription, importance)

        val intentFilter = IntentFilter()
        intentFilter.addAction(IA_POSITIVE)
        intentFilter.addAction(IA_NEGATIVE)
        intentFilter.addAction(IA_DELETE)
        context.registerReceiver(broadcastReceiver, intentFilter)
    }

    override fun initialize(): List<Command>? {
        ready = true
        handleNextEvent()
        return null
    }

    override fun close() {
        context.unregisterReceiver(broadcastReceiver)
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
        handleNextEvent()
    }

    private fun handleDataSourcePacket(packet: ByteArray) {
        Log.d(LOG_TAG, "Notification Data Packet")
        notificationDataReader?.let {
            it.readPacket(packet)
            if (it.isFinished) {
                handleNotificationEvent(it.event, it.attributes)
                notificationDataReader = null
                handleNextEvent()
            }
        }
    }

    private fun handleNextEvent() {
        if (!ready || notificationDataReader != null || eventQueue.isEmpty()) {
            return
        }
        val event = eventQueue.poll() ?: return
        Log.d(LOG_TAG, "Next event: $event")
        when (event.type) {
            NotificationEvent.Type.Added -> {
                val attributesToRead = mutableListOf(
                        NotificationAttribute.AppIdentifier,
                        NotificationAttribute.Title,
                        NotificationAttribute.Subtitle,
                        NotificationAttribute.Message
                )
                if (event.isPreExisting) {
                    attributesToRead.add(NotificationAttribute.Date)
                }
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
                if (event.category == NotificationEvent.Category.IncomingCall) {
                    context.sendBroadcast(Intent(IA_CALL_ENDED))
                }
            }
            NotificationEvent.Type.Reserved -> throw IllegalStateException("Can't handle Reserved notification type")
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
        packet[0] = 0x00 // CommandIDGetNotificationAttributes
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

    private val dateFormat: DateFormat by lazy { SimpleDateFormat("yyyyMMdd'T'HHmmSS", Locale.US) }
    private fun handleNotificationEvent(event: NotificationEvent, attributes: Map<NotificationAttribute, String>) {
        Log.d(LOG_TAG, "Event: $event")
        Log.d(LOG_TAG, "Attributes: $attributes")
        if (event.category == NotificationEvent.Category.IncomingCall) {
            handleIncomingCall(event, attributes)
            return
        }
        when (event.type) {
            NotificationEvent.Type.Added,
            NotificationEvent.Type.Modified -> {
                // Build pending intent for when the user swipes the card away
                val deleteIntent = Intent(IA_DELETE)
                deleteIntent.putExtra(IE_NOTIFICATION_UID, event.uid)
                val deleteAction = PendingIntent.getBroadcast(context, event.uidInt, deleteIntent, 0)

                val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                        .setDeleteIntent(deleteAction)
                        .setPriority(Notification.PRIORITY_MAX)
                attributes[NotificationAttribute.Title]?.let {
                    val subtitle = attributes[NotificationAttribute.Subtitle]
                    notificationBuilder.setContentTitle(if (subtitle?.isNotBlank() == true) "$it\n$subtitle" else it)
                }
                attributes[NotificationAttribute.Message]?.let {
                    notificationBuilder.setContentText(it)
                }
                attributes[NotificationAttribute.Date]?.let {
                    val date = dateFormat.parse(it) ?: return@let
                    notificationBuilder.setWhen(date.time)
                    notificationBuilder.setShowWhen(true)
                }
                attributes[NotificationAttribute.AppIdentifier]?.let {
                    notificationBuilder.setGroup(it)
                }
                attributes[NotificationAttribute.PositiveActionLabel]?.let {
                    val positiveIntent = Intent(IA_POSITIVE)
                    positiveIntent.putExtra(IE_NOTIFICATION_UID, event.uid)
                    val positiveActionIntent = PendingIntent.getBroadcast(context, event.uidInt, positiveIntent, 0)
                    val positiveAction = NotificationCompat.Action.Builder(R.drawable.ic_check, it, positiveActionIntent).build()
                    notificationBuilder.addAction(positiveAction)
                }
                attributes[NotificationAttribute.NegativeActionLabel]?.let {
                    val negativeIntent = Intent(IA_NEGATIVE)
                    negativeIntent.putExtra(IE_NOTIFICATION_UID, event.uid)
                    val negativeActionIntent = PendingIntent.getBroadcast(context, event.uidInt, negativeIntent, 0)
                    val negativeAction = NotificationCompat.Action.Builder(R.drawable.ic_remove, it, negativeActionIntent).build()
                    notificationBuilder.addAction(negativeAction)
                }

                if (event.isSilent) {
                    notificationBuilder.setNotificationSilent()
                }
                notificationBuilder.setSmallIcon(event.category.icon)

                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
                    val background = BitmapFactory.decodeResource(context.resources, R.drawable.bg_notification)
                    val wearableExtender = NotificationCompat.WearableExtender().setBackground(background)
                    notificationBuilder.extend(wearableExtender)
                }

                notificationManager.notify(event.uidString, NOTIFICATION_ID, notificationBuilder.build())
            }
            NotificationEvent.Type.Removed -> {
                notificationManager.cancel(event.uidString, NOTIFICATION_ID)
            }
            NotificationEvent.Type.Reserved -> {
                Log.wtf(LOG_TAG, "Unexpected event: $event")
            }
        }
    }

    private fun handleIncomingCall(event: NotificationEvent, attributes: Map<NotificationAttribute, String>) {
        PhoneActivity.start(context, attributes[NotificationAttribute.Title], attributes[NotificationAttribute.Message], event.uid)
    }

    // Notification action: immediately
    // Delete intent: after 7~8sec.
    private fun handleLocalAction(action: String, uid: ByteArray) {
        val uidString = String(uid)
        // Dismiss notification
        notificationManager.cancel(uidString, NOTIFICATION_ID)

        var actionId: Byte = 0x00 // ActionIDPositive
        if ((action == IA_NEGATIVE) or (action == IA_DELETE)) {
            actionId = 0x01 // ActionIDNegative
        }

        // Perform user selected action
        val performActionPacket = byteArrayOf(
                0x02, // CommandIDGetNotificationAttributes
                uid[0], uid[1], uid[2], uid[3],
                actionId
        )
        val performActionCommand = Command(ANCSContract.serviceUuid, ANCSContract.controlPointCharacteristicUuid, performActionPacket)
        commandHandler.handleCommand(performActionCommand)
    }

    companion object {
        private val LOG_TAG: String = NotificationServiceManager::class.java.simpleName
        private const val NOTIFICATION_ID: Int = 1001
        private const val NOTIFICATION_CHANNEL_ID: String = "com.codegy.aerlink.service.notifications"
        const val IA_POSITIVE: String = "com.codegy.aerlink.service.notifications.IA_POSITIVE"
        const val IA_NEGATIVE: String = "com.codegy.aerlink.service.notifications.IA_NEGATIVE"
        const val IA_DELETE: String = "com.codegy.aerlink.service.notifications.IA_DELETE"
        const val IA_CALL_ENDED: String = "com.codegy.aerlink.service.notifications.IA_CALL_ENDED"
        const val IE_NOTIFICATION_UID: String = "com.codegy.aerlink.service.notifications.IE_NOTIFICATION_UID"
    }
}