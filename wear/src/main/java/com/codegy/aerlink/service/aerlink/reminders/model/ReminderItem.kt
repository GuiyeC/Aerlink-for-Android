package com.codegy.aerlink.service.aerlink.reminders.model

import org.json.JSONObject

data class ReminderItem(val title: String, val identifier: String, var isCompleted: Boolean) {
    constructor(jsonObject: JSONObject) : this(
            jsonObject.getString("t"),
            jsonObject.getString("i"),
            jsonObject.getInt("c") == 1
    )
}