package com.codegy.aerlink.connection

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Looper
import android.util.Log
import com.codegy.aerlink.extensions.getCharacteristic
import com.codegy.aerlink.extensions.stringFromConnectionError
import com.codegy.aerlink.extensions.stringFromStatus
import com.codegy.aerlink.extensions.subscribeToCharacteristic
import com.codegy.aerlink.extensions.unpair
import com.codegy.aerlink.utils.ScheduledTask
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class ConnectionManager(val device: BluetoothDevice, private val context: Context, var callback: Callback?, bluetoothManager: BluetoothManager) {
    interface Callback {
        fun onReadyToSubscribe(connectionManager: ConnectionManager): List<CharacteristicIdentifier>
        fun onConnectionReady(connectionManager: ConnectionManager)
        fun onDisconnected(connectionManager: ConnectionManager)
        fun onConnectionError(connectionManager: ConnectionManager)
        fun onBondingRequired(connectionManager: ConnectionManager, device: BluetoothDevice)
        fun onCharacteristicChanged(connectionManager: ConnectionManager, characteristic: BluetoothGattCharacteristic)
    }

    private var atomicRunning = AtomicBoolean(true)
    var isRunning: Boolean
        get() = atomicRunning.get()
        private set(value) = atomicRunning.set(value)

    private val lock = Object()
    private val timeoutController: ScheduledTask = ScheduledTask(Looper.getMainLooper())
    private val characteristicsToSubscribe: Queue<CharacteristicIdentifier> = ConcurrentLinkedQueue()
    private var currentCommand: Command? = null
    private val commands: Queue<Command> = ConcurrentLinkedQueue()

    private val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    private val bluetoothGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            timeoutController.cancel()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(LOG_TAG, "Connection state changed :: State: $newState")
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.i(LOG_TAG, "Connected")
                        timeoutController.schedule(OPERATION_DELAY) {
                            if (!isRunning) { return@schedule }
                            requestMtu(gatt)
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.i(LOG_TAG, "Disconnected")
                        gatt.close()
                        bluetoothGatt = null

                        callback?.onDisconnected(this@ConnectionManager)
                    }
                }
            } else {
                Log.e(LOG_TAG, "ERROR: Connection state changed :: Status: ${stringFromConnectionError(status)}")
                onConnectionError()
            }
        }

        fun requestMtu(gatt: BluetoothGatt, numberOfTries: Int = 0) {
            val success = gatt.requestMtu(DESIRED_MTU)
            if (!success) {
                if (numberOfTries < 3) {
                    Log.w(LOG_TAG, "Error requesting MTU change: $numberOfTries")
                    requestMtu(gatt, numberOfTries + 1)
                } else {
                    Log.e(LOG_TAG, "Too many errors requesting MTU change, will try to discover services")
                    discoverServices(gatt)
                }
                return
            }
            Log.d(LOG_TAG, "Success requesting MTU change: $DESIRED_MTU")

            timeoutController.schedule(MTU_CHANGE_TIMEOUT) {
                if (!isRunning) { return@schedule }
                discoverServices(gatt)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(LOG_TAG, "MTU changed :: MTU: $mtu")
            } else {
                Log.e(LOG_TAG, "ERROR: MTU changed :: Status: ${stringFromStatus(status)}")
            }

            timeoutController.cancel()
            // Continue with connection even if the MTU change failed
            timeoutController.schedule(OPERATION_DELAY) {
                if (!isRunning) { return@schedule }
                discoverServices(gatt)
            }
        }

        fun discoverServices(gatt: BluetoothGatt, numberOfTries: Int = 0) {
            val success = gatt.discoverServices()
            if (!success) {
                if (numberOfTries < 3) {
                    Log.w(LOG_TAG, "Error requesting service discovery: $numberOfTries")
                    discoverServices(gatt, numberOfTries + 1)
                } else {
                    Log.e(LOG_TAG, "Too many errors requesting service discovery, giving up on connection")
                    onConnectionError()
                }
                return
            }
            Log.d(LOG_TAG, "Success requesting service discovery: $DESIRED_MTU")

            timeoutController.schedule(SERVICE_DISCOVERY_TIMEOUT) {
                if (!isRunning) { return@schedule }
                onConnectionTimedOut()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)

            timeoutController.cancel()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(LOG_TAG, "Services Discovered: ${gatt.services}")
                val characteristics = callback?.onReadyToSubscribe(this@ConnectionManager)
                if (characteristics?.isNotEmpty() == true) {
                    characteristicsToSubscribe.addAll(characteristics)
                    timeoutController.schedule(OPERATION_DELAY) {
                        if (!isRunning) { return@schedule }
                        checkCharacteristicsToSubscribe()
                    }
                } else {
                    Log.wtf(LOG_TAG, "No services available, giving up on connection")
                    onConnectionError()
                }
            } else {
                Log.e(LOG_TAG, "ERROR: Services Discovered :: Status: ${stringFromStatus(status)}")
                onConnectionError()
            }
        }

        fun checkCharacteristicsToSubscribe() {
            val characteristic = characteristicsToSubscribe.poll() ?: run {
                connectionsFailed = 0
                callback?.onConnectionReady(this@ConnectionManager)
                return
            }
            subscribeToCharacteristic(characteristic)
        }

        fun subscribeToCharacteristic(characteristicIdentifier: CharacteristicIdentifier, numberOfTries: Int = 0) {
            val success = bluetoothGatt?.subscribeToCharacteristic(characteristicIdentifier) == true
            if (!success) {
                if (numberOfTries < 3) {
                    subscribeToCharacteristic(characteristicIdentifier, numberOfTries + 1)
                } else {
                    onConnectionError()
                }
                return
            }

            timeoutController.schedule(SERVICE_SUBSCRIPTION_TIMEOUT) {
                if (!isRunning) { return@schedule }
                onConnectionTimedOut()
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)

            timeoutController.cancel()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(LOG_TAG, "Descriptor write: ${descriptor.characteristic.uuid}")

                timeoutController.schedule(OPERATION_DELAY) {
                    if (!isRunning) { return@schedule }
                    checkCharacteristicsToSubscribe()
                }
            } else {
                Log.e(LOG_TAG, "ERROR: Descriptor write :: Status: ${stringFromStatus(status)}")
                Log.d(LOG_TAG, "Descriptor: ${descriptor.characteristic.uuid}")
                onConnectionError()
            }
        }

        fun checkCommands() {
            if (currentCommand != null) {
                return
            }
            commands.poll()?.let {
                currentCommand = it
                handleCommand(it)
            }
        }

        fun handleCommand(command: Command, numberOfTries: Int = 0) {
            val characteristic = bluetoothGatt?.getCharacteristic(command.serviceUUID, command.characteristicUUID) ?: return

            val success: Boolean
            if (command.isWriteCommand) {
                characteristic.value = command.packet
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                success = bluetoothGatt?.writeCharacteristic(characteristic) == true
                Log.d(LOG_TAG, "Started writing command: $success")
            } else {
                success = bluetoothGatt?.readCharacteristic(characteristic) == true
                Log.d(LOG_TAG, "Started reading command: $success")
            }

            if (!success) {
                if (numberOfTries < 3) {
                    handleCommand(command, numberOfTries + 1)
                } else {
                    onConnectionError()
                }
                return
            }

            timeoutController.schedule(COMMAND_TIMEOUT) {
                if (!isRunning) { return@schedule }
                onConnectionError()
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)

            timeoutController.cancel()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(LOG_TAG, "Characteristic write: ${characteristic.uuid}")

                currentCommand?.completeWithSuccess()
            } else {
                Log.e(LOG_TAG, "ERROR: Characteristic write :: Status: ${stringFromStatus(status)}")
                Log.d(LOG_TAG, "Characteristic: ${characteristic.uuid}")

                currentCommand?.let {
                    it.completeWithFailure()
                    if (it.shouldRetryAgain()) {
                        commands.add(it)
                    }
                }
            }
            Log.d(LOG_TAG, "Characteristic value: ${characteristic.value}")

            currentCommand = null
            timeoutController.schedule(OPERATION_DELAY) {
                if (!isRunning) { return@schedule }
                checkCommands()
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            super.onCharacteristicRead(gatt, characteristic, status)

            timeoutController.cancel()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(LOG_TAG, "Characteristic read: ${characteristic.uuid}")
                callback?.onCharacteristicChanged(this@ConnectionManager, characteristic)

                currentCommand?.completeWithSuccess()
            } else {
                Log.e(LOG_TAG, "ERROR: Characteristic read :: Status: ${stringFromStatus(status)}")
                Log.d(LOG_TAG, "Characteristic: ${characteristic.uuid}")

                currentCommand?.let {
                    it.completeWithFailure()
                    if (it.shouldRetryAgain()) {
                        commands.add(it)
                    }
                }
            }
            Log.d(LOG_TAG, "Characteristic value: ${characteristic.value}")

            currentCommand = null
            timeoutController.schedule(OPERATION_DELAY) {
                if (!isRunning) { return@schedule }
                checkCommands()
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(gatt, characteristic)
            Log.d(LOG_TAG, "Characteristic changed: ${characteristic.uuid}")

            callback?.onCharacteristicChanged(this@ConnectionManager, characteristic)
        }
    }

    fun connect(autoConnect: Boolean = false) {
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            Log.w(LOG_TAG, "Trying to connect to an unbonded device")
            callback?.onBondingRequired(this, device)
            return
        }

        synchronized(lock) {
            characteristicsToSubscribe.clear()
            bluetoothGatt?.close()

            Log.i(LOG_TAG, "Connecting...: " + device.name)
            bluetoothGatt = device.connectGatt(context, autoConnect, bluetoothGattCallback)
            if (!autoConnect) {
                timeoutController.schedule(CONNECTION_TIMEOUT) {
                    if (!isRunning) { return@schedule }
                    onConnectionTimedOut()
                }
            }
        }
    }

    fun disconnect() {
        synchronized(lock) {
            callback?.onDisconnected(this)

            try {
                bluetoothGatt?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                bluetoothGatt = null
            }
        }
    }

    fun checkServiceAvailability(serviceUuid: UUID): Boolean {
        return bluetoothGatt?.getService(serviceUuid) != null
    }

    fun handleCommand(command: Command) {
        if (!isRunning || bluetoothGatt == null) {
            return
        }

        commands.add(command)
        bluetoothGattCallback.checkCommands()
    }

    private fun onConnectionError(unpairDevice: Boolean = false) {
        bluetoothGatt?.close()
        Log.w(LOG_TAG, "Closed BluetoothGatt")
        bluetoothGatt = null

        if (unpairDevice) {
            device.unpair()
            connectionsFailed = 0
        }

        bluetoothAdapter.disable()

        callback?.onConnectionError(this@ConnectionManager)
    }

    private fun onConnectionTimedOut() {
        Log.w(LOG_TAG, "Connecting timed out")
        connectionsFailed++
        if (connectionsFailed > 0 && connectionsFailed % 3 == 0) {
            onConnectionError(connectionsFailed % 9 == 0)
        } else {
            disconnect()
        }
    }

    fun close() {
        // Check if already closed
        if (!isRunning) {
            return
        }

        isRunning = false
        callback = null
        disconnect()
        Log.i(LOG_TAG, "ConnectionManager Closed")
    }

    companion object {
        private var connectionsFailed = 0
        private val LOG_TAG = ConnectionManager::class.java.simpleName
        private const val DESIRED_MTU: Int = 512
        private const val OPERATION_DELAY: Long = 180
        private const val CONNECTION_TIMEOUT: Long = 5000
        private const val MTU_CHANGE_TIMEOUT: Long = 1000
        private const val SERVICE_DISCOVERY_TIMEOUT: Long = 2000
        private const val SERVICE_SUBSCRIPTION_TIMEOUT: Long = 3000
        private const val COMMAND_TIMEOUT: Long = 2000
    }
}
