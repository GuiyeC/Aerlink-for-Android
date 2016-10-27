package com.codegy.aerlink.utils;

import com.codegy.aerlink.connection.ConnectionState;

/**
 * Created by Guiye on 25/10/16.
 */

public interface ServiceObserver {

    void onConnectionStateChanged(ConnectionState state);

}
