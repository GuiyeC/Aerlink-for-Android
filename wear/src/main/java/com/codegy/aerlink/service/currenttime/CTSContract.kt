package com.codegy.aerlink.service.currenttime

import android.content.Context
import com.codegy.aerlink.connection.CharacteristicIdentifier
import com.codegy.aerlink.service.ServiceContract
import com.codegy.aerlink.service.ServiceManager
import com.codegy.aerlink.utils.CommandHandler
import java.util.*

object CTSContract: ServiceContract {

    // CTS - Current Time Service
    override val serviceUuid: UUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb")
    val currentTimeCharacteristicUuid: UUID = UUID.fromString("00002a2b-0000-1000-8000-00805f9b34fb")
    val localTimeInformationCharacteristicUuid: UUID = UUID.fromString("00002a0f-0000-1000-8000-00805f9b34fb")
    val referenceTimeInformationCharacteristicUuid: UUID = UUID.fromString("00002a14-0000-1000-8000-00805f9b34fb")

    override val characteristicsToSubscribe: List<CharacteristicIdentifier> = listOf(
            CharacteristicIdentifier(serviceUuid, currentTimeCharacteristicUuid)
    )

    override fun createManager(context: Context, commandHandler: CommandHandler): ServiceManager {
        return CurrentTimeServiceManager()
    }

}