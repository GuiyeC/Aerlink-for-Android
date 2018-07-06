package com.codegy.aerlink.services.notifications;

import android.app.Notification;
import android.app.PendingIntent;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.util.Log;
import com.codegy.aerlink.Constants;
import com.codegy.aerlink.R;
import com.codegy.aerlink.connection.command.Command;
import com.codegy.aerlink.services.aerlink.ALSConstants;
import com.codegy.aerlink.utils.ScheduledTask;
import com.codegy.aerlink.utils.ServiceHandler;
import com.codegy.aerlink.utils.ServiceUtils;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Created by Guiye on 18/5/15.
 */
public class NotificationServiceHandler extends ServiceHandler {

    private static final String LOG_TAG = NotificationServiceHandler.class.getSimpleName();

    public static final int NOTIFICATION_REGULAR = 1000;
    private static final long VIBRATION_PATTERN[] = { 100, 400, 200, 40, 40, 40, 70, 200 };
    private static final long SILENT_VIBRATION_PATTERN[] = { 200, 110 };


    private Context mContext;
    private ServiceUtils mServiceUtils;

    private NotificationPacketProcessor mPacketProcessor;
    private int mNotificationNumber = 0;

    private List<NotificationData> mPendingNotifications = new ArrayList<>();


    public NotificationServiceHandler(Context context, ServiceUtils serviceUtils) {
        this.mContext = context;
        this.mServiceUtils = serviceUtils;

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.IA_POSITIVE);
        intentFilter.addAction(Constants.IA_NEGATIVE);
        intentFilter.addAction(Constants.IA_DELETE);
        context.registerReceiver(mBroadcastReceiver, intentFilter);
    }


    @Override
    public void close() {
        mNotificationNumber = 0;
        reset();

        mContext.unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    public void reset() {
        mPacketProcessor = null;
        mPendingNotifications.clear();

        cancelClearOldNotificationsTask();
    }

    @Override
    public UUID getServiceUUID() {
        return ANCSConstants.SERVICE_UUID;
    }

    @Override
    public List<String> getCharacteristicsToSubscribe() {
        List<String> characteristics = new ArrayList<>();

        characteristics.add(ANCSConstants.CHARACTERISTIC_DATA_SOURCE);
        characteristics.add(ANCSConstants.CHARACTERISTIC_NOTIFICATION_SOURCE);

        return characteristics;
    }

    @Override
    public boolean canHandleCharacteristic(BluetoothGattCharacteristic characteristic) {
        String characteristicUUID = characteristic.getUuid().toString().toLowerCase();
        return characteristicUUID.equals(ANCSConstants.CHARACTERISTIC_DATA_SOURCE) || characteristicUUID.equals(ANCSConstants.CHARACTERISTIC_NOTIFICATION_SOURCE);
    }

    @Override
    public void handleCharacteristic(BluetoothGattCharacteristic characteristic) {
        // Get notification packet from iOS
        byte[] packet = characteristic.getValue();

        switch (characteristic.getUuid().toString().toLowerCase()) {
            case ANCSConstants.CHARACTERISTIC_DATA_SOURCE:
                if (mPacketProcessor == null && packet.length >= 5) {
                    byte[] notificationUID = new byte[] { packet[1], packet[2], packet[3], packet[4] };
                    int notificationIndex = -1;

                    for (int i = 0; i < mPendingNotifications.size(); i++) {
                        NotificationData notificationData = mPendingNotifications.get(i);

                        if (notificationData.compareUID(notificationUID)) {
                            notificationIndex = i;
                            break;
                        }
                    }

                    if (notificationIndex != -1) {
                        mPacketProcessor = new NotificationPacketProcessor(mPendingNotifications.get(notificationIndex));
                        mPendingNotifications.remove(notificationIndex);
                    }
                }

                if (mPacketProcessor != null) {
                    // Only remove callback if we are getting useful data
                    cancelClearOldNotificationsTask();

                    mPacketProcessor.process(packet);

                    if (mPacketProcessor.hasFinishedProcessing()) {
                        NotificationData notificationData = mPacketProcessor.getNotificationData();

                        if (notificationData != null) {
                            if (notificationData.isIncomingCall()) {
                                onIncomingCall(notificationData);
                            }
                            else {
                                onNotificationReceived(notificationData);
                            }
                        }

                        mPacketProcessor = null;
                    }
                }

                if (mPendingNotifications.size() > 0 || mPacketProcessor != null) {
                    // Clear notifications in case data never arrives
                    scheduleClearOldNotificationsTask();
                }

                break;
            case ANCSConstants.CHARACTERISTIC_NOTIFICATION_SOURCE:
                try {
                    switch (packet[0]) {
                        case ANCSConstants.EventIDNotificationAdded:
                        case ANCSConstants.EventIDNotificationModified:
                            // Request attributes for the new notification
                            byte[] getAttributesPacket = new byte[] {
                                    ANCSConstants.CommandIDGetNotificationAttributes,

                                    // UID
                                    packet[4], packet[5], packet[6], packet[7],

                                    // App Identifier - NotificationAttributeIDAppIdentifier
                                    ANCSConstants.NotificationAttributeIDAppIdentifier,

                                    // Title - NotificationAttributeIDTitle
                                    // Followed by a 2-bytes max length parameter
                                    ANCSConstants.NotificationAttributeIDTitle,
                                    (byte) 0xff,
                                    (byte) 0xff,

                                    // Message - NotificationAttributeIDMessage
                                    // Followed by a 2-bytes max length parameter
                                    ANCSConstants.NotificationAttributeIDMessage,
                                    (byte) 0xff,
                                    (byte) 0xff,
                            };


                            NotificationData notificationData = new NotificationData(packet);
                            mPendingNotifications.add(notificationData);


                            if (notificationData.hasPositiveAction()) {
                                getAttributesPacket = NotificationPacketProcessor.concat(getAttributesPacket, new byte[]{
                                        // Positive Action Label - NotificationAttributeIDPositiveActionLabel
                                        ANCSConstants.NotificationAttributeIDPositiveActionLabel
                                });
                            }
                            if (notificationData.hasNegativeAction()) {
                                getAttributesPacket = NotificationPacketProcessor.concat(getAttributesPacket, new byte[]{
                                        // Negative Action Label - NotificationAttributeIDNegativeActionLabel
                                        ANCSConstants.NotificationAttributeIDNegativeActionLabel
                                });
                            }

                            Command getAttributesCommand = new Command(ANCSConstants.SERVICE_UUID, ANCSConstants.CHARACTERISTIC_CONTROL_POINT, getAttributesPacket);

                            mServiceUtils.addCommandToQueue(getAttributesCommand);


                            // Clear notifications in case data never arrives
                            scheduleClearOldNotificationsTask();

                            break;
                        case ANCSConstants.EventIDNotificationRemoved:
                            if (packet[2] == 1) {
                                // Call ended
                                onCallEnded();
                            }
                            else {
                                // Cancel notification in watch
                                String notificationId = new String(Arrays.copyOfRange(packet, 4, 8));
                                onNotificationCanceled(notificationId);
                            }

                            break;
                    }
                }
                catch(Exception e) {
                    Log.d(LOG_TAG, "error");
                    e.printStackTrace();
                }

                break;
        }
    }


    private void onIncomingCall(NotificationData notificationData) {
        Log.d(LOG_TAG, "Incoming call");
        try {
            Intent phoneIntent = new Intent(mContext, PhoneActivity.class);
            phoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            phoneIntent.putExtra(Constants.IE_NOTIFICATION_UID, notificationData.getUID());
            phoneIntent.putExtra(Constants.IE_NOTIFICATION_TITLE, notificationData.getTitle());
            phoneIntent.putExtra(Constants.IE_NOTIFICATION_MESSAGE, notificationData.getMessage());

            mContext.startActivity(phoneIntent);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onNotificationReceived(NotificationData notificationData) {
        Log.d(LOG_TAG, "Notification received");

        // Build pending intent for when the user swipes the card away
        Intent deleteIntent = new Intent(Constants.IA_DELETE);
        deleteIntent.putExtra(Constants.IE_NOTIFICATION_UID, notificationData.getUID());
        PendingIntent deleteAction = PendingIntent.getBroadcast(mContext, mNotificationNumber, deleteIntent, 0);

        Notification.Builder notificationBuilder = new Notification.Builder(mContext)
                .setContentTitle(notificationData.getTitle())
                .setContentText(notificationData.getMessage())
                .setSmallIcon(notificationData.getAppIcon())
                .setGroup(notificationData.getAppId())
                .setDeleteIntent(deleteAction)
                .setPriority(Notification.PRIORITY_MAX);


        if (notificationData.isUnknown() && !notificationData.getAppId().isEmpty()) {
            Bitmap bitmap = loadImageFromStorage(notificationData.getAppId());
            if (bitmap != null) {
                Log.i(LOG_TAG, "Icon loaded");
                Icon icon = Icon.createWithBitmap(bitmap);
                notificationBuilder.setSmallIcon(icon);
            }
            else if (mServiceUtils.isAerlinkAvailable()) {
                Bitmap background = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.bg_notification);

                Notification.WearableExtender wearableExtender = new Notification.WearableExtender()
                        .setBackground(background);
                notificationBuilder.extend(wearableExtender);

                Log.i(LOG_TAG, "Requesting icon");
                String dataString = (char) 0x01 + notificationData.getAppId();
                Command iconCommand = new Command(ALSConstants.SERVICE_UUID, ALSConstants.CHARACTERISTIC_UTILS_ACTION, dataString.getBytes());

                mServiceUtils.addCommandToQueue(iconCommand);
            }
        }
        else {
            Bitmap background;
            if (notificationData.getBackground() != -1) {
                background = BitmapFactory.decodeResource(mContext.getResources(), notificationData.getBackground());
            }
            else {
                background = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
                background.eraseColor(notificationData.getBackgroundColor());
            }

            Notification.WearableExtender wearableExtender = new Notification.WearableExtender()
                    .setBackground(background);
            notificationBuilder.extend(wearableExtender);
        }

        // Build positive action intent only if available
        if (notificationData.getPositiveAction() != null) {
            Intent positiveIntent = new Intent(Constants.IA_POSITIVE);
            positiveIntent.putExtra(Constants.IE_NOTIFICATION_UID, notificationData.getUID());
            PendingIntent positiveActionIntent = PendingIntent.getBroadcast(mContext, mNotificationNumber, positiveIntent, 0);

            Icon icon = Icon.createWithResource(mContext, R.drawable.ic_action_accept);
            Notification.Action positiveAction = new Notification.Action.Builder(icon, notificationData.getPositiveAction(), positiveActionIntent).build();
            notificationBuilder.addAction(positiveAction);
        }
        // Build negative action intent only if available
        if (notificationData.getNegativeAction() != null) {
            Intent negativeIntent = new Intent(Constants.IA_NEGATIVE);
            negativeIntent.putExtra(Constants.IE_NOTIFICATION_UID, notificationData.getUID());
            PendingIntent negativeActionIntent = PendingIntent.getBroadcast(mContext, mNotificationNumber, negativeIntent, 0);

            Icon icon = Icon.createWithResource(mContext, R.drawable.ic_action_remove);
            Notification.Action negativeAction = new Notification.Action.Builder(icon, notificationData.getNegativeAction(), negativeActionIntent).build();
            notificationBuilder.addAction(negativeAction);
        }

        if (!notificationData.isPreExisting()) {
            if (!notificationData.isSilent()) {
                notificationBuilder.setVibrate(VIBRATION_PATTERN);
            }
            else {
                notificationBuilder.setVibrate(SILENT_VIBRATION_PATTERN);
            }
        }


        // Build and notify
        Notification notification = notificationBuilder.build();
        mServiceUtils.notify(notificationData.getUIDString(), NOTIFICATION_REGULAR, notification);


        mNotificationNumber++;
    }

    private void onCallEnded() {
        Log.d(LOG_TAG, "Call ended");
        mContext.sendBroadcast(new Intent(Constants.IA_END_CALL));
    }

    private void onNotificationCanceled(String notificationId) {
        Log.d(LOG_TAG, "Notification canceled");
        mServiceUtils.cancelNotification(notificationId, NOTIFICATION_REGULAR);
    }

    private ScheduledTask mClearOldNotificationsTask;

    private void scheduleClearOldNotificationsTask() {
        if (mClearOldNotificationsTask == null) {
            mClearOldNotificationsTask = new ScheduledTask(1000, mContext.getMainLooper(), new Runnable() {
                @Override
                public void run() {
                    Log.i(LOG_TAG, "Clear old notifications");
                    mPacketProcessor = null;
                    mPendingNotifications.clear();
                }
            });
        }
        else {
            mClearOldNotificationsTask.cancel();
        }

        mClearOldNotificationsTask.schedule();
    }

    private void cancelClearOldNotificationsTask() {
        if (mClearOldNotificationsTask != null) {
            mClearOldNotificationsTask.cancel();
        }
    }

    private Bitmap loadImageFromStorage(String bundleIdentifier) {
        ContextWrapper cw = new ContextWrapper(mContext);
        File directory = cw.getDir("AppIconDir", Context.MODE_PRIVATE);
        File path = new File(directory, bundleIdentifier+".png");
        Bitmap bitmap = null;

        try {
            bitmap = BitmapFactory.decodeStream(new FileInputStream(path));
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return bitmap;
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            // perform notification action: immediately
            // delete intent: after 7~8sec.
            try {
                String action = intent.getAction();

                byte[] UID = intent.getByteArrayExtra(Constants.IE_NOTIFICATION_UID);
                String notificationId = new String(UID);

                // Dismiss notification
                mServiceUtils.cancelNotification(notificationId, NOTIFICATION_REGULAR);

                byte actionId = ANCSConstants.ActionIDPositive;
                if (action.equals(Constants.IA_NEGATIVE) | action.equals(Constants.IA_DELETE)) {
                    actionId = ANCSConstants.ActionIDNegative;
                }

                // Perform user selected action
                byte[] performActionPacket = {
                        ANCSConstants.CommandIDPerformNotificationAction,

                        // Notification UID
                        UID[0], UID[1], UID[2], UID[3],

                        // Action Id
                        actionId
                };

                Command performActionCommand = new Command(ANCSConstants.SERVICE_UUID, ANCSConstants.CHARACTERISTIC_CONTROL_POINT, performActionPacket);

                mServiceUtils.addCommandToQueue(performActionCommand);
            }
            catch (Exception e) {
                Log.d(LOG_TAG, "error");
                e.printStackTrace();
            }
        }

    };

}
