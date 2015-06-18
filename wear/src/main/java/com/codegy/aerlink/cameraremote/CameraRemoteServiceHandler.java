package com.codegy.aerlink.cameraremote;

import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import com.codegy.aerlink.ALSConstants;
import com.codegy.aerlink.connection.Command;
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


    public void takePicture() {
        Command cameraCommand = new Command(ALSConstants.SERVICE_UUID, ALSConstants.CHARACTERISTIC_CAMERA_REMOTE_ACTION, new byte[] {
                (byte) 0x01,
        });
        cameraCommand.setImportance(Command.IMPORTANCE_MIN);

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

/*
        if (cameraOpen) {
            Bitmap background = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            background.eraseColor(0);

            Notification.WearableExtender wearableExtender = new Notification.WearableExtender()
                    .setBackground(background)
                    .setContentAction(0);

            Intent cameraIntent = new Intent(mContext, CameraRemoteActivity.class);
            cameraIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent cameraPendingIntent = PendingIntent.getBroadcast(mContext, 0, cameraIntent, 0);

            Notification.Action cameraAction = new Notification.Action.Builder(
                    R.mipmap.ic_launcher_camera,
                    "Camera",
                    cameraPendingIntent
            ).build();

            String title = "Hacer una foto";
            String text = "Toca para empezar a capturar";

            Notification.Builder builder = new Notification.Builder(mContext)
                    .setSmallIcon(R.mipmap.ic_launcher_camera)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setPriority(Notification.PRIORITY_MAX)
                    .extend(wearableExtender)
                    .addAction(cameraAction);

            mServiceUtils.notify(null, NOTIFICATION_CAMERA, builder.build());
        }
        else {
            mServiceUtils.cancelNotification(null, NOTIFICATION_CAMERA);
        }
        */
    }

    public boolean isCameraOpen() {
        return cameraOpen;
    }


    @Override
    public void reset() {
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
        characteristics.add(ALSConstants.CHARACTERISTIC_CAMERA_REMOTE_ACTION);

        return characteristics;
    }

    @Override
    public boolean canHandleCharacteristic(BluetoothGattCharacteristic characteristic) {
        String characteristicUUID = characteristic.getUuid().toString().toLowerCase();
        return characteristicUUID.equals(ALSConstants.CHARACTERISTIC_CAMERA_REMOTE_DATA) | characteristicUUID.equals(ALSConstants.CHARACTERISTIC_CAMERA_REMOTE_ACTION);
    }

    @Override
    public void handleCharacteristic(BluetoothGattCharacteristic characteristic) {
        byte[] packet = characteristic.getValue();

        switch (characteristic.getUuid().toString().toLowerCase()) {
            case ALSConstants.CHARACTERISTIC_CAMERA_REMOTE_DATA:
                if (mPacketProcessor == null) {
                    mPacketProcessor = new PacketProcessor(packet);

                    if (mPacketProcessor.getStatus() != 0x01) {
                        mPacketProcessor = null;
                    }
                    else if (cameraRemoteCallback != null) {
                        cameraRemoteCallback.onImageTransferStarted();
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

                break;
            case ALSConstants.CHARACTERISTIC_CAMERA_REMOTE_ACTION:
                try {
                    switch (packet[0]) {
                        case 0x01:
                            setCameraOpen(packet[1] == 0x01);

                            break;
                        case 0x02:
                            if (cameraRemoteCallback != null) {
                                cameraRemoteCallback.onCountdownStarted(packet[1]);
                            }
                            break;
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }

                break;
        }
    }


}
