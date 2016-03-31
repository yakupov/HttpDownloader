package org.iyakupov.downloader.core.file.impl;

import org.apache.commons.io.FilenameUtils;
import org.iyakupov.downloader.core.DownloadStatus;
import org.iyakupov.downloader.core.file.IDownloadableFile;
import org.iyakupov.downloader.core.file.IDownloadableFilePart;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.iyakupov.downloader.core.DownloadStatus.*;

/**
 * Created by Ilia on 26.03.2016.
 */
public class DownloadableFile implements IDownloadableFile {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final URL url;
    private final File outputFile;
    private final int maxThreadCount;
    private volatile boolean fileSaved = false;

    private final List<IDownloadableFilePart> fileParts = new ArrayList<>();

    public DownloadableFile(URL url, File outputDir, int maxThreadCount) {
        this.url = url;
        if (!outputDir.isDirectory()) {
            throw new IllegalArgumentException("The given path is not a directory: " + outputDir);
        }
        final String fileName = FilenameUtils.getName(url.toString());
        if (fileName == null) {
            throw new IllegalArgumentException("The given URL has null file name: " + url);
        }
        this.outputFile = new File(outputDir, fileName);
        this.maxThreadCount = maxThreadCount;
    }

    @Override
    public int getDownloadSpeed() {
        return getDownloadableParts().stream().mapToInt(IDownloadableFilePart::getDownloadSpeed).sum();
    }

    @NotNull
    @Override
    //TODO: maybe this method is not needed? Or optimize...
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
    public synchronized boolean saveToDisk() throws IOException {
        if (fileSaved)
            return false;

        if (getDownloadableParts().size() == 1) {
            fileSaved = true;
            return true; // The results are already in the correct file
        }

        try (OutputStream outputFileStream = new FileOutputStream(outputFile)) {
            for (IDownloadableFilePart part : getDownloadableParts()) {
                logger.trace("Copy data from " + part.getOutputFile() + " to " + getOutputFile());

                //TODO: Create a new file if exists, maybe
                Files.copy(part.getOutputFile().toPath(), outputFileStream);
                Files.delete(part.getOutputFile().toPath());
            }
            fileSaved = true;
            return true;
        }
    }

    @NotNull
    @Override
    public File getOutputFile() {
        return outputFile;
    }

    @Override
    public String getLocator() {
        return url.toString();
    }

    @Override
    public synchronized void addPart(IDownloadableFilePart part) {
        fileParts.add(part);
    }
}
