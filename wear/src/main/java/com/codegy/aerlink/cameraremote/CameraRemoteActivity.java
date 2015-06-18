package com.codegy.aerlink.cameraremote;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.wearable.view.WatchViewStub;
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
    private RelativeLayout mShutterRelativeLayout;
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_camera_remote);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                setInfoTextView((TextView) stub.findViewById(R.id.infoTextView));

                tryToConnect();

                mShutterImageView = (ImageView) stub.findViewById(R.id.shutterImageView);
                mCountdownTextView = (TextView) stub.findViewById(R.id.countdownTextView);
                mShutterRelativeLayout = (RelativeLayout) stub.findViewById(R.id.shutterRelativeLayout);
                mShutterRelativeLayout.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (getService() != null) {
                            CameraRemoteServiceHandler serviceHandler = (CameraRemoteServiceHandler) getService().getServiceHandler(CameraRemoteServiceHandler.class);
                            if (serviceHandler != null) {
                                if (serviceHandler.isCameraOpen()) {
                                    serviceHandler.takePicture();
                                }
                                else {
                                    showCameraClosed();
                                }
                            }
                            else {
                                showDisconnected();
                            }
                        }
                        else {
                            showDisconnected();
                        }
                    }
                });
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
    public void tryToConnect() {
        if (getService() != null) {
            CameraRemoteServiceHandler serviceHandler = (CameraRemoteServiceHandler) getService().getServiceHandler(CameraRemoteServiceHandler.class);
            if (serviceHandler != null) {
                serviceHandler.setCameraCallback(CameraRemoteActivity.this);

                if (serviceHandler.isCameraOpen()) {
                    hideInfoTextView();
                } else {
                    showCameraClosed();
                }
            }
            else {
                showDisconnected();
            }
        }
        else {
            showDisconnected();
        }
    }

    @Override
    public void disconnect() {
        mCountdown = 0;
        cancelCountdownTask();

        if (getService() != null) {
            CameraRemoteServiceHandler serviceHandler = (CameraRemoteServiceHandler) getService().getServiceHandler(CameraRemoteServiceHandler.class);
            if (serviceHandler != null) {
                serviceHandler.setCameraCallback(null);
            }
        }
    }

    @Override
    public void hideInfoTextView() {
        super.hideInfoTextView();

        if (mCountdownTextView != null && mShutterImageView != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mCountdownTextView.setVisibility(View.GONE);
                    mShutterImageView.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    private void showCameraClosed() {
        showInfoText("Camare closed.\nStart the camera on \"Aerlink\" on your iOS device.");
        mCountdown = 0;
        cancelCountdownTask();
    }

    @Override
    public void showDisconnected() {
        super.showDisconnected();

        mCountdown = 0;
        cancelCountdownTask();
    }

    @Override
    public void onCameraChangedOpen(boolean open) {
        if (open) {
            hideInfoTextView();
        }
        else {
            showCameraClosed();
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
        mCountdown = 0;
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
        if (mCountdownTask != null) {
            mCountdownTask.cancel();
        }
    }

}
