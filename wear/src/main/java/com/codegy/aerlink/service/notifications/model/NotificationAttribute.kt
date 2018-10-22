package com.codegy.aerlink.service.notifications.model

enum class NotificationAttribute(val value: Byte) {
    AppIdentifier(0),
    Title(1),
    Subtitle(2),
    Message(3),
    MessageSize(4),
    Date(5),
    PositiveActionLabel(6),
    NegativeActionLabel(7),
    Reserved(8);

    companion object {
        fun fromRaw(eventId: Int): NotificationAttribute {
            if (eventId >= NotificationAttribute.values().size) {
                return NotificationAttribute.Reserved
            }

            return NotificationAttribute.values()[eventId]
        }
    }

    fun needsLengthParameter(): Boolean {
        return when (this) {
            Title, Subtitle, Message -> true
            else -> false
        }
    }

}