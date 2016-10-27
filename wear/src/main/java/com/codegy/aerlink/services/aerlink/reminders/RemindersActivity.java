package com.codegy.aerlink.services.aerlink.reminders;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.wearable.view.ProgressSpinner;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import com.codegy.aerlink.Constants;
import com.codegy.aerlink.R;
import com.codegy.aerlink.utils.AerlinkActivity;

import java.util.List;

public class RemindersActivity extends AerlinkActivity implements ReminderServiceHandler.RemindersCallback, AdapterView.OnItemClickListener {

    private static final String TAG_LOG = RemindersActivity.class.getSimpleName();

    private ReminderCalendar mCalendar;

    private TextView mTitleTextView;
    private ListView mListView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reminders);
        setAmbientEnabled();

        Intent intent = getIntent();
        mCalendar = intent.getParcelableExtra(Constants.IE_CALENDAR);

        mDisconnectedLayout = findViewById(R.id.disconnectedLinearLayout);
        mConnectionInfoTextView = (TextView) findViewById(R.id.connectionInfoTextView);

        mConnectionErrorLayout = findViewById(R.id.connectionErrorLinearLayout);

        int calendarColor = Color.parseColor("#"+mCalendar.getColor());

        mLoadingLayout = findViewById(R.id.loadingLayout);
        mLoadingSpinner = (ProgressSpinner) findViewById(R.id.loadingSpinner);
        mLoadingSpinner.setColors(new int[] { calendarColor });

        mTitleTextView = (TextView) findViewById(R.id.titleTextView);
        mTitleTextView.setText(mCalendar.getTitle());
        mTitleTextView.setTextColor(calendarColor);

        mListView = (ListView) findViewById(R.id.listView);
        mListView.setAdapter(new ReminderListAdapter(RemindersActivity.this, null));
        mListView.setOnItemClickListener(RemindersActivity.this);

        mTitleTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ReminderServiceHandler serviceHandler = (ReminderServiceHandler) getServiceHandler(ReminderServiceHandler.class);

                if (serviceHandler != null) {
                    serviceHandler.requestRemindersUpdate(false, new Runnable() {
                        @Override
                        public void run() {
                            setLoading(false);

                            showErrorInterface(true);
                        }
                    });
                }
                else {
                    onDisconnectedFromDevice();
                }
            }
        });

                /*
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(RemindersActivity.this);
                String remindersData = sp.getString(Constants.SPK_REMINDER_ITEMS_DATA + mCalendar.getIdentifier(), null);
                if (remindersData != null) {
                    onRemindersUpdated(remindersData);
                }
                */

        updateInterface();
    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);

        mTitleTextView.setTextColor(Color.LTGRAY);
        ((ReminderListAdapter) mListView.getAdapter()).setAmbient(true);
    }

    @Override
    public void onExitAmbient() {
        super.onExitAmbient();

        mTitleTextView.setTextColor(Color.parseColor("#"+mCalendar.getColor()));
        ((ReminderListAdapter) mListView.getAdapter()).setAmbient(false);
    }

    @Override
    public void onConnectedToDevice() {
        ReminderServiceHandler serviceHandler = (ReminderServiceHandler) getServiceHandler(ReminderServiceHandler.class);

        if (serviceHandler != null) {
            setLoading(true);
            scheduleTimeOutTask();

            serviceHandler.setRemindersCallback(this);
            serviceHandler.requestRemindersUpdate(false, new Runnable() {
                @Override
                public void run() {
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
            serviceHandler.setRemindersCallback(null);
        }
    }

    @Override
    public void onDataTransferStarted() {
        cancelTimeOutTask();
    }

    @Override
    public void onRemindersUpdated(final List<ReminderItem> reminders) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setLoading(false);
                showErrorInterface(false);
                cancelTimeOutTask();

                if (mListView != null) {
                    ((ReminderListAdapter) mListView.getAdapter()).refresh(reminders);
                }
            }
        });
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final ReminderServiceHandler serviceHandler = (ReminderServiceHandler) getServiceHandler(ReminderServiceHandler.class);

        if (serviceHandler != null) {
            CheckBox checkBox = (CheckBox) view.findViewById(R.id.checkBox);
            if (checkBox != null) {
                final boolean completed = !checkBox.isChecked();
                checkBox.setChecked(completed);

                setLoading(true);

                final ReminderItem item = ((ReminderListAdapter) mListView.getAdapter()).getItem(position);
                item.setCompleted(completed);
                serviceHandler.setReminderCompleted(item,
                        new Runnable() { // success
                            @Override
                            public void run() {
                                setLoading(false);
                                showErrorInterface(false);

                                serviceHandler.updateCachedData(mCalendar.getIdentifier(), item);
                            }
                        },
                        new Runnable() { // failure
                            @Override
                            public void run() {
                                setLoading(false);

                                showErrorInterface(true);
                            }
                        }
                );
            }
        }
        else {
            onDisconnectedFromDevice();
        }
    }

}
