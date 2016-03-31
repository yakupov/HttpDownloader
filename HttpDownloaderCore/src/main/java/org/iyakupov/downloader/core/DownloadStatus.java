package org.iyakupov.downloader.core;

/**
 * Created by Ilia on 26.03.2016.
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
