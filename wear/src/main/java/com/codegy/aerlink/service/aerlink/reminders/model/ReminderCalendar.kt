package com.codegy.aerlink.service.aerlink.reminders.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.json.JSONObject

@Parcelize
data class ReminderCalendar(val title: String, val identifier: String, val color: String) : Parcelable {
    constructor(jsonObject: JSONObject) : this(
            jsonObject.getString("t"),
            jsonObject.getString("i"),
            jsonObject.getString("c")
    )
}
