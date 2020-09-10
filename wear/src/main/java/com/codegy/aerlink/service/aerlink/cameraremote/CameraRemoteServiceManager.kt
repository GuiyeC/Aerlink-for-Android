package com.codegy.aerlink.service.aerlink.cameraremote

import android.app.Notification
import android.app.PendingIntent
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.codegy.aerlink.R
import com.codegy.aerlink.connection.Command
import com.codegy.aerlink.extensions.NotificationChannelImportance
import com.codegy.aerlink.extensions.createChannelIfNeeded
import com.codegy.aerlink.service.ServiceManager
import com.codegy.aerlink.ui.cameraremote.CameraRemoteActivity
import com.codegy.aerlink.utils.CommandHandler
import com.codegy.aerlink.utils.PacketProcessor
import com.codegy.aerlink.utils.ScheduledTask

class CameraRemoteServiceManager(private val context: Context, private val commandHandler: CommandHandler): ServiceManager {
    interface Callback {
        fun onCameraStateChanged(open: Boolean)
        fun onCountdownStarted(countdown: Int)
        fun onImageTransferStarted()
        fun onImageTransferFinished(image: Bitmap)
        fun onCameraError()
    }

    private val notificationManager: NotificationManagerCompat by lazy { NotificationManagerCompat.from(context) }
    var callback: Callback? = null
        set(value) {
            field = value
            isCameraOpen?.let {
                callback?.onCameraStateChanged(it)
            }
        }
    var isCameraOpen: Boolean? = null
        set(value) {
            if (value != null) {
                handleCameraStateChange(value)
            }
            field = value
        }
    private var packetProcessor: PacketProcessor? = null
    private val timeoutController: ScheduledTask = ScheduledTask(Looper.getMainLooper())

    init {
        val channelDescription = context.getString(R.string.camera_remote_notification_channel)
        val importance = NotificationChannelImportance.High
        notificationManager.createChannelIfNeeded(NOTIFICATION_CHANNEL_ID, channelDescription, importance)
    }

    override fun initialize(): List<Command>? = null

    override fun close() {
        timeoutController.cancel()
        callback = null
    }

    override fun canHandleCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        return characteristic.uuid == ACRSContract.cameraRemoteDataCharacteristicUuid
    }

    override fun handleCharacteristic(characteristic: BluetoothGattCharacteristic) {
        timeoutController.cancel()
        val packet = characteristic.value

        var processor = packetProcessor
        if (processor != null) {
            processor.process(packet)
        } else {
            processor = PacketProcessor(packet)
            when (processor.action) {
                0x01 -> {
                    if (processor.status == 0x01) {
                        packetProcessor = processor
                        callback?.onImageTransferStarted()
                    }
                }
                0x02 -> {
                    isCameraOpen = processor.status == 0x01
                }
                0x03 -> {
                    callback?.onCountdownStarted(processor.status)
                }
            }
        }

        processor = packetProcessor
        if (processor?.isFinished == true) {
            val image = processor.bitmapValue
            image?.let {
                callback?.onImageTransferFinished(it)
            }
            packetProcessor = null
        }
    }

    fun requestCameraState() {
        timeoutController.schedule(TIMEOUT) {
            callback?.onCameraError()
        }
        val command = Command(
                ACRSContract.serviceUuid,
                ACRSContract.cameraRemoteActionCharacteristicUuid,
                byteArrayOf(0x02.toByte())
        )
        command.failureBlock = { callback?.onCameraError() }
        commandHandler.handleCommand(command)
    }

    fun takePicture() {
        timeoutController.schedule(TIMEOUT) {
            callback?.onCameraError()
        }
        val command = Command(
                ACRSContract.serviceUuid,
                ACRSContract.cameraRemoteActionCharacteristicUuid,
                byteArrayOf(0x03.toByte()),
                Command.IMPORTANCE_MIN
        )
        command.failureBlock = { callback?.onCameraError() }
        commandHandler.handleCommand(command)
    }

    private fun handleCameraStateChange(open: Boolean) {
        Log.v(LOG_TAG, "Camera " + if (open) "OPEN" else "CLOSED")
        callback?.onCameraStateChanged(open)

        if (open) {
            showNotification()
        } else {
            hideNotification()
        }
    }

    private fun showNotification() {
        if (callback != null) return
        val cameraIntent = Intent(context, CameraRemoteActivity::class.java)
        cameraIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val cameraPendingIntent = PendingIntent.getActivity(context, 0, cameraIntent, 0)
        val cameraAction = NotificationCompat.Action.Builder(
                R.drawable.nic_camera, context.getString(R.string.camera_remote_notification_action), cameraPendingIntent
        ).build()
        val wearableExtender = NotificationCompat.WearableExtender()
                .setContentIcon(R.drawable.nic_camera)
                .setHintHideIcon(true)
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            val background = BitmapFactory.decodeResource(context.resources, R.drawable.bg_camera)
            wearableExtender.background = background
        }
        val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.nic_camera)
                .setContentTitle(context.getString(R.string.camera_remote_notification_title))
                .setContentText(context.getString(R.string.camera_remote_notification_text))
                .setPriority(Notification.PRIORITY_MAX)
                .setContentIntent(cameraPendingIntent)
                .addAction(cameraAction)
                .extend(wearableExtender)
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    fun hideNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    companion object {
        private val LOG_TAG = CameraRemoteServiceManager::class.java.simpleName
        private const val NOTIFICATION_ID: Int = 2001
        private const val NOTIFICATION_CHANNEL_ID: String = "com.codegy.aerlink.service.aerlink"
        private const val TIMEOUT: Long = 3000
    }
}