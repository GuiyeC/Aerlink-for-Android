package com.shiitakeo.android_wear_for_ios;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Created by Guiye on 13/4/15.
 */
public class NotificationData {

    private int appIcon = R.drawable.ic_launcher;
    private int background = -1;
    private int backgroundColor = Color.rgb(140, 140, 145);
    private byte[] UID;
    private String appId;
    private String title;
    private String message;
    private String positiveAction;
    private String negativeAction;

    public NotificationData(byte[] UID, String appId, String title, String message, String positiveAction, String negativeAction) {
        this.UID = UID;
        this.appId = appId;
        this.title = title;
        this.message = message;

        if (positiveAction.length() > 0) {
            this.positiveAction = positiveAction;
        }
        if (negativeAction.length() > 0) {
            this.negativeAction = negativeAction;
        }
    }

    public byte[] getUID() {
        return UID;
    }

    public String getUIDString() {
        return new String(UID);
    }

    public int getAppIcon() {
        return appIcon;
    }

    public void setAppIcon(int appIcon) {
        this.appIcon = appIcon;
    }

    public int getBackground() {
        return background;
    }

    public void setBackground(int background) {
        this.background = background;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPositiveAction() {
        return positiveAction;
    }

    public String getNegativeAction() {
        return negativeAction;
    }

    public String getGroup() {
        return appId + title;
    }
}
