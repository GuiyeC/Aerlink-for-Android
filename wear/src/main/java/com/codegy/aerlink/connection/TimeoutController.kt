package com.codegy.aerlink.connection

class TimeOutController {
    private val lock = Object()
    private var success = false

    fun wait(timeout: Long, timeoutBlock: () -> Unit) {
        success = false
        lock.wait(timeout)
        if (!success) {
            timeoutBlock()
        }
    }

    fun cancel() {
        success = true
        lock.notify()
    }

}