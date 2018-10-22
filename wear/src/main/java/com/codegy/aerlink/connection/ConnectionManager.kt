package com.codegy.aerlink.connection

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.codegy.aerlink.extensions.getCharacteristic
import com.codegy.aerlink.extensions.resetBondedDevices
import com.codegy.aerlink.extensions.subscribeToCharacteristic
import com.codegy.aerlink.extensions.unpair
import com.codegy.aerlink.utils.ScheduledTask
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class ConnectionManager(private val context: Context, var callback: Callback?, bluetoothManager: BluetoothManager) {

    interface Callback {
        fun onConnectionStateChange(state: ConnectionState)
        fun onBondingRequired(device: BluetoothDevice)
        fun onReadyToSubscribe(): List<CharacteristicIdentifier>
        fun onCharacteristicChanged(characteristic: BluetoothGattCharacteristic)
    }

    var state: ConnectionState = ConnectionState.Disconnected
        private set(value) {
            if (field == value) {
                return
            }
            field = value
            callback?.onConnectionStateChange(value)
        }
    private var atomicRunning = AtomicBoolean(true)
    var isRunning: Boolean
        get() = atomicRunning.get()
        set(value) = atomicRunning.set(value)

    private var bondsFailed = 0
    private var connectionsFailed = 0
    private val lock = Object()
    private val timeoutController: ScheduledTask = ScheduledTask(Looper.getMainLooper())
    private val characteristicsToSubscribe: Queue<CharacteristicIdentifier> = ConcurrentLinkedQueue()

    private val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter
    private val device: BluetoothDevice?
        get() = bluetoothGatt?.device
    private var bluetoothGatt: BluetoothGatt? = null
    private val bluetoothGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Log.d(LOG_TAG, "onConnectionStateChange: $status -> $newState")

            val bondState = device?.bondState ?: 10
            Log.d(LOG_TAG, "onConnectionStateChange BOND STATE: $bondState")
            if (bondState == BluetoothDevice.BOND_NONE) {
                device?.createBond()
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        gatt.requestMtu(DESIRED_MTU)
                        timeoutController.schedule(MTU_CHANGE_TIMEOUT) {
                            bluetoothGatt?.discoverServices()
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        state = ConnectionState.Disconnected

                        Log.e(LOG_TAG, "Disconnected")
                        gatt.close()
                        Log.w(LOG_TAG, "Closed")
                        bluetoothGatt = null

                        tryToReconnect()
                    }
                }
            } else {
                state = ConnectionState.Disconnected

                Log.wtf(LOG_TAG, "ON CONNECTION STATE CHANGED ERROR: $status")
                gatt.close()
                Log.w(LOG_TAG, "Closed BluetoothGatt")
                bluetoothGatt = null

                when (status) {
                    0x01 -> { /* GATT CONN L2C FAILURE */ }
                    0x08 -> { /* GATT CONN TIMEOUT */ } // just reconnect when possible
                    0x13 -> { /* GATT CONN TERMINATE PEER USER */
                        gatt.device?.unpair() } // iPhone unbonded the watch?
                    0x16 -> { /* GATT CONN TERMINATE LOCAL HOST */ }
                    0x3E -> { /* GATT CONN FAIL ESTABLISH */ }
                    0x22 -> { /* GATT CONN LMP TIMEOUT */ }
                    0x0100 -> { /* GATT CONN CANCEL */ }
                    0x0085 -> { /* GATT ERROR */
                        gatt.device?.unpair()
                    }
                }

                tryToReconnect()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            Log.d(LOG_TAG, "onMtuChanged: $mtu status: $status")

            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt.discoverServices()

                timeoutController.schedule(SERVICE_DISCOVERY_TIMEOUT) {
                    onSubscribingTimedOut()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            Log.d(LOG_TAG, "onServicesDiscovered: $status")

            if (status == BluetoothGatt.GATT_SUCCESS) {
                timeoutController.cancel()
                val characteristics = callback?.onReadyToSubscribe()
                if (characteristics != null) {
                    characteristicsToSubscribe.addAll(characteristics)
                }

                subscribeCharacteristic(CharacteristicIdentifier(UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb"), UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")))
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(LOG_TAG, "Descriptor write successful:  ${descriptor.characteristic.uuid}")

                // TODO callback: success on subscribing
                checkCharacteristicsToSubscribe()
            } else {
                val device2 = gatt.device

                // Don't do anything while bonding
                if (device2 == null || device2.bondState != BluetoothDevice.BOND_BONDING) {
                    Log.e(LOG_TAG, "Status: write not permitted")

                    device2.unpair()
                    tryToReconnect()
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(LOG_TAG, "Characteristic write successful: " + characteristic.uuid.toString())

                // TODO callback: success on writing
            } else {
                Log.w(LOG_TAG, "Characteristic write error: " + status + " :: " + characteristic.uuid.toString())

                // TODO callback: error on writing
            }

            Log.d(LOG_TAG, "Characteristic value: " + String(characteristic.value))
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            super.onCharacteristicRead(gatt, characteristic, status)
            Log.d(LOG_TAG, "onCharacteristicRead status:: $status")

            if (status == BluetoothGatt.GATT_SUCCESS) {
                callback?.onCharacteristicChanged(characteristic)
            }

            // TODO callback: success on reading, ignore error?
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(gatt, characteristic)
            Log.d(LOG_TAG, "onCharacteristicChanged: " + characteristic.uuid.toString())

            callback?.onCharacteristicChanged(characteristic)
        }

    }

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null || intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                return
            }

            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
            val prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)

            Log.d(LOG_TAG, "Bond state changed: state = $state prevState = $prevState")
            if (state == BluetoothDevice.BOND_BONDED && prevState == BluetoothDevice.BOND_BONDING) {
                Log.i(LOG_TAG, "Bond successful")

                device?.let {
                    connectToDevice(it)
                }
            } else if (state == BluetoothDevice.BOND_NONE && (prevState == BluetoothDevice.BOND_BONDING || prevState == BluetoothDevice.BOND_BONDED)) {
                // TODO: Bond was rejected
                Log.e(LOG_TAG, "Bond failed")

                callback?.onConnectionStateChange(ConnectionState.Disconnected)
            }
        }
    }

    init {
        context.registerReceiver(bondReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
    }

    fun close() {
        // Check if already closed
        if (!isRunning) {
            return
        }

        Log.i(LOG_TAG, "ConnectionManager Closed")
        isRunning = false
        context.unregisterReceiver(bondReceiver)
        disconnectDevice()
    }

    fun connectToDevice(device: BluetoothDevice) {
        if (bluetoothGatt != null) {
            return
        }

        Handler(Looper.getMainLooper()).post {
            synchronized(lock) {
                val bondState = device.bondState
                Log.d(LOG_TAG, "connectToDevice BOND STATE: $bondState")
                if (bondState == BluetoothDevice.BOND_NONE) {
                    device.createBond()
                    return@post
                }

                bluetoothGatt = device.connectGatt(context, false, bluetoothGattCallback)
                timeoutController.schedule(CONNECTION_TIMEOUT) {
                    onConnectionTimedOut()
                }

                Log.i(LOG_TAG, "Connecting...: " + device.name)
                callback?.onConnectionStateChange(ConnectionState.Connecting)
            }
        }
    }

    fun disconnectDevice() {
        synchronized(lock) {
            try {
                bluetoothGatt?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                bluetoothGatt = null
            }
        }
    }

    @Synchronized
    fun tryToReconnect() {
        if (!isRunning) {
            return
        }

        val currentDevice = device
        disconnectDevice()

        if (currentDevice == null) {
            callback?.onConnectionStateChange(ConnectionState.Disconnected)
            return
        }

        Log.w(LOG_TAG, "Reconnecting")
        connectToDevice(currentDevice)
    }

    fun checkServiceAvailability(serviceUuid: UUID): Boolean {
        return bluetoothGatt?.getService(serviceUuid) != null
    }

    fun handleCommand(command: Command) {
        if (!isRunning || bluetoothGatt == null) {
            return
        }

        Handler(Looper.getMainLooper()).post(Runnable {
            try {
                val characteristic = bluetoothGatt?.getCharacteristic(command.serviceUUID, command.characteristicUUID)
                        ?: return@Runnable

                if (command.isWriteCommand) {
                    // not being used
                    // mBluetoothGattCharacteristic.setWriteType(command.getWriteType());

                    characteristic.value = command.packet
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    val result = bluetoothGatt?.writeCharacteristic(characteristic)
                    Log.d(LOG_TAG, "Started writing command: $result")
                } else {
                    val result = bluetoothGatt?.readCharacteristic(characteristic)
                    Log.d(LOG_TAG, "Started reading command: $result")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        })
    }

    private fun checkCharacteristicsToSubscribe() {
        if (characteristicsToSubscribe.size == 0) {
            state = ConnectionState.Ready
            return
        }

        val characteristic = characteristicsToSubscribe.poll()
        subscribeCharacteristic(characteristic)
    }

    fun subscribeCharacteristic(characteristicIdentifier: CharacteristicIdentifier) {
        if (bluetoothGatt == null) {
            return
        }

        val handler = Handler(Looper.getMainLooper())
        handler.post {
            val success = bluetoothGatt?.subscribeToCharacteristic(characteristicIdentifier)
        }
    }

    fun onConnectionReady() {
        Log.i(LOG_TAG, "Ready")

        callback?.onConnectionStateChange(ConnectionState.Ready)

        bondsFailed = 0
        connectionsFailed = 0
    }

    fun onConnectionTimedOut() {
        Log.w(LOG_TAG, "Connecting timed out")
        if (!isRunning) {
            return
        }

        if (device != null) {
            val bondState = device!!.bondState

            // Don't do anything while bonding
            if (bondState == BluetoothDevice.BOND_BONDING && bondsFailed < 30) {
                Log.w(LOG_TAG, "Waiting for bond...")
                bondsFailed++

                timeoutController.schedule(CONNECTION_TIMEOUT) {
                    onConnectionTimedOut()
                }
            } else {
                bondsFailed = 0
                connectionsFailed++

                if (connectionsFailed > 0 && connectionsFailed % 3 == 0) {
                    bluetoothAdapter.disable()
                }

                tryToReconnect()
            }
        } else {
            Log.w(LOG_TAG, "Start scanning again")

            tryToReconnect()
        }
    }

    fun onSubscribingTimedOut() {
        Log.w(LOG_TAG, "Subscribing timed out")
        if (callback == null) {
            return
        }

        if (device != null) {
            val bondState = device!!.bondState

            // Don't do anything while bonding
            if (bondState == BluetoothDevice.BOND_BONDING && bondsFailed < 30) {
                Log.w(LOG_TAG, "Waiting for bond...")
                bondsFailed++

                timeoutController.schedule(BONDING_TIMEOUT) {
                    onSubscribingTimedOut()
                }
            } else {
                bondsFailed = 0
                connectionsFailed++

                if (connectionsFailed >= 7) {
                    connectionsFailed = 0

                    // Last resort to prepare for a new connection
                    bluetoothAdapter.resetBondedDevices()
                }

                if (connectionsFailed > 0 && connectionsFailed % 3 == 0) {
                    bluetoothAdapter.disable()
                }

                tryToReconnect()
            }
        } else {
            Log.w(LOG_TAG, "Start scanning again")

            tryToReconnect()
        }
    }

    companion object {
        private val LOG_TAG = ConnectionManager::class.java.simpleName
        private const val DESIRED_MTU: Int = 512
        private const val CONNECTION_TIMEOUT: Long = 5000
        private const val MTU_CHANGE_TIMEOUT: Long = 1000
        private const val SERVICE_DISCOVERY_TIMEOUT: Long = 2000
        private const val BONDING_TIMEOUT: Long = 5000
        private const val SERVICE_SUBSCRIPTION_TIMEOUT: Long = 3000
    }

}
