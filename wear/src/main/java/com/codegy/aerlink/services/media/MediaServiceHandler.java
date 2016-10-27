package com.codegy.aerlink.services.media;

import android.app.Notification;
import android.app.PendingIntent;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.VolumeProvider;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;
import com.codegy.aerlink.Constants;
import com.codegy.aerlink.R;
import com.codegy.aerlink.connection.command.Command;
import com.codegy.aerlink.utils.ServiceHandler;
import com.codegy.aerlink.utils.ServiceUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by Guiye on 19/5/15.
 */
public class MediaServiceHandler extends ServiceHandler {
    
    private static final String LOG_TAG = MediaServiceHandler.class.getSimpleName();

    public static final int NOTIFICATION_MEDIA = 1001;

    private Context mContext;
    private ServiceUtils mServiceUtils;
    
    private MediaSession mSession;
    private boolean mediaPlaying;
    private boolean mediaHidden = true;
    private String mediaTitle;
    private String mediaArtist;


    public MediaServiceHandler(Context context, ServiceUtils serviceUtils) {
        this.mContext = context;
        this.mServiceUtils = serviceUtils;

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.IA_HIDE_MEDIA);
        context.registerReceiver(mBroadcastReceiver, intentFilter);

        // Run on main thread
        final Handler handler = new Handler(mContext.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                prepareMediaSession();

                handler.removeCallbacksAndMessages(null);
            }
        });
    }


    @Override
    public void close() {
        mediaTitle = null;
        mediaArtist = null;

        if (mSession != null) {
            mSession.release();
            mSession = null;
        }

        mContext.unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    public UUID getServiceUUID() {
        return AMSConstants.SERVICE_UUID;
    }

    @Override
    public List<String> getCharacteristicsToSubscribe() {
        List<String> characteristics = new ArrayList<>();

        characteristics.add(AMSConstants.CHARACTERISTIC_ENTITY_UPDATE);
//        characteristics.add(AMSConstants.CHARACTERISTIC_ENTITY_ATTRIBUTE);

        return characteristics;
    }

    @Override
    public boolean canHandleCharacteristic(BluetoothGattCharacteristic characteristic) {
        String characteristicUUID = characteristic.getUuid().toString().toLowerCase();
        return characteristicUUID.equals(AMSConstants.CHARACTERISTIC_ENTITY_UPDATE) || characteristicUUID.equals(AMSConstants.CHARACTERISTIC_ENTITY_ATTRIBUTE);
    }

    @Override
    public void handleCharacteristic(BluetoothGattCharacteristic characteristic) {
        try {
            byte[] packet = characteristic.getValue();
            if (packet != null) {
                String attribute = characteristic.getStringValue(3);
                Log.d(LOG_TAG, "AMS ATTRIBUTE: " + attribute);

                switch (packet[0]) {
                    case 0:
                        switch (packet[1]) {
                            case 1:
                                if (attribute.length() > 0) {
                                    mediaPlaying = !attribute.substring(0, 1).equals("0");

                                    if (attribute.equals("0,,")) {
                                        mediaHidden = true;
                                        mServiceUtils.cancelNotification(null, NOTIFICATION_MEDIA);
                                    }
                                }

                                break;
                        }

                        updatePlaybackState();
                        break;
                    case 2:
                        boolean truncated = (packet[2] & 1) != 0;

                        switch (packet[1]) {
                            case 0:
                                mediaArtist = attribute;

                                if (truncated) {
                                    mediaArtist += "...";
                                    Command attributeCommand = new Command(AMSConstants.SERVICE_UUID, AMSConstants.CHARACTERISTIC_ENTITY_ATTRIBUTE, new byte[] {
                                            AMSConstants.EntityIDTrack,
                                            AMSConstants.TrackAttributeIDArtist
                                    });
                                    attributeCommand.setImportance(Command.IMPORTANCE_MIN);

                                    mServiceUtils.addCommandToQueue(attributeCommand);

                                    Command readAttributeCommand = new Command(AMSConstants.SERVICE_UUID, AMSConstants.CHARACTERISTIC_ENTITY_ATTRIBUTE);
                                    readAttributeCommand.setImportance(Command.IMPORTANCE_MIN);
                                    mServiceUtils.addCommandToQueue(readAttributeCommand);
                                }
                                else if (attribute.length() == 0) {
                                    mediaArtist = null;
                                }

                                break;
                            case 2:
                                mediaTitle = attribute;

                                if (truncated) {
                                    mediaTitle += "...";
                                    Command attributeCommand = new Command(AMSConstants.SERVICE_UUID, AMSConstants.CHARACTERISTIC_ENTITY_ATTRIBUTE, new byte[] {
                                            AMSConstants.EntityIDTrack,
                                            AMSConstants.TrackAttributeIDTitle
                                    });
                                    attributeCommand.setImportance(Command.IMPORTANCE_MIN);

                                    mServiceUtils.addCommandToQueue(attributeCommand);

                                    Command readAttributeCommand = new Command(AMSConstants.SERVICE_UUID, AMSConstants.CHARACTERISTIC_ENTITY_ATTRIBUTE);
                                    readAttributeCommand.setImportance(Command.IMPORTANCE_MIN);
                                    mServiceUtils.addCommandToQueue(readAttributeCommand);
                                }
                                else if (attribute.length() == 0) {
                                    mediaTitle = null;
                                }

                                break;
                        }

                        updateMetadata();

                        break;
                }


                if (mediaPlaying || !mediaHidden) {
                    buildMediaNotification();
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendPlay() {
        Command remoteCommand = new Command(AMSConstants.SERVICE_UUID, AMSConstants.CHARACTERISTIC_REMOTE_COMMAND, new byte[]{
                AMSConstants.RemoteCommandIDTogglePlayPause
        });
        remoteCommand.setImportance(Command.IMPORTANCE_MIN);

        mServiceUtils.addCommandToQueue(remoteCommand);
    }

    private void buildMediaNotification() {
        mediaHidden = false;

        Bitmap background = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.bg_media);


        // Build pending intent for when the user swipes the card away
        Intent deleteIntent = new Intent(Constants.IA_HIDE_MEDIA);
        PendingIntent deleteAction = PendingIntent.getBroadcast(mContext, 0, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.MediaStyle style = new Notification.MediaStyle()
                .setMediaSession(mSession.getSessionToken());

        Notification.WearableExtender wearableExtender = new Notification.WearableExtender()
                .setBackground(background)
                .setHintHideIcon(true);

        Notification.Builder builder = new Notification.Builder(mContext)
                .setSmallIcon(R.drawable.nic_music)
                .setDeleteIntent(deleteAction)
                .setStyle(style)
                .extend(wearableExtender)
                .setPriority(Notification.PRIORITY_LOW);

        //notificationManager.cancel(NOTIFICATION_MEDIA);
        mServiceUtils.notify(null, NOTIFICATION_MEDIA, builder.build());
    }
    
    private void prepareMediaSession() {
        mSession = new MediaSession(mContext, "Aerlink_session");
        mSession.setActive(true);
        mSession.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS | MediaSession.FLAG_HANDLES_MEDIA_BUTTONS);
        mSession.setPlaybackToRemote(new VolumeProvider(VolumeProvider.VOLUME_CONTROL_RELATIVE, 50, 100) {

            @Override
            public void onAdjustVolume(int direction) {
                super.onAdjustVolume(direction);

                try {
                    Command volumeCommand;
                    if (direction == 1) {
                        volumeCommand = new Command(AMSConstants.SERVICE_UUID, AMSConstants.CHARACTERISTIC_REMOTE_COMMAND, new byte[]{
                                AMSConstants.RemoteCommandIDVolumeUp
                        });
                    } else {
                        volumeCommand = new Command(AMSConstants.SERVICE_UUID, AMSConstants.CHARACTERISTIC_REMOTE_COMMAND, new byte[]{
                                AMSConstants.RemoteCommandIDVolumeDown
                        });
                    }

                    mServiceUtils.addCommandToQueue(volumeCommand);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        });

        mSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                super.onPlay();
                Log.e(LOG_TAG, "onPlay");

                Command remoteCommand = new Command(AMSConstants.SERVICE_UUID, AMSConstants.CHARACTERISTIC_REMOTE_COMMAND, new byte[]{
                        AMSConstants.RemoteCommandIDTogglePlayPause
                });
                remoteCommand.setImportance(Command.IMPORTANCE_MIN);

                mServiceUtils.addCommandToQueue(remoteCommand);
            }

            @Override
            public void onPause() {
                super.onPause();
                Log.e(LOG_TAG, "onPause");

                Command remoteCommand = new Command(AMSConstants.SERVICE_UUID, AMSConstants.CHARACTERISTIC_REMOTE_COMMAND, new byte[]{
                        AMSConstants.RemoteCommandIDTogglePlayPause
                });
                remoteCommand.setImportance(Command.IMPORTANCE_MIN);

                mServiceUtils.addCommandToQueue(remoteCommand);
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                Log.e(LOG_TAG, "onSkipToNext");

                Command remoteCommand = new Command(AMSConstants.SERVICE_UUID, AMSConstants.CHARACTERISTIC_REMOTE_COMMAND, new byte[]{
                        AMSConstants.RemoteCommandIDNextTrack
                });
                remoteCommand.setImportance(Command.IMPORTANCE_MIN);

                mServiceUtils.addCommandToQueue(remoteCommand);
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                Log.e(LOG_TAG, "onSkipToPrevious");

                Command remoteCommand = new Command(AMSConstants.SERVICE_UUID, AMSConstants.CHARACTERISTIC_REMOTE_COMMAND, new byte[]{
                        AMSConstants.RemoteCommandIDPreviousTrack
                });
                remoteCommand.setImportance(Command.IMPORTANCE_MIN);

                mServiceUtils.addCommandToQueue(remoteCommand);
            }

            @Override
            public boolean onMediaButtonEvent(@NonNull Intent mediaButtonIntent) {
                return super.onMediaButtonEvent(mediaButtonIntent);
            }

        });

        updatePlaybackState();
        updateMetadata();
    }

    private void updatePlaybackState() {
        long position = PlaybackState.PLAYBACK_POSITION_UNKNOWN;
        PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackState.ACTION_SKIP_TO_NEXT);
        stateBuilder.setState(mediaPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, position, 1.0f);
        mSession.setPlaybackState(stateBuilder.build());
    }

    private void updateMetadata() {
        MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder();

        if (mediaTitle == null && mediaArtist == null) {
            metadataBuilder.putString(MediaMetadata.METADATA_KEY_ARTIST, "No info");
        }
        else {
            // And at minimum the title and artist for legacy support
            metadataBuilder.putString(MediaMetadata.METADATA_KEY_TITLE, mediaTitle);
            metadataBuilder.putString(MediaMetadata.METADATA_KEY_ARTIST, mediaArtist);
        }

        // Add any other fields you have for your data as well
        mSession.setMetadata(metadataBuilder.build());
    }


    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            mediaHidden = true;

            if (mediaPlaying) {
                buildMediaNotification();
            }
        }

    };

}
