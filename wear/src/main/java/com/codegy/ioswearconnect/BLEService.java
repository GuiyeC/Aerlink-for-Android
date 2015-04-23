package com.codegy.ioswearconnect;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.*;
import android.graphics.*;
import android.media.MediaMetadata;
import android.media.VolumeProvider;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.*;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;


/**
 * Created by codegy on 15/03/15.
 */
public class BLEService extends Service implements BLEManager.BLEManagerCallback {

    public static final int NOTIFICATION_REGULAR = 1000;
    public static final int NOTIFICATION_MEDIA = 1001;
    public static final int NOTIFICATION_BATTERY = 1002;
    public static final int NOTIFICATION_HELP = 2000;

    private static final long SCREEN_TIME_OUT = 1000;
    private static final long VIBRATION_PATTERN[] = { 200, 100, 200, 100 };
    private static final long SILENT_VIBRATION_PATTERN[] = { 200, 110 };
    
    private static final String TAG_LOG = "BLEService";
    public static final String INTENT_EXTRA_UID = "INTENT_EXTRA_UID";



    private BLEManager mManager;

    private NotificationManagerCompat notificationManager;
    private int notificationNumber = 0;

    private Vibrator vibrator;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;

    private MediaSession mSession;
    private boolean mediaPlaying;
    private boolean mediaHidden = true;
    private String mediaTitle;
    private String mediaArtist;

    private int batteryLevel;
    private boolean batteryUpdates;
    private boolean colorBackgrounds;


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("Service", "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(BLEService.this);
        batteryUpdates = sp.getBoolean(Constants.SPK_BATTERY_UPDATES, true);
        colorBackgrounds = sp.getBoolean(Constants.SPK_COLOR_BACKGROUNDS, false);


        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.IA_POSITIVE);
        intentFilter.addAction(Constants.IA_NEGATIVE);
        intentFilter.addAction(Constants.IA_DELETE);
        intentFilter.addAction(Constants.IA_HIDE_MEDIA);
        intentFilter.addAction(Constants.IA_BATTERY_UPDATES_CHANGED);
        intentFilter.addAction(Constants.IA_COLOR_BACKGROUNDS_CHANGED);
        registerReceiver(mBroadcastReceiver, intentFilter);


        notificationManager = NotificationManagerCompat.from(getApplicationContext());

        // Show help card
        onConnectionStateChange(false);

        prepareMediaSession();

        mManager = new BLEManager(getApplicationContext(), this);


        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG_LOG, "~~~~~~~~ service onDestroy");

        try {
            unregisterReceiver(mBroadcastReceiver);

            if (mSession != null) {
                mSession.release();
            }

            reset();

            if (mManager != null) {
                mManager.close();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        
        super.onDestroy();
    }

    private void reset() {
        notificationManager.cancelAll();
        notificationNumber = 0;

        mediaPlaying = false;
        mediaHidden = true;
        mediaArtist = null;
        mediaTitle = null;

        batteryLevel = -1;
    }
    
    private Vibrator getVibrator() {
        if (vibrator == null) {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        }
        
        return vibrator;
    }

    private PowerManager getPowerManager() {
        if (powerManager == null) {
            powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        }

        return powerManager;
    }

    private PowerManager.WakeLock getWakeLock() {
        if (wakeLock == null) {
            wakeLock = getPowerManager().newWakeLock((PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP), "iOS_WEAR_TAG");
        }

        return wakeLock;
    }

    private void wakeScreen() {
        if (!getWakeLock().isHeld()) {
            Log.d(TAG_LOG, "Waking Screen");
            getWakeLock().acquire(SCREEN_TIME_OUT);
        }
    }

    @Override
    public void onConnectionStateChange(boolean connected) {
        Bitmap background = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        background.eraseColor(0);

        Notification.WearableExtender wearableExtender = new Notification.WearableExtender()
                .setBackground(background);

        if (!connected) {
            // Clear current data
            reset();

            // Add help page
            wearableExtender.addPage(new Notification.Builder(this)
                    .setContentTitle(getString(R.string.help))
                    .setContentText(getString(R.string.help_how_to))
                    .build());
        }

        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(connected ? R.string.help_title_connected : R.string.help_title_searching))
                .setContentText(getString(connected ? R.string.help_subtitle_connected : R.string.help_subtitle_searching))
                .setPriority(Notification.PRIORITY_MAX)
                .setOngoing(!connected)
                .extend(wearableExtender);

        notificationManager.cancel(NOTIFICATION_HELP);
        notificationManager.notify(NOTIFICATION_HELP, builder.build());
    }

    @Override
    public void onIncomingCall(NotificationData notificationData) {
        Intent phoneIntent = new Intent(this, PhoneActivity.class);
        phoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        phoneIntent.putExtra(INTENT_EXTRA_UID, notificationData.getUID());
        phoneIntent.putExtra(PhoneActivity.EXTRA_TITLE, notificationData.getTitle());
        phoneIntent.putExtra(PhoneActivity.EXTRA_MESSAGE, notificationData.getMessage());

        startActivity(phoneIntent);
    }

    @Override
    public void onCallEnded() {
        Log.d(TAG_LOG, "Call ended");
        sendBroadcast(new Intent(PhoneActivity.ACTION_END_CALL));
    }

    @Override
    public void onNotificationReceived(NotificationData notificationData) {
        Bitmap background;
        if (notificationData.getBackground() != -1) {
            background = BitmapFactory.decodeResource(getResources(), notificationData.getBackground());
        }
        else {
            background = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            if (colorBackgrounds) {
                background.eraseColor(notificationData.getBackgroundColor());
            }
            else {
                background.eraseColor(0);
            }
        }


        // Build pending intent for when the user swipes the card away
        Intent deleteIntent = new Intent(Constants.IA_DELETE);
        deleteIntent.putExtra(INTENT_EXTRA_UID, notificationData.getUID());
        PendingIntent deleteAction = PendingIntent.getBroadcast(getApplicationContext(), notificationNumber, deleteIntent, 0);

        NotificationCompat.WearableExtender wearableExtender = new NotificationCompat.WearableExtender()
                .setBackground(background);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getApplicationContext())
                .setContentTitle(notificationData.getTitle())
                .setContentText(notificationData.getMessage())
                .setSmallIcon(notificationData.getAppIcon())
                .setGroup(notificationData.getAppId())
                .setDeleteIntent(deleteAction)
                .setPriority(Notification.PRIORITY_HIGH)
                .extend(wearableExtender);

        // Build positive action intent only if available
        if (notificationData.getPositiveAction() != null) {
            Intent positiveIntent = new Intent(Constants.IA_POSITIVE);
            positiveIntent.putExtra(INTENT_EXTRA_UID, notificationData.getUID());
            PendingIntent positiveAction = PendingIntent.getBroadcast(getApplicationContext(), notificationNumber, positiveIntent, 0);

            notificationBuilder.addAction(R.drawable.ic_action_accept, notificationData.getPositiveAction(), positiveAction);
        }
        // Build negative action intent only if available
        if (notificationData.getNegativeAction() != null) {
            Intent negativeIntent = new Intent(Constants.IA_NEGATIVE);
            negativeIntent.putExtra(INTENT_EXTRA_UID, notificationData.getUID());
            PendingIntent negativeAction = PendingIntent.getBroadcast(getApplicationContext(), notificationNumber, negativeIntent, 0);

            notificationBuilder.addAction(R.drawable.ic_action_remove, notificationData.getNegativeAction(), negativeAction);
        }

        // Build and notify
        Notification notification = notificationBuilder.build();
        notificationManager.notify(notificationData.getUIDString(), NOTIFICATION_REGULAR, notification);


        notificationNumber++;


        if (!notificationData.isPreExisting()) {
            if (!notificationData.isSilent()) {
                getVibrator().vibrate(VIBRATION_PATTERN, -1);
                wakeScreen();
            }
            else {
                getVibrator().vibrate(SILENT_VIBRATION_PATTERN, -1);
            }
        }
    }

    @Override
    public void onNotificationCanceled(String notificationId) {
        notificationManager.cancel(notificationId, NOTIFICATION_REGULAR);
    }

    @Override
    public void onBatteryLevelChanged(int newBatteryLevel) {
        batteryLevel = newBatteryLevel;

        if (!batteryUpdates || batteryLevel == -1) {
            return;
        }

        buildBatteryNotification();
    }

    @Override
    public void onMediaDataUpdated(byte[] packet, String attribute) {
        try {
            if (packet != null) {
                Log.d(TAG_LOG, "AMS ATTRIBUTE: " + attribute);

                switch (packet[0]) {
                    case 0:
                        switch (packet[1]) {
                            case 1:
                                mediaPlaying = !attribute.substring(0, 1).equals("0");
                                break;
                        }

                        long position = PlaybackState.PLAYBACK_POSITION_UNKNOWN;
                        PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                                .setActions(getAvailableActions());
                        stateBuilder.setState(mediaPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, position, 1.0f);
                        mSession.setPlaybackState(stateBuilder.build());

                        if (mediaPlaying || !mediaHidden) {
                            buildMediaNotification();
                        }
                        break;
                    case 2:
                        boolean truncated = (packet[2] & 1) != 0;

                        switch (packet[1]) {
                            case 0:
                                mediaArtist = attribute;

                                if (truncated) {
                                    mediaArtist += "...";
                                    /*
                                    Command attributeCommand = new Command(ServicesConstants.UUID_AMS, ServicesConstants.CHARACTERISTIC_ENTITY_ATTRIBUTE, new byte[] {
                                            ServicesConstants.EntityIDTrack,
                                            ServicesConstants.TrackAttributeIDArtist
                                    });
                                    pendingCommands.add(attributeCommand);

                                    sendCommand();
                                    */
                                }
                                break;
                            case 2:
                                mediaTitle = attribute;

                                if (truncated) {
                                    mediaTitle += "...";
                                    /*
                                    Command attributeCommand = new Command(ServicesConstants.UUID_AMS, ServicesConstants.CHARACTERISTIC_ENTITY_ATTRIBUTE, new byte[] {
                                            ServicesConstants.EntityIDTrack,
                                            ServicesConstants.TrackAttributeIDTitle
                                    });
                                    pendingCommands.add(attributeCommand);

                                    sendCommand();
                                    */
                                }
                                break;
                        }

                        updateMetadata();
                        break;
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // perform notification action: immediately
            // delete intent: after 7~8sec.
            if (action.equals(Constants.IA_POSITIVE) | action.equals(Constants.IA_NEGATIVE) | action.equals(Constants.IA_DELETE)) {
                try {
                    byte[] UID = intent.getByteArrayExtra(INTENT_EXTRA_UID);
                    String notificationId = new String(UID);

                    if (!action.equals(Constants.IA_DELETE)) {
                        // Dismiss notification
                        notificationManager.cancel(notificationId, NOTIFICATION_REGULAR);
                    }
                    
                    byte actionId = ServicesConstants.ActionIDPositive;
                    if (action.equals(Constants.IA_NEGATIVE) | action.equals(Constants.IA_DELETE)) {
                        actionId = ServicesConstants.ActionIDNegative;
                    }
                    
                    // Perform user selected action
                    byte[] performActionPacket = {
                            ServicesConstants.CommandIDPerformNotificationAction,

                            // Notification UID
                            UID[0], UID[1], UID[2], UID[3],

                            // Action Id
                            actionId
                    };

                    Command performActionCommand = new Command(ServicesConstants.UUID_ANCS, ServicesConstants.CHARACTERISTIC_CONTROL_POINT, performActionPacket);

                    mManager.addCommandToQueue(performActionCommand);
                } 
                catch (Exception e) {
                    Log.d(TAG_LOG, "error");
                    e.printStackTrace();
                }
            }
            else if (action.equals(Constants.IA_HIDE_MEDIA)) {
                mediaHidden = true;

                if (mediaPlaying) {
                    buildMediaNotification();
                }
            }
            else if (action.equals(Constants.IA_BATTERY_UPDATES_CHANGED)) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(BLEService.this);
                batteryUpdates = sp.getBoolean(Constants.SPK_BATTERY_UPDATES, true);

                if (batteryUpdates) {
                    buildBatteryNotification();
                }
                else {
                    notificationManager.cancel(NOTIFICATION_BATTERY);
                }
            }
            else if (action.equals(Constants.IA_COLOR_BACKGROUNDS_CHANGED)) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(BLEService.this);
                colorBackgrounds = sp.getBoolean(Constants.SPK_COLOR_BACKGROUNDS, false);

                if (!mediaHidden) {
                    buildMediaNotification();
                }
            }
        }
        
    };

    private void buildBatteryNotification() {
        Bitmap background = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        background.eraseColor(0);

        int batteryIcon;

        if (batteryLevel > 85) {
            batteryIcon = R.drawable.ic_battery_5;
        }
        else if (batteryLevel > 65) {
            batteryIcon = R.drawable.ic_battery_4;
        }
        else if (batteryLevel > 45) {
            batteryIcon = R.drawable.ic_battery_3;
        }
        else if (batteryLevel > 25) {
            batteryIcon = R.drawable.ic_battery_2;
        }
        else {
            batteryIcon = R.drawable.ic_battery_1;
        }


        Notification.WearableExtender wearableExtender = new Notification.WearableExtender()
                .setBackground(background);

        Notification.Builder builder = new Notification.Builder( this )
                .setSmallIcon(batteryIcon)
                .setContentTitle(getString(R.string.battery_level))
                .setContentText(batteryLevel + "%")
                .extend(wearableExtender)
                .setPriority(Notification.PRIORITY_MIN);

        notificationManager.cancel(NOTIFICATION_BATTERY);
        notificationManager.notify(NOTIFICATION_BATTERY, builder.build());
    }

    private void buildMediaNotification() {
        mediaHidden = false;

        Bitmap background = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        background.eraseColor(Color.rgb(230, 16, 71));

        // Build pending intent for when the user swipes the card away
        Intent deleteIntent = new Intent(Constants.IA_HIDE_MEDIA);
        PendingIntent deleteAction = PendingIntent.getBroadcast(getApplicationContext(), 0, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.MediaStyle style = new Notification.MediaStyle()
                .setMediaSession(mSession.getSessionToken());

        Notification.WearableExtender wearableExtender = new Notification.WearableExtender()
                .setBackground(background)
                .setHintHideIcon(true);

        Notification.Builder builder = new Notification.Builder( this )
                .setSmallIcon(R.drawable.ic_music)
                 .setDeleteIntent(deleteAction)
                .setStyle(style)
                .extend(wearableExtender)
                .setPriority(Notification.PRIORITY_LOW);

        notificationManager.cancel(NOTIFICATION_MEDIA);
        notificationManager.notify(NOTIFICATION_MEDIA, builder.build());
    }

    private void prepareMediaSession() {
        mSession = new MediaSession(getApplicationContext(), "iOS_Wear_session");
        mSession.setActive(true);
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS | MediaSession.FLAG_HANDLES_MEDIA_BUTTONS);
        mSession.setPlaybackToRemote(new VolumeProvider(VolumeProvider.VOLUME_CONTROL_RELATIVE, 50, 100) {

            @Override
            public void onAdjustVolume(int direction) {
                super.onAdjustVolume(direction);

                try {
                    Command volumeCommand;
                    if (direction == 1) {
                        volumeCommand = new Command(ServicesConstants.UUID_AMS, ServicesConstants.CHARACTERISTIC_REMOTE_COMMAND, new byte[] {
                                ServicesConstants.RemoteCommandIDVolumeUp
                        });
                    }
                    else {
                        volumeCommand = new Command(ServicesConstants.UUID_AMS, ServicesConstants.CHARACTERISTIC_REMOTE_COMMAND, new byte[] {
                                ServicesConstants.RemoteCommandIDVolumeDown
                        });
                    }

                    mManager.addCommandToQueue(volumeCommand);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        });

        mSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                super.onPlay();
                Log.e(TAG_LOG, "onPlay");

                Command remoteCommand = new Command(ServicesConstants.UUID_AMS, ServicesConstants.CHARACTERISTIC_REMOTE_COMMAND, new byte[] {
                        ServicesConstants.RemoteCommandIDTogglePlayPause
                });

                mManager.addCommandToQueue(remoteCommand);
            }

            @Override
            public void onPause() {
                super.onPause();
                Log.e(TAG_LOG, "onPause");

                Command remoteCommand = new Command(ServicesConstants.UUID_AMS, ServicesConstants.CHARACTERISTIC_REMOTE_COMMAND, new byte[] {
                        ServicesConstants.RemoteCommandIDTogglePlayPause
                });

                mManager.addCommandToQueue(remoteCommand);
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                Log.e(TAG_LOG, "onSkipToNext");

                Command remoteCommand = new Command(ServicesConstants.UUID_AMS, ServicesConstants.CHARACTERISTIC_REMOTE_COMMAND, new byte[] {
                        ServicesConstants.RemoteCommandIDNextTrack
                });

                mManager.addCommandToQueue(remoteCommand);
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                Log.e(TAG_LOG, "onSkipToPrevious");

                Command remoteCommand = new Command(ServicesConstants.UUID_AMS, ServicesConstants.CHARACTERISTIC_REMOTE_COMMAND, new byte[] {
                        ServicesConstants.RemoteCommandIDPreviousTrack
                });

                mManager.addCommandToQueue(remoteCommand);
            }

            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                return super.onMediaButtonEvent(mediaButtonIntent);
            }

        });


        long position = PlaybackState.PLAYBACK_POSITION_UNKNOWN;
        PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                .setActions(getAvailableActions());
        stateBuilder.setState(mediaPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, position, 1.0f);
        mSession.setPlaybackState(stateBuilder.build());


        MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder();
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_ARTIST, "No info");
        mSession.setMetadata(metadataBuilder.build());
    }

    private long getAvailableActions() {
        long actions = PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | PlaybackState.ACTION_SKIP_TO_NEXT;

        /*
        if (mCurrentIndexOnQueue > 0) {
            actions |= PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        }
        if (mCurrentIndexOnQueue < mPlayingQueue.size() - 1) {
            actions |= PlaybackState.ACTION_SKIP_TO_NEXT;
        }
        */

        return actions;
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

        if (mediaPlaying || !mediaHidden) {
            buildMediaNotification();
        }
    }
}