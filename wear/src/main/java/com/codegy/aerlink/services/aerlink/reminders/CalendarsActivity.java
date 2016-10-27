package com.codegy.aerlink.services.aerlink.reminders;

import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.view.ProgressSpinner;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import com.codegy.aerlink.Constants;
import com.codegy.aerlink.R;
import com.codegy.aerlink.utils.AerlinkActivity;

import java.util.List;

public class CalendarsActivity extends AerlinkActivity implements ReminderServiceHandler.CalendarsCallback, AdapterView.OnItemClickListener {

    private static final String TAG_LOG = CalendarsActivity.class.getSimpleName();

    private ListView mListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendars);
        setAmbientEnabled();


        mDisconnectedLayout = findViewById(R.id.disconnectedLinearLayout);
        mConnectionInfoTextView = (TextView) findViewById(R.id.connectionInfoTextView);

        mConnectionErrorLayout = findViewById(R.id.connectionErrorLinearLayout);

        mLoadingLayout = findViewById(R.id.loadingLayout);
        mLoadingSpinner = (ProgressSpinner) findViewById(R.id.loadingSpinner);

        mListView = (ListView) findViewById(R.id.listView);
        mListView.setAdapter(new CalendarsListAdapter(CalendarsActivity.this, null));
        mListView.setOnItemClickListener(CalendarsActivity.this);

                /*
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(CalendarsActivity.this);
                String calendarsData = sp.getString(Constants.SPK_REMINDER_CALENDARS_DATA, null);
                if (calendarsData != null) {
                    onCalendarsUpdated(calendarsData);
                }
                */

        updateInterface();
    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);

        ((CalendarsListAdapter) mListView.getAdapter()).setAmbient(true);
    }

    @Override
    public void onExitAmbient() {
        super.onExitAmbient();

        ((CalendarsListAdapter) mListView.getAdapter()).setAmbient(false);
    }

    @Override
    public void onConnectedToDevice() {
        ReminderServiceHandler serviceHandler = (ReminderServiceHandler) getServiceHandler(ReminderServiceHandler.class);

        if (serviceHandler != null) {
            setLoading(true);
            scheduleTimeOutTask();

            serviceHandler.setCalendarsCallback(this);
            serviceHandler.requestCalendarsUpdate(false, new Runnable() {
                @Override
                public void run() {
                    cancelTimeOutTask();
                    setLoading(false);

                    showErrorInterface(true);
                }
            });
        }
        else {
            showErrorInterface(true);
        }
    }

    @Override
    public void onDisconnectedFromDevice() {
        super.onDisconnectedFromDevice();

        ReminderServiceHandler serviceHandler = (ReminderServiceHandler) getServiceHandler(ReminderServiceHandler.class);

        if (serviceHandler != null) {
            serviceHandler.setCalendarsCallback(null);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        ReminderServiceHandler serviceHandler = (ReminderServiceHandler) getServiceHandler(ReminderServiceHandler.class);

        if (serviceHandler != null) {
            ReminderCalendar calendar = ((CalendarsListAdapter) mListView.getAdapter()).getItem(position);
            serviceHandler.setSelectedCalendar(calendar);

            Intent remindersIntent = new Intent(this, RemindersActivity.class);
            remindersIntent.putExtra(Constants.IE_CALENDAR, calendar);

            startActivity(remindersIntent);
        }
        else {
            onDisconnectedFromDevice();
        }
    }

    @Override
    public void onDataTransferStarted() {
        cancelTimeOutTask();
    }

    @Override
    public void onCalendarsUpdated(final List<ReminderCalendar>  calendars) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setLoading(false);
                showErrorInterface(false);
                cancelTimeOutTask();

                if (mListView != null) {
                    ((CalendarsListAdapter) mListView.getAdapter()).refresh(calendars);
                }
            }
        });
    }
}
