package com.codegy.aerlink.connection

import java.util.*

data class CharacteristicIdentifier(
        val serviceUUID: UUID,
        val characteristicUUID: UUID
)