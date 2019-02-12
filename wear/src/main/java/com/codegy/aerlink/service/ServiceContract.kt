package com.codegy.aerlink.service

import android.content.Context
import com.codegy.aerlink.connection.CharacteristicIdentifier
import com.codegy.aerlink.utils.CommandHandler
import java.util.*

interface ServiceContract {
    val serviceUuid: UUID
    val characteristicsToSubscribe: List<CharacteristicIdentifier>
    fun createManager(context: Context, commandHandler: CommandHandler): ServiceManager
}