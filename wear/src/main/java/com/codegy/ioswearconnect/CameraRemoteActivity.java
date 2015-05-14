package com.codegy.ioswearconnect;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.IBinder;
import android.support.wearable.view.WatchViewStub;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class CameraRemoteActivity extends Activity implements BLEManager.BLECameraRemoteCallback {

    BLEService mService;
    boolean mServiceBound = false;

    private Button mButton;
    private ImageView mCameraImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_remote);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mCameraImageView = (ImageView) stub.findViewById(R.id.cameraImageView);
                mButton = (Button) stub.findViewById(R.id.button);
                mButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mService != null) {
                            mService.takePicture();
                        }
                    }
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        Intent intent = new Intent(this, BLEService.class);
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mServiceBound) {
            unbindService(mServiceConnection);
            mServiceBound = false;
            mService.getManager().setCameraRemoteCallback(null);
        }
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceBound = false;
            mService.getManager().setCameraRemoteCallback(null);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BLEService.ServiceBinder myBinder = (BLEService.ServiceBinder) service;
            mService = myBinder.getService();
            mService.getManager().setCameraRemoteCallback(CameraRemoteActivity.this);
            mServiceBound = true;
        }
    };

    @Override
    public void onCameraImageChanged(final Bitmap cameraImage) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mCameraImageView.setImageBitmap(cameraImage);
            }
        });
    }
}
