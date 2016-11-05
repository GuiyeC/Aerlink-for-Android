package com.codegy.aerlink.services.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.wearable.activity.WearableActivity;
import android.view.View;
import android.widget.TextView;
import com.codegy.aerlink.Constants;
import com.codegy.aerlink.R;

public class PhoneActivity extends WearableActivity {

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
        setAmbientEnabled();

        Intent intent = getIntent();

        String callerId = intent.getStringExtra(Constants.IE_NOTIFICATION_TITLE);
        String message = intent.getStringExtra(Constants.IE_NOTIFICATION_MESSAGE);
        callUID = intent.getByteArrayExtra(Constants.IE_NOTIFICATION_UID);


        mCallerIdTextView = (TextView) findViewById(R.id.callerIdTextView);
        mMessageTextView = (TextView) findViewById(R.id.messageTextView);

        mCallerIdTextView.setText(callerId);
        mMessageTextView.setText(message);


        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock((PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP), "PhoneActivity_TAG");
            wakeLock.acquire();
        }

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null) {
            vibrator.vibrate(CALL_VIBRATION_PATTERN, 0);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        try {
            final String callerId = intent.getStringExtra(Constants.IE_NOTIFICATION_TITLE);
            final String message = intent.getStringExtra(Constants.IE_NOTIFICATION_MESSAGE);
            callUID = intent.getByteArrayExtra(Constants.IE_NOTIFICATION_UID);

            mCallerIdTextView.setText(callerId);
            mMessageTextView.setText(message);
        }
        catch (Exception e) {
            e.printStackTrace();
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

        registerReceiver(mEndCallReceiver, new IntentFilter(Constants.IA_END_CALL));
    }

    @Override
    protected void onPause() {
        super.onPause();

        unregisterReceiver(mEndCallReceiver);
    }

    public void answer(View v) {
        if (callUID != null) {
            Intent positiveIntent = new Intent(Constants.IA_POSITIVE);
            positiveIntent.putExtra(Constants.IE_NOTIFICATION_UID, callUID);

            sendBroadcast(positiveIntent);
        }

        finish();
    }

    public void hangUp(View v) {
        if (callUID != null) {
            Intent negativeIntent = new Intent(Constants.IA_NEGATIVE);
            negativeIntent.putExtra(Constants.IE_NOTIFICATION_UID, callUID);

            sendBroadcast(negativeIntent);
        }

        finish();
    }

}
