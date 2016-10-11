package com.codegy.aerlink.connection;

import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Guiye on 28/8/15.
 */
public class DiscoveryHelper {

    public interface DiscoveryCallback {
        void onDeviceDiscovery(BluetoothDevice device);
    }

    private static final String LOG_TAG = DiscoveryHelper.class.getSimpleName();

    private Context mContext;
    private DiscoveryCallback mCallback;

    private boolean mScanning;

    private BluetoothAdapter mBluetoothAdapter;

    private List<String> mAllowedDevices;
    private ScanSettings mScanSettings;
    private BluetoothLeScanner mScanner;


    public DiscoveryHelper(Context mContext, DiscoveryCallback mCallback, BluetoothManager bluetoothManager) {
        this.mContext = mContext;
        this.mCallback = mCallback;


        this.mAllowedDevices = new ArrayList<>(3);
        this.mAllowedDevices.add("Aerlink");
        this.mAllowedDevices.add("BLE Utility");
        this.mAllowedDevices.add("Blank");


        mBluetoothAdapter = bluetoothManager.getAdapter();

        /* Start server to be discovered by advertising
            if (mBluetoothGattServer == null) {
                mBluetoothGattServer = bluetoothManager.openGattServer(mContext, mGattServerCallback);
            }
         */
    }


    public void startDiscovery() {
        // If disabled -> enable bluetooth
        if (!mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
            Log.wtf(LOG_TAG, "Bluetooth was disabled");

            stopDiscovery();
        }
        else if (mScanning && mScanner != null) {
            // Scanner is already running
            // Don't start anything

            return;
        }


        try {
            mScanning = startScanning();
        }
        catch (Exception e) {
            e.printStackTrace();

            mScanning = false;
            stopScanning();
        }


        if (mScanning) {
            /*
            try {
                startAdvertising();
            }
            catch (Exception e) {
                e.printStackTrace();

                stopAdvertising();
            }
            */
        }
        else {
            // Scanning did not work, try again in a moment

            Handler handler = new Handler(mContext.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startDiscovery();
                }
            }, 3000);
        }
    }

    public void stopDiscovery() {
        mScanning = false;

        stopScanning();
        //stopAdvertising();
    }


    private boolean startScanning() throws Exception {
        if (mScanning && mScanner != null) {
            // Scanner is already running
            return true;
        }

        boolean result = false;

        synchronized (this) {
            mScanner = mBluetoothAdapter.getBluetoothLeScanner();

            // If bluetooth was disabled, the scanner may be null
            if (mScanner != null) {
                if (mScanSettings == null) {
                    mScanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build();
                }

                try {
                    mScanner.startScan(null, mScanSettings, mScanCallback);

                    Log.d(LOG_TAG, "Scanning started");

                    result = true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return result;
    }

    private void stopScanning() {
        if (mScanner != null) {
            synchronized (this) {
                try {
                    mScanner.stopScan(mScanCallback);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                mScanner = null;

                Log.d(LOG_TAG, "Scanning stopped");
            }
        }
    }


    private final ScanCallback mScanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(LOG_TAG, "Scan Result: " + result.toString());

            BluetoothDevice device = result.getDevice();
            String deviceName = device != null ? device.getName() : null;

            if (deviceName != null && mAllowedDevices.contains(deviceName)) {
                mCallback.onDeviceDiscovery(device);

                stopScanning();
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.i(LOG_TAG, "Batch Scan Results: " + results.toString());
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.d(LOG_TAG, "Scan Failed: " + errorCode);

            stopScanning();
            startDiscovery();
        }

    };


    /*

    private AdvertiseData mAdvertiseData;
    private AdvertiseSettings mAdvertiseSettings;
    private BluetoothLeAdvertiser mAdvertiser;

    private boolean startAdvertising() throws Exception {
        boolean result = false;

        if (mAdvertiser != null) {
            // Advertiser is already running
            result = true;
        }
        else if (mScanning) {
            mAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();

            if (mAdvertiser != null) {
                if (mAdvertiseSettings == null) {
                    mAdvertiseSettings = new AdvertiseSettings.Builder()
                            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                            .setConnectable(true)
                            .build();
                }
                if (mAdvertiseData == null) {
                    mAdvertiseData = new AdvertiseData.Builder()
                            .setIncludeDeviceName(false)
                            .setIncludeTxPowerLevel(true)
                            .addServiceUuid(new ParcelUuid(ALSConstants.SERVICE_UUID))
                            .build();
                }

                try {
                    mAdvertiser.stopAdvertising(mAdvertiseCallback);
                    mAdvertiser.startAdvertising(mAdvertiseSettings, mAdvertiseData, mAdvertiseCallback);

                    Log.d(LOG_TAG, "Advertising started");
                    result = true;
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return result;
    }

    private void stopAdvertising() {
        if (mAdvertiser != null) {
            try {
                mAdvertiser.stopAdvertising(mAdvertiseCallback);
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            mAdvertiser = null;

            Log.d(LOG_TAG, "Advertising stopped");
        }
    }

    private final AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            Log.e(LOG_TAG, "Not broadcasting: " + errorCode);

            stopAdvertising();

            try {
                startAdvertising();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.v(LOG_TAG, "Broadcasting");
        }
    };

    private BluetoothGattServer mBluetoothGattServer;
    private final BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, final int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    Log.i(LOG_TAG, "Connected to device: " + device.getAddress());

                    mCallback.onDeviceDiscovery(device);
                }
                else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Log.v(LOG_TAG, "Disconnected from device");
                }
            }
            else {
                Log.e(LOG_TAG, "Error when connecting: " + status);
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded,
                                                 int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite,
                    responseNeeded, offset, value);
            Log.v(LOG_TAG, "Characteristic Write request: " + Arrays.toString(value));
            if (responseNeeded) {
                mBluetoothGattServer.sendResponse(device, requestId, 0,
                        0, // No need to respond with an offset
                        null); // No need to respond with a value
            }
        }
    };
    */

}
