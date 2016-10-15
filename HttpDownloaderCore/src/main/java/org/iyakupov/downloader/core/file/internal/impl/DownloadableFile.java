package org.iyakupov.downloader.core.file.internal.impl;

import com.google.common.collect.Sets;
import org.apache.commons.io.FilenameUtils;
import org.iyakupov.downloader.core.file.state.FileDownloadState;
import org.iyakupov.downloader.core.file.internal.IManagedDownloadableFile;
import org.iyakupov.downloader.core.file.IDownloadableFilePart;
import org.iyakupov.downloader.core.file.internal.IManagedDownloadableFilePart;
import org.iyakupov.downloader.core.file.state.FilePartDownloadState;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.iyakupov.downloader.core.file.state.FileDownloadState.*;

/**
 * File download request default implementation
 */
public class DownloadableFile implements IManagedDownloadableFile {
    private final String locator;
    private final File outputFile;
    private final int maxThreadCount;

    private final List<IManagedDownloadableFilePart> fileParts = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger unsavedPartsCount = new AtomicInteger(0);

    private volatile boolean fileSaved = false;
    private volatile boolean errorHappened = false;

    public DownloadableFile(@NotNull String locator, @NotNull File outputDir, int maxThreadCount) {
        this.locator = locator;
        if (!outputDir.isDirectory()) {
            throw new IllegalArgumentException("The given path is not a directory: " + outputDir);
        }
        final String fileName = FilenameUtils.getName(locator);
        this.outputFile = new File(outputDir, fileName);
        this.maxThreadCount = maxThreadCount;
    }

    @Override
    public int getDownloadSpeed() {
        return getDownloadableParts().stream().mapToInt(IDownloadableFilePart::getDownloadSpeed).sum();
    }

    @NotNull
    @Override
    public synchronized FileDownloadState getStatus() {
        if (fileSaved) {
            return DONE;
        } else if (errorHappened) {
            return FAILED;
        } else if (getDownloadableParts().isEmpty()) {
            return INITIATED;
        } else {
            boolean isDownloading = false;
            boolean isPaused = false; //TODO: intermediate status "pausing". And check statuses in general
            boolean isDone = true;
            for (IDownloadableFilePart part: getDownloadableParts()) {
                final FilePartDownloadState status = part.getStatus();
                if (status != FilePartDownloadState.DONE) {
                    isDone = false;
                    if (status == FilePartDownloadState.FAILED) {
                        return FAILED;
                    } else if (status == FilePartDownloadState.CANCELLED) {
                        return CANCELLED;
                    } else if (status == FilePartDownloadState.DOWNLOADING) {
                        isDownloading = true;
                    } else if (status == FilePartDownloadState.PAUSE_REQUESTED || status == FilePartDownloadState.PAUSED) {
                        isPaused = true;
                    }
                }
            }
            if (isDone)
                return UNSAVED;
            if (isDownloading)
                return DOWNLOADING;
            else if (isPaused)
                return PAUSED;
            else
                return PENDING;
        }
    }

    @Override
    public int getActiveThreadCount() {
        return (int) getDownloadableParts().stream()
                .map(IDownloadableFilePart::getStatus)
                .filter(FilePartDownloadState.DOWNLOADING::equals)
                .count();
    }

    @Override
    public int getMaxThreadCount() {
        return maxThreadCount;
    }

    @Override
    public double getProgress() {
        return getDownloadableParts().stream()
                .mapToDouble(IDownloadableFilePart::getProgress)
                .average()
                .orElse(0);
    }

    @Override
    public synchronized boolean pause() {
        return getDownloadableParts().stream()
                .map(IDownloadableFilePart::pause)
                .reduce((b1, b2) -> b1 & b2)
                .orElse(false);
    }

    @Override
    public synchronized boolean cancel() {
        return getDownloadableParts().stream()
                .map(IDownloadableFilePart::cancel)
                .reduce((b1, b2) -> b1 & b2)
                .orElse(false);
    }

    @NotNull
    @Override
    public Collection<IManagedDownloadableFilePart> getDownloadableParts() {
        return Collections.unmodifiableList(fileParts); //FIXME: ugly construction. Ordering is not obvious
    }

    @Override
    public synchronized void addPart(@NotNull IManagedDownloadableFilePart part) {
        fileParts.add(part);
        unsavedPartsCount.incrementAndGet();
    }

    @Override
    public void markAsSaved() {
        if (unsavedPartsCount.get() == 0) {
            fileSaved = true;
        } else {
            throw new IllegalStateException("Tried to mark file as saved, although there are " +
                    unsavedPartsCount.get() + " parts that are not (yet) successfully downloaded");
        }
    }

    @Override
    public void errorHappened() {
        errorHappened = true;
    }

    @Override
    public int decrementAndGetNonSuccessfullyDownloadedPartsCount() {
        return unsavedPartsCount.decrementAndGet();
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final DownloadableFile that = (DownloadableFile) o;
        return locator.equals(that.locator);

    }

    @Override
    public int hashCode() {
        return locator.hashCode();
    }

    @Override
    public String toString() {
        return "DownloadableFile{" +
                "locator='" + locator + '\'' +
                ", outputFile=" + outputFile +
                ", maxThreadCount=" + maxThreadCount +
                ", fileSaved=" + fileSaved +
                ", errorHappened=" + errorHappened +
                ", partsCount=" + getDownloadableParts().size() +
                ", status=" + getStatus() +
                '}';
    }
}
