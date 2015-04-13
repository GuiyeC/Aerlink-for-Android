package com.shiitakeo.android_wear_for_ios;

import android.graphics.Color;

/**
 * Created by kusabuka on 15/03/15.
 */
public class NotificationDataManager {

    private static final int IC_PHONE = R.drawable.ic_phone;
    private static final int IC_IMESSAGE = R.drawable.ic_imessage;
    private static final int IC_APPSTORE = R.drawable.ic_appstore;
    private static final int IC_FACEBOOK = R.drawable.ic_facebook;
    private static final int IC_MESSENGER = R.drawable.ic_messenger;
    private static final int IC_LINE = R.drawable.line;
    private static final int IC_TWITTER = R.drawable.ic_twitter;
    private static final int IC_GMAIL = R.drawable.gmail;
    private static final int IC_WHATSAPP = R.drawable.ic_whatsapp;
    private static final int IC_MAIL = R.drawable.mail;
    private static final int IC_CALENDAR = R.drawable.calendar;
    private static final int IC_HANGOUTS = R.drawable.ic_hangouts;
    private static final int IC_TELEGRAM = R.drawable.ic_telegram;
    private static final int IC_INBOX = R.drawable.ic_inbox;
    private static final int IC_TEEVEE = R.drawable.ic_teevee;
    private static final int IC_LINKEDIN = R.drawable.ic_linkedin;

    public static void updateData(NotificationData notificationData) {
        boolean messageApp = false;

        switch (notificationData.getAppId()) {
            case "com.apple.mobilephone":
                notificationData.setAppIcon(IC_PHONE);
                notificationData.setBackgroundColor(Color.rgb(228, 240, 249));

                break;
            case "com.apple.MobileSMS":
                notificationData.setAppIcon(IC_IMESSAGE);
                notificationData.setBackgroundColor(Color.rgb(228, 240, 249));

                break;
            case "com.apple.AppStore":
                notificationData.setAppIcon(IC_APPSTORE);
                notificationData.setBackgroundColor(Color.rgb(228, 240, 249));

                break;
            case "com.apple.mobilemail":
                notificationData.setAppIcon(IC_MAIL);

                break;
            case "com.apple.mobilecal":
                notificationData.setAppIcon(IC_CALENDAR);

                break;
            case "com.google.Gmail":
                notificationData.setAppIcon(IC_GMAIL);

                break;
            case "jp.naver.line":
                notificationData.setAppIcon(IC_LINE);

                break;
            case "com.facebook.Facebook":
                notificationData.setAppIcon(IC_FACEBOOK);
                notificationData.setBackgroundColor(Color.rgb(48, 77, 139));

                break;
            case "com.tapbots.Tweetbot":
                notificationData.setAppIcon(IC_TWITTER);

                break;
            case "com.tapbots.Tweetbot3":
                notificationData.setAppIcon(IC_TWITTER);

                break;
            case "com.google.hangouts":
                notificationData.setAppIcon(IC_HANGOUTS);
                notificationData.setBackgroundColor(Color.rgb(117, 180, 235));

                break;
            case "ph.telegra.Telegraph":
                notificationData.setAppIcon(IC_TELEGRAM);
                notificationData.setBackgroundColor(Color.rgb(41, 161, 218));

                messageApp = true;

                break;
            case "net.whatsapp.WhatsApp":
                notificationData.setAppIcon(IC_WHATSAPP);
                notificationData.setBackgroundColor(Color.rgb(67, 195, 84));

                messageApp = true;

                break;
            case "com.google.inbox":
                notificationData.setAppIcon(IC_INBOX);
                notificationData.setBackgroundColor(Color.rgb(66, 133, 244));

                break;
            case "com.crazyapps.TeeVee2":
                notificationData.setAppIcon(IC_TEEVEE);
                notificationData.setBackgroundColor(Color.rgb(246, 173, 2));

                break;
            case "com.linkedin.LinkedIn":
                notificationData.setAppIcon(IC_LINKEDIN);
                notificationData.setBackgroundColor(Color.rgb(1, 83, 128));

                break;
            case "com.facebook.Messenger":
                notificationData.setAppIcon(IC_MESSENGER);
                notificationData.setBackgroundColor(Color.rgb(0, 155, 255));

                break;
            default:
                break;
        }

        if (messageApp) {
            int index = notificationData.getMessage().indexOf(": ");
            if (index > 0) {
                String title = notificationData.getMessage().substring(0, index);
                String message = notificationData.getMessage().substring(index + 2);

                notificationData.setTitle(title);
                notificationData.setMessage(message);
            }
        }
    }
}
