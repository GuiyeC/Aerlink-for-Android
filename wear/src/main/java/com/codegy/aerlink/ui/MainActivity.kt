package com.codegy.aerlink.ui

import android.bluetooth.BluetoothManager
import android.os.Bundle
import android.view.View
import com.codegy.aerlink.R
import com.codegy.aerlink.connection.ConnectionState
import com.codegy.aerlink.extensions.resetBondedDevices
import com.codegy.aerlink.service.battery.BatteryServiceManager
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : ServiceActivity(), BatteryServiceManager.Observer {
    var batteryServiceManager: BatteryServiceManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Enables Always-on
        setAmbientEnabled()
        serviceSwitch.setOnCheckedChangeListener { switch, checked ->
            if (!switch.isPressed) {
                return@setOnCheckedChangeListener
            }
            if (checked) {
                startService()
            } else {
                stopService()
            }
        }

        unbondButton.setOnClickListener {
            if (isServiceRunning) {
                stopService()
                serviceSwitch.isChecked = false
            }

            getSystemService(BluetoothManager::class.java)?.adapter?.resetBondedDevices()
        }
    }

    override fun onResume() {
        super.onResume()

        serviceSwitch.isChecked = isServiceRunning
    }

    override fun onPause() {
        super.onPause()

        batteryServiceManager?.observer = null
        batteryServiceManager = null
        batteryInfoTextView.visibility = View.GONE
    }

    override fun onConnectedToDevice() {
        batteryServiceManager = getServiceManager(BatteryServiceManager::class) as? BatteryServiceManager
        batteryServiceManager?.observer = this
    }

    override fun onBatteryLevelChanged(batteryLevel: Int) {
        runOnUiThread {
            batteryInfoTextView.visibility = View.VISIBLE
            batteryInfoTextView.setTextColor(getColor(if (batteryLevel > 20) R.color.connection_state_ready else R.color.connection_state_disconnected))
            batteryInfoTextView.text = "$batteryLevel%"
        }
    }

    override fun updateInterfaceForState(state: ConnectionState) {
        runOnUiThread {
            when (state) {
                ConnectionState.Ready -> {
                    connectionInfoTextView.setTextColor(getColor(R.color.connection_state_ready))
                    connectionInfoTextView.text = getString(R.string.connection_state_ready)
                    connectionInfoImageView.setColorFilter(getColor(R.color.connection_state_ready))
                    connectionInfoImageView.setImageResource(R.drawable.status_connected)
                }
                ConnectionState.Connecting -> {
                    connectionInfoTextView.setTextColor(getColor(R.color.connection_state_connecting))
                    connectionInfoTextView.text = getString(R.string.connection_state_connecting)
                    connectionInfoImageView.setColorFilter(getColor(R.color.connection_state_connecting))
                    connectionInfoImageView.setImageResource(R.drawable.status_connected)
                    batteryInfoTextView.visibility = View.GONE
                }
                ConnectionState.Bonding -> {
                    connectionInfoTextView.setTextColor(getColor(R.color.connection_state_bonding))
                    connectionInfoTextView.text = getString(R.string.connection_state_bonding)
                    connectionInfoImageView.setColorFilter(getColor(R.color.connection_state_bonding))
                    connectionInfoImageView.setImageResource(R.drawable.status_connected)
                    batteryInfoTextView.visibility = View.GONE
                }
                ConnectionState.Disconnected -> {
                    connectionInfoTextView.setTextColor(getColor(R.color.connection_state_disconnected))
                    connectionInfoTextView.text = getString(R.string.connection_state_disconnected)
                    connectionInfoImageView.setColorFilter(getColor(R.color.connection_state_disconnected))
                    connectionInfoImageView.setImageResource(R.drawable.status_disconnected)
                    batteryInfoTextView.visibility = View.GONE
                }
                ConnectionState.Stopped -> {
                    connectionInfoTextView.setTextColor(getColor(R.color.connection_state_stopped))
                    connectionInfoTextView.text = getString(R.string.connection_state_stopped)
                    connectionInfoImageView.setColorFilter(getColor(R.color.connection_state_stopped))
                    connectionInfoImageView.setImageResource(R.drawable.status_disconnected)
                    batteryInfoTextView.visibility = View.GONE
                }
            }
        }
    }
}