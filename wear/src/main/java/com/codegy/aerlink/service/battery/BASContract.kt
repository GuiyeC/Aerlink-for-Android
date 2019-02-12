package com.codegy.aerlink.service.battery

import android.content.Context
import com.codegy.aerlink.connection.CharacteristicIdentifier
import com.codegy.aerlink.service.ServiceContract
import com.codegy.aerlink.service.ServiceManager
import com.codegy.aerlink.utils.CommandHandler
import java.util.*

object BASContract: ServiceContract {

    // BAS - Battery Service
    override val serviceUuid: UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
    val batteryLevelCharacteristicUuid: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    override val characteristicsToSubscribe: List<CharacteristicIdentifier> = listOf(CharacteristicIdentifier(serviceUuid, batteryLevelCharacteristicUuid))

    override fun createManager(context: Context, commandHandler: CommandHandler): ServiceManager {
        return BatteryServiceManager(context)
    }

}