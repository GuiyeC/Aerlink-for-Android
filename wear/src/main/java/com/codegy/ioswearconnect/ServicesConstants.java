package com.codegy.ioswearconnect;

import java.util.UUID;

/**
 * Created by Guiye on 22/4/15.
 */
public class ServicesConstants {

    // ANCS - Apple Notification Center Service Profile
    public static final UUID UUID_ANCS = UUID.fromString("7905f431-b5ce-4e99-a40f-4b1e122d00d0");
    public static final String CHARACTERISTIC_NOTIFICATION_SOURCE = "9fbf120d-6301-42d9-8c58-25e699a21dbd";
    public static final String CHARACTERISTIC_DATA_SOURCE =         "22eac6e9-24d6-4bb5-be44-b36ace7c7bfb";
    public static final String CHARACTERISTIC_CONTROL_POINT =       "69d1d8f3-45e1-49a8-9821-9bbdfdaad9d9";

    public static final byte EventIDNotificationAdded = 0x00;
    public static final byte EventIDNotificationModified = 0x01;
    public static final byte EventIDNotificationRemoved = 0x02;

    public static final byte CommandIDGetNotificationAttributes = 0x00;
    public static final byte CommandIDGetAppAttributes = 0x01;
    public static final byte CommandIDPerformNotificationAction = 0x02;

    public static final byte NotificationAttributeIDAppIdentifier = 0x00;
    public static final byte NotificationAttributeIDTitle = 0x01;
    public static final byte NotificationAttributeIDSubtitle = 0x02;
    public static final byte NotificationAttributeIDMessage = 0x03;
    public static final byte NotificationAttributeIDMessageSize = 0x04;
    public static final byte NotificationAttributeIDDate = 0x05;
    public static final byte NotificationAttributeIDPositiveActionLabel = 0x06;
    public static final byte NotificationAttributeIDNegativeActionLabel = 0x07;

    public static final byte ActionIDPositive = 0x00;
    public static final byte ActionIDNegative = 0x01;


    // AMS - Apple Media Service Profile
    public static final UUID UUID_AMS = UUID.fromString("89d3502b-0f36-433a-8ef4-c502ad55f8dc");
    public static final String CHARACTERISTIC_REMOTE_COMMAND =   "9b3c81d8-57b1-4a8a-b8df-0e56f7ca51c2";
    public static final String CHARACTERISTIC_ENTITY_UPDATE =    "2f7cabce-808d-411f-9a0c-bb92ba96c102";
    public static final String CHARACTERISTIC_ENTITY_ATTRIBUTE = "c6b2f38c-23ab-46d8-a6ab-a3a870bbd5d7";

    public static final byte RemoteCommandIDPlay = 0x00;
    public static final byte RemoteCommandIDPause = 0x01;
    public static final byte RemoteCommandIDTogglePlayPause = 0x02;
    public static final byte RemoteCommandIDNextTrack = 0x03;
    public static final byte RemoteCommandIDPreviousTrack = 0x04;
    public static final byte RemoteCommandIDVolumeUp = 0x05;
    public static final byte RemoteCommandIDVolumeDown = 0x06;
    public static final byte RemoteCommandIDAdvanceRepeatMode = 0x07;
    public static final byte RemoteCommandIDAdvanceShuffleMode = 0x08;
    public static final byte RemoteCommandIDSkipForward = 0x09;
    public static final byte RemoteCommandIDSkipBackward = 0x0A;

    public static final byte EntityIDPlayer = 0x00;
    public static final byte EntityIDQueue = 0x01;
    public static final byte EntityIDTrack = 0x02;

    public static final byte TrackAttributeIDArtist = 0x00;
    public static final byte TrackAttributeIDAlbum = 0x01;
    public static final byte TrackAttributeIDTitle = 0x02;
    public static final byte TrackAttributeIDDuration = 0x03;

    public static final byte PlayerAttributeIDName = 0x00;
    public static final byte PlayerAttributeIDPlaybackInfo = 0x01;
    public static final byte PlayerAttributeIDVolume = 0x02;


    // BAS - Battery Service
    public static final UUID UUID_BAS = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
    public static final String CHARACTERISTIC_BATTERY_LEVEL = "00002a19-0000-1000-8000-00805f9b34fb";


    // CTS - Current Time Service
    public static final UUID UUID_CTS = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb");
    public static final String CHARACTERISTIC_CURRENT_TIME = "00002a2b-0000-1000-8000-00805f9b34fb";

}
