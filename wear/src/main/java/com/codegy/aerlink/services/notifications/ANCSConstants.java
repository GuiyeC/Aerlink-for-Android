package com.codegy.aerlink.services.notifications;

import java.util.UUID;

/**
 * Created by Guiye on 19/5/15.
 */
public class ANCSConstants {

    // ANCS - Apple Notification Center Service Profile
    public static final UUID SERVICE_UUID = UUID.fromString("7905f431-b5ce-4e99-a40f-4b1e122d00d0");
    public static final String CHARACTERISTIC_NOTIFICATION_SOURCE = "9fbf120d-6301-42d9-8c58-25e699a21dbd";
    public static final String CHARACTERISTIC_DATA_SOURCE         = "22eac6e9-24d6-4bb5-be44-b36ace7c7bfb";
    public static final String CHARACTERISTIC_CONTROL_POINT       = "69d1d8f3-45e1-49a8-9821-9bbdfdaad9d9";

    public static final byte EventIDNotificationAdded    = 0x00;
    public static final byte EventIDNotificationModified = 0x01;
    public static final byte EventIDNotificationRemoved  = 0x02;

    public static final byte CommandIDGetNotificationAttributes = 0x00;
    public static final byte CommandIDGetAppAttributes          = 0x01;
    public static final byte CommandIDPerformNotificationAction = 0x02;

    public static final byte NotificationAttributeIDAppIdentifier       = 0x00;
    public static final byte NotificationAttributeIDTitle               = 0x01;
    public static final byte NotificationAttributeIDSubtitle            = 0x02;
    public static final byte NotificationAttributeIDMessage             = 0x03;
    public static final byte NotificationAttributeIDMessageSize         = 0x04;
    public static final byte NotificationAttributeIDDate                = 0x05;
    public static final byte NotificationAttributeIDPositiveActionLabel = 0x06;
    public static final byte NotificationAttributeIDNegativeActionLabel = 0x07;

    public static final byte ActionIDPositive = 0x00;
    public static final byte ActionIDNegative = 0x01;

}
