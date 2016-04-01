package org.iyakupov.downloader.core.file.internal.impl;

import org.iyakupov.downloader.core.DownloadStatus;
import org.iyakupov.downloader.core.file.internal.IDownloadableFilePartInt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static org.iyakupov.downloader.core.DownloadStatus.*;

/**
 * Continuous chunk of a file that is downloaded and stored in a temporary file.
 */
public class DownloadableFilePart implements IDownloadableFilePartInt {
    private final File outputFile;

    private final String locator;

    private final long start;
    private long length;

    private volatile DownloadStatus status = PENDING;
    private volatile int downloadSpeed = 0;
    private volatile long downloadedBytesCount = 0;
    private volatile String errorText = null;

    public DownloadableFilePart(File outputFile, String locator, long start, long length) {
        this.outputFile = outputFile;
        this.locator = locator;
        this.start = start;
        this.length = length;
    }

    @Override
    public int getDownloadSpeed() {
        return downloadSpeed;
    }

    @Override
    public void setDownloadSpeed(int speed) {
        this.downloadSpeed = speed;
    }

    @NotNull
    @Override
    public DownloadStatus getStatus() {
        return status;
    }

    @Override
    public double getProgress() {
        if (length <= 0) {
            return 0;
        } else {
            return (double) downloadedBytesCount / length;
        }
    }

    @Override
    public void pause() {
        if (status != ERROR && status != DONE) {
            status = PAUSED;
        }
    }

    @Override
    public void suspend() {
        if (status != ERROR && status != DONE) {
            status = SUSPENDED;
        }
    }

    @Override
    public void start() {
        if (status != ERROR && status != DONE) {
            status = DOWNLOADING;
        }
    }

    @Override
    public void cancel() {
        status = CANCELLED;
    }

    @Override
    public void completeSuccessfully() {
        if (status != ERROR)
            status = DONE;
    }

    @Override
    public void completeWithError(@NotNull String errorMessage) {
        status = ERROR;
        this.errorText = errorMessage;
    }

    @Nullable
    @Override
    public String getErrorText() {
        return errorText;
    }

    @NotNull
    @Override
    public File getOutputFile() {
        return outputFile;
    }

    @NotNull
    @Override
    public String getLocator() {
        return locator;
    }

    @Override
    public synchronized long getCurrentStartPosition() {
        return start + downloadedBytesCount;
    }

    @Override
    public synchronized long getRemainingLength() {
        if (length <= 0)
            return -1;
        else
            return length - downloadedBytesCount;
    }

    @Override
    public synchronized void incrementDownloadedBytesCount(long diff) {
        downloadedBytesCount += diff;
    }

    @Override
    public void updateTotalLength(long length) {
        if (this.length <= 0)
            this.length = length;
    }
}
