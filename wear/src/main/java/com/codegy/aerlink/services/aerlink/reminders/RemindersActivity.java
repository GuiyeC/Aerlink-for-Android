package com.codegy.aerlink.services.aerlink.reminders;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.wearable.view.WatchViewStub;
import android.view.View;
import android.widget.*;
import com.codegy.aerlink.Constants;
import com.codegy.aerlink.R;
import com.codegy.aerlink.utils.AerlinkActivity;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class RemindersActivity extends AerlinkActivity implements ReminderServiceHandler.RemindersCallback, AdapterView.OnItemClickListener {

    private static final String TAG_LOG = RemindersActivity.class.getSimpleName();

    private TextView mTitleTextView;
    private ListView mListView;
    private LinearLayout mDisconnectedLinearLayout;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setAmbientEnabled();

        setContentView(R.layout.activity_reminders);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mDisconnectedLinearLayout = (LinearLayout) stub.findViewById(R.id.disconnectedLinearLayout);

                mTitleTextView = (TextView) stub.findViewById(R.id.titleTextView);

                mListView = (ListView) findViewById(R.id.listView);
                mListView.setAdapter(new ReminderListAdapter(RemindersActivity.this, null));
                mListView.setOnItemClickListener(RemindersActivity.this);

                mTitleTextView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ReminderServiceHandler serviceHandler = (ReminderServiceHandler) getServiceHandler(ReminderServiceHandler.class);

                        if (serviceHandler != null) {
                            serviceHandler.requestDataUpdate();
                        }
                        else {
                            onDisconnectedFromDevice();
                        }
                    }
                });

                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(RemindersActivity.this);
                String remindersData = sp.getString(Constants.SPK_REMINDERS_DATA, null);
                if (remindersData != null) {
                    onRemindersUpdated(remindersData);
                }


                updateInterface();
            }
        });
    }

    @Override
    public void updateInterface() {
        if (mDisconnectedLinearLayout != null) {
            mDisconnectedLinearLayout.setVisibility(isConnected() ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void onConnectedToDevice() {
        super.onConnectedToDevice();

        ReminderServiceHandler serviceHandler = (ReminderServiceHandler) getServiceHandler(ReminderServiceHandler.class);

        if (serviceHandler != null) {
            serviceHandler.setRemindersCallback(this);
            serviceHandler.requestDataUpdate();
        }
        else {
            onDisconnectedFromDevice();
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
    public void onRemindersUpdated(final String remindersData) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONArray jsonItems = new JSONArray(remindersData);
                    String title = jsonItems.get(0).toString();

                    mTitleTextView.setText(title);

                    List<ReminderItem> items = new ArrayList<>(jsonItems.length());

                    int uncompleted = 0;
                    for (int i = 1; i < jsonItems.length(); i++) {
                        ReminderItem item = new ReminderItem(jsonItems.get(i).toString(), i - 1);

                        if (item.isCompleted()) {
                            items.add(item);
                        }
                        else {
                            items.add(uncompleted, item);
                            uncompleted++;
                        }
                    }

                    ((ReminderListAdapter) mListView.getAdapter()).refresh(items);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        ReminderServiceHandler serviceHandler = (ReminderServiceHandler) getServiceHandler(ReminderServiceHandler.class);

        if (serviceHandler != null) {
            CheckBox checkBox = (CheckBox) view.findViewById(R.id.checkBox);
            if (checkBox != null) {
                checkBox.setChecked(!checkBox.isChecked());

                ReminderItem item = ((ReminderListAdapter) mListView.getAdapter()).getItem(position);
                item.setCompleted(checkBox.isChecked());
                serviceHandler.setReminderCompleted(item);
            }
        }
        else {
            onDisconnectedFromDevice();
        }
    }

}
