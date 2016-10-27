package com.codegy.aerlink.services.aerlink;

import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.util.Log;
import com.codegy.aerlink.utils.PacketProcessor;
import com.codegy.aerlink.utils.ServiceHandler;
import com.codegy.aerlink.utils.ServiceUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by Guiye on 21/10/16.
 */

public class UtilsServiceHandler extends ServiceHandler {

    private static final String LOG_TAG = UtilsServiceHandler.class.getSimpleName();

    private Context mContext;
    private ServiceUtils mServiceUtils;
    private PacketProcessor mPacketProcessor;
    private String mBundleIdentifier;


    public UtilsServiceHandler(Context context, ServiceUtils serviceUtils) {
        this.mContext = context;
        this.mServiceUtils = serviceUtils;
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

        characteristics.add(ALSConstants.CHARACTERISTIC_UTILS_DATA);

        return characteristics;
    }

    @Override
    public boolean canHandleCharacteristic(BluetoothGattCharacteristic characteristic) {
        String characteristicUUID = characteristic.getUuid().toString().toLowerCase();
        return characteristicUUID.equals(ALSConstants.CHARACTERISTIC_UTILS_DATA);
    }

    @Override
    public void handleCharacteristic(BluetoothGattCharacteristic characteristic) {
        byte[] packet = characteristic.getValue();

        if (mPacketProcessor == null) {
            mPacketProcessor = new PacketProcessor(packet);
        }
        else {
            mPacketProcessor.process(packet);
        }

        if (mPacketProcessor == null || !mPacketProcessor.isFinished()) {
            return;
        }
        switch (mPacketProcessor.getAction()) {
            case 0x01:
                switch (mPacketProcessor.getStatus()) {
                    case 0x01:
                        mBundleIdentifier = mPacketProcessor.getStringValue();
                        break;
                    case 0x02:
                        if (mBundleIdentifier == null) {
                            return;
                        }

                        Bitmap appIcon = mPacketProcessor.getBitmapValue();

                        if (appIcon != null) {
                            saveToInternalStorage(appIcon, mBundleIdentifier);
                        }
                        break;
                }
                break;
        }

        mPacketProcessor = null;
    }

    private void saveToInternalStorage(Bitmap bitmap, String bundleIdentifier) {
        Log.i(LOG_TAG, "Saving icon");

        ContextWrapper cw = new ContextWrapper(mContext);
        File directory = cw.getDir("AppIconDir", Context.MODE_PRIVATE);
        File path = new File(directory, bundleIdentifier+".png");

        try (FileOutputStream fos = new FileOutputStream(path)) {
            // Use the compress method on the BitMap object to write image to the OutputStream
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            Log.i(LOG_TAG, "Icon saved");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
