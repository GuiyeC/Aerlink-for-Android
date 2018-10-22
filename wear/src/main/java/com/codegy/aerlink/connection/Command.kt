package com.codegy.aerlink.connection

import java.util.*

data class Command(val serviceUUID: UUID, val characteristicUUID: UUID, val packet: ByteArray? = null) {

    var isWriteCommand: Boolean = packet != null
    private var retryCount = 0
//    var writeType: Int? = null
    var importance = IMPORTANCE_NORMAL
    var successBlock: Runnable? = null
    var failureBlock: Runnable? = null
    //private int writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;

    fun completeWithSuccess() {
        successBlock?.run()
    }

    fun completeWithFailure() {
        failureBlock?.run()
    }

    fun shouldRetryAgain(): Boolean {
        if (retryCount >= importance) {
            return false
        }

        if (importance <= IMPORTANCE_MAX) {
            retryCount++
        }

        return true
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is Command) {
            return false
        }

        return serviceUUID == other.serviceUUID &&
                characteristicUUID == other.characteristicUUID &&
                Arrays.equals(packet, other.packet)
    }

    override fun hashCode(): Int {
        return Objects.hash(serviceUUID, characteristicUUID, packet)
    }

    companion object {
        const val IMPORTANCE_MIN = 1
        const val IMPORTANCE_NORMAL = 3
        const val IMPORTANCE_MAX = 500
    }
}