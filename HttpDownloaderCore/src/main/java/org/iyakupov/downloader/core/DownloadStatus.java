package org.iyakupov.downloader.core;

/**
 * Statuses of a file (part) download request.
 */
public enum DownloadStatus {
    INITIATED,
    PENDING,
    DOWNLOADING,
    SUSPENDED,
    PAUSED,
    ERROR,
    CANCELLED,
    UNSAVED,
    DONE
}
