package com.codegy.aerlink.service.notifications.model

import java.util.*

class NotificationEvent(packet: ByteArray) {

    enum class Type {
        Added,
        Modified,
        Removed,
        Reserved;

        companion object {
            fun fromRaw(eventId: Int): Type {
                if (eventId >= Type.values().size) {
                    return Type.Reserved
                }

                return Type.values()[eventId]
            }
        }
    }

    private enum class EventFlag(val value: Int) {
        Silent(1 shl 0),
        Important(1 shl 1),
        PreExisting(1 shl 2),
        PositiveAction(1 shl 3),
        NegativeAction(1 shl 4);

        fun check(eventFlags: Int): Boolean {
            return eventFlags and value != 0 
        }
    }

    enum class Category {
        Other,
        IncomingCall,
        MissedCall,
        Voicemail,
        Social,
        Schedule,
        Email,
        News,
        HealthAndFitness,
        BusinessAndFinance,
        Location,
        Entertainment,
        Reserved;

        companion object {
            fun fromRaw(categoryId: Int): Category {
                if (categoryId >= Category.values().size) {
                    return Category.Reserved
                }

                return Category.values()[categoryId]
            }
        }
    }

    val uid: ByteArray = Arrays.copyOfRange(packet, 4, 8)
    val type: Type = Type.fromRaw(packet[0].toInt())
    val category: Category = Category.fromRaw(packet[2].toInt())
    val categoryCount: Int = packet[3].toInt()
    val isSilent: Boolean
    val isImportant: Boolean
    val isPreExisting: Boolean
    val hasPositiveAction: Boolean
    val hasNegativeAction: Boolean

    init {
        val eventFlags = packet[1].toInt()
        isSilent = EventFlag.Silent.check(eventFlags)
        isImportant = EventFlag.Important.check(eventFlags)
        isPreExisting = EventFlag.PreExisting.check(eventFlags)
        hasPositiveAction = EventFlag.PositiveAction.check(eventFlags)
        hasNegativeAction = EventFlag.NegativeAction.check(eventFlags)
    }

}