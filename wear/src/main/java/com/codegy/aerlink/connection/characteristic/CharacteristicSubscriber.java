package com.codegy.aerlink.connection.characteristic;

/**
 * Created by Guiye on 12/10/16.
 */

public interface CharacteristicSubscriber {
    void subscribeCharacteristic(CharacteristicIdentifier characteristicIdentifier);
    void onConnectionFailed();
    void onConnectionReady();
}
