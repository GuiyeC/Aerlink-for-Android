package com.codegy.aerlink

import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.codegy.aerlink.connection.BondManager
import com.codegy.aerlink.connection.CharacteristicIdentifier
import com.codegy.aerlink.connection.Command
import com.codegy.aerlink.connection.ConnectionManager
import com.codegy.aerlink.connection.ConnectionState
import com.codegy.aerlink.connection.DiscoveryManager
import com.codegy.aerlink.extensions.NotificationChannelImportance
import com.codegy.aerlink.extensions.createChannelIfNeeded
import com.codegy.aerlink.service.ServiceManager
import com.codegy.aerlink.service.aerlink.cameraremote.ACRSContract
import com.codegy.aerlink.service.aerlink.reminders.ARSContract
import com.codegy.aerlink.service.battery.BASContract
import com.codegy.aerlink.service.media.AMSContract
import com.codegy.aerlink.service.notifications.ANCSContract
import com.codegy.aerlink.ui.MainActivity
import com.codegy.aerlink.utils.CommandHandler
import kotlin.reflect.KClass


class MainService : Service(), DiscoveryManager.Callback, BondManager.Callback, ConnectionManager.Callback, CommandHandler {
    interface Observer {
        fun onConnectionStateChanged(state: ConnectionState)
    }

    var state: ConnectionState = ConnectionState.Disconnected
        set(value) {
            if (field == ConnectionState.Stopped || field == value) return
            field = value
            updateNotification()
            observers.forEach { it.onConnectionStateChanged(field) }
        }
    private var observers: MutableList<Observer> = mutableListOf()
    private val bluetoothManager: BluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }
    private val notificationManager: NotificationManagerCompat by lazy { NotificationManagerCompat.from(this) }
    private var discoveryManager: DiscoveryManager? = null
    private var bondManager: BondManager? = null
    private var connectionManager: ConnectionManager? = null
    private val serviceManagers: MutableMap<KClass<out ServiceManager>, ServiceManager> = mutableMapOf()

    override fun onCreate() {
        super.onCreate()

        Log.i(LOG_TAG, "-=-=-=-=-=-=-=-=-=  Service created  =-=-=-=-=-=-=-=-=-")

        val channelDescription = getString(R.string.service_notification_channel)
        val importance = NotificationChannelImportance.Min
        notificationManager.createChannelIfNeeded(NOTIFICATION_CHANNEL_ID, channelDescription, importance)

        updateNotification()
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

    fun getServiceManager(managerClass: KClass<out ServiceManager>): ServiceManager? {
        return serviceManagers[managerClass]
    }

    fun addObserver(observer: Observer) {
        if (!observers.contains(observer)) {
            observers.add(observer)
        }
        observer.onConnectionStateChanged(state)
    }

    fun removeObserver(observer: Observer) {
        observers.remove(observer)
    }

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
        state = ConnectionState.Disconnected

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
        Log.d(LOG_TAG, "onReady To Subscribe")
        val characteristicsToSubscribe = mutableListOf<CharacteristicIdentifier>()
        serviceContracts.forEach {
            if (connectionManager.checkServiceAvailability(it.serviceUuid)) {
                val serviceManager = it.createManager(this, this)
                Log.v(LOG_TAG, "Manager created: ${serviceManager::class}")
                serviceManagers[serviceManager::class] = serviceManager
                characteristicsToSubscribe.addAll(it.characteristicsToSubscribe)
            } else {
                Log.v(LOG_TAG, "Service not available: ${it.serviceUuid}")
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

    private fun updateNotification() {
        if (state == ConnectionState.Ready &&
                android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            notificationManager.cancel(NOTIFICATION_ID)
            return
        }
        val title = when (state) {
            ConnectionState.Stopped, ConnectionState.Disconnected -> R.string.connection_state_disconnected
            ConnectionState.Bonding -> R.string.connection_state_bonding
            ConnectionState.Connecting -> R.string.connection_state_connecting
            ConnectionState.Ready -> R.string.connection_state_ready
        }
        val text = if (state == ConnectionState.Ready) R.string.connection_state_ready_help
                   else  R.string.connection_state_disconnected_help
        val aerlinkIntent = Intent(this, MainActivity::class.java)
        aerlinkIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(this, 0, aerlinkIntent, 0)
        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.nic_aerlink)
                .setOngoing(true)
                .setColor(getColor(R.color.aerlink_blue))
                .setContentTitle(getString(title))
                .setContentText(getString(text))
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, notificationBuilder.build())
        } else {
            val background = BitmapFactory.decodeResource(resources, R.drawable.bg_aerlink)
            val wearableExtender = NotificationCompat.WearableExtender().setBackground(background)
            notificationBuilder.extend(wearableExtender)
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
        }
    }

    companion object {
        private const val NOTIFICATION_ID: Int = 1000
        private const val NOTIFICATION_CHANNEL_ID: String = "com.codegy.aerlink.service"
        private val serviceContracts = listOf(
                BASContract,
                AMSContract,
                ACRSContract,
                ARSContract,
                ANCSContract
        )
        private val LOG_TAG = MainService::class.java.simpleName
    }

    inner class ServiceBinder : Binder() {
        val service: MainService
            get() = this@MainService
    }
}
