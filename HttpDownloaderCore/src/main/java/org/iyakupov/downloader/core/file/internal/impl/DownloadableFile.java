package org.iyakupov.downloader.core.file.internal.impl;

import org.apache.commons.io.FilenameUtils;
import org.iyakupov.downloader.core.DownloadStatus;
import org.iyakupov.downloader.core.file.internal.IDownloadableFileInt;
import org.iyakupov.downloader.core.file.IDownloadableFilePart;
import org.iyakupov.downloader.core.file.internal.IDownloadableFilePartInt;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.iyakupov.downloader.core.DownloadStatus.*;

/**
 * File download request default implementation
 */
public class DownloadableFile implements IDownloadableFileInt {
    private final String locator;
    private final File outputFile;
    private final int maxThreadCount;

    private volatile boolean fileSaved = false;
    private final AtomicInteger unsavedPartsCount = new AtomicInteger(0);

    private final List<IDownloadableFilePart> fileParts = new ArrayList<>();

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
    public DownloadStatus getStatus() {
        if (fileSaved) {
            return DONE;
        } else if (getDownloadableParts().isEmpty()) {
            return INITIATED;
        } else {
            boolean isDownloading = false;
            boolean isSuspended = false;
            boolean isPaused = false;
            boolean isDone = true;
            for (IDownloadableFilePart part: getDownloadableParts()) {
                //logger.trace("Part " + part.getOutputFile() + " status " + part.getStatus());
                final DownloadStatus status = part.getStatus();
                if (status != DONE) {
                    isDone = false;
                    if (status == ERROR || status == CANCELLED) {
                        return status;
                    } else if (status == DOWNLOADING) {
                        isDownloading = true;
                    } else if (status == SUSPENDED) {
                        isSuspended = true;
                    } else if (status == PAUSED) {
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
            else if (isSuspended)
                return SUSPENDED;
            else
                return PENDING;
        }
    }

    @Override
    public int getActiveThreadCount() {
        return (int) getDownloadableParts().stream().filter(p -> p.getStatus() == DOWNLOADING).count();
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
    public synchronized void pause() {
        getDownloadableParts().stream().forEach(IDownloadableFilePart::pause);
    }

    @Override
    public synchronized void cancel() {
        getDownloadableParts().stream().forEach(IDownloadableFilePart::cancel);
    }

    @NotNull
    @Override
    public List<IDownloadableFilePart> getDownloadableParts() {
        return Collections.unmodifiableList(fileParts);
    }

    @Override
    public synchronized void addPart(@NotNull IDownloadableFilePartInt part) {
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
}
