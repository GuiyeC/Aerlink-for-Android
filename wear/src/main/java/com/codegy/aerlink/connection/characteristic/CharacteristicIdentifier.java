package com.codegy.aerlink.connection.characteristic;

import java.util.UUID;

/**
 * Created by Guiye on 19/5/15.
 */
public class CharacteristicIdentifier {

    private UUID serviceUUID;
    private UUID characteristicUUID;

    public CharacteristicIdentifier(UUID serviceUUID, String characteristicUUID) {
        this.serviceUUID = serviceUUID;
        this.characteristicUUID = UUID.fromString(characteristicUUID);
    }

    public UUID getServiceUUID() {
        return serviceUUID;
    }

    public UUID getCharacteristicUUID() {
        return characteristicUUID;
    }

}
