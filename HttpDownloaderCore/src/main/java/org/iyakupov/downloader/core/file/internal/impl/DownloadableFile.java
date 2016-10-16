package org.iyakupov.downloader.core.file.internal.impl;

import org.apache.commons.io.FilenameUtils;
import org.iyakupov.downloader.core.file.IDownloadableFilePart;
import org.iyakupov.downloader.core.file.internal.IManagedDownloadableFile;
import org.iyakupov.downloader.core.file.internal.IManagedDownloadableFilePart;
import org.iyakupov.downloader.core.file.state.FileDownloadState;
import org.iyakupov.downloader.core.file.state.FilePartDownloadState;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.iyakupov.downloader.core.file.state.FileDownloadState.DONE;
import static org.iyakupov.downloader.core.file.state.FileDownloadState.FAILED;

/**
 * File download request default implementation
 */
public class DownloadableFile implements IManagedDownloadableFile {
    private final String locator;
    private final File outputFile;
    private final int maxThreadCount;

    private final List<IManagedDownloadableFilePart> fileParts = new ArrayList<>();
    private final AtomicInteger unsavedPartsCount = new AtomicInteger(0);

    private volatile boolean fileSaved = false;
    private volatile boolean errorHappened = false;

    /**
     * @param locator        Pointer to a remote file. URL, for example
     * @param outputDir      Path to the directory where the file should be stored
     * @param maxThreadCount In how many pieces should the file be cut
     */
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
    public FileDownloadState getStatus() {
        if (fileSaved) {
            return DONE;
        } else if (errorHappened) {
            return FAILED;
        } else {
            return getDownloadableParts().stream()
                    .map(IDownloadableFilePart::getStatus)
                    .distinct()
                    .reduce(FileDownloadState.INITIATED,
                            (fileDownloadState, filePartDownloadState) -> {
                                switch (filePartDownloadState) {
                                    case CANCELLED:
                                        return fileDownloadState.onCancelled();
                                    case DONE:
                                        return fileDownloadState.onCompleted();
                                    case DOWNLOADING:
                                        return fileDownloadState.onDownloading();
                                    case FAILED:
                                        return fileDownloadState.onError();
                                    case PAUSE_REQUESTED:
                                        return fileDownloadState.onPauseRequested();
                                    case PAUSED:
                                        return fileDownloadState.onPaused();
                                    case PENDING:
                                        return fileDownloadState.onPending();
                                    case SUSPEND_REQUESTED:
                                        return fileDownloadState.onSuspendRequested();
                                    default:
                                        return fileDownloadState;
                                }
                            },
                            (s1, s2) -> s1.getPriority() > s2.getPriority() ? s1 : s2);
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
        return fileParts.stream()
                .map(IDownloadableFilePart::pause)
                .reduce((b1, b2) -> b1 & b2)
                .orElse(false);
    }

    @Override
    public synchronized boolean cancel() {
        return fileParts.stream()
                .map(IDownloadableFilePart::cancel)
                .reduce((b1, b2) -> b1 & b2)
                .orElse(false);
    }

    /**
     * @return A copied list of this file's parts. Thread-safe.
     */
    @NotNull
    @Override
    public synchronized List<IManagedDownloadableFilePart> getDownloadableParts() {
        return new ArrayList<>(fileParts);
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
                ", partsCount=" + fileParts.size() +
                ", status=" + getStatus() +
                '}';
    }
}
