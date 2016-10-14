package com.codegy.aerlink.services.media;

import java.util.UUID;

/**
 * Created by Guiye on 19/5/15.
 */
public class AMSConstants {

    // AMS - Apple Media Service Profile
    public static final UUID SERVICE_UUID = UUID.fromString("89d3502b-0f36-433a-8ef4-c502ad55f8dc");
    public static final String CHARACTERISTIC_REMOTE_COMMAND   = "9b3c81d8-57b1-4a8a-b8df-0e56f7ca51c2";
    public static final String CHARACTERISTIC_ENTITY_UPDATE    = "2f7cabce-808d-411f-9a0c-bb92ba96c102";
    public static final String CHARACTERISTIC_ENTITY_ATTRIBUTE = "c6b2f38c-23ab-46d8-a6ab-a3a870bbd5d7";

    public static final byte RemoteCommandIDPlay               = 0x00;
    public static final byte RemoteCommandIDPause              = 0x01;
    public static final byte RemoteCommandIDTogglePlayPause    = 0x02;
    public static final byte RemoteCommandIDNextTrack          = 0x03;
    public static final byte RemoteCommandIDPreviousTrack      = 0x04;
    public static final byte RemoteCommandIDVolumeUp           = 0x05;
    public static final byte RemoteCommandIDVolumeDown         = 0x06;
    public static final byte RemoteCommandIDAdvanceRepeatMode  = 0x07;
    public static final byte RemoteCommandIDAdvanceShuffleMode = 0x08;
    public static final byte RemoteCommandIDSkipForward        = 0x09;
    public static final byte RemoteCommandIDSkipBackward       = 0x0A;

    public static final byte EntityIDPlayer = 0x00;
    public static final byte EntityIDQueue  = 0x01;
    public static final byte EntityIDTrack  = 0x02;

    public static final byte TrackAttributeIDArtist   = 0x00;
    public static final byte TrackAttributeIDAlbum    = 0x01;
    public static final byte TrackAttributeIDTitle    = 0x02;
    public static final byte TrackAttributeIDDuration = 0x03;

    public static final byte PlayerAttributeIDName         = 0x00;
    public static final byte PlayerAttributeIDPlaybackInfo = 0x01;
    public static final byte PlayerAttributeIDVolume       = 0x02;

}
