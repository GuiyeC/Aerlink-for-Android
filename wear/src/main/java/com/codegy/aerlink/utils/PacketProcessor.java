package com.codegy.aerlink.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;

/**
 * Created by Guiye on 20/5/15.
 */
public class PacketProcessor {

    private ByteArrayOutputStream processingData;
    private int status;
    // The number of bytes left to process
    private int bytesLeftToProcess;

    public PacketProcessor(byte[] packet) {
        if (packet.length > 3) {
            status = packet[0];

            if (status == 0x01) {
                byte[] byteLength = {packet[2], packet[1]};
                BigInteger length = new BigInteger(byteLength);
                bytesLeftToProcess = length.intValue();

                Log.d("PacketProcessor", "DATA length " + length);

                processingData = new ByteArrayOutputStream();
                processingData.write(packet, 3, packet.length - 3);

                bytesLeftToProcess -= (packet.length - 3);
            }
        }
    }

    public void process(byte[] packet) {
        processingData.write(packet, 0, packet.length);
        bytesLeftToProcess -= packet.length;
    }

    public int getStatus() {
        return status;
    }

    public boolean isFinished() {
        return bytesLeftToProcess <= 0;
    }

    public String getStringValue() {
        String value = null;
        try {
            value = new String(processingData.toByteArray(), "UTF-8");
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return value;
    }

    public Bitmap getBitmapValue() {
        Bitmap value = null;
        try {
            value = BitmapFactory.decodeByteArray(processingData.toByteArray(), 0, processingData.toByteArray().length);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return value;
    }

}
