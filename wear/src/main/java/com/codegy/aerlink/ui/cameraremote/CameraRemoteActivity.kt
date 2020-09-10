package com.codegy.aerlink.ui.cameraremote

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import com.codegy.aerlink.R
import com.codegy.aerlink.service.aerlink.cameraremote.CameraRemoteServiceManager
import com.codegy.aerlink.ui.AerlinkActivity
import com.codegy.aerlink.utils.ScheduledTask
import kotlinx.android.synthetic.main.activity_camera_remote.*

class CameraRemoteActivity : AerlinkActivity(), CameraRemoteServiceManager.Callback {
    private var countdown: Int? = null
        set(value) {
            countdownTask.cancel()
            if (value == null || value <= 0) {
                field = null
            } else {
                field = value
                scheduleCountdownTask()
            }
            updateCountdownInterface()
        }
    private val countdownTask: ScheduledTask by lazy {  ScheduledTask(Looper.getMainLooper()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_remote)
        setAmbientEnabled()

        shutterRelativeLayout.setOnClickListener { takePicture() }
    }

    override fun onPause() {
        val serviceManager = getServiceManager(CameraRemoteServiceManager::class)
        serviceManager?.callback = null
        super.onPause()
    }

    override fun onConnectedToDevice() {
        val serviceManager = getServiceManager(CameraRemoteServiceManager::class)
        if (serviceManager == null) {
            Log.v(LOG_TAG, "Service manager not available")
            showError(true)
            return
        }

        serviceManager.callback = this
        serviceManager.hideNotification()
        showLoading(true)
        serviceManager.requestCameraState()
    }

    override fun onCameraStateChanged(open: Boolean) {
        Log.v(LOG_TAG, "onCameraStateChanged: $open")
        showLoading(false)
        this.countdown = null

        runOnUiThread {
            cameraClosedTextView.visibility = if (open) View.GONE else View.VISIBLE
        }
    }

    override fun onCountdownStarted(countdown: Int) {
        Log.v(LOG_TAG, "onCountdownStarted: $countdown")
        showLoading(false)
        this.countdown = countdown
    }

    override fun onImageTransferStarted() {
        Log.v(LOG_TAG, "onImageTransferStarted")
        showLoading(true)
    }

    override fun onImageTransferFinished(image: Bitmap) {
        Log.v(LOG_TAG, "onImageTransferFinished")
        showLoading(false)
        this.countdown = null
        CameraImageActivity.startWithImage(this, image)
    }

    override fun onCameraError() {
        Log.v(LOG_TAG, "onCameraError")
        showLoading(false)
        showError(true)
    }

    private fun takePicture() {
        val serviceManager = getServiceManager(CameraRemoteServiceManager::class) as? CameraRemoteServiceManager
        if (serviceManager?.isCameraOpen == true) {
            showLoading(true)
            serviceManager.takePicture()
        }
    }

    private fun updateCountdownInterface() {
        runOnUiThread {
            val currentCountdown = countdown
            if (currentCountdown == null) {
                countdownTextView.visibility = View.GONE
                shutterImageView.visibility = View.VISIBLE
            } else {
                countdownTextView.text = "$currentCountdown"
                countdownTextView.visibility = View.VISIBLE
                shutterImageView.visibility = View.GONE
            }
        }
    }

    private fun scheduleCountdownTask() {
        countdownTask.schedule(1000) {
            val currentCountdown = countdown ?: return@schedule
            countdown = currentCountdown - 1
        }
    }

    companion object {
        private val LOG_TAG = CameraRemoteActivity::class.java.simpleName
    }
}