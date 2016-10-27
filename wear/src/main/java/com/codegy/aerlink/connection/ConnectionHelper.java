package com.codegy.aerlink.connection;

import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.codegy.aerlink.R;
import com.codegy.aerlink.utils.ServiceUtils;

/**
 * Created by Guiye on 19/5/15.
 */
public class ConnectionHelper {

    private static final int NOTIFICATION_HELP = 2000;
    // private static final long CONNECTION_PATTERN[] = { 80, 60 };
    private static final long DISCONNECTION_PATTERN[] = { 80, 90 };


    private Context mContext;
    private ServiceUtils mServiceUtils;


    public ConnectionHelper(Context context, ServiceUtils serviceUtils) {
        this.mContext = context;
        this.mServiceUtils = serviceUtils;
    }

    public void showHelpForState(ConnectionState state) {
        if (state == ConnectionState.Ready) {
            mServiceUtils.cancelNotification(null, NOTIFICATION_HELP);
            return;
        }


        Bitmap background = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.bg_aerlink);

        Notification.WearableExtender wearableExtender = new Notification.WearableExtender()
                .setBackground(background);

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
                .setOngoing(state != ConnectionState.NoBluetooth)
                .extend(wearableExtender);


        if (state == ConnectionState.Disconnected || state == ConnectionState.NoBluetooth) {
            builder.setVibrate(DISCONNECTION_PATTERN);
        }


        mServiceUtils.notify(null, NOTIFICATION_HELP, builder.build());
    }

}
