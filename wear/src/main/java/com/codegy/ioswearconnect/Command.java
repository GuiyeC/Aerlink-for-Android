package com.codegy.ioswearconnect;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import java.util.UUID;

/**
 * Created by Guiye on 18/4/15.
 */
public class Command {

    private UUID serviceUUID;
    private String characteristic;
    private byte[] packet;
    private int retryCount = 0;
    //private int writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;

    public Command(UUID serviceUUID, String characteristic, byte[] packet) {
        this.serviceUUID = serviceUUID;
        this.characteristic = characteristic;
        this.packet = packet;
    }

    public UUID getServiceUUID() {
        return serviceUUID;
    }

    public String getCharacteristic() {
        return characteristic;
    }

    public byte[] getPacket() {
        return packet;
    }

    public int getRetryCount() {
        return retryCount;
    }

    /*
    public int getWriteType() {
        return writeType;
    }

    public void setWriteType(int writeType) {
        this.writeType = writeType;
    }
    */

    public boolean shouldRetryAgain() {
        if (retryCount >= 5) {
            return false ;
        }

        retryCount++;

        return true;
    }

}
