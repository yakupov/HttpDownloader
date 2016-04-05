package org.iyakupov.downloader.core.dispatch;

/**
 * Possible priorities of tasks for the Dispatcher
 */
public enum TaskPriority {
    NEW_CHECK(0),
    NEW_PART_DOWNLOAD(10),
    PAUSED_TASK(20),
    EVICTED_TASK(100);

    final int priority;

    TaskPriority(int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }
}
