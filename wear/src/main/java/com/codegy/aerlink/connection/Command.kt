package com.codegy.aerlink.connection

import java.util.*

data class Command(val serviceUUID: UUID, val characteristicUUID: UUID, val packet: ByteArray? = null, var importance: Int = IMPORTANCE_NORMAL) {

    var isWriteCommand: Boolean = packet != null
    private var retryCount = 0
    var successBlock: (() -> Unit)? = null
    var failureBlock: (() -> Unit)? = null

    fun completeWithSuccess() {
        successBlock?.invoke()
    }

    fun completeWithFailure() {
        failureBlock?.invoke()
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
