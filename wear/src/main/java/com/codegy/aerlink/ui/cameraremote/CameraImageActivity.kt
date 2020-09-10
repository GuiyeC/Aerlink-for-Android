package com.codegy.aerlink.ui.cameraremote

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import com.codegy.aerlink.R
import kotlinx.android.synthetic.main.activity_camera_image.*

class CameraImageActivity : Activity() {
    private val image: Bitmap?
        get() = intent?.getParcelableExtra(IE_CAMERA_IMAGE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_image)
        updateImage()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateImage()
    }

    private fun updateImage() {
        image?.let { cameraImageView.setImageBitmap(it) } ?: run { finish() }
    }

    companion object {
        const val IE_CAMERA_IMAGE: String = "com.codegy.aerlink.ui.cameraremote.IE_CAMERA_IMAGE"

        fun startWithImage(context: Context, image: Bitmap) {
            val intent = Intent(context, CameraImageActivity::class.java)
            intent.putExtra(IE_CAMERA_IMAGE, image)
            context.startActivity(intent)
        }
    }
}
