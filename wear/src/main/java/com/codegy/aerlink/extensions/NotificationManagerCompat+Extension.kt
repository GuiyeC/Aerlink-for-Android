package com.codegy.aerlink.extensions

import android.app.NotificationChannel
import androidx.core.app.NotificationManagerCompat

enum class NotificationChannelImportance {
    Default,
    High,
    Low,
    Max,
    Min,
    None,
    Unspecified;

    val value: Int get() {
        return when (this) {
            Default -> NotificationManagerCompat.IMPORTANCE_DEFAULT
            High -> NotificationManagerCompat.IMPORTANCE_HIGH
            Low -> NotificationManagerCompat.IMPORTANCE_LOW
            Max -> NotificationManagerCompat.IMPORTANCE_MAX
            Min -> NotificationManagerCompat.IMPORTANCE_MIN
            None -> NotificationManagerCompat.IMPORTANCE_NONE
            Unspecified -> NotificationManagerCompat.IMPORTANCE_UNSPECIFIED
        }
    }
}

fun NotificationManagerCompat.createChannelIfNeeded(
        id: String, description: CharSequence, importance: NotificationChannelImportance
) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        if (getNotificationChannel(id) != null) return
        val notificationChannel = NotificationChannel(id, description, importance.value)
        createNotificationChannel(notificationChannel)
    }
}