package com.codegy.ioswearconnect;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.*;
import android.graphics.*;
import android.media.MediaMetadata;
import android.media.VolumeProvider;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.*;
import android.preference.PreferenceManager;
import android.util.Log;


/**
 * Created by codegy on 15/03/15.
 */
public class BLEService extends Service implements BLEManager.BLEManagerCallback {

    public static final int NOTIFICATION_SERVICE = 500;
    public static final int NOTIFICATION_REGULAR = 1000;
    public static final int NOTIFICATION_MEDIA = 1001;
    public static final int NOTIFICATION_BATTERY = 1002;
    public static final int NOTIFICATION_HELP = 2000;

    private static final long SCREEN_TIME_OUT = 1000;

    private static final long CONNECTION_PATTERN[] = { 80, 60 };
    private static final long DISCONNECTION_PATTERN[] = { 80, 90 };
    private static final long VIBRATION_PATTERN[] = { 200, 100, 200, 100 };
    private static final long SILENT_VIBRATION_PATTERN[] = { 200, 110 };
    
    private static final String TAG_LOG = "BLEService";
    public static final String INTENT_EXTRA_UID = "INTENT_EXTRA_UID";


    private IBinder mBinder = new ServiceBinder();

    private BLEManager mManager;

    private NotificationManager notificationManager;
    private int notificationNumber = 0;

    private Vibrator vibrator;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;

    private MediaSession mSession;
    private boolean mediaPlaying;
    private boolean mediaHidden = true;
    private String mediaTitle;
    private String mediaArtist;

    private int bondPassKey = -1;

    private int batteryLevel;
    private boolean completeBatteryInfo;
    private boolean batteryUpdates;
    private boolean batteryHidden = false;
    private boolean colorBackgrounds;


    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG_LOG, "in onBind");
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.v(TAG_LOG, "in onRebind");
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.v(TAG_LOG, "in onUnbind");
        return true;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d("Service", "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        /* // Could fix Moto 360
        Notification notification = new Notification(R.mipmap.ic_launcher, getText(R.string.app_name),
                System.currentTimeMillis());
        notification.priority = Notification.PRIORITY_MIN;
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        notification.setLatestEventInfo(this, getText(R.string.app_name),
                getText(R.string.app_name), pendingIntent);
        startForeground(NOTIFICATION_SERVICE, notification);
        */

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        batteryUpdates = sp.getBoolean(Constants.SPK_BATTERY_UPDATES, true);
        colorBackgrounds = sp.getBoolean(Constants.SPK_COLOR_BACKGROUNDS, false);
        completeBatteryInfo = sp.getBoolean(Constants.SPK_COMPLETE_BATTERY_INFO, false);


        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.IA_TRY_CONNECTING);
        intentFilter.addAction(Constants.IA_POSITIVE);
        intentFilter.addAction(Constants.IA_NEGATIVE);
        intentFilter.addAction(Constants.IA_DELETE);
        intentFilter.addAction(Constants.IA_HIDE_MEDIA);
        intentFilter.addAction(Constants.IA_HIDE_BATTERY);
        intentFilter.addAction(Constants.IA_BATTERY_UPDATES_CHANGED);
        intentFilter.addAction(Constants.IA_COLOR_BACKGROUNDS_CHANGED);
        intentFilter.addAction("android.bluetooth.device.action.PAIRING_REQUEST");
        registerReceiver(mBroadcastReceiver, intentFilter);


        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Show help card
        onConnectionStateChange(BLEManager.BLEManagerState.Disconnected);

        prepareMediaSession();

        mManager = new BLEManager(this, this);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG_LOG, "~~~~~~~~ service onDestroy");

        try {
            unregisterReceiver(mBroadcastReceiver);

            if (mSession != null) {
                mSession.release();
                mSession = null;
            }

            reset();

            if (mManager != null) {
                mManager.close();
                mManager = null;
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

    public BLEManager getManager() {
        return mManager;
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
    public void onConnectionStateChange(BLEManager.BLEManagerState state) {
        Bitmap background = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        background.eraseColor(0);

        Notification.WearableExtender wearableExtender = new Notification.WearableExtender()
                .setBackground(background);

        PendingIntent connectPendingIntent = null;
        if (state == BLEManager.BLEManagerState.Disconnected) {
            // Clear current data
            reset();

            // Build pending intent for when the user swipes the card away
            Intent connectIntent = new Intent(Constants.IA_TRY_CONNECTING);
            connectPendingIntent = PendingIntent.getBroadcast(this, 0, connectIntent, 0);

            // Add help page
            wearableExtender.setContentAction(0).
                    addPage(new Notification.Builder(this)
                    .setContentTitle(getString(R.string.help))
                    .setContentText(getString(R.string.help_how_to))
                    .build());
        }

        String title = null;
        String text = null;

        switch (state) {
            case NoBluetooth:
                title = getString(R.string.help_title_no_bluetooth);
                text = getString(R.string.help_no_bluetooth);
                break;
            case Disconnected:
                title = getString(R.string.help_title_disconnected);
                text = getString(R.string.help_disconnected);
                break;
            case Scanning:
                title = getString(R.string.help_title_scanning);
                text = getString(R.string.help_scanning);
                break;
            case Connecting:
                title = getString(R.string.help_title_connecting);
                text = getString(R.string.help_connecting);
                break;
            case Preparing:
                title = getString(R.string.help_title_preparing);
                text = getString(R.string.help_preparing);
                break;
            case Ready:
                title = getString(R.string.help_title_ready);
                text = getString(R.string.help_ready);
                break;
        }

        if (bondPassKey != -1) {
            text += "\nPIN: " + bondPassKey;
            bondPassKey = -1;
        }

        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(Notification.PRIORITY_MAX)
                .setOngoing(state != BLEManager.BLEManagerState.Ready && state != BLEManager.BLEManagerState.NoBluetooth)
                .extend(wearableExtender);

        if (connectPendingIntent != null) {
            Notification.Action connectAction = new Notification.Action.Builder(
                    android.R.drawable.ic_menu_search,
                    "Reconnect",
                    connectPendingIntent
            ).build();
            builder.addAction(connectAction);
        }

        //notificationManager.cancel(NOTIFICATION_HELP);
        notificationManager.notify(NOTIFICATION_HELP, builder.build());

        if (state == BLEManager.BLEManagerState.Ready) {
            getVibrator().vibrate(CONNECTION_PATTERN , -1);
        }
        else if (mManager!= null && state == BLEManager.BLEManagerState.Disconnected) {
            getVibrator().vibrate(DISCONNECTION_PATTERN, -1);
        }
    }

    @Override
    public void onIncomingCall(NotificationData notificationData) {
        try {
            Intent phoneIntent = new Intent(this, PhoneActivity.class);
            phoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            phoneIntent.putExtra(INTENT_EXTRA_UID, notificationData.getUID());
            phoneIntent.putExtra(PhoneActivity.EXTRA_TITLE, notificationData.getTitle());
            phoneIntent.putExtra(PhoneActivity.EXTRA_MESSAGE, notificationData.getMessage());

            startActivity(phoneIntent);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
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
        PendingIntent deleteAction = PendingIntent.getBroadcast(this, notificationNumber, deleteIntent, 0);

        Notification.WearableExtender wearableExtender = new Notification.WearableExtender()
                .setBackground(background);

        Notification.Builder notificationBuilder = new Notification.Builder(this)
                .setContentTitle(notificationData.getTitle())
                .setContentText(notificationData.getMessage())
                .setSmallIcon(notificationData.getAppIcon())
                .setGroup(notificationData.getAppId())
                .setDeleteIntent(deleteAction)
                .setPriority(Notification.PRIORITY_MAX)
                .extend(wearableExtender);

        // Build positive action intent only if available
        if (notificationData.getPositiveAction() != null) {
            Intent positiveIntent = new Intent(Constants.IA_POSITIVE);
            positiveIntent.putExtra(INTENT_EXTRA_UID, notificationData.getUID());
            PendingIntent positiveAction = PendingIntent.getBroadcast(this, notificationNumber, positiveIntent, 0);

            notificationBuilder.addAction(R.drawable.ic_action_accept, notificationData.getPositiveAction(), positiveAction);
        }
        // Build negative action intent only if available
        if (notificationData.getNegativeAction() != null) {
            Intent negativeIntent = new Intent(Constants.IA_NEGATIVE);
            negativeIntent.putExtra(INTENT_EXTRA_UID, notificationData.getUID());
            PendingIntent negativeAction = PendingIntent.getBroadcast(this, notificationNumber, negativeIntent, 0);

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
    public boolean shouldUpdateBatteryLevel() {
        return batteryLevel == -1;
    }

    @Override
    public void onBatteryLevelChanged(int newBatteryLevel) {
        // If the battery is running down, vibrate at 20, 15, 10 and 5
        if (batteryLevel > newBatteryLevel && newBatteryLevel <= 20 && newBatteryLevel % 5 == 0) {
            getVibrator().vibrate(SILENT_VIBRATION_PATTERN, -1);
        }

        batteryLevel = newBatteryLevel;

        if (completeBatteryInfo || batteryLevel <= 25 || batteryLevel % 10 == 0) {
            batteryHidden = false;
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
                                if (attribute.length() > 0) {
                                    mediaPlaying = !attribute.substring(0, 1).equals("0");

                                    if (attribute.equals("0,,")) {
                                        mediaHidden = true;
                                        notificationManager.cancel(NOTIFICATION_MEDIA);
                                    }
                                }

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
                                    Command attributeCommand = new Command(ServicesConstants.UUID_AMS, ServicesConstants.CHARACTERISTIC_ENTITY_ATTRIBUTE, new byte[] {
                                            ServicesConstants.EntityIDTrack,
                                            ServicesConstants.TrackAttributeIDArtist
                                    });
                                    attributeCommand.setImportance(1);

                                    mManager.addCommandToQueue(attributeCommand);
                                }
                                else if (attribute.length() == 0) {
                                    mediaArtist = null;
                                }

                                break;
                            case 2:
                                mediaTitle = attribute;

                                if (truncated) {
                                    mediaTitle += "...";
                                    Command attributeCommand = new Command(ServicesConstants.UUID_AMS, ServicesConstants.CHARACTERISTIC_ENTITY_ATTRIBUTE, new byte[] {
                                            ServicesConstants.EntityIDTrack,
                                            ServicesConstants.TrackAttributeIDTitle
                                    });
                                    attributeCommand.setImportance(1);

                                    mManager.addCommandToQueue(attributeCommand);
                                }
                                else if (attribute.length() == 0) {
                                    mediaTitle = null;
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

    @Override
    public void onMediaArtistUpdated(String mediaArtist) {
        this.mediaArtist = mediaArtist;

        updateMetadata();
    }

    @Override
    public void onMediaTitleUpdated(String mediaTitle) {
        this.mediaTitle = mediaTitle;

        updateMetadata();
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

                    // Dismiss notification
                    notificationManager.cancel(notificationId, NOTIFICATION_REGULAR);
                    
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
            else if (action.equals(Constants.IA_HIDE_BATTERY)) {
                batteryHidden = true;
            }
            else if (action.equals(Constants.IA_BATTERY_UPDATES_CHANGED)) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(BLEService.this);
                batteryUpdates = sp.getBoolean(Constants.SPK_BATTERY_UPDATES, true);
                completeBatteryInfo = sp.getBoolean(Constants.SPK_COMPLETE_BATTERY_INFO, false);

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
            else if (action.equals(Constants.IA_TRY_CONNECTING)) {
                mManager.tryConnectingAgain();
            }
            else if (intent.getAction().equals("android.bluetooth.device.action.PAIRING_REQUEST")) {
                try {
                    bondPassKey = intent.getExtras().getInt("android.bluetooth.device.extra.PAIRING_KEY");
                    Log.d(TAG_LOG, "Passkey: " + bondPassKey);

                    onConnectionStateChange(mManager.getState());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        
    };

    private void buildBatteryNotification() {
        if (!batteryUpdates || batteryHidden || batteryLevel == -1) {
            return;
        }

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

        // Build pending intent for when the user swipes the card away
        Intent deleteIntent = new Intent(Constants.IA_HIDE_BATTERY);
        PendingIntent deleteAction = PendingIntent.getBroadcast(this, 0, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.WearableExtender wearableExtender = new Notification.WearableExtender()
                .setBackground(background);

        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(batteryIcon)
                .setDeleteIntent(deleteAction)
                .setContentTitle(getString(R.string.battery_level))
                .setContentText(batteryLevel + "%")
                .extend(wearableExtender)
                .setPriority(Notification.PRIORITY_MIN);

        //notificationManager.cancel(NOTIFICATION_BATTERY);
        notificationManager.notify(NOTIFICATION_BATTERY, builder.build());
    }

    private void buildMediaNotification() {
        mediaHidden = false;

        Bitmap background = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        background.eraseColor(Color.rgb(230, 16, 71));

        // Build pending intent for when the user swipes the card away
        Intent deleteIntent = new Intent(Constants.IA_HIDE_MEDIA);
        PendingIntent deleteAction = PendingIntent.getBroadcast(this, 0, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.MediaStyle style = new Notification.MediaStyle()
                .setMediaSession(mSession.getSessionToken());

        Notification.WearableExtender wearableExtender = new Notification.WearableExtender()
                .setBackground(background)
                .setHintHideIcon(true);

        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_music)
                .setDeleteIntent(deleteAction)
                .setStyle(style)
                .extend(wearableExtender)
                .setPriority(Notification.PRIORITY_LOW);

        //notificationManager.cancel(NOTIFICATION_MEDIA);
        notificationManager.notify(NOTIFICATION_MEDIA, builder.build());
    }

    private void prepareMediaSession() {
        mSession = new MediaSession(this, "iOS_Wear_session");
        mSession.setActive(true);
        mSession.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS | MediaSession.FLAG_HANDLES_MEDIA_BUTTONS);
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

    public void takePicture() {
        if (mManager != null) {
            mManager.takePicture();
        }
    }

    public class ServiceBinder extends Binder {
        public BLEService getService() {
            return BLEService.this;
        }
    }
}