package org.iyakupov.downloader.core.file.state;

/**
 * Statuses of a file download request.
 */
public enum FileDownloadState {
    //TODO: javadocs
    INITIATED,
    PENDING,
    DOWNLOADING,
    PAUSED,
    FAILED,
    CANCELLED,
    UNSAVED,
    DONE
}
