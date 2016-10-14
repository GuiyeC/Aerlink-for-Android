package com.codegy.aerlink.connection.command;

import android.util.Log;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * FIFO command queue, handles waiting for new command and retrying
 */

public class CommandQueueThread extends Thread {

    private static final String LOG_TAG = CommandQueueThread.class.getSimpleName();

    private static final int RETRY_TIME = 1600;

    private CommandHandler handler;
    private Command currentCommand;
    private BlockingQueue<Command> queue = new LinkedBlockingDeque<>();

    private boolean run = true;
    private boolean ready = false;

    private final Object lock = new Object();


    /**
     * Creates a new CommandQueueThread with a command handler
     * @param handler command handler
     */
    public CommandQueueThread(CommandHandler handler) {
        this.handler = handler;
    }


    @Override
    public void run() {
        while (run) {
            try {
                waitAndSendNextCommand();
            }
            catch (InterruptedException ignored) {}
        }
    }

    /**
     * Kill the thread, discards all commands in queue
     */
    public void kill() {
        run = false;

        // Interrupt in case the thread is waiting for a command
        interrupt();
    }

    private void waitAndSendNextCommand() throws InterruptedException {
        synchronized(lock) {
            while (!ready) {
                Log.d(LOG_TAG, "Waiting for ready...");
                lock.wait();
            }

            Log.d(LOG_TAG, "Waiting for command...");
            // If currentCommand is not null, it means it has failed
            // try again with the same command unless it has tried to many times
            if (currentCommand == null || !currentCommand.shouldRetryAgain()) {
                currentCommand = queue.take();
            }

            Log.d(LOG_TAG, "Handling command...");
            handler.handleCommand(currentCommand);

            lock.wait(RETRY_TIME);
        }
    }

    /**
     * Updates the ready status, pausing or starting the thread
     * @param ready new ready value
     */
    public void setReady(boolean ready) {
        if (this.ready == ready) {
            return;
        }

        this.ready = ready;

        // Interrupt in case the thread is waiting for a command
        interrupt();

        Log.d(LOG_TAG, "Ready: " + ready);
    }

    /**
     * Add command to queue
     * @param command command to be added
     */
    public void put(Command command) {
        queue.offer(command);

        Log.d(LOG_TAG, "Command added: " + command.isWriteCommand());
    }

    /**
     * Clear all commands in queue
     */
    public void clear() {
        queue.clear();

        Log.d(LOG_TAG, "Commands cleared");
    }

    /**
     * Remove current command and keep going with the next one
     */
    public void remove() {
        synchronized(lock) {
            Log.d(LOG_TAG, "Command sent");

            currentCommand = null;

            lock.notify();
        }
    }

    /**
     * Move current command to back and keep going with the next one
     */
    public void moveToBack() {
        synchronized(lock) {
            if (currentCommand != null && currentCommand.shouldRetryAgain()) {
                queue.offer(currentCommand);
            }
            currentCommand = null;

            Log.d(LOG_TAG, "Command moved to back");
        }
    }

}
