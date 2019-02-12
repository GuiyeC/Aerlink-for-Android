package com.codegy.aerlink.utils

import com.codegy.aerlink.connection.Command

interface CommandHandler {
    fun handleCommand(command: Command)
}