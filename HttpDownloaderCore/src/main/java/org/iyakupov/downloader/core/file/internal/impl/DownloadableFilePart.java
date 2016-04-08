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
    private boolean partialDownloadSupported = true;

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
        if (status != CANCELLED && status != ERROR && status != DONE && status != PAUSE_CONFIRMED) {
            status = PAUSED;
        }
    }

    @Override
    public void suspend() {
        if (status != CANCELLED && status != ERROR && status != DONE) {
            status = SUSPENDED;
        }
    }

    @Override
    public void resumeDownload() {
        if (status != CANCELLED && status != DONE) {
            status = PENDING;
        }
    }

    @Override
    public void confirmPause() {
        if (status == PAUSED)
            status = PAUSE_CONFIRMED;
    }

    @Override
    public void confirmCancel() {
        if (status == CANCELLED)
            status = CANCEL_CONFIRMED;
    }

    @Override
    public void start() {
        if (status != CANCELLED && status != ERROR && status != DONE) {
            if (!partialDownloadSupported) {
                downloadedBytesCount = 0;
            }
            status = DOWNLOADING;
        }
    }

    @Override
    public void cancel() {
        if (status != CANCEL_CONFIRMED)
        status = CANCELLED;
    }

    @Override
    public void completeSuccessfully() {
        if (status != CANCELLED && status != ERROR) {
            status = DONE;
            length = downloadedBytesCount;
        }
    }

    @Override
    public void completeWithError(@NotNull String errorMessage) {
        if (status != CANCELLED) {
            status = ERROR;
            this.errorText = errorMessage;
        }
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
        if (length < 0)
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
        if (this.length < 0)
            this.length = length;
    }

    @Override
    public boolean isDownloadResumeSupported() {
        return partialDownloadSupported;
    }

    @Override
    public void setDownloadResumeNotSupported() {
        partialDownloadSupported = false;
    }
}
