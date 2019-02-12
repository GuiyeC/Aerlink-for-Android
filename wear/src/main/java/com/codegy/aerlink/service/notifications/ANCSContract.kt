package com.codegy.aerlink.service.notifications

import android.content.Context
import com.codegy.aerlink.connection.CharacteristicIdentifier
import com.codegy.aerlink.service.ServiceContract
import com.codegy.aerlink.service.ServiceManager
import com.codegy.aerlink.utils.ServiceUtils
import java.util.*

object ANCSContract: ServiceContract {

    // ANCS - Apple Notification Center Service Profile
    override val serviceUuid = UUID.fromString("7905f431-b5ce-4e99-a40f-4b1e122d00d0")
    val notificationSourceCharacteristicUuid = UUID.fromString("9fbf120d-6301-42d9-8c58-25e699a21dbd")
    val dataSourceCharacteristicUuid         = UUID.fromString("22eac6e9-24d6-4bb5-be44-b36ace7c7bfb")
    val controlPointCharacteristicUuid       = UUID.fromString("69d1d8f3-45e1-49a8-9821-9bbdfdaad9d9")

    override val characteristicsToSubscribe: List<CharacteristicIdentifier> = listOf(
            CharacteristicIdentifier(serviceUuid, notificationSourceCharacteristicUuid),
            CharacteristicIdentifier(serviceUuid, dataSourceCharacteristicUuid)
    )

    override fun createManager(context: Context, commandHandler: CommandHandler): ServiceManager {
        return NotificationServiceManager(context, commandHandler)
    }

}