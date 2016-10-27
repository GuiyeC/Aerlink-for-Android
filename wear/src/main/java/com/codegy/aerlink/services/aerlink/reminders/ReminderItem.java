package com.codegy.aerlink.services.aerlink.reminders;

/**
 * Created by Guiye on 20/5/15.
 */
public class ReminderItem {

    private String title;
    private String identifier;
    private boolean completed;
    private int position;

    public ReminderItem(int completed, String title, String identifier) {
        this.completed = completed == 1;
        this.title = title;
        this.identifier = identifier;
    }

    public String getTitle() {
        return title;
    }

    public String getIdentifier() {
        return identifier;
    }

    public boolean isCompleted() {
        return completed;
    }

    public int getPosition() {
        return position;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

}
