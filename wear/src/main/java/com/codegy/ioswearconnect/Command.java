package com.codegy.ioswearconnect;

import java.util.UUID;

/**
 * Created by Guiye on 18/4/15.
 */
public class Command {

    private UUID serviceUUID;
    private String characteristic;
    private byte[] packet;
    private int retryCount = 0;

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

    public boolean shouldRetryAgain() {
        if (retryCount >= 3) {
            return false ;
        }

        retryCount++;

        return true;
    }

}
