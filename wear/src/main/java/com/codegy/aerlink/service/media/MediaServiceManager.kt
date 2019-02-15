package com.codegy.aerlink.service.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothGattCharacteristic
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.media.VolumeProvider
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.VolumeProviderCompat
import androidx.media.session.MediaButtonReceiver
import com.codegy.aerlink.R
import com.codegy.aerlink.connection.Command
import com.codegy.aerlink.service.ServiceManager
import com.codegy.aerlink.service.media.model.MediaCommand
import com.codegy.aerlink.service.media.model.MediaEntity
import com.codegy.aerlink.utils.CommandHandler
import kotlin.experimental.and

class MediaServiceManager(private val context: Context, private val commandHandler: CommandHandler): ServiceManager {

    private val notificationManager: NotificationManagerCompat by lazy { NotificationManagerCompat.from(context) }
    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            showMedia = false
            if (mediaPlaying) {
                buildNotification()
            }
        }
    }
    private lateinit var session: MediaSessionCompat
    private val metadataBuilder: MediaMetadataCompat.Builder = MediaMetadataCompat.Builder()
    private val playbackStateBuilder: PlaybackStateCompat.Builder = PlaybackStateCompat.Builder()
    private var mediaPlaying: Boolean = false
    private var showMedia: Boolean = false
    private var mediaTitle: String? = null
    private var mediaArtist: String? = null

    init {
        // Since android Oreo notification channel is needed.
        // Check if notification channel exists and if not create one
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            var notificationChannel = notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
            if (notificationChannel == null) {
                val channelDescription = "Aerlink Media"
                val importance = NotificationManager.IMPORTANCE_HIGH
                notificationChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID, channelDescription, importance)
                notificationChannel.enableVibration(true)
// TODO:                notificationChannel.vibrationPattern = SILENT_VIBRATION_PATTERN
                notificationManager.createNotificationChannel(notificationChannel)
            }
        }

        val intentFilter = IntentFilter()
        intentFilter.addAction(IA_HIDE_MEDIA)
        context.registerReceiver(broadcastReceiver, intentFilter)

        Handler(Looper.getMainLooper()).post {
            session = MediaSessionCompat(
                    context, "Aerlink",
                    ComponentName(context, MediaButtonReceiver::class.java), null)
            initializeSession()
        }
    }

    override fun initialize(): List<Command>? {
        return listOf(
                Command(AMSContract.serviceUuid, AMSContract.entityUpdateCharacteristicUuid,
                        byteArrayOf(
                                MediaEntity.Track.value,
                                MediaEntity.Track.Attribute.Title.value,
                                MediaEntity.Track.Attribute.Artist.value
                        )
                ),
                Command(AMSContract.serviceUuid, AMSContract.entityUpdateCharacteristicUuid,
                        byteArrayOf(
                                MediaEntity.Player.value,
                                MediaEntity.Player.Attribute.PlaybackInfo.value
                        )
                )
        )
    } 

    private fun initializeSession() {
        session.isActive = true
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS or MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS)
        session.setPlaybackToRemote(object : VolumeProviderCompat(VolumeProvider.VOLUME_CONTROL_RELATIVE, 100, 50) {
            override fun onAdjustVolume(direction: Int) {
                super.onAdjustVolume(direction)
                if (direction == 1) {
                    commandHandler.handleCommand(MediaCommand.VolumeUp.command)
                } else {
                    commandHandler.handleCommand(MediaCommand.VolumeDown.command)
                }
            }
        })
        session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                commandHandler.handleCommand(MediaCommand.Play.command)
            }
            override fun onPause() {
                commandHandler.handleCommand(MediaCommand.Pause.command)
            }
            override fun onSkipToNext() {
                commandHandler.handleCommand(MediaCommand.NextTrack.command)
            }
            override fun onSkipToPrevious() {
                commandHandler.handleCommand(MediaCommand.PreviousTrack.command)
            }
        })

        updatePlaybackState()
        updateMetadata()
    }

    override fun close() {
        context.unregisterReceiver(broadcastReceiver)
        session.release()
    }

    override fun canHandleCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        return AMSContract.characteristicsToSubscribe.find { it.characteristicUUID == characteristic.uuid } != null
    }

    override fun handleCharacteristic(characteristic: BluetoothGattCharacteristic) {
        when (characteristic.uuid) {
            AMSContract.entityUpdateCharacteristicUuid -> {
                handleEntityUpdatePacket(characteristic)
            }
            AMSContract.entityAttributeCharacteristicUuid -> {
                handleEntityAttributePacket(characteristic)
            }
        }

        if (mediaPlaying || showMedia) {
            buildNotification()
        }
    }

    private fun handleEntityUpdatePacket(characteristic: BluetoothGattCharacteristic) {
        val packet = characteristic.value
        val value = characteristic.getStringValue(3)
        val entity = MediaEntity.fromRaw(packet[0])
        when (entity) {
            MediaEntity.Player -> {
                val attribute = MediaEntity.Player.Attribute.fromRaw(packet[1])
                when (attribute) {
                    MediaEntity.Player.Attribute.PlaybackInfo -> {
                        if (value.isNotEmpty()) {
                            mediaPlaying = value.substring(0, 1) != "0"

                            if (value == "0,,") {
                                showMedia = false
                                notificationManager.cancel(null, NOTIFICATION_ID)
                            }
                        }
                    }
                    MediaEntity.Player.Attribute.Name -> TODO()
                    MediaEntity.Player.Attribute.Volume -> TODO()
                    MediaEntity.Player.Attribute.Reserved -> TODO()
                }
                updatePlaybackState()
            }
            MediaEntity.Queue -> {
                TODO()
            }
            MediaEntity.Track  -> {
                val attribute = MediaEntity.Track.Attribute.fromRaw(packet[1])
                val truncated = (packet[2] and 1) != 0.toByte()
                when (attribute) {
                    MediaEntity.Track.Attribute.Artist -> {
                        if (value.isEmpty()) {
                            mediaArtist = null
                        } else {
                            mediaArtist = value
                            if (truncated) {
                                mediaArtist += "..."
                            }
                        }
                    }
                    MediaEntity.Track.Attribute.Title -> {
                        if (value.isEmpty()) {
                            mediaTitle = null
                        } else {
                            mediaTitle = value
                            if (truncated) {
                                mediaTitle += "..."
                            }
                        }
                    }
                    MediaEntity.Track.Attribute.Album -> TODO()
                    MediaEntity.Track.Attribute.Duration -> TODO()
                    MediaEntity.Track.Attribute.Reserved -> TODO()
                }
                updateMetadata()
            }
        }
    }

    private fun handleEntityAttributePacket(characteristic: BluetoothGattCharacteristic) {
        val packet = characteristic.value
        val value = characteristic.getStringValue(3)
    }

    private fun updatePlaybackState() {
        val position = PlaybackState.PLAYBACK_POSITION_UNKNOWN
        playbackStateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE
                        or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                        or PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
        playbackStateBuilder.setState(if (mediaPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, position, 1.0f)
        session.setPlaybackState(playbackStateBuilder.build())
    }

    private fun updateMetadata() {
        if (mediaTitle == null && mediaArtist == null) {
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "No info")
        } else {
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, mediaTitle)
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, mediaArtist)
        }
        session.setMetadata(metadataBuilder.build())
    }

    private fun buildNotification() {
        showMedia = true

        // Build pending intent for when the user swipes the card away
        val deleteIntent = Intent(IA_HIDE_MEDIA)
        val deleteAction = PendingIntent.getBroadcast(context, 0, deleteIntent, PendingIntent.FLAG_CANCEL_CURRENT)
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.nic_low_battery)
                .setDeleteIntent(deleteAction)
                .setColor(Color.BLUE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(mediaPlaying)
                .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(session.sessionToken)
                        .setShowActionsInCompactView(0))

        notificationManager.notify(null, NOTIFICATION_ID, builder.build())
    }

    companion object {
        private val LOG_TAG: String = MediaServiceManager::class.java.simpleName
        private const val NOTIFICATION_ID: Int = 1001
        private const val NOTIFICATION_CHANNEL_ID: String = "com.codegy.aerlink.service.media"
        private const val IA_HIDE_MEDIA: String = "com.codegy.aerlink.service.media.IA_HIDE_MEDIA"
    }
}