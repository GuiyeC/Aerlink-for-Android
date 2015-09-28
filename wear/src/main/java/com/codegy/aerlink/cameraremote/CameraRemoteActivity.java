package com.codegy.aerlink.cameraremote;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.wearable.view.WatchViewStub;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.codegy.aerlink.Constants;
import com.codegy.aerlink.R;
import com.codegy.aerlink.utils.AerlinkActivity;
import com.codegy.aerlink.utils.ScheduledTask;

public class CameraRemoteActivity extends AerlinkActivity implements CameraRemoteServiceHandler.CameraRemoteCallback {

    private int mCountdown = 0;

    private ImageView mShutterImageView;
    private TextView mCountdownTextView;
    private RelativeLayout mShutterRelativeLayout;
    private LinearLayout mDisconnectedLinearLayout;
    private TextView mCameraClosedTextView;

    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_camera_remote);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mDisconnectedLinearLayout = (LinearLayout) stub.findViewById(R.id.disconnectedLinearLayout);
                mCameraClosedTextView = (TextView) stub.findViewById(R.id.cameraClosedTextView);

                mShutterImageView = (ImageView) stub.findViewById(R.id.shutterImageView);
                mCountdownTextView = (TextView) stub.findViewById(R.id.countdownTextView);
                mShutterRelativeLayout = (RelativeLayout) stub.findViewById(R.id.shutterRelativeLayout);
                mShutterRelativeLayout.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        CameraRemoteServiceHandler serviceHandler = (CameraRemoteServiceHandler) getServiceHandler(CameraRemoteServiceHandler.class);

                        if (serviceHandler != null) {
                            if (serviceHandler.isCameraOpen()) {
                                serviceHandler.takePicture();
                            }
                            else {
                                cancelCountdownTask();

                                if (mCameraClosedTextView != null) {
                                    mCameraClosedTextView.setVisibility(View.VISIBLE);
                                }
                            }
                        }
                        else {
                            onDisconnectedFromDevice();
                        }
                    }
                });

                updateInterface();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        try {
            mCountdownTextView.setVisibility(View.GONE);
            mShutterImageView.setVisibility(View.VISIBLE);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock((PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP), "CameraRemoteActivity_TAG");
            wakeLock.acquire();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (wakeLock != null) {
            wakeLock.release();
        }
    }

    @Override
    public void updateInterface() {
        boolean connected = isConnected();

        if (mDisconnectedLinearLayout != null) {
            mDisconnectedLinearLayout.setVisibility(connected ? View.GONE : View.VISIBLE);
        }

        cancelCountdownTask();

        if (mCountdownTextView != null && mShutterImageView != null) {
            mCountdownTextView.setVisibility(View.GONE);
            mShutterImageView.setVisibility(View.VISIBLE);
        }

        if (connected && mCameraClosedTextView != null) {
            // We are connected, check if the camera is open or closed
            CameraRemoteServiceHandler serviceHandler = (CameraRemoteServiceHandler) getServiceHandler(CameraRemoteServiceHandler.class);

            if (serviceHandler != null) {
                mCameraClosedTextView.setVisibility(serviceHandler.isCameraOpen() ? View.GONE : View.VISIBLE);
            }
        }
    }

    @Override
    public void onConnectedToDevice() {
        super.onConnectedToDevice();

        CameraRemoteServiceHandler serviceHandler = (CameraRemoteServiceHandler) getServiceHandler(CameraRemoteServiceHandler.class);

        if (serviceHandler != null) {
            serviceHandler.setCameraCallback(this);
        }
        else {
            onDisconnectedFromDevice();
        }
    }

    @Override
    public void onDisconnectedFromDevice() {
        super.onDisconnectedFromDevice();

        CameraRemoteServiceHandler serviceHandler = (CameraRemoteServiceHandler) getServiceHandler(CameraRemoteServiceHandler.class);

        if (serviceHandler != null) {
            serviceHandler.setCameraCallback(null);
        }
    }

    @Override
    public void onCameraChangedOpen(boolean open) {
        cancelCountdownTask();

        if (mCameraClosedTextView != null) {
            mCameraClosedTextView.setVisibility(open ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void onCountdownStarted(int countdown) {
        mCountdown = countdown;

        try {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mCountdownTextView.setText(Integer.toString(mCountdown));

                    mCountdownTextView.setVisibility(View.VISIBLE);
                    mShutterImageView.setVisibility(View.GONE);
                }
            });

            scheduleCountdownTask();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onImageTransferStarted() {
        cancelCountdownTask();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mCountdownTextView.setVisibility(View.GONE);
                mShutterImageView.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onImageTransferFinished(final Bitmap cameraImage) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent cameraImageIntent = new Intent(CameraRemoteActivity.this, CameraImageActivity.class);
                cameraImageIntent.putExtra(Constants.IE_CAMERA_IMAGE, cameraImage);
                startActivity(cameraImageIntent);
            }
        });
    }


    private ScheduledTask mCountdownTask;

    private void scheduleCountdownTask() {
        if (mCountdownTask == null) {
            mCountdownTask = new ScheduledTask(1000, getMainLooper(), new Runnable() {
                @Override
                public void run() {
                    mCountdown--;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mCountdown > 0) {
                                mCountdownTextView.setText(Integer.toString(mCountdown));
                                mCountdownTextView.setVisibility(View.VISIBLE);
                                mShutterImageView.setVisibility(View.GONE);

                                scheduleCountdownTask();
                            }
                            else {
                                mCountdownTextView.setVisibility(View.GONE);
                            }
                        }
                    });
                }
            });
        }
        else {
            mCountdownTask.cancel();
        }

        mCountdownTask.schedule();
    }

    private void cancelCountdownTask() {
        mCountdown = 0;

        if (mCountdownTask != null) {
            mCountdownTask.cancel();
        }
    }

}
