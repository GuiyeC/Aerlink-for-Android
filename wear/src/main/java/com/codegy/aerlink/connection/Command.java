package com.codegy.aerlink.connection;

import java.util.Arrays;
import java.util.UUID;

/**
 * Created by Guiye on 18/5/15.
 */
public class Command {

    public static final int IMPORTANCE_MIN = 1;
    public static final int IMPORTANCE_NORMAL = 3;
    public static final int IMPORTANCE_MAX = 500;

    private UUID serviceUUID;
    private String characteristic;
    private byte[] packet;
    private int retryCount = 0;
    private int importance = IMPORTANCE_NORMAL;
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

    public void setImportance(int importance) {
        this.importance = importance;
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
        if (retryCount >= importance) {
            return false ;
        }

        if (importance <= IMPORTANCE_MAX) {
            retryCount++;
        }

        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (o != null && o instanceof Command) {
            Command otherCommand = (Command) o;
            return serviceUUID.equals(otherCommand.getServiceUUID()) &&
                    characteristic.equals(otherCommand.characteristic) &&
                    Arrays.equals(packet, otherCommand.getPacket());
        }
        return false;
    }
}
