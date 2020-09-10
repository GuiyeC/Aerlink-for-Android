package com.codegy.aerlink.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Vibrator
import android.support.wearable.activity.WearableActivity
import android.view.View
import com.codegy.aerlink.R
import com.codegy.aerlink.service.notifications.NotificationServiceManager
import kotlinx.android.synthetic.main.activity_phone.*

class PhoneActivity : WearableActivity() {
    private var vibrator: Vibrator? = null
    private val callUID: ByteArray?
        get() = intent?.getByteArrayExtra(NotificationServiceManager.IE_NOTIFICATION_UID)
    private val title: String?
        get() = intent?.getStringExtra(IE_TITLE)
    private val message: String?
        get() = intent?.getStringExtra(IE_MESSAGE)

    private val endCallReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone)
        setAmbientEnabled()
        vibrator = getSystemService(Vibrator::class.java)

        callerIdTextView.text = title
        messageTextView.text = message
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        callerIdTextView.text = title
        messageTextView.text = message
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(endCallReceiver, IntentFilter(NotificationServiceManager.IA_CALL_ENDED))
        vibrator?.vibrate(CALL_VIBRATION_PATTERN, 0)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(endCallReceiver)
        vibrator?.cancel()
    }

    fun answer(view: View) {
        callUID?.let {
            val positiveIntent = Intent(NotificationServiceManager.IA_POSITIVE)
            positiveIntent.putExtra(NotificationServiceManager.IE_NOTIFICATION_UID, it)
            sendBroadcast(positiveIntent)
        }

        finish()
    }

    fun hangUp(view: View) {
        callUID?.let {
            val negativeIntent = Intent(NotificationServiceManager.IA_NEGATIVE)
            negativeIntent.putExtra(NotificationServiceManager.IE_NOTIFICATION_UID, it)
            sendBroadcast(negativeIntent)
        }

        finish()
    }

    companion object {
        private val CALL_VIBRATION_PATTERN = longArrayOf(600, 600)
        const val IE_TITLE: String = "com.codegy.aerlink.ui.PhoneActivity.IE_TITLE"
        const val IE_MESSAGE: String = "com.codegy.aerlink.ui.PhoneActivity.IE_MESSAGE"

        fun start(context: Context, title: String?, message: String?, callUID: ByteArray) {
            val intent = Intent(context, PhoneActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            intent.putExtra(IE_TITLE, title)
            intent.putExtra(IE_MESSAGE, message)
            intent.putExtra(NotificationServiceManager.IE_NOTIFICATION_UID, callUID)
            context.startActivity(intent)
        }
    }
}
