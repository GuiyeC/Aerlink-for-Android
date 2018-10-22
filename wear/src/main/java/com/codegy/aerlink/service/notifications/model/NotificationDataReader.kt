package com.codegy.aerlink.service.notifications.model

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.*

class NotificationDataReader(val event: NotificationEvent, attributesToRead: List<NotificationAttribute>) {

    private var firstPacket = true
    private var attributesToRead: MutableList<NotificationAttribute> = attributesToRead.toMutableList()
    private var leftoverBytes: ByteArray? = null
    private var currentAttribute: NotificationAttribute? = null
    private var bytesLeftFromCurrentAttribute: Int? = null
    private var currentAttributeData: ByteArrayOutputStream = ByteArrayOutputStream()
    val attributes: MutableMap<NotificationAttribute, String> = mutableMapOf()
    val isFinished: Boolean
        get() = attributesToRead.isEmpty()

    fun readPacket(packet: ByteArray) {
        val offset = if (firstPacket) {
            firstPacket = false
            5
            // TODO: check uid
        } else {
            0
        }

        val bytesFromPreviousPacket = leftoverBytes
        val packetWithLeftover = if (bytesFromPreviousPacket != null) {
            bytesFromPreviousPacket + packet
        } else {
            packet
        }
        leftoverBytes = null

        val currentAttribute = this.currentAttribute
        val bytesLeftFromCurrentAttribute = this.bytesLeftFromCurrentAttribute
        if (currentAttribute == null || bytesLeftFromCurrentAttribute == null) {
            readNextAttribute(packetWithLeftover, offset)
        } else {
            readData(packetWithLeftover, offset, currentAttribute, bytesLeftFromCurrentAttribute)
        }
    }

    private fun readNextAttributeIfPossible(packet: ByteArray, offset: Int) {
        val bytesLeft = packet.size - offset
        if (bytesLeft > 0) {
            if (bytesLeft > 3) {
                readNextAttribute(packet, offset)
            } else {
                // We don't have enough bytes to read the next attribute, save for the next packet
                leftoverBytes = Arrays.copyOfRange(packet, offset, packet.size)
            }
        }
    }

    private fun readNextAttribute(packet: ByteArray, offset: Int) {
        val attribute = NotificationAttribute.fromRaw(packet[offset].toInt())
        val attributeLength = ((packet[offset + 2].toInt() and 0xFF) shl 8) + (packet[offset + 1].toInt() and 0xFF)

        currentAttribute = attribute
        bytesLeftFromCurrentAttribute = attributeLength

        readData(packet, offset + 3, attribute, attributeLength)
    }

    private fun readData(packet: ByteArray, offset: Int, attribute: NotificationAttribute, bytesToRead: Int) {
        if (offset >= packet.size) {
            // Wait for next packet to continue
            return
        }

        val bytesAvailableToRead = packet.size - offset
        if (bytesAvailableToRead >= bytesToRead) {
            currentAttributeData.write(packet, offset, bytesToRead)

            // Current attribute is finished, save it
            attributes[attribute] = String(currentAttributeData.toByteArray(), Charset.forName("UTF-8"))
            attributesToRead.remove(attribute)
            reset()

            if (attributesToRead.size > 0) {
                val newOffset = offset + bytesToRead
                readNextAttributeIfPossible(packet, newOffset)
            }
        } else {
            currentAttributeData.write(packet, offset, bytesAvailableToRead)
            bytesLeftFromCurrentAttribute = bytesToRead - bytesAvailableToRead
            // Wait for next packet to continue
        }
    }

    private fun reset() {
        currentAttribute = null
        bytesLeftFromCurrentAttribute = null
        currentAttributeData.reset()
    }

}