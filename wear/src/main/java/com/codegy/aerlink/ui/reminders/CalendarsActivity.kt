package com.codegy.aerlink.ui.reminders

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.codegy.aerlink.R
import com.codegy.aerlink.service.aerlink.reminders.RemindersServiceManager
import com.codegy.aerlink.service.aerlink.reminders.model.ReminderCalendar
import com.codegy.aerlink.ui.AerlinkActivity
import kotlinx.android.synthetic.main.activity_calendars.*
import kotlinx.android.synthetic.main.list_item_calendar.view.*

class CalendarsActivity : AerlinkActivity(), RemindersServiceManager.CalendarsCallback {
    inner class Adapter: RecyclerView.Adapter<Adapter.ViewHolder>() {
        private var calendars: List<ReminderCalendar> = listOf()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val layoutInflater: LayoutInflater = LayoutInflater.from(parent.context)
            val itemView = layoutInflater.inflate(R.layout.list_item_calendar, parent, false)
            return ViewHolder(itemView)
        }

        override fun getItemCount(): Int = calendars.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val calendar = calendars[position]
            holder.textView.text = calendar.title
            if (isAmbient) {
                holder.textView.setTextColor(Color.LTGRAY)
                holder.cardView.setCardBackgroundColor(Color.rgb(24, 24, 24))
            } else {
                holder.textView.setTextColor(Color.WHITE)
                holder.cardView.setCardBackgroundColor(Color.parseColor("#52" + calendar.color))
            }
            holder.itemView.setOnClickListener {
                RemindersActivity.startWithCalendar(holder.itemView.context, calendar)
            }
        }

        fun reloadData(calendars: List<ReminderCalendar>) {
            this.calendars = calendars
            notifyDataSetChanged()
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cardView: CardView = itemView.cardView
            val textView: TextView = itemView.textView
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendars)
        setAmbientEnabled()

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = Adapter()
    }

    override fun onPause() {
        val serviceManager = getServiceManager(RemindersServiceManager::class)
        serviceManager?.calendarsCallback = null
        super.onPause()
    }

    override fun onEnterAmbient(ambientDetails: Bundle?) {
        super.onEnterAmbient(ambientDetails)
        recyclerView.adapter?.notifyDataSetChanged()
    }

    override fun onExitAmbient() {
        super.onExitAmbient()
        recyclerView.adapter?.notifyDataSetChanged()
    }

    override fun onConnectedToDevice() {
        val serviceManager = getServiceManager(RemindersServiceManager::class)
        if (serviceManager == null) {
            Log.v(LOG_TAG, "Service manager not available")
            showError(true)
            return
        }

        serviceManager.calendarsCallback = this
        showLoading(true)
        serviceManager.requestCalendarsUpdate(false)
    }

    override fun onError() {
        showError(true)
    }

    override fun onDataTransferStarted() {
        showLoading(true)
    }

    override fun onCalendarsUpdated(calendars: List<ReminderCalendar>) {
        runOnUiThread {
            showLoading(false)
            val adapter = recyclerView.adapter as? Adapter
            adapter?.reloadData(calendars)
        }
    }

    companion object {
        private val LOG_TAG = CalendarsActivity::class.java.simpleName
    }
}
