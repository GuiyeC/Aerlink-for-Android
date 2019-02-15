package com.codegy.aerlink.service.media

import android.content.Context
import com.codegy.aerlink.connection.CharacteristicIdentifier
import com.codegy.aerlink.service.ServiceContract
import com.codegy.aerlink.service.ServiceManager
import com.codegy.aerlink.service.battery.BatteryServiceManager
import com.codegy.aerlink.utils.CommandHandler
import java.util.*

object AMSContract: ServiceContract {

    // AMS - Apple Media Service Profile
    override val serviceUuid: UUID = UUID.fromString("89d3502b-0f36-433a-8ef4-c502ad55f8dc")
    val remoteCommandCharacteristicUuid: UUID = UUID.fromString("9b3c81d8-57b1-4a8a-b8df-0e56f7ca51c2")
    val entityUpdateCharacteristicUuid: UUID = UUID.fromString("2f7cabce-808d-411f-9a0c-bb92ba96c102")
    val entityAttributeCharacteristicUuid: UUID = UUID.fromString("c6b2f38c-23ab-46d8-a6ab-a3a870bbd5d7")

    override val characteristicsToSubscribe: List<CharacteristicIdentifier> = listOf(
            CharacteristicIdentifier(serviceUuid, entityUpdateCharacteristicUuid)
//            CharacteristicIdentifier(serviceUuid, entityAttributeCharacteristicUuid)
    )

    override fun createManager(context: Context, commandHandler: CommandHandler): ServiceManager {
        return MediaServiceManager(context, commandHandler)
    }

}