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
public class DiscoveryManager {

    public interface Callback {
        void onDeviceDiscovery(BluetoothDevice device);
    }

    private static final String LOG_TAG = DiscoveryManager.class.getSimpleName();
    private static final int RETRY_TIME = 3000;

    private Context mContext;
    private Callback mCallback;

    private boolean mScanning;

    private BluetoothAdapter mBluetoothAdapter;

    private List<String> mAllowedDevices;
    private ScanSettings mScanSettings;
    private BluetoothLeScanner mScanner;


    public DiscoveryManager(Context mContext, Callback mCallback, BluetoothManager bluetoothManager) {
        this.mContext = mContext;
        this.mCallback = mCallback;


        this.mAllowedDevices = new ArrayList<>(3);
        this.mAllowedDevices.add("Aerlink");
        this.mAllowedDevices.add("BLE Utility");
        this.mAllowedDevices.add("Blank");


        mBluetoothAdapter = bluetoothManager.getAdapter();
    }


    public void startDiscovery() {
        // If disabled -> enable bluetooth
        if (!mBluetoothAdapter.isEnabled()) {
            stopDiscovery();

            mBluetoothAdapter.enable();
            Log.wtf(LOG_TAG, "Bluetooth was disabled");

            Handler handler = new Handler(mContext.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startDiscovery();
                }
            }, RETRY_TIME);

            return;
        }
        else if (isRunning()) {
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


        if (!mScanning) {
            // Scanning did not work, try again in a moment

            Handler handler = new Handler(mContext.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startDiscovery();
                }
            }, RETRY_TIME);
        }
    }

    public void stopDiscovery() {
        mScanning = false;

        stopScanning();
    }

    public boolean isRunning() {
        return mScanning && mScanner != null;
    }


    private synchronized boolean startScanning() throws Exception {
        boolean result = false;

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

}
