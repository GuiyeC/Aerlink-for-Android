package com.codegy.aerlink.services.aerlink.reminders;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by Guiye on 19/10/16.
 */

public class ReminderCalendar implements Parcelable {

    private String title;
    private String identifier;
    private String color;

    public ReminderCalendar(String title, String identifier, String color) {
        this.title = title;
        this.identifier = identifier;
        this.color = color;
    }

    public String getTitle() {
        return title;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getColor() {
        return color;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(title);
        out.writeString(identifier);
        out.writeString(color);
    }

    public static final Parcelable.Creator<ReminderCalendar> CREATOR = new Parcelable.Creator<ReminderCalendar>() {
        public ReminderCalendar createFromParcel(Parcel in) {
            return new ReminderCalendar(in);
        }

        public ReminderCalendar[] newArray(int size) {
            return new ReminderCalendar[size];
        }
    };

    private ReminderCalendar(Parcel in) {
        title = in.readString();
        identifier = in.readString();
        color = in.readString();
    }

}
