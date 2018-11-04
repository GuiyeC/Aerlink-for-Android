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
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.codegy.aerlink.extensions.getCharacteristic
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

    private var connectionsFailed = 0
    private val lock = Object()
    private val timeoutController: ScheduledTask = ScheduledTask(Looper.getMainLooper())
    private val characteristicsToSubscribe: Queue<CharacteristicIdentifier> = ConcurrentLinkedQueue()
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
                        requestMtu(gatt)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.i(LOG_TAG, "Disconnected")
                        gatt.close()
                        bluetoothGatt = null

                        callback?.onDisconnected(this@ConnectionManager)
                    }
                }
            } else {
                Log.e(LOG_TAG, "ERROR: Connection state changed :: Status: ${gatt.stringFromStatus(status)}")
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
                discoverServices(gatt)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(LOG_TAG, "MTU changed :: MTU: $mtu")
            } else {
                Log.e(LOG_TAG, "ERROR: MTU changed :: Status: ${gatt.stringFromStatus(status)}")
            }

            timeoutController.cancel()
            // Continue with connection even if the MTU change failed
            discoverServices(gatt)
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
                    checkCharacteristicsToSubscribe()
                } else {
                    Log.wtf(LOG_TAG, "No services available, giving up on connection")
                    onConnectionError()
                }
            } else {
                Log.e(LOG_TAG, "ERROR: Services Discovered :: Status: ${gatt.stringFromStatus(status)}")
                onConnectionError()
            }
        }

        fun checkCharacteristicsToSubscribe() {
            if (characteristicsToSubscribe.size == 0) {
                connectionsFailed = 0
                callback?.onConnectionReady(this@ConnectionManager)
                return
            }

            val characteristic = characteristicsToSubscribe.poll()
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
                onConnectionTimedOut()
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)

            timeoutController.cancel()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(LOG_TAG, "Descriptor write: ${descriptor.characteristic.uuid}")

                checkCharacteristicsToSubscribe()
            } else {
                Log.e(LOG_TAG, "ERROR: Descriptor write :: Status: ${gatt.stringFromStatus(status)}")
                Log.d(LOG_TAG, "Descriptor: ${descriptor.characteristic.uuid}")
                onConnectionError()
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(LOG_TAG, "Characteristic write: ${characteristic.uuid}")

                // TODO callback: success on writing
            } else {
                Log.e(LOG_TAG, "ERROR: Characteristic write :: Status: ${gatt.stringFromStatus(status)}")
                Log.d(LOG_TAG, "Characteristic: ${characteristic.uuid}")

                // TODO callback: error on writing
            }

            Log.d(LOG_TAG, "Characteristic value: " + String(characteristic.value))
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            super.onCharacteristicRead(gatt, characteristic, status)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(LOG_TAG, "Characteristic read: ${characteristic.uuid}")
                callback?.onCharacteristicChanged(this@ConnectionManager, characteristic)
            } else {
                Log.e(LOG_TAG, "ERROR: Characteristic read :: Status: ${gatt.stringFromStatus(status)}")
                Log.d(LOG_TAG, "Characteristic: ${characteristic.uuid}")
            }

            // TODO callback: success on reading, ignore error?
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(gatt, characteristic)
            Log.d(LOG_TAG, "Characteristic changed: ${characteristic.uuid}")

            callback?.onCharacteristicChanged(this@ConnectionManager, characteristic)
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

    fun connect(autoConnect: Boolean = false) {
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            Log.w(LOG_TAG, "Trying to connect to an unbonded device")
            callback?.onBondingRequired(this, device)
            return
        }

        Handler(Looper.getMainLooper()).post {
            synchronized(lock) {
                characteristicsToSubscribe.clear()
                bluetoothGatt?.close()

                Log.i(LOG_TAG, "Connecting...: " + device.name)
                bluetoothGatt = device.connectGatt(context, autoConnect, bluetoothGattCallback)
                if (!autoConnect) {
                    timeoutController.schedule(CONNECTION_TIMEOUT) {
                        onConnectionTimedOut()
                    }
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

    private fun onConnectionError(unpairDevice: Boolean = false) {
        bluetoothGatt?.close()
        Log.w(LOG_TAG, "Closed BluetoothGatt")
        bluetoothGatt = null

        if (unpairDevice) {
            device.unpair()
        }

        callback?.onConnectionError(this@ConnectionManager)
    }

    private fun onConnectionTimedOut() {
        Log.w(LOG_TAG, "Connecting timed out")
        if (!isRunning) {
            return
        }

        connectionsFailed++
        if (connectionsFailed > 0 && connectionsFailed % 3 == 0) {
            bluetoothAdapter.disable()
            onConnectionError()
        } else {
            disconnect()
        }
    }

    companion object {
        private val LOG_TAG = ConnectionManager::class.java.simpleName
        private const val DESIRED_MTU: Int = 512
        private const val CONNECTION_TIMEOUT: Long = 5000
        private const val MTU_CHANGE_TIMEOUT: Long = 1000
        private const val SERVICE_DISCOVERY_TIMEOUT: Long = 2000
        private const val SERVICE_SUBSCRIPTION_TIMEOUT: Long = 3000
    }

}
