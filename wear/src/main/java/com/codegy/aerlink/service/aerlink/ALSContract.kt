package com.codegy.aerlink.service.aerlink

import android.content.Context
import com.codegy.aerlink.connection.CharacteristicIdentifier
import com.codegy.aerlink.service.ServiceContract
import com.codegy.aerlink.service.ServiceManager
import com.codegy.aerlink.utils.CommandHandler
import java.util.*

object ALSContract: ServiceContract {

    // ALS - Aerlink Service
    override val serviceUuid: UUID = UUID.fromString("0d6a2c7d-392a-4781-b432-db437f70f643")
    val cameraRemoteDataCharacteristicUuid: UUID = UUID.fromString("7be5ff0a-e736-453a-9257-c94fffdc6a97")
    val cameraRemoteActionCharacteristicUuid: UUID = UUID.fromString("19c3577c-0952-4dc1-b03e-3db3fffc381a")
    val remindersDataCharacteristicUuid: UUID = UUID.fromString("1e082d2c-c279-4f49-a63c-a70c74f562d6")
    val remindersActionCharacteristicUuid: UUID = UUID.fromString("b708a912-5d7e-4baf-8a63-f915c6717050")
    val weatherDataCharacteristicUuid: UUID = UUID.fromString("504b6d67-08d0-4a34-8258-8f8324ce0092")
    val weatherActionCharacteristicUuid: UUID = UUID.fromString("303964c2-d488-4a31-bd28-037c85aeffa7")
    val utilsDataCharacteristicUuid: UUID = UUID.fromString("96eaff02-2e7a-4013-91a7-81581c6a7a4b")
    val utilsActionCharacteristicUuid: UUID = UUID.fromString("e476843e-02c9-4ac5-9ca8-6bda217b225f")

    override val characteristicsToSubscribe = listOf(
            CharacteristicIdentifier(serviceUuid, cameraRemoteDataCharacteristicUuid)
    )

    override fun createManager(context: Context, commandHandler: CommandHandler): ServiceManager {
        return UtilsServiceManager()
    }
    
}