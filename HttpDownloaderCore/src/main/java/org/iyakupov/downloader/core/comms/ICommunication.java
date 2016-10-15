package org.iyakupov.downloader.core.comms;

/**
 * An algorithm of communication with an external system. Basically, this interface represents a worker.
 *
 * By "communication" we mean "transfer of information from a source to a target".
 * Transfer of data from a remote HTTP Server to a local HDD is a good example of
 * a "communication".
 */
public interface ICommunication extends Runnable {
    /**
     * @return The numeric value of this communications priority.
     * The higher value means the higher priority of a task.
     */
    int getPriority();
}
