package com.codegy.aerlink

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.codegy.aerlink.connection.CharacteristicIdentifier
import com.codegy.aerlink.connection.Command
import com.codegy.aerlink.connection.ConnectionManager
import com.codegy.aerlink.connection.ConnectionState
import com.codegy.aerlink.connection.DiscoveryManager
import com.codegy.aerlink.service.ServiceContract
import com.codegy.aerlink.service.ServiceManager
import com.codegy.aerlink.service.notifications.ANCSContract
import com.codegy.aerlink.utils.ServiceUtils
import kotlin.reflect.KClass

class MainService : Service(), ServiceUtils, DiscoveryManager.Callback, ConnectionManager.Callback {

    val state: ConnectionState
        get() = connectionManager.state
    private val bluetoothManager: BluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }
    private val notificationManager: NotificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val discoveryManager: DiscoveryManager by lazy { DiscoveryManager(this, bluetoothManager) }
    private val connectionManager: ConnectionManager by lazy { ConnectionManager(this, this, bluetoothManager) }
    private val serviceManagers: MutableMap<KClass<out ServiceManager>, ServiceManager> = mutableMapOf()

    override fun onCreate() {
        super.onCreate()

        Log.i(LOG_TAG, "-=-=-=-=-=-=-=-=-=  Service created  =-=-=-=-=-=-=-=-=-")

        discoveryManager.startDiscovery()
    }

    override fun onDestroy() {
        super.onDestroy()

        notificationManager.cancelAll()

        discoveryManager.callback = null
        discoveryManager.stopDiscovery()

        connectionManager.close()

        Log.i(LOG_TAG, "xXxXxXxXxXxXxXxXxX Service destroyed XxXxXxXxXxXxXxXxXx")
    }

    override fun onBind(intent: Intent): IBinder = ServiceBinder()

    override fun onDeviceDiscovery(device: BluetoothDevice) {
        Log.v(LOG_TAG, "Device discovered: $device")

        discoveryManager.stopDiscovery()
        connectionManager.connectToDevice(device)
    }

    override fun onConnectionStateChange(state: ConnectionState) {
        when (state) {
//            ConnectionState.Stopped -> TODO()
//            ConnectionState.Disconnected -> TODO()
//            ConnectionState.Bonding -> TODO()
//            ConnectionState.Connecting -> TODO()
            ConnectionState.Ready -> {
                val initializingCommands = mutableListOf<Command>()
                for (serviceManager in serviceManagers.values) {
                    val commands = serviceManager.initialize()
                    if (commands != null) {
                        initializingCommands.addAll(commands)
                    }
                }

                for (command in initializingCommands) {
                    connectionManager.handleCommand(command)
                }
            }
        }
    }

    override fun onReadyToSubscribe(): List<CharacteristicIdentifier> {
        val serviceContracts = listOf<ServiceContract>(
//                ALSContract,
//                BASContract,
                ANCSContract
        )

        val characteristicsToSubscribe = mutableListOf<CharacteristicIdentifier>()
        serviceContracts.forEach {
            if (connectionManager.checkServiceAvailability(it.serviceUuid)) {
                characteristicsToSubscribe.addAll(it.characteristicsToSubscribe)

                val serviceManager = it.createManager(this, this)
                serviceManagers[serviceManager::class] = serviceManager
            }
        }

        return characteristicsToSubscribe
    }

    override fun onBondingRequired(device: BluetoothDevice) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onCharacteristicChanged(characteristic: BluetoothGattCharacteristic) {
        for (serviceManager in serviceManagers.values) {
            if (serviceManager.canHandleCharacteristic(characteristic)) {
                serviceManager.handleCharacteristic(characteristic)
                break
            }
        }
    }


    override fun addCommandToQueue(command: Command) {
        connectionManager.handleCommand(command)
    }

    override fun notify(tag: String?, id: Int, notification: Notification) {
        notificationManager.notify(tag, id, notification)
    }

    override fun cancelNotification(tag: String?, id: Int) {
        notificationManager.cancel(tag, id)
    }


    companion object {
        private val LOG_TAG = MainService::class.java.simpleName
    }

    inner class ServiceBinder : Binder() {
        val service: MainService
            get() = this@MainService
    }

}
