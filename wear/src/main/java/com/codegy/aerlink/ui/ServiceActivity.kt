package com.codegy.aerlink.ui

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.support.wearable.activity.WearableActivity
import android.util.Log
import androidx.core.app.ActivityCompat
import com.codegy.aerlink.MainService
import com.codegy.aerlink.connection.ConnectionState
import com.codegy.aerlink.service.ServiceManager
import kotlin.reflect.KClass

abstract class ServiceActivity : WearableActivity(), MainService.Observer {
    protected var service: MainService? = null
        set(value) {
            field?.removeObserver(this)
            field = value
            value?.addObserver(this)
        }
    val isConnected: Boolean
        get() = service?.state == ConnectionState.Ready
    val isServiceBound: Boolean
        get() = service != null
    val isServiceRunning: Boolean
        get() {
            val manager = getSystemService(ActivityManager::class.java) ?: return false
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (MainService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            Log.i(LOG_TAG, "Service disconnected")
            service = null
            onConnectionStateChanged(ConnectionState.Disconnected)
        }

        override fun onServiceConnected(name: ComponentName, serviceBinder: IBinder) {
            Log.i(LOG_TAG, "Service connected")
            val binder = serviceBinder as MainService.ServiceBinder
            service = binder.service
        }
    }

    override fun onResume() {
        super.onResume()

        when {
            isServiceRunning -> bindService()
            isServiceBound -> stopService()
            else -> updateInterfaceForState(ConnectionState.Stopped)
        }
    }

    override fun onPause() {
        super.onPause()
        unbindService()
    }

    fun <T : ServiceManager>getServiceManager(managerClass: KClass<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return service?.getServiceManager(managerClass) as? T
    }

    fun startService() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                    PERMISSIONS_REQUEST_CODE
            )
            return
        }

        startService(Intent(this, MainService::class.java))
        bindService()
    }

    fun stopService() {
        unbindService()
        stopService(Intent(this, MainService::class.java))
        onConnectionStateChanged(ConnectionState.Stopped)
    }

    private fun bindService() {
        if (isServiceBound) return
        val intent = Intent(this, MainService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindService() {
        if (!isServiceBound) return
        try {
            unbindService(serviceConnection)
        } catch (ignored: Exception) {}
        service = null
    }

    override fun onConnectionStateChanged(state: ConnectionState) {
        if (state == ConnectionState.Ready) {
            onConnectedToDevice()
        }
        updateInterfaceForState(state)
    }

    abstract fun onConnectedToDevice()

    abstract fun updateInterfaceForState(state: ConnectionState)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            val success = grantResults.size == permissions.size
                    && grantResults.any { it != PackageManager.PERMISSION_GRANTED }
            if (success) {
                startService()
            } else {
                stopService()
            }
        }
    }

    companion object {
        private val LOG_TAG: String = ServiceActivity::class.java.simpleName
        private const val PERMISSIONS_REQUEST_CODE: Int = 1000
    }
}