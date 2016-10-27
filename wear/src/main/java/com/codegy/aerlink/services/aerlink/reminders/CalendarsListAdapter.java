package com.codegy.aerlink.services.aerlink.reminders;

import android.content.Context;
import android.graphics.Color;
import android.support.v7.widget.CardView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.codegy.aerlink.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Guiye on 19/10/16.
 */

public class CalendarsListAdapter extends BaseAdapter {

    private static final int layoutResourceId = R.layout.list_item_calendar;

    private List<ReminderCalendar> mCalendars;
    private final LayoutInflater mInflater;
    private boolean ambient;

    // Provide a suitable constructor (depends on the kind of dataset)
    public CalendarsListAdapter(Context context, List<ReminderCalendar> items) {
        mInflater = LayoutInflater.from(context);

        if (items != null) {
            mCalendars = items;
        }
        else {
            mCalendars = new ArrayList<>();
        }
    }


    public void setAmbient(boolean ambient) {
        this.ambient = ambient;

        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mCalendars.size();
    }

    @Override
    public ReminderCalendar getItem(int position) {
        return mCalendars.get(position);
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
            ReminderCalendar calendar = getItem(position);
            holder.textView.setText(calendar.getTitle());
            if (ambient) {
                holder.textView.setTextColor(Color.LTGRAY);
                holder.cardView.setCardBackgroundColor(Color.rgb(24, 24, 24));
            }
            else {
                holder.textView.setTextColor(Color.WHITE);
                holder.cardView.setCardBackgroundColor(Color.parseColor("#52" + calendar.getColor()));
            }
        }
        catch (IndexOutOfBoundsException ignored) {}

        return convertView;
    }

    // Provide a reference to the type of views you're using
    public static class ItemViewHolder {
        private CardView cardView;
        private TextView textView;

        public ItemViewHolder(View itemView) {
            cardView = (CardView) itemView.findViewById(R.id.cardView);
            textView = (TextView) itemView.findViewById(R.id.textView);
        }
    }

    public void refresh(List<ReminderCalendar> items) {
        mCalendars = items;

        notifyDataSetChanged();
    }
}
