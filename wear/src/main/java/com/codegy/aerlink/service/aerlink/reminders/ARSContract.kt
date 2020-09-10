package com.codegy.aerlink.service.aerlink.reminders

import android.content.Context
import com.codegy.aerlink.connection.CharacteristicIdentifier
import com.codegy.aerlink.service.ServiceContract
import com.codegy.aerlink.service.ServiceManager
import com.codegy.aerlink.utils.CommandHandler
import java.util.UUID

object ARSContract: ServiceContract {
    // ARS - Aerlink Reminders Service
    override val serviceUuid: UUID = UUID.fromString("0d6a2c7d-392a-4781-b432-db437f70f643")
    val remindersDataCharacteristicUuid: UUID = UUID.fromString("1e082d2c-c279-4f49-a63c-a70c74f562d6")
    val remindersActionCharacteristicUuid: UUID = UUID.fromString("b708a912-5d7e-4baf-8a63-f915c6717050")

    override val characteristicsToSubscribe = listOf(
            CharacteristicIdentifier(serviceUuid, remindersDataCharacteristicUuid)
    )

    override fun createManager(context: Context, commandHandler: CommandHandler): ServiceManager {
        return RemindersServiceManager(context, commandHandler)
    }
}