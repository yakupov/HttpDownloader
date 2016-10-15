package org.iyakupov.downloader.core.file.internal.impl;

import org.iyakupov.downloader.core.file.internal.IManagedDownloadableFilePart;
import org.iyakupov.downloader.core.file.state.FilePartDownloadState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.iyakupov.downloader.core.file.state.FilePartDownloadState.*;

/**
 * Continuous chunk of a file that is downloaded and stored in a temporary file.
 */
public class DownloadableFilePart implements IManagedDownloadableFilePart {
    private final File outputFile;
    private final String locator;
    private final long start;

    //These data elements are OK for publishing via race
    private volatile boolean partialDownloadSupported = true;
    private volatile int downloadSpeed = 0;
    private volatile String errorText = null;

    private AtomicReference<FilePartDownloadState> status = new AtomicReference<>(PENDING);
    private AtomicLong downloadedBytesCount = new AtomicLong(0);
    private AtomicLong length;

    public DownloadableFilePart(File outputFile, String locator, long start, long length) {
        this.outputFile = outputFile;
        this.locator = locator;
        this.start = start;
        this.length = new AtomicLong(length);
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
    public FilePartDownloadState getStatus() {
        return status.get();
    }

    @Override
    public double getProgress() {
        if (length.get() <= 0) {
            return 0;
        } else {
            return (double) downloadedBytesCount.get() / length.get();
        }
    }

    @Override
    public boolean suspend() {
        final FilePartDownloadState currentState = status.get();
        final FilePartDownloadState nextState = currentState.onSuspendRequest();
        return status.compareAndSet(currentState, nextState);
    }

    @Override
    public boolean pause() {
        final FilePartDownloadState currentState = status.get();
        final FilePartDownloadState nextState = currentState.onPauseRequest();
        return status.compareAndSet(currentState, nextState);
    }

    @Override
    public boolean confirmPause() {
        final FilePartDownloadState currentState = status.get();
        final FilePartDownloadState nextState = currentState.onPauseConfirm();
        return status.compareAndSet(currentState, nextState);
    }

    @Override
    public boolean resume() {
        final FilePartDownloadState currentState = status.get();
        final FilePartDownloadState nextState = currentState.onResume();
        return status.compareAndSet(currentState, nextState);
    }

    @Override
    public boolean confirmSuspendAndRestart() {
        final FilePartDownloadState currentState = status.get();
        if (currentState != SUSPEND_REQUESTED)
            return false;
        final FilePartDownloadState nextState = currentState.onPauseConfirm().onResume();
        return status.compareAndSet(currentState, nextState);
    }

    @Override
    public boolean start() {
        final FilePartDownloadState currentState = status.get();
        final FilePartDownloadState nextState = currentState.onStarted();
        return status.compareAndSet(currentState, nextState);
    }

    @Override
    public boolean cancel() {
        final FilePartDownloadState currentState = status.get();
        final FilePartDownloadState nextState = currentState.onCancel();
        return status.compareAndSet(currentState, nextState);
    }

    @Override
    public boolean completeSuccessfully() {
        final FilePartDownloadState currentState = status.get();
        final FilePartDownloadState nextState = currentState.onCompleted();
        return status.compareAndSet(currentState, nextState);
    }

    @Override
    public void completeWithError(@NotNull String errorMessage) {
        final FilePartDownloadState currentState = status.get();
        final FilePartDownloadState nextState = currentState.onError();
        if (status.compareAndSet(currentState, nextState) && currentState != nextState) {
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
    public long getCurrentStartPosition() {
        return start + downloadedBytesCount.get();
    }

    @Override
    public long getRemainingLength() {
        if (length.get() < 0)
            return -1;
        else
            return length.get() - downloadedBytesCount.get();
    }

    @Override
    public void incrementDownloadedBytesCount(long diff) {
        downloadedBytesCount.addAndGet(diff);
    }

    @Override
    public boolean updateTotalLength(long newLength) {
        final long prevLength = length.get();
        return prevLength < 0 && length.compareAndSet(prevLength, newLength);
    }

    @Override
    public boolean isDownloadResumeSupported() {
        return partialDownloadSupported;
    }

    @Override
    public void setDownloadResumeNotSupported() {
        partialDownloadSupported = false;
    }

    @Override
    public String toString() {
        return "DownloadableFilePart{" +
                "outputFile=" + outputFile +
                ", locator='" + locator + '\'' +
                ", start=" + start +
                ", partialDownloadSupported=" + partialDownloadSupported +
                ", errorText='" + errorText + '\'' +
                ", status=" + status +
                '}';
    }
}
