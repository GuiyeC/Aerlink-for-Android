package com.codegy.aerlink.connection;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.codegy.aerlink.Constants;
import com.codegy.aerlink.R;
import com.codegy.aerlink.utils.ServiceUtils;

/**
 * Created by Guiye on 19/5/15.
 */
public class ConnectionHelper {

    public static final int NOTIFICATION_HELP = 2000;
    private static final long CONNECTION_PATTERN[] = { 80, 60 };
    private static final long DISCONNECTION_PATTERN[] = { 80, 90 };

    private Context mContext;
    private ServiceUtils mServiceUtils;


    public ConnectionHelper(Context context, ServiceUtils serviceUtils) {
        this.mContext = context;
        this.mServiceUtils = serviceUtils;
    }

    public void showHelpForState(ConnectionHandler.ConnectionState state) {
        if (state == ConnectionHandler.ConnectionState.Ready) {
            mServiceUtils.cancelNotification(null, NOTIFICATION_HELP);
            return;
        }


        Bitmap background = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.bg_texture);

        Notification.WearableExtender wearableExtender = new Notification.WearableExtender()
                .setBackground(background);

        /*
        PendingIntent connectPendingIntent = null;
        if (state == ConnectionHandler.ConnectionState.Disconnected) {
            // Build pending intent for when the user swipes the card away
            Intent connectIntent = new Intent(Constants.IA_TRY_CONNECTING);
            connectPendingIntent = PendingIntent.getBroadcast(mContext, 0, connectIntent, 0);

        }*/

        String title = null;
        String text = null;

        switch (state) {
            case NoBluetooth:
                title = mContext.getString(R.string.help_title_no_bluetooth);
                text = mContext.getString(R.string.help_no_bluetooth);
                break;
            case Disconnected:
            case Connecting:
                title = mContext.getString(R.string.help_title_disconnected);
                text = mContext.getString(R.string.help_disconnected);

                // Add help page
                wearableExtender.setContentAction(0).
                        addPage(new Notification.Builder(mContext)
                                .setContentTitle(mContext.getString(R.string.help))
                                .setContentText(mContext.getString(R.string.help_how_to))
                                .build());

                break;
        }

        Notification.Builder builder = new Notification.Builder(mContext)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(Notification.PRIORITY_MAX)
                .setOngoing(state != ConnectionHandler.ConnectionState.NoBluetooth)
                .extend(wearableExtender);

        /*
        if (connectPendingIntent != null) {
            Notification.Action connectAction = new Notification.Action.Builder(
                    android.R.drawable.ic_menu_search,
                    "Reconnect",
                    connectPendingIntent
            ).build();
            builder.addAction(connectAction);
        }

        if (state == ConnectionHandler.ConnectionState.Disconnected || state == ConnectionHandler.ConnectionState.NoBluetooth) {
            mServiceUtils.vibrate(DISCONNECTION_PATTERN, -1);

            builder.setVibrate(DISCONNECTION_PATTERN);
        }
        */


        mServiceUtils.notify(null, NOTIFICATION_HELP, builder.build());
    }

}
