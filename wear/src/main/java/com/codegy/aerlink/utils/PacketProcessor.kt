package com.codegy.aerlink.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.charset.StandardCharsets

class PacketProcessor(packet: ByteArray) {
    private val processingData: ByteArrayOutputStream = ByteArrayOutputStream()
    var action: Int = 0
        private set
    var status: Int = 0
        private set
    private var bytesLeftToProcess: Int = 0
    val isFinished: Boolean
        get() = bytesLeftToProcess <= 0
    val value: ByteArray
        get() = processingData.toByteArray()
    val stringValue: String?
        get() {
            return try {
                String(processingData.toByteArray(), StandardCharsets.UTF_8)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    val bitmapValue: Bitmap?
        get() {
            return try {
                BitmapFactory.decodeByteArray(processingData.toByteArray(), 0, processingData.toByteArray().size)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    init {
        if (packet.size >= 2) {
            action = packet[0].toInt()
            status = packet[1].toInt()

            if (status != 0x00 && packet.size > 4) {
                val byteLength = byteArrayOf(packet[2], packet[3])
                val length = BigInteger(byteLength)
                bytesLeftToProcess = length.toInt()

                Log.d(LOG_TAG, "DATA length $length")
                process(packet, 4)
            }
        }
    }

    fun process(packet: ByteArray, offset: Int = 0) {
        processingData.write(packet, offset, packet.size - offset)
        bytesLeftToProcess -= packet.size - offset
    }

    companion object {
        private val LOG_TAG = PacketProcessor::class.java.simpleName
    }
}
