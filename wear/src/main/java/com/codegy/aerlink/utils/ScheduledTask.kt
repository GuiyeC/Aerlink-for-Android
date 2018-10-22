package com.codegy.aerlink.utils

import android.os.Handler
import android.os.Looper

class ScheduledTask(looper: Looper) {
    private val handler: Handler = Handler(looper)

    fun schedule(delay: Long, runnable: () -> Unit) {
        cancel()
        handler.postDelayed(runnable, delay)
    }

    fun cancel() {
        handler.removeCallbacksAndMessages(null)
    }

}
