package com.codegy.aerlink.reminders;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;
import com.codegy.aerlink.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Guiye on 20/5/15.
 */
public class ReminderListAdapter extends BaseAdapter {

    private static final int layoutResourceId = R.layout.list_item_reminder;

    private List<ReminderItem> mItems;
    private final Context mContext;
    private final LayoutInflater mInflater;

    // Provide a suitable constructor (depends on the kind of dataset)
    public ReminderListAdapter(Context context, List<ReminderItem> items) {
        mContext = context;
        mInflater = LayoutInflater.from(context);

        if (items != null) {
            mItems = items;
        }
        else {
            mItems = new ArrayList<>();
        }
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public ReminderItem getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ItemViewHolder holder;

        if (convertView == null) {
            convertView = mInflater.inflate(layoutResourceId, parent, false);

            holder = new ItemViewHolder(convertView);

            convertView.setTag(holder);
        }
        else {
            holder = (ItemViewHolder) convertView.getTag();
        }


        try {
            ReminderItem item = getItem(position);
            holder.textView.setText(item.getTitle());
            holder.checkBox.setChecked(item.isCompleted());
        }
        catch (IndexOutOfBoundsException e) {}

        return convertView;
    }

    // Provide a reference to the type of views you're using
    public static class ItemViewHolder {
        private TextView textView;
        private CheckBox checkBox;

        public ItemViewHolder(View itemView) {
            textView = (TextView) itemView.findViewById(R.id.textView);
            checkBox = (CheckBox) itemView.findViewById(R.id.checkBox);
        }
    }

    public void refresh(List<ReminderItem> items) {
        mItems.clear();
        mItems.addAll(items);

        notifyDataSetChanged();
    }

}
