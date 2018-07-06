package com.codegy.aerlink.utils;

import android.app.Notification;
import com.codegy.aerlink.connection.command.Command;

/**
 * Created by Guiye on 19/5/15.
 */
public interface ServiceUtils {

    boolean isAerlinkAvailable();
    void addCommandToQueue(Command command);
    void notify(String tag, int id, Notification notification);
    void cancelNotification(String tag, int id);

}
