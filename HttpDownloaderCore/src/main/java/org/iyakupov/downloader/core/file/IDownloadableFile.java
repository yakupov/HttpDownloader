package org.iyakupov.downloader.core.file;

import org.iyakupov.downloader.core.file.state.FileDownloadState;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;

/**
 * This interface represents a file download request.
 * It is designed to control this request and monitor its status.
 */
public interface IDownloadableFile {
    /**
     * @return speed in bytes/s
     */
    int getDownloadSpeed();

    @NotNull
    FileDownloadState getStatus();

    /**
     * @return Number of threads that download parts of this file
     */
    int getActiveThreadCount();

    /**
     * @return Number of pieces to cut this file into in order to perform parallel download
     */
    int getMaxThreadCount();

    /**
     * @return Percentage of the file that was already downloaded, possible values: [0, 1]
     */
    double getProgress();

    /**
     * Sets statuses of all parts of this file to SUSPENDED.
     * This will cause download of all parts of this file to be suspended.
     *
     * @return Whether the status was changed. If not - possibly because the status was changed by another thread.
     */
    boolean pause();

    /**
     * Sets statuses of all parts of this file to CANCELLED.
     * This will cause download of all parts of this file to be cancelled.
     *
     * @return Whether the status was changed. If not - possibly because the status was changed by another thread.
     */
    boolean cancel();

    /**
     * @return Ordered list of downloadable pieces of this file.
     */
    @NotNull
    Collection<? extends IDownloadableFilePart> getDownloadableParts();

     /**
     * @return {@link File} that points to the point where this file should be stored.
     */
    @NotNull
    File getOutputFile();

    /**
     * @return Locator that points to this file on a remote server (e.g. URL)
     */
    @NotNull
    String getLocator();
}
