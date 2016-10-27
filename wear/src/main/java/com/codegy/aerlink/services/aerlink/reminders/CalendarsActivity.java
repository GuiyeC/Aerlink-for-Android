package com.codegy.aerlink.services.aerlink.reminders;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.wearable.view.ProgressSpinner;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import com.codegy.aerlink.Constants;
import com.codegy.aerlink.R;
import com.codegy.aerlink.connection.ConnectionState;
import com.codegy.aerlink.utils.AerlinkActivity;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class CalendarsActivity extends AerlinkActivity implements ReminderServiceHandler.CalendarsCallback, AdapterView.OnItemClickListener {

    private static final String TAG_LOG = CalendarsActivity.class.getSimpleName();

    private ListView mListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setAmbientEnabled();

        setContentView(R.layout.activity_calendars);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mDisconnectedLayout = stub.findViewById(R.id.disconnectedLinearLayout);
                mConnectionInfoTextView = (TextView) stub.findViewById(R.id.connectionInfoTextView);

                mConnectionErrorLayout = stub.findViewById(R.id.connectionErrorLinearLayout);

                mLoadingLayout = stub.findViewById(R.id.loadingLayout);
                mLoadingSpinner = (ProgressSpinner) stub.findViewById(R.id.loadingSpinner);

                mListView = (ListView) stub.findViewById(R.id.listView);
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
                updateLoadingInterface();
                updateErrorInterface();
            }
        });
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
