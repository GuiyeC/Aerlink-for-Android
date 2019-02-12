package com.codegy.aerlink

import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.codegy.aerlink.connection.BondManager
import com.codegy.aerlink.connection.CharacteristicIdentifier
import com.codegy.aerlink.connection.Command
import com.codegy.aerlink.connection.ConnectionManager
import com.codegy.aerlink.connection.ConnectionState
import com.codegy.aerlink.connection.DiscoveryManager
import com.codegy.aerlink.service.ServiceContract
import com.codegy.aerlink.service.ServiceManager
import com.codegy.aerlink.service.notifications.ANCSContract
import com.codegy.aerlink.utils.CommandHandler
import kotlin.reflect.KClass


class MainService : Service(), CommandHandler, DiscoveryManager.Callback, BondManager.Callback, ConnectionManager.Callback {

    var state: ConnectionState = ConnectionState.Disconnected
        set(value) {
            if (field == ConnectionState.Stopped || field == value) return
            field = value
        }
    private val bluetoothManager: BluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }
    private val notificationManager: NotificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private var discoveryManager: DiscoveryManager? = null
    private var bondManager: BondManager? = null
    private var connectionManager: ConnectionManager? = null
    private val serviceManagers: MutableMap<KClass<out ServiceManager>, ServiceManager> = mutableMapOf()

    override fun onCreate() {
        super.onCreate()

        Log.i(LOG_TAG, "-=-=-=-=-=-=-=-=-=  Service created  =-=-=-=-=-=-=-=-=-")

        startDiscovery()
    }

    override fun onDestroy() {
        super.onDestroy()

        state = ConnectionState.Stopped

        notificationManager.cancelAll()

        serviceManagers.values.forEach { it.close() }
        serviceManagers.clear()

        discoveryManager?.close()
        bondManager?.close()
        connectionManager?.close()

        Log.i(LOG_TAG, "xXxXxXxXxXxXxXxXxX Service destroyed XxXxXxXxXxXxXxXxXx")
    }

    override fun onBind(intent: Intent): IBinder = ServiceBinder()

    private fun startDiscovery() {
        Log.d(LOG_TAG, "startDiscovery")
        val discoveryManager = DiscoveryManager(this, bluetoothManager)
        discoveryManager.startDiscovery()
        this.discoveryManager = discoveryManager
    }

    override fun onDeviceDiscovery(discoveryManager: DiscoveryManager, device: BluetoothDevice) {
        Log.d(LOG_TAG, "onDeviceDiscovery :: Device: $device")

        discoveryManager.stopDiscovery()
        this.discoveryManager = null

        connectToDevice(device)
    }

    private fun bondToDevice(device: BluetoothDevice) {
        Log.d(LOG_TAG, "bondToDevice :: Device: $device")
        state = ConnectionState.Bonding

        val bondManager = BondManager(device, this, this)
        bondManager.createBond()
        this.bondManager = bondManager
    }

    override fun onBondSuccessful(bondManager: BondManager, device: BluetoothDevice) {
        Log.d(LOG_TAG, "onBondSuccessful :: Device: $device")
        bondManager.close()
        this.bondManager = null

        connectToDevice(device)
    }

    override fun onBondFailed(bondManager: BondManager, device: BluetoothDevice) {
        Log.w(LOG_TAG, "onBondFailed :: Device: $device")
        bondManager.close()
        this.bondManager = null

        startDiscovery()
    }

    private fun connectToDevice(device: BluetoothDevice) {
        Log.d(LOG_TAG, "connectToDevice :: Device: $device")
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            bondToDevice(device)
        } else {
            state = ConnectionState.Connecting

            val connectionManager = ConnectionManager(device, this, this, bluetoothManager)
            connectionManager.connect()
            this.connectionManager = connectionManager
        }
    }

    override fun onReadyToSubscribe(connectionManager: ConnectionManager): List<CharacteristicIdentifier> {
        Log.d(LOG_TAG, "oReady To Subscribe")
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

    override fun onConnectionReady(connectionManager: ConnectionManager) {
        Log.d(LOG_TAG, "Connection Ready")
        state = ConnectionState.Ready

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

    override fun onDisconnected(connectionManager: ConnectionManager) {
        Log.w(LOG_TAG, "Disconnected")
        state = ConnectionState.Disconnected

//        notificationManager.cancelAll()
//        connectionManager.connect(true)

        connectionManager.close()
        this.connectionManager = null
        notificationManager.cancelAll()

        startDiscovery()
    }

    override fun onConnectionError(connectionManager: ConnectionManager) {
        Log.e(LOG_TAG, "Connection Error")
        state = ConnectionState.Disconnected

        serviceManagers.values.forEach { it.close() }
        serviceManagers.clear()
        connectionManager.close()
        this.connectionManager = null
        notificationManager.cancelAll()

        startDiscovery()
    }

    override fun onBondingRequired(connectionManager: ConnectionManager, device: BluetoothDevice) {
        Log.d(LOG_TAG, "onBondingRequired :: Device: $device")
        connectionManager.close()
        this.connectionManager = null
        notificationManager.cancelAll()

        bondToDevice(device)
    }

    override fun onCharacteristicChanged(connectionManager: ConnectionManager, characteristic: BluetoothGattCharacteristic) {
        Log.d(LOG_TAG, "onCharacteristicChanged :: Characteristic: $characteristic")
        val serviceManager = serviceManagers.values.firstOrNull { it.canHandleCharacteristic(characteristic) } ?: return
        serviceManager.handleCharacteristic(characteristic)
    }

    override fun handleCommand(command: Command) {
        Log.d(LOG_TAG, "handleCommand :: Command: $command")
        connectionManager?.handleCommand(command)
    }


    companion object {
        private val LOG_TAG = MainService::class.java.simpleName
    }

    inner class ServiceBinder : Binder() {
        val service: MainService
            get() = this@MainService
    }

}
