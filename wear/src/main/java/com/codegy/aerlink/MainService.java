package com.codegy.aerlink;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import com.codegy.aerlink.connection.*;
import com.codegy.aerlink.connection.characteristic.CharacteristicIdentifier;
import com.codegy.aerlink.connection.command.Command;
import com.codegy.aerlink.utils.ServiceHandler;
import com.codegy.aerlink.utils.ServiceUtils;

import java.util.Queue;


public class MainService extends Service implements ServiceUtils, ConnectionManager.Callback, DiscoveryManager.Callback {

    private static final String LOG_TAG = MainService.class.getSimpleName();


    private IBinder mBinder = new ServiceBinder();

    private ConnectionState state = ConnectionState.Disconnected;
    private int mBondedDeviceFailedConnections = 0;

    private ConnectionHelper connectionHelper;
    private DiscoveryManager discoveryManager;
    private ConnectionManager connectionManager;
    private NotificationManager notificationManager;
    private ServiceHandlerManager serviceHandlerManager;


    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(LOG_TAG, "-=-=-=-=-=-=-=-=-=  Service created  =-=-=-=-=-=-=-=-=-");

        start();
    }

    @Override
    public void onDestroy() {
        Log.e(LOG_TAG, "xXxXxXxXxXxXxXxXxX Service destroyed XxXxXxXxXxXxXxXxXx");

        stop();

        getNotificationManager().cancelAll();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.v(LOG_TAG, "onBind");
        return mBinder;
    }


    private void start() {
        Log.v(LOG_TAG, "Starting...");
        // Just in case, try to close everything
        //stop();

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);


        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) || bluetoothManager == null) {
            setState(ConnectionState.NoBluetooth);
            return;
        }

        discoveryManager = new DiscoveryManager(this, this, bluetoothManager);
        connectionManager = new ConnectionManager(this, this, bluetoothManager.getAdapter());
        serviceHandlerManager = new ServiceHandlerManager(this, this);

        setState(ConnectionState.Disconnected);

        Log.v(LOG_TAG, "Started");
    }

    private void stop() {
        Log.v(LOG_TAG, "Stopping...");

        if (serviceHandlerManager != null) {
            serviceHandlerManager.close();
            serviceHandlerManager = null;
        }

        if (discoveryManager != null) {
            discoveryManager.stopDiscovery();
            discoveryManager = null;
        }

        if (connectionManager != null) {
            connectionManager.close();
            connectionManager = null;
        }

        Log.v(LOG_TAG, "Stopped");
    }

    public void setState(ConnectionState state) {
        switch (state) {
            case NoBluetooth:
                Log.wtf(LOG_TAG, "State: Bluetooth not supported");
                connectionManager = null;

                stop();

                break;
            case Stopped:
                Log.i(LOG_TAG, "State: Stopped");
                break;
            case Ready:
                mBondedDeviceFailedConnections = 0;
                Log.i(LOG_TAG, "State: Ready");
                break;
            case Disconnected:
                Log.i(LOG_TAG, "State: Disconnected");

                BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                BluetoothDevice device = BluetoothUtils.getBondedDevice(bluetoothManager.getAdapter());

                if (device == null || mBondedDeviceFailedConnections >= 5) {
                    Log.i(LOG_TAG, "Starting discovery");
                    discoveryManager.startDiscovery();
                }
                else {
                    Log.i(LOG_TAG, "Connecting to previously bonded device");
                    mBondedDeviceFailedConnections += 1;
                    connectionManager.connectToDevice(device);
                }

                break;
            case Connecting:
                Log.i(LOG_TAG, "State: Connecting");
                break;
        }

        if (this.state == state) {
            return;
        }

        this.state = state;

        if (state != ConnectionState.Ready) {
            Intent stateIntent = new Intent(Constants.IA_SERVICE_NOT_READY);
            sendBroadcast(stateIntent);
        }
        else {
            Intent stateIntent = new Intent(Constants.IA_SERVICE_READY);
            sendBroadcast(stateIntent);
        }

        if (connectionHelper == null) {
            connectionHelper = new ConnectionHelper(this, this);
        }

        connectionHelper.showHelpForState(state);
    }

    public boolean isConnectionReady() {
        return state == ConnectionState.Ready;
    }

    public ServiceHandler getServiceHandler(Class serviceHandlerClass) {
        return serviceHandlerManager.getServiceHandler(serviceHandlerClass);
    }


    private NotificationManager getNotificationManager() {
        if (notificationManager == null) {
            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }

        return notificationManager;
    }

    @Override
    public void addCommandToQueue(Command command) {
        connectionManager.addCommandToQueue(command);
    }

    @Override
    public void notify(String tag, int id, Notification notification) {
        getNotificationManager().notify(tag, id, notification);
    }

    @Override
    public void cancelNotification(String tag, int id) {
        getNotificationManager().cancel(tag, id);
    }

    @Override
    public void onDeviceDiscovery(BluetoothDevice device) {
        Log.v(LOG_TAG, "Device discovered");

        connectionManager.connectToDevice(device);
    }

    @Override
    public void onConnectionStateChange(ConnectionState state) {
        setState(state);
    }

    @Override
    public void onReadyToSubscribe(BluetoothGatt bluetoothGatt) {
        Log.i(LOG_TAG, "Ready to Subscribe");
        Queue<CharacteristicIdentifier> requests = serviceHandlerManager.subscribeToServices(bluetoothGatt);

        connectionManager.addSubscribeRequests(requests);
    }

    @Override
    public void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        serviceHandlerManager.handleCharacteristic(characteristic);
    }


    public class ServiceBinder extends Binder {
        public MainService getService() {
            return MainService.this;
        }
    }

}
