package com.codegy.aerlink.service.notifications.model

import com.codegy.aerlink.R
import java.nio.ByteBuffer
import kotlin.experimental.and

class NotificationEvent(packet: ByteArray) {
    enum class Type {
        Added,
        Modified,
        Removed,
        Reserved;

        companion object {
            fun fromRaw(eventId: Byte): Type {
                return values().getOrNull(eventId.toInt()) ?: Reserved
            }
        }
    }

    private enum class EventFlag(val value: Byte) {
        Silent(1 shl 0),
        Important(1 shl 1),
        PreExisting(1 shl 2),
        PositiveAction(1 shl 3),
        NegativeAction(1 shl 4);

        fun check(eventFlags: Byte): Boolean {
            return eventFlags and value != 0.toByte()
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

        val icon: Int
            get() {
                return when (this) {
                    Other -> R.drawable.nic_other
                    IncomingCall -> R.drawable.nic_incoming_call
                    MissedCall -> R.drawable.nic_missed_call
                    Voicemail -> R.drawable.nic_voicemail
                    Social -> R.drawable.nic_social
                    Schedule -> R.drawable.nic_scheduled
                    Email -> R.drawable.nic_email
                    News -> R.drawable.nic_news
                    HealthAndFitness -> R.drawable.nic_health_and_fitness
                    BusinessAndFinance -> R.drawable.nic_business_and_finance
                    Location -> R.drawable.nic_location
                    Entertainment -> R.drawable.nic_entertainment
                    Reserved -> R.drawable.nic_reserved
                }
            }

        companion object {
            fun fromRaw(categoryId: Byte): Category {
                return values().getOrNull(categoryId.toInt()) ?: Reserved
            }
        }
    }

    val uid: ByteArray = packet.copyOfRange(4, 8)
    val uidString: String
        get() = String(uid)
    val uidInt: Int
        get() = ByteBuffer.wrap(uid).int
    val type: Type = Type.fromRaw(packet[0])
    val category: Category = Category.fromRaw(packet[2])
    val categoryCount: Int = packet[3].toInt()
    val isSilent: Boolean
    val isImportant: Boolean
    val isPreExisting: Boolean
    val hasPositiveAction: Boolean
    val hasNegativeAction: Boolean

    init {
        val eventFlags = packet[1]
        isSilent = EventFlag.Silent.check(eventFlags)
        isImportant = EventFlag.Important.check(eventFlags)
        isPreExisting = EventFlag.PreExisting.check(eventFlags)
        hasPositiveAction = EventFlag.PositiveAction.check(eventFlags)
        hasNegativeAction = EventFlag.NegativeAction.check(eventFlags)
    }

    override fun toString(): String {
        return "NotificationEvent: $uidString $type $category Silent: $isSilent Important: $isImportant PreExisting: $isPreExisting Positive Action: $hasPositiveAction Negative Action: $hasNegativeAction "
    }
}