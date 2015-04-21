package com.codegy.ioswearconnect;

import android.graphics.Color;

import java.util.Arrays;

/**
 * Created by Guiye on 13/4/15.
 */
public class NotificationData {

    private int appIcon = R.drawable.ic_notification;
    private int background = -1;
//    private int backgroundColor = Color.rgb(140, 140, 145);
    private int backgroundColor = Color.rgb(0, 0, 0);
    private byte[] UID;
    private String appId;
    private String title;
    private String message;
    private String positiveAction;
    private String negativeAction;
    boolean silent;
    boolean preExisting;
    boolean incomingCall;
    boolean hasPositiveAction;
    boolean hasNegativeAction;

    public NotificationData(byte[] packet) {
        int eventFlags = packet[1];
        this.silent = (eventFlags & 1) != 0; // EventFlagSilent
        // boolean important = (eventFlags & 2) != 0; // EventFlagImportant
        this.preExisting = (eventFlags & 4) != 0; // EventFlagPreExisting
        this.hasPositiveAction = (eventFlags & 8) != 0; // EventFlagPositiveAction
        this.hasNegativeAction = (eventFlags & 16) != 0; // EventFlagNegativeAction

        if (packet[2] == 1) {
            this.incomingCall = true;
        }

        this.UID = Arrays.copyOfRange(packet, 4, 8);
    }

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

    public void setPositiveAction(String positiveAction) {
        if (positiveAction != null && positiveAction.length() > 0) {
            this.positiveAction = positiveAction;
        }
        else {
            this.positiveAction = null;
        }
    }

    public String getNegativeAction() {
        return negativeAction;
    }

    public void setNegativeAction(String negativeAction) {
        if (negativeAction != null && negativeAction.length() > 0) {
            this.negativeAction = negativeAction;
        }
        else {
            this.negativeAction = null;
        }
    }

    public boolean isSilent() {
        return silent;
    }

    public boolean isPreExisting() {
        return preExisting;
    }

    public boolean isIncomingCall() {
        return incomingCall;
    }

    public boolean hasPositiveAction() {
        return hasPositiveAction;
    }

    public boolean hasNegativeAction() {
        return hasNegativeAction;
    }
}
