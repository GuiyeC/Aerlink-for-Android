package com.codegy.aerlink.service.media.model

sealed class MediaEntity(val value: Byte) {
    object Player: MediaEntity(0) {
        enum class Attribute(val value: Byte) {
            Name(0),
            PlaybackInfo(1),
            Volume(2),
            Reserved(3);

            companion object {
                fun fromRaw(attributeId: Byte): Attribute {
                    return values().getOrNull(attributeId.toInt()) ?: Reserved
                }
            }
        }
    }
    object Queue: MediaEntity(1) {
        enum class Attribute(val value: Byte) {
            Index(0),
            Count(1),
            ShuffleMode(2),
            RepeatMode(3),
            Reserved(4);

            companion object {
                fun fromRaw(attributeId: Byte): Attribute {
                    return values().getOrNull(attributeId.toInt()) ?: Reserved
                }
            }
        }
    }
    object Track: MediaEntity(2) {
        enum class Attribute(val value: Byte) {
            Artist(0),
            Album(1),
            Title(2),
            Duration(3),
            Reserved(4);

            companion object {
                fun fromRaw(attributeId: Byte): Attribute {
                    return values().getOrNull(attributeId.toInt()) ?: Reserved
                }
            }
        }
    }
    object Reserved: MediaEntity(4)

    companion object {
        fun fromRaw(entityId: Byte): MediaEntity {
            if (entityId >= Reserved.value) {
                return Reserved
            }
            return listOf(Player, Queue, Track, Reserved)[entityId.toInt()]
        }
    }
}