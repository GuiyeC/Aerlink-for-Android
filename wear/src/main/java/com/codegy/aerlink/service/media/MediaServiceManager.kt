package com.codegy.aerlink.service.media

import android.app.PendingIntent
import android.bluetooth.BluetoothGattCharacteristic
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.VolumeProvider
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.VolumeProviderCompat
import androidx.media.session.MediaButtonReceiver
import com.codegy.aerlink.R
import com.codegy.aerlink.connection.Command
import com.codegy.aerlink.extensions.NotificationChannelImportance
import com.codegy.aerlink.extensions.createChannelIfNeeded
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
    private var showMedia: Boolean = false
    private val metadataBuilder: MediaMetadataCompat.Builder = MediaMetadataCompat.Builder()
    private val playbackStateBuilder: PlaybackStateCompat.Builder = PlaybackStateCompat.Builder()
    private val mediaPlaying: Boolean
        get() {
            val state = mediaState ?: 0
            return state >= 1
        }
    private var mediaState: Int? = null
        set(value) {
            if (value == field) return
            field = value
            val state: Int
            val position = PlaybackState.PLAYBACK_POSITION_UNKNOWN
            if (value == null) {
                mediaArtist = null
                mediaTitle = null
                session.isActive = false
                state = PlaybackStateCompat.STATE_NONE
            } else {
                session.isActive = true
                state = when (mediaState) {
                    0 -> PlaybackStateCompat.STATE_PAUSED
                    1 -> PlaybackStateCompat.STATE_PLAYING
                    2 -> PlaybackStateCompat.STATE_REWINDING
                    3 -> PlaybackStateCompat.STATE_FAST_FORWARDING
                    else -> PlaybackStateCompat.STATE_NONE
                }
            }
            playbackStateBuilder.setState(state, position, 1f)
            session.setPlaybackState(playbackStateBuilder.build())
        }
    private var mediaTitle: String? = null
        set(value) {
            if (value == field) return
            field = value
            if (value != null && mediaArtist == null) {
                metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, context.getString(R.string.media_no_info))
            } else {
                metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, value)
            }
            session.setMetadata(metadataBuilder.build())
        }
    private var mediaArtist: String? = null
        set(value) {
            if (value == field) return
            field = value
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, value)
            session.setMetadata(metadataBuilder.build())
        }

    init {
        val channelDescription = context.getString(R.string.media_notification_channel)
        val importance = NotificationChannelImportance.High
        notificationManager.createChannelIfNeeded(NOTIFICATION_CHANNEL_ID, channelDescription, importance)

        val intentFilter = IntentFilter(IA_HIDE_MEDIA)
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
        session.setPlaybackToRemote(object : VolumeProviderCompat(VolumeProvider.VOLUME_CONTROL_ABSOLUTE, 100, 50) {
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

        playbackStateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE
                or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                or PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
        session.setPlaybackState(playbackStateBuilder.build())
        session.setMetadata(metadataBuilder.build())
    }

    override fun close() {
        Log.i(LOG_TAG, "Close")
        context.unregisterReceiver(broadcastReceiver)
        session.setCallback(null)
        session.isActive = false
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
                            if (value == "0,,") {
                                showMedia = false
                                mediaState = null
                                notificationManager.cancel(NOTIFICATION_ID)
                            } else {
                                mediaState = value.substringBefore(",").toInt()
                            }
                        }
                    }
                    MediaEntity.Player.Attribute.Name,
                    MediaEntity.Player.Attribute.Volume,
                    MediaEntity.Player.Attribute.Reserved -> {
                        Log.wtf(LOG_TAG, "Unexpected player attribute: $attribute $value")
                    }
                }
            }
            MediaEntity.Queue ->  {
                Log.wtf(LOG_TAG, "Unexpected queue attribute: $value")
            }
            MediaEntity.Track  -> {
                val attribute = MediaEntity.Track.Attribute.fromRaw(packet[1])
                val truncated = (packet[2] and 1) != 0.toByte()
                when (attribute) {
                    MediaEntity.Track.Attribute.Artist -> {
                        mediaArtist = parseTrackAttribute(value, truncated)
                    }
                    MediaEntity.Track.Attribute.Title -> {
                        mediaTitle = parseTrackAttribute(value, truncated)
                    }
                    MediaEntity.Track.Attribute.Album,
                    MediaEntity.Track.Attribute.Duration,
                    MediaEntity.Track.Attribute.Reserved -> {
                        Log.wtf(LOG_TAG, "Unexpected track attribute: $attribute $value")
                    }
                }
            }
        }
    }

    private fun parseTrackAttribute(value: String, truncated: Boolean): String? {
        return if (value.isEmpty()) {
            null
        } else if (!truncated) {
            value
        } else  {
            "$value..."
        }
    }

    private fun buildNotification() {
        showMedia = true
        // Build pending intent for when the user swipes the card away
        val deleteIntent = Intent(IA_HIDE_MEDIA)
        val deleteAction = PendingIntent.getBroadcast(context, 0, deleteIntent, PendingIntent.FLAG_CANCEL_CURRENT)

        val background = BitmapFactory.decodeResource(context.resources, R.drawable.bg_media)
        val wearableExtender = NotificationCompat.WearableExtender()
                .setBackground(background)
                .setHintHideIcon(true)
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.nic_music)
                .setDeleteIntent(deleteAction)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(mediaPlaying)
                .extend(wearableExtender)
                .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(session.sessionToken))
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun sendPlay() {
        commandHandler.handleCommand(MediaCommand.Play.command)
    }

    companion object {
        private val LOG_TAG: String = MediaServiceManager::class.java.simpleName
        private const val NOTIFICATION_ID: Int = 1002
        private const val NOTIFICATION_CHANNEL_ID: String = "com.codegy.aerlink.service.media"
        private const val IA_HIDE_MEDIA: String = "com.codegy.aerlink.service.media.IA_HIDE_MEDIA"
    }
}