package com.codegy.aerlink.services.aerlink.cameraremote;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.wearable.view.ProgressSpinner;
import android.view.View;
import android.widget.ImageView;
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
    private TextView mCameraClosedTextView;

    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_remote);
        
        mDisconnectedLayout = findViewById(R.id.disconnectedLinearLayout);
        mConnectionInfoTextView = (TextView) findViewById(R.id.connectionInfoTextView);

        mConnectionErrorLayout = findViewById(R.id.connectionErrorLinearLayout);

        mLoadingLayout = findViewById(R.id.loadingLayout);
        mLoadingSpinner = (ProgressSpinner) findViewById(R.id.loadingSpinner);

        mCameraClosedTextView = (TextView) findViewById(R.id.cameraClosedTextView);

        mShutterImageView = (ImageView) findViewById(R.id.shutterImageView);
        mCountdownTextView = (TextView) findViewById(R.id.countdownTextView);
        RelativeLayout shutterRelativeLayout = (RelativeLayout) findViewById(R.id.shutterRelativeLayout);
        shutterRelativeLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CameraRemoteServiceHandler serviceHandler = (CameraRemoteServiceHandler) getServiceHandler(CameraRemoteServiceHandler.class);

                if (serviceHandler != null) {
                    if (serviceHandler.isCameraOpen()) {
                        setLoading(true);
                        scheduleTimeOutTask();

                        serviceHandler.takePicture(new Runnable() {
                            @Override
                            public void run() {
                                cancelTimeOutTask();
                                setLoading(false);

                                showErrorInterface(true);
                            }
                        });
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

    @Override
    protected void onResume() {
        super.onResume();

        if (mCountdownTextView != null && mShutterImageView != null) {
            mCountdownTextView.setVisibility(View.GONE);
            mShutterImageView.setVisibility(View.VISIBLE);
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
        super.updateInterface();

        boolean connected = isConnected();

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
        CameraRemoteServiceHandler serviceHandler = (CameraRemoteServiceHandler) getServiceHandler(CameraRemoteServiceHandler.class);

        if (serviceHandler != null) {
            setLoading(true);
            scheduleTimeOutTask();

            serviceHandler.setCameraCallback(this);
            serviceHandler.requestState(new Runnable() {
                @Override
                public void run() {
                    setLoading(false);

                    showErrorInterface(true);
                }
            });
        }
        else {
            showErrorInterface(true);
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
    public void onCameraChangedOpen(final boolean open) {
        setLoading(false);

        cancelCountdownTask();
        cancelTimeOutTask();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mCameraClosedTextView != null) {
                    mCameraClosedTextView.setVisibility(open ? View.GONE : View.VISIBLE);
                }
            }
        });
    }

    @Override
    public void onCountdownStarted(int countdown) {
        mCountdown = countdown;
        cancelTimeOutTask();

        setLoading(false);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mCountdownTextView != null && mShutterImageView != null) {
                    mCountdownTextView.setText(Integer.toString(mCountdown));

                    mCountdownTextView.setVisibility(View.VISIBLE);
                    mShutterImageView.setVisibility(View.GONE);
                }
            }
        });

        scheduleCountdownTask();
    }

    @Override
    public void onImageTransferStarted() {
        cancelCountdownTask();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setLoading(true);

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
                setLoading(false);

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
