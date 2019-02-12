package com.codegy.aerlink

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import android.support.wearable.activity.WearableActivity
import com.codegy.aerlink.extensions.resetBondedDevices
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : WearableActivity() {

    private val PERMISSIONS_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Enables Always-on
        setAmbientEnabled()
        serviceSwitch.setOnCheckedChangeListener { compoundButton, b ->
            if (!compoundButton.isChecked) {
                stopService(Intent(this, MainService::class.java))
                return@setOnCheckedChangeListener
            }

            if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this@MainActivity,
                        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                        PERMISSIONS_REQUEST_CODE)
                return@setOnCheckedChangeListener
            }

            startService(Intent(this, MainService::class.java))
        }

        unbondButton.setOnClickListener {
            if (serviceSwitch.isChecked) {
                serviceSwitch.isChecked = true
                stopService(Intent(this, MainService::class.java))
            }

            getSystemService(BluetoothManager::class.java).adapter.resetBondedDevices()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            var success = true

            if (grantResults.size == permissions.size) {
                for (result in grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        success = false
                        break
                    }
                }
            } else {
                success = false
            }

            if (success) {
                startService(Intent(this, MainService::class.java))
            }
        }
    }

}
