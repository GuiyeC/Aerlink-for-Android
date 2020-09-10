package com.codegy.aerlink.service.aerlink.cameraremote

import android.content.Context
import com.codegy.aerlink.connection.CharacteristicIdentifier
import com.codegy.aerlink.service.ServiceContract
import com.codegy.aerlink.service.ServiceManager
import com.codegy.aerlink.utils.CommandHandler
import java.util.UUID

object ACRSContract: ServiceContract {
    // ACRS - Aerlink Camera Remote Service
    override val serviceUuid: UUID = UUID.fromString("0d6a2c7d-392a-4781-b432-db437f70f643")
    val cameraRemoteDataCharacteristicUuid: UUID = UUID.fromString("7be5ff0a-e736-453a-9257-c94fffdc6a97")
    val cameraRemoteActionCharacteristicUuid: UUID = UUID.fromString("19c3577c-0952-4dc1-b03e-3db3fffc381a")

    override val characteristicsToSubscribe = listOf(
            CharacteristicIdentifier(serviceUuid, cameraRemoteDataCharacteristicUuid)
    )

    override fun createManager(context: Context, commandHandler: CommandHandler): ServiceManager {
        return CameraRemoteServiceManager(context, commandHandler)
    }
}