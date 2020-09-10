package com.codegy.aerlink.ui.reminders

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.codegy.aerlink.R
import com.codegy.aerlink.service.aerlink.reminders.RemindersServiceManager
import com.codegy.aerlink.service.aerlink.reminders.model.ReminderCalendar
import com.codegy.aerlink.service.aerlink.reminders.model.ReminderItem
import com.codegy.aerlink.ui.AerlinkActivity
import kotlinx.android.synthetic.main.activity_reminders.*
import kotlinx.android.synthetic.main.list_item_reminder.view.*

class RemindersActivity : AerlinkActivity(), RemindersServiceManager.RemindersCallback {
    private val calendar: ReminderCalendar?
        get() = intent?.getParcelableExtra(IE_CALENDAR)

    inner class Adapter: RecyclerView.Adapter<Adapter.ViewHolder>() {
        private var reminders: List<ReminderItem> = listOf()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val layoutInflater: LayoutInflater = LayoutInflater.from(parent.context)
            val itemView = layoutInflater.inflate(R.layout.list_item_reminder, parent, false)
            return ViewHolder(itemView)
        }

        override fun getItemCount(): Int = reminders.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val reminder = reminders[position]
            holder.checkBox.text = reminder.title
            holder.checkBox.isChecked = reminder.isCompleted
            if (isAmbient) {
                holder.checkBox.setTextColor(Color.LTGRAY)
            } else {
                holder.checkBox.setTextColor(Color.WHITE)
            }
            holder.itemView.setOnClickListener {
                val newValue = !holder.checkBox.isChecked
                val calendarIdentifier = calendar?.identifier ?: return@setOnClickListener
                val serviceManager = getServiceManager(RemindersServiceManager::class) ?: return@setOnClickListener
                serviceManager.setReminderCompleted(newValue, reminder, calendarIdentifier) {
                    holder.checkBox.post {
                        holder.checkBox.isChecked = reminder.isCompleted
                    }
                }
            }
        }

        fun reloadData(reminders: List<ReminderItem>) {
            this.reminders = reminders
            notifyDataSetChanged()
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val checkBox: CheckBox = itemView.checkBox
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminders)
        setAmbientEnabled()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = Adapter()
        updateUI()
    }

    override fun onPause() {
        val serviceManager = getServiceManager(RemindersServiceManager::class)
        serviceManager?.remindersCallback = null
        super.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateUI()
        updateReminders()
    }

    private fun updateUI() {
        val calendar = calendar ?: return
        val calendarColor = Color.parseColor("#" + calendar.color)
        titleTextView.text = calendar.title
        titleTextView.setTextColor(calendarColor)
    }

    private fun updateReminders() {
        val serviceManager = getServiceManager(RemindersServiceManager::class) ?: return
        calendar?.let {
            showLoading(true)
            serviceManager.requestRemindersUpdate(false, it.identifier)
        } ?: run { finish() }
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

        serviceManager.remindersCallback = this
        updateReminders()
    }

    override fun onError() {
        showLoading(false)
        showError(true)
    }

    override fun onDataTransferStarted() {
        showLoading(true)
    }

    override fun onRemindersUpdated(reminders: List<ReminderItem>) {
        runOnUiThread {
            showLoading(false)
            val adapter = recyclerView.adapter as? Adapter
            adapter?.reloadData(reminders)
        }
    }

    companion object {
        private val LOG_TAG = RemindersActivity::class.java.simpleName
        const val IE_CALENDAR: String = "com.codegy.aerlink.ui.reminders.IE_CALENDAR"

        fun startWithCalendar(context: Context, calendar: ReminderCalendar) {
            val intent = Intent(context, RemindersActivity::class.java)
            intent.putExtra(IE_CALENDAR, calendar)
            context.startActivity(intent)
        }
    }
}
