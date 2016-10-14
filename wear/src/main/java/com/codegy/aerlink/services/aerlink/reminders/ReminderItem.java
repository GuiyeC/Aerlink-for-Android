package com.codegy.aerlink.services.aerlink.reminders;

/**
 * Created by Guiye on 20/5/15.
 */
public class ReminderItem {

    private String title;
    private boolean completed;
    private int position;

    public ReminderItem(String title, int position) {
        this.completed = title.charAt(0) == 'X';
        this.title = title.substring(1);
        this.position = position;
    }

    public String getTitle() {
        return title;
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
