package com.codegy.ioswearconnect;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.wearable.view.WatchViewStub;
import android.view.View;
import android.widget.TextView;

public class PhoneActivity extends Activity {

    public static final String ACTION_END_CALL = "ACTION_END_CALL";
    public static final String EXTRA_TITLE = "EXTRA_TITLE";
    public static final String EXTRA_MESSAGE = "EXTRA_MESSAGE";
    private static final long CALL_VIBRATION_PATTERN[] = { 600, 600 };

    private byte[] callUID;
    private TextView mCallerIdTextView;
    private TextView mMessageTextView;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;

    private final BroadcastReceiver mEndCallReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone);

        final Intent intent = getIntent();

        final String callerId = intent.getStringExtra(EXTRA_TITLE);
        final String message = intent.getStringExtra(EXTRA_MESSAGE);
        callUID = intent.getByteArrayExtra(BLEService.INTENT_EXTRA_UID);

        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mCallerIdTextView = (TextView) stub.findViewById(R.id.callerIdTextView);
                mMessageTextView = (TextView) stub.findViewById(R.id.messageTextView);

                mCallerIdTextView.setText(callerId);
                mMessageTextView.setText(message);
            }
        });


        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock((PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP), "iOS_WEAR_CALL_TAG");
            wakeLock.acquire();
        }

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null) {
            vibrator.vibrate(CALL_VIBRATION_PATTERN, 0);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }

        if (wakeLock != null) {
            wakeLock.release();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(mEndCallReceiver, new IntentFilter(ACTION_END_CALL));
    }

    @Override
    protected void onPause() {
        super.onPause();

        unregisterReceiver(mEndCallReceiver);
    }

    public void answer(View v) {
        if (callUID != null) {
            Intent positiveIntent = new Intent(BLEService.INTENT_ACTION_POSITIVE);
            positiveIntent.putExtra(BLEService.INTENT_EXTRA_UID, callUID);

            sendBroadcast(positiveIntent);
        }

        finish();
    }

    public void hangUp(View v) {
        if (callUID != null) {
            Intent negativeIntent = new Intent(BLEService.INTENT_ACTION_NEGATIVE);
            negativeIntent.putExtra(BLEService.INTENT_EXTRA_UID, callUID);

            sendBroadcast(negativeIntent);
        }

        finish();
    }
}
