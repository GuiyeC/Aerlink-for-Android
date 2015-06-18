package com.codegy.aerlink;

import java.util.UUID;

/**
 * Created by Guiye on 19/5/15.
 */
public class Constants {

    // ALS - Aerlink Service
    public static final UUID SERVICE_UUID = UUID.fromString("0d6a2c7d-392a-4781-b432-db437f70f643");
    public static final String CHARACTERISTIC_DATA_CAMERA_REMOTE   = "7be5ff0a-e736-453a-9257-c94fffdc6a97";
    public static final String CHARACTERISTIC_ACTION_CAMERA_REMOTE = "19c3577c-0952-4dc1-b03e-3db3fffc381a";
    public static final String CHARACTERISTIC_DATA_REMINDERS       = "1e082d2c-c279-4f49-a63c-a70c74f562d6";
    public static final String CHARACTERISTIC_ACTION_REMINDERS     = "b708a912-5d7e-4baf-8a63-f915c6717050";
    public static final String CHARACTERISTIC_FIND_DEVICE          = "e476843e-02c9-4ac5-9ca8-6bda217b225f";

    // Shared Preferences Keys
    public static final String SPK_COLOR_BACKGROUNDS         = "SPK_COLOR_BACKGROUNDS";
    public static final String SPK_BATTERY_UPDATES             = "SPK_BATTERY_UPDATES";
    public static final String SPK_COMPLETE_BATTERY_INFO = "SPK_COMPLETE_BATTERY_INFO";
    public static final String SPK_REMINDERS_DATA               = "SPK_REMINDERS_DATA";

    // Intent Actions
    public static final String IA_SERVICE_READY                         = "com.codegy.IA_SERVICE_READY";
    public static final String IA_SERVICE_NOT_READY                 = "com.codegy.IA_SERVICE_NOT_READY";
    public static final String IA_TRY_CONNECTING                       = "com.codegy.IA_TRY_CONNECTING";
    public static final String IA_COLOR_BACKGROUNDS_CHANGED = "com.codegy.IA_COLOR_BACKGROUNDS_CHANGED";
    public static final String IA_BATTERY_UPDATES_CHANGED     = "com.codegy.IA_BATTERY_UPDATES_CHANGED";
    public static final String IA_POSITIVE                                   = "com.codegy.IA_POSITIVE";
    public static final String IA_NEGATIVE                                   = "com.codegy.IA_NEGATIVE";
    public static final String IA_DELETE                                       = "com.codegy.IA_DELETE";
    public static final String IA_HIDE_MEDIA                               = "com.codegy.IA_HIDE_MEDIA";
    public static final String IA_HIDE_BATTERY                           = "com.codegy.IA_HIDE_BATTERY";
    public static final String IA_END_CALL                                   = "com.codegy.IA_END_CALL";

    // Intent Extras
    public static final String IE_NOTIFICATION_UID         = "IE_NOTIFICATION_UID";
    public static final String IE_NOTIFICATION_TITLE     = "IE_NOTIFICATION_TITLE";
    public static final String IE_NOTIFICATION_MESSAGE = "IE_NOTIFICATION_MESSAGE";
    public static final String IE_CAMERA_IMAGE = "IE_CAMERA_IMAGE";

}
