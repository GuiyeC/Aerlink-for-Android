package com.codegy.aerlink.connection;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import com.codegy.aerlink.ALSConstants;
import com.codegy.aerlink.utils.ScheduledTask;

import java.util.List;

/**
 * Created by Guiye on 28/8/15.
 */
public class DiscoveryHelper {

    public interface DiscoveryCallback {
        void connectToDevice(BluetoothDevice device);
    }

    private static final String LOG_TAG = DiscoveryHelper.class.getSimpleName();

    private Context mContext;
    private DiscoveryCallback mCallback;

    private boolean mScanning;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;

    private ScanSettings mScanSettings;
    private BluetoothLeScanner mScanner;

    private AdvertiseData mAdvertiseData;
    private AdvertiseSettings mAdvertiseSettings;
    private BluetoothLeAdvertiser mAdvertiser;


    public DiscoveryHelper(Context mContext, DiscoveryCallback mCallback) {
        this.mContext = mContext;
        this.mCallback = mCallback;

        mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
    }

    public void close() {
        stopScanningAndAdvertising();
    }


    public void startScanningAndAdvertising() {
        // If disabled -> enable bluetooth
        if (!mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
            Log.wtf(LOG_TAG, "Bluetooth was disabled");

            stopScanningAndAdvertising();
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
            try {
                startAdvertising();
            }
            catch (Exception e) {
                e.printStackTrace();

                stopAdvertising();
            }
        }
        else {
            // Scanning did not work, try again in a moment

            Handler handler = new Handler(mContext.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startScanningAndAdvertising();
                }
            }, 3000);
        }
    }

    public void stopScanningAndAdvertising() {
        mScanning = false;

        stopScanning();
        stopAdvertising();
    }


    private boolean startScanning() throws Exception {
        boolean result = false;

        if (mScanning && mScanner != null) {
            // Scanner is already running
            result = true;
        }
        else {
            mScanner = mBluetoothAdapter.getBluetoothLeScanner();

            // If bluetooth was disabled, the scanner may be null
            if (mScanner != null) {
                if (mScanSettings == null) {
                    mScanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build();
                }

                        try {
                            mScanner.startScan(null, mScanSettings, mScanCallback);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }


                Log.d(LOG_TAG, "Scanning started");

                result = true;
            }
        }

        return result;
    }

    private void stopScanning() {
        if (mScanner != null) {
            try {
                mScanner.stopScan(mScanCallback);
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            mScanner = null;

            Log.d(LOG_TAG, "Scanning stopped");
        }
    }


    private final ScanCallback mScanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, android.bluetooth.le.ScanResult result) {
            Log.d(LOG_TAG, "Scan Result: " + result.toString());

            BluetoothDevice device = result.getDevice();
            String deviceName = device != null ? device.getName() : null;

            if (mCallback != null && deviceName != null && (deviceName.equals("Aerlink") || deviceName.equals("BLE Utility") || deviceName.equals("Blank"))) {
                mCallback.connectToDevice(device);
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
            startScanningAndAdvertising();
        }

    };


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
                } catch (Exception e) {
                    e.printStackTrace();
                }


                Log.d(LOG_TAG, "Advertising started");

                result = true;
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

}
