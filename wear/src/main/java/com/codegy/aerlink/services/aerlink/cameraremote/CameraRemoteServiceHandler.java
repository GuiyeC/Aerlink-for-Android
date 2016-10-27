package com.codegy.aerlink.services.aerlink.cameraremote;

import android.app.Notification;
import android.app.PendingIntent;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.util.Log;
import com.codegy.aerlink.R;
import com.codegy.aerlink.services.aerlink.ALSConstants;
import com.codegy.aerlink.connection.command.Command;
import com.codegy.aerlink.utils.PacketProcessor;
import com.codegy.aerlink.utils.ServiceHandler;
import com.codegy.aerlink.utils.ServiceUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by Guiye on 21/5/15.
 */
public class CameraRemoteServiceHandler extends ServiceHandler {

    public interface CameraRemoteCallback {
        void onCameraChangedOpen(boolean open);
        void onCountdownStarted(int countdown);
        void onImageTransferStarted();
        void onImageTransferFinished(Bitmap cameraImage);
    }

    private static final String LOG_TAG = CameraRemoteServiceHandler.class.getSimpleName();

    private static final int NOTIFICATION_CAMERA = 2001;


    private Context mContext;
    private ServiceUtils mServiceUtils;
    private CameraRemoteCallback cameraRemoteCallback;

    private boolean cameraOpen = false;
    private PacketProcessor mPacketProcessor;


    public CameraRemoteServiceHandler(Context context, ServiceUtils serviceUtils) {
        this.mContext = context;
        this.mServiceUtils = serviceUtils;
    }


    public void requestState(Runnable failure) {
        Command cameraCommand = new Command(ALSConstants.SERVICE_UUID, ALSConstants.CHARACTERISTIC_CAMERA_REMOTE_ACTION, new byte[] {
                (byte) 0x02,
        });
        cameraCommand.setFailureBlock(failure);

        mServiceUtils.addCommandToQueue(cameraCommand);
    }

    public void takePicture(Runnable failure) {
        Command cameraCommand = new Command(ALSConstants.SERVICE_UUID, ALSConstants.CHARACTERISTIC_CAMERA_REMOTE_ACTION, new byte[] {
                (byte) 0x03,
        });
        cameraCommand.setImportance(Command.IMPORTANCE_MIN);
        cameraCommand.setFailureBlock(failure);

        mServiceUtils.addCommandToQueue(cameraCommand);
    }

    public void setCameraCallback(CameraRemoteCallback cameraRemoteCallback) {
        this.cameraRemoteCallback = cameraRemoteCallback;
    }

    public void setCameraOpen(boolean cameraOpen) {
        this.cameraOpen = cameraOpen;

        Log.v(LOG_TAG, "Camera " + (cameraOpen ? "OPEN" : "CLOSED"));

        if (cameraRemoteCallback != null) {
            cameraRemoteCallback.onCameraChangedOpen(cameraOpen);
        }

        if (cameraOpen) {
            Bitmap background = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.bg_camera);

            Intent cameraIntent = new Intent(mContext, CameraRemoteActivity.class);
            cameraIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent cameraPendingIntent = PendingIntent.getActivity(mContext, 0, cameraIntent, 0);

            Icon cameraIcon = Icon.createWithResource(mContext, R.drawable.nic_camera);
            Notification.Action cameraAction = new Notification.Action.Builder(
                    cameraIcon,
                    "Camera",
                    cameraPendingIntent
            ).build();

            Notification.WearableExtender wearableExtender = new Notification.WearableExtender()
                    .setBackground(background)
                    .setContentIcon(R.drawable.nic_camera)
                    .setHintHideIcon(true)
                    .setContentAction(0)
                    .addAction(cameraAction);

            Notification.Builder builder = new Notification.Builder(mContext)
                    .setSmallIcon(cameraIcon)
                    .setContentTitle(mContext.getString(R.string.take_photo_title))
                    .setContentText(mContext.getString(R.string.take_photo_text))
                    .setPriority(Notification.PRIORITY_MAX)
                    .extend(wearableExtender);

            mServiceUtils.notify(null, NOTIFICATION_CAMERA, builder.build());
        }
        else {
            mServiceUtils.cancelNotification(null, NOTIFICATION_CAMERA);
        }
    }

    public boolean isCameraOpen() {
        return cameraOpen;
    }


    @Override
    public void reset() {
        cameraOpen = false;
        mPacketProcessor = null;
    }

    @Override
    public UUID getServiceUUID() {
        return ALSConstants.SERVICE_UUID;
    }

    @Override
    public List<String> getCharacteristicsToSubscribe() {
        List<String> characteristics = new ArrayList<>();

        characteristics.add(ALSConstants.CHARACTERISTIC_CAMERA_REMOTE_DATA);

        return characteristics;
    }

    @Override
    public boolean canHandleCharacteristic(BluetoothGattCharacteristic characteristic) {
        String characteristicUUID = characteristic.getUuid().toString().toLowerCase();
        return characteristicUUID.equals(ALSConstants.CHARACTERISTIC_CAMERA_REMOTE_DATA);
    }

    @Override
    public void handleCharacteristic(BluetoothGattCharacteristic characteristic) {
        byte[] packet = characteristic.getValue();

        if (mPacketProcessor == null) {
            mPacketProcessor = new PacketProcessor(packet);

            switch (mPacketProcessor.getAction()) {
                case 0x01:
                    if (mPacketProcessor.getStatus() != 0x01) {
                        mPacketProcessor = null;
                    }
                    else if (cameraRemoteCallback != null) {
                        cameraRemoteCallback.onImageTransferStarted();
                    }
                    break;
                case 0x02:
                    setCameraOpen(mPacketProcessor.getStatus() == 0x01);
                    mPacketProcessor = null;
                    return;
                case 0x03:
                    if (cameraRemoteCallback != null) {
                        cameraRemoteCallback.onCountdownStarted(mPacketProcessor.getStatus());
                    }
                    mPacketProcessor = null;
                    return;
            }
        }
        else {
            mPacketProcessor.process(packet);
        }

        if (mPacketProcessor != null && mPacketProcessor.isFinished()) {
            Bitmap cameraImage = mPacketProcessor.getBitmapValue();

            if (cameraImage != null && cameraRemoteCallback != null) {
                cameraRemoteCallback.onImageTransferFinished(cameraImage);
            }

            mPacketProcessor = null;
        }
    }


}
