package com.codegy.aerlink.notifications;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * Created by Guiye on 18/5/15.
 */
public class NotificationPacketProcessor {

    private enum PacketProcessingStatus {
        Init,
        AppId,
        Title,
        Message,
        PositiveAction,
        NegativeAction,
        Finished
    }

    private static final String LOG_TAG = NotificationPacketProcessor.class.getSimpleName();


    private NotificationData notificationData;
    private ByteArrayOutputStream processingAttribute;

    private byte[] bytesFromPreviousPacket;
    // The number of bytes left to process on the current packet
    private int bytesLeftToProcess;
    // The number of bytes of the current attribute being processed that are in the next packet
    private int attributeBytesInNextPacket;

    private PacketProcessingStatus processingStatus;


    public NotificationPacketProcessor(NotificationData notificationData) {
        processingStatus = PacketProcessingStatus.Init;

        bytesLeftToProcess = 0;
        attributeBytesInNextPacket = 0;
        bytesFromPreviousPacket = new byte[] {};

        processingAttribute = new ByteArrayOutputStream();

        this.notificationData = notificationData;
    }

    public NotificationData getNotificationData() {
        return notificationData;
    }

    public boolean hasFinishedProcessing() {
        return processingStatus == PacketProcessingStatus.Finished || notificationData == null;
    }

    private int getAttributeLength(byte[] packet, int lengthIndex){
        //get att0's length
        byte[] byteLength = {packet[lengthIndex + 2], packet[lengthIndex + 1]};
        BigInteger length = new BigInteger(byteLength);
        return length.intValue();
    }

    public static byte[] concat(byte[] a, byte[] b) {
        int aLen = a.length;
        int bLen = b.length;
        byte[] c = new byte[aLen+bLen];
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);
        return c;
    }

    private void updateProcessingStatus() {
        switch (processingStatus) {
            case Init:
                processingStatus = PacketProcessingStatus.Title;
                break;
            case AppId:
                processingStatus = PacketProcessingStatus.Title;
                try {
                    notificationData.setAppId(new String(processingAttribute.toByteArray(), "UTF-8"));
                    processingAttribute.reset();
                    Log.d(LOG_TAG, "App ID: " + notificationData.getAppId());
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                break;
            case Title:
                processingStatus = PacketProcessingStatus.Message;
                try {
                    notificationData.setTitle(new String(processingAttribute.toByteArray(), "UTF-8"));
                    processingAttribute.reset();
                    Log.d(LOG_TAG, "Title: " + notificationData.getTitle());
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                break;
            case Message:
                if (notificationData.hasPositiveAction()) {
                    processingStatus = PacketProcessingStatus.PositiveAction;
                }
                else if (notificationData.hasNegativeAction()) {
                    processingStatus = PacketProcessingStatus.NegativeAction;
                }
                else {
                    processingStatus = PacketProcessingStatus.Finished;

                    NotificationDataExpander.updateData(notificationData);
                }

                try {
                    notificationData.setMessage(new String(processingAttribute.toByteArray(), "UTF-8"));
                    processingAttribute.reset();
                    Log.d(LOG_TAG, "Message: " + notificationData.getMessage());
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                break;
            case PositiveAction:
                if (notificationData.hasNegativeAction()) {
                    processingStatus = PacketProcessingStatus.NegativeAction;
                }
                else {
                    processingStatus = PacketProcessingStatus.Finished;

                    NotificationDataExpander.updateData(notificationData);
                }

                try {
                    notificationData.setPositiveAction(new String(processingAttribute.toByteArray(), "UTF-8"));
                    processingAttribute.reset();
                    Log.d(LOG_TAG, "Positive Action: " + notificationData.getPositiveAction());
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                break;
            case NegativeAction:
                processingStatus = PacketProcessingStatus.Finished;

                NotificationDataExpander.updateData(notificationData);

                try {
                    notificationData.setNegativeAction(new String(processingAttribute.toByteArray(), "UTF-8"));
                    processingAttribute.reset();
                    Log.d(LOG_TAG, "Negative Action: " + notificationData.getNegativeAction());
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                break;
        }
    }

    public void process(byte[] packet) {
        // Get size of received data
        packet = concat(bytesFromPreviousPacket, packet);
        bytesLeftToProcess = packet.length;
        bytesFromPreviousPacket = new byte[] {};

        int attributeIndex;

        while (bytesLeftToProcess > 0) {
            if (attributeBytesInNextPacket > 0) {
                // Still processing attribute started in a previous packet

                if (bytesLeftToProcess < attributeBytesInNextPacket) {
                    // The attribute is still not finished with this packet

                    // Save attribute data
                    processingAttribute.write(packet, 0, bytesLeftToProcess);

                    // Update bytes left of current attribute
                    attributeBytesInNextPacket -= bytesLeftToProcess;

                    // All bytes have been processed
                    bytesLeftToProcess = 0;
                }
                else {
                    // The attribute ends in this packet

                    // Save attribute data
                    processingAttribute.write(packet, 0, attributeBytesInNextPacket);

                    // There may be bytes of another attribute left in this packet
                    bytesLeftToProcess -= attributeBytesInNextPacket;

                    if (bytesLeftToProcess > 0 && bytesLeftToProcess <= 2) {
                        // Not enough bytes to start processing next attribute

                        // Save bytes for next packet
                        bytesFromPreviousPacket = Arrays.copyOfRange(packet, attributeBytesInNextPacket, packet.length);
                        bytesLeftToProcess = 0;
                    }

                    // This attribute's bytes have been processed
                    attributeBytesInNextPacket = 0;

                    updateProcessingStatus();
                }
            }
            else if (bytesLeftToProcess > 0) {
                // Attribute index
                if (processingStatus == PacketProcessingStatus.Init) {
                    // Previous bytes' data is already known
                    attributeIndex = 5;
                    processingStatus = PacketProcessingStatus.AppId;
                }
                else {
                    attributeIndex = packet.length - bytesLeftToProcess;
                }

                // Length of attribute to read
                int attributeLength = getAttributeLength(packet, attributeIndex);

                // Not counting bytes offering attribute length info
                int bytesInCurrentPacket = packet.length - (attributeIndex + 3);

                if (bytesInCurrentPacket < attributeLength) {
                    // The attribute is divided

                    // Save attribute data
                    processingAttribute.write(packet, attributeIndex + 3, bytesInCurrentPacket);

                    // Update bytes left of current attribute
                    attributeBytesInNextPacket = attributeLength - bytesInCurrentPacket;

                    // All bytes have been processed
                    bytesLeftToProcess = 0;
                }
                else {
                    // The attribute ends in this packet

                    // Save attribute data
                    processingAttribute.write(packet, attributeIndex + 3, attributeLength);

                    // This attribute's bytes have been processed
                    attributeBytesInNextPacket = 0;

                    // There may be bytes of another attribute left in this packet
                    bytesLeftToProcess = bytesInCurrentPacket - attributeLength;

                    if (bytesLeftToProcess > 0 && bytesLeftToProcess <= 2) {
                        // Not enough bytes to start processing next attribute

                        // Offset of processed bytes
                        int offset = attributeIndex + 3 + attributeLength;

                        // Save bytes for next packet
                        bytesFromPreviousPacket = Arrays.copyOfRange(packet, offset, packet.length);
                        bytesLeftToProcess = 0;
                    }

                    updateProcessingStatus();
                }
            }
        }
    }

}
