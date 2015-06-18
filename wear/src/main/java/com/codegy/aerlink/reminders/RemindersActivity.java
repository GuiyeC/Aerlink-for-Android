package com.codegy.aerlink.reminders;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.wearable.view.WatchViewStub;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setAmbientEnabled();

        setContentView(R.layout.activity_reminders);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                setInfoTextView((TextView) stub.findViewById(R.id.infoTextView));

                tryToConnect();

                mTitleTextView = (TextView) stub.findViewById(R.id.titleTextView);

                mListView = (ListView) findViewById(R.id.listView);
                mListView.setAdapter(new ReminderListAdapter(RemindersActivity.this, null));
                mListView.setOnItemClickListener(RemindersActivity.this);

                mTitleTextView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (getService() != null) {
                            ReminderServiceHandler serviceHandler = (ReminderServiceHandler) getService().getServiceHandler(ReminderServiceHandler.class);
                            if (serviceHandler != null) {
                                serviceHandler.requestDataUpdate();
                            } else {
                                showDisconnected();
                            }
                        } else {
                            showDisconnected();
                        }
                    }
                });

                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(RemindersActivity.this);
                String remindersData = sp.getString(Constants.SPK_REMINDERS_DATA, null);
                if (remindersData != null) {
                    onRemindersUpdated(remindersData);
                }
            }
        });
    }

    @Override
    public void tryToConnect() {
        if (getService() != null) {
            ReminderServiceHandler serviceHandler = (ReminderServiceHandler) getService().getServiceHandler(ReminderServiceHandler.class);
            if (serviceHandler != null) {
                serviceHandler.setRemindersCallback(RemindersActivity.this);
                serviceHandler.requestDataUpdate();

                hideInfoTextView();
            }
            else {
                showDisconnected();
            }
        }
        else {
            showDisconnected();
        }
    }

    @Override
    public void disconnect() {
        if (getService() != null) {
            ReminderServiceHandler serviceHandler = (ReminderServiceHandler) getService().getServiceHandler(ReminderServiceHandler.class);
            if (serviceHandler != null) {
                serviceHandler.setRemindersCallback(null);
            }
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
        ReminderServiceHandler serviceHandler = (ReminderServiceHandler) getService().getServiceHandler(ReminderServiceHandler.class);
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
            showDisconnected();
        }
    }

}
