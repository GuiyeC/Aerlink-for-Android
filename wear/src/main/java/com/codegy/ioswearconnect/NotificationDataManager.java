package com.codegy.ioswearconnect;

import android.graphics.Color;

/**
 * Created by kusabuka on 15/03/15.
 */
public class NotificationDataManager {

    public static void updateData(NotificationData notificationData) {
        boolean messageApp = false;

        switch (notificationData.getAppId()) {
            case "com.apple.mobilephone":
                notificationData.setAppIcon(R.drawable.ic_phone);
                notificationData.setBackgroundColor(Color.rgb(228, 240, 249));

                break;
            case "com.apple.MobileSMS":
                notificationData.setAppIcon(R.drawable.ic_imessage);
                notificationData.setBackgroundColor(Color.rgb(228, 240, 249));

                break;
            case "com.apple.AppStore":
                notificationData.setAppIcon(R.drawable.ic_appstore);
                notificationData.setBackgroundColor(Color.rgb(228, 240, 249));

                break;
            case "com.apple.mobilemail":
                notificationData.setAppIcon(R.drawable.ic_mail);
                notificationData.setBackgroundColor(Color.rgb(228, 240, 249));

                break;
            case "com.apple.mobilecal":
                notificationData.setAppIcon(R.drawable.ic_calendar);
                notificationData.setBackgroundColor(Color.rgb(228, 240, 249));

                break;
            case "com.google.Gmail":
                notificationData.setAppIcon(R.drawable.ic_gmail);
                notificationData.setBackgroundColor(Color.rgb(232, 90, 77));

                break;
            case "jp.naver.line":
                notificationData.setAppIcon(R.drawable.ic_line);
                notificationData.setBackgroundColor(Color.rgb(67, 195, 84));

                break;
            case "com.facebook.Facebook":
                notificationData.setAppIcon(R.drawable.ic_facebook);
                notificationData.setBackgroundColor(Color.rgb(48, 77, 139));

                break;
            case "com.atebits.Tweetie2":
            case "com.tapbots.Tweetbot":
            case "com.tapbots.Tweetbot3":
                notificationData.setAppIcon(R.drawable.ic_twitter);
                notificationData.setBackgroundColor(Color.rgb(61, 139, 199));

                break;
            case "com.google.hangouts":
                notificationData.setAppIcon(R.drawable.ic_hangouts);
                notificationData.setBackgroundColor(Color.rgb(117, 180, 235));

                break;
            case "ph.telegra.Telegraph":
                notificationData.setAppIcon(R.drawable.ic_telegram);
                notificationData.setBackgroundColor(Color.rgb(41, 161, 218));

                messageApp = true;

                break;
            case "net.whatsapp.WhatsApp":
                notificationData.setAppIcon(R.drawable.ic_whatsapp);
                notificationData.setBackgroundColor(Color.rgb(67, 195, 84));

                messageApp = true;

                break;
            case "com.google.inbox":
                notificationData.setAppIcon(R.drawable.ic_inbox);
                notificationData.setBackgroundColor(Color.rgb(66, 133, 244));

                break;
            case "com.crazyapps.TeeVee2":
                notificationData.setAppIcon(R.drawable.ic_teevee);
                notificationData.setBackgroundColor(Color.rgb(246, 173, 2));

                break;
            case "com.linkedin.LinkedIn":
                notificationData.setAppIcon(R.drawable.ic_linkedin);
                notificationData.setBackgroundColor(Color.rgb(1, 83, 128));

                break;
            case "com.burbn.instagram":
                notificationData.setAppIcon(R.drawable.ic_instagram);
                notificationData.setBackgroundColor(Color.rgb(40, 61, 89));

                break;
            case "com.facebook.Messenger":
                notificationData.setAppIcon(R.drawable.ic_messenger);
                notificationData.setBackgroundColor(Color.rgb(0, 155, 255));

                messageApp = true;

                break;
            case "com.toyopagroup.picaboo": // Snapchat
                notificationData.setAppIcon(R.drawable.ic_snapchat);
                notificationData.setBackgroundColor(Color.rgb(238, 226, 0));

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
