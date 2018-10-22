package com.codegy.aerlink.utils

import android.app.Notification
import com.codegy.aerlink.connection.Command

interface ServiceUtils {
    fun addCommandToQueue(command: Command)
    fun notify(tag: String? = null, id: Int, notification: Notification)
    fun cancelNotification(tag: String? = null, id: Int)
}