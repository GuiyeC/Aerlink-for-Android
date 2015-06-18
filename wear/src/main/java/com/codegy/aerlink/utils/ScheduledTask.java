package com.codegy.aerlink.utils;

import android.os.Handler;
import android.os.Looper;

/**
 * Created by Guiye on 18/5/15.
 */
public class ScheduledTask {

    private long delay;
    private Handler mHandler;
    private Runnable mRunnable;

    public ScheduledTask(long delay, Looper looper, Runnable runnable) {
        this.delay = delay;
        this.mRunnable = runnable;

        mHandler = new Handler(looper);
    }

    public void schedule() {
        mHandler.postDelayed(mRunnable, delay);
        /*
        Thread thread = new Thread() {
            public void run() {
                mHandler.postDelayed(mRunnable, mDelay);
            }
        };
        thread.start();
        */
    }

    public void cancel() {
        mHandler.removeCallbacksAndMessages(null);
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

}
