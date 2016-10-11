package com.codegy.aerlink.connection;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Created by Guiye on 25/8/16.
 */

public class BluetoothUtils {

    private static final String LOG_TAG = BluetoothUtils.class.getSimpleName();


    public static BluetoothDevice getBondedDevice(BluetoothAdapter bluetoothAdapter) {
        BluetoothDevice bondedDevice = null;

        if (bluetoothAdapter != null) {
            try {
                Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();
                if (devices != null) {
                    for (BluetoothDevice device : devices) {
                        String deviceName = device.getName();
                        if (deviceName != null && (deviceName.equals("Aerlink") || deviceName.equals("BLE Utility") || deviceName.equals("Blank"))) {
                            bondedDevice = device;
                            break;
                        }
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        return bondedDevice;
    }

    public static void resetBondedDevices(BluetoothAdapter bluetoothAdapter) {
        if (bluetoothAdapter != null) {
            try {
                Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();
                if (devices != null) {
                    for (BluetoothDevice device : devices) {
                        String deviceName = device.getName();
                        if (deviceName != null && (deviceName.equals("Aerlink") || deviceName.equals("BLE Utility") || deviceName.equals("Blank"))) {
                            unpairDevice(device);
                        }
                    }
                }

                Log.d(LOG_TAG, "Bonded devices reset");
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void unpairDevice(BluetoothDevice device) {
        Log.d(LOG_TAG, device.getName() + ": Unpairing...");

        try {
            Method m = device.getClass().getMethod("removeBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void disableBluetooth(BluetoothAdapter bluetoothAdapter) {
        try {
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.disable();

                Log.d(LOG_TAG, "Disabling bluetooth");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
