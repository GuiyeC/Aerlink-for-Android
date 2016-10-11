package com.codegy.aerlink.exceptions;

/**
 * Created by Guiye on 25/8/16.
 */

public class NoBluetoothException extends RuntimeException {

    @Override
    public String getMessage() {
        return "Bluetooth not supported";
    }

}
