package org.iyakupov.downloader.core.file;

import org.iyakupov.downloader.core.DownloadStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Contiguous chunk of a file that is downloaded and stored in a temp file.
 */
public interface IDownloadableFilePart {
    /**
     * @return speed in bytes/s
     */
    int getDownloadSpeed();

    void setDownloadSpeed(int speed);

    @NotNull
    DownloadStatus getStatus();

    /**
     * @return Percentage of the file that was already downloaded, possible values: [0, 1]
     */
    double getProgress();

    /**
     * Manually pause this download.
     */
    void pause();

    /**
     * Automatically suspend task because of the shortage of available threads
     */
    void suspend();

    void start();

    void cancel();

    /**
     * Set status DONE
     */
    void completeSuccessfully();

    /**
     * Set status ERROR and store the error message
     *
     * @param errorText Error message
     */
    void completeWithError(@NotNull String errorText);

    @Nullable
    String getErrorText();

    /**
     * @return {@link File} to the temporary file that stores this part
     */
    @NotNull
    File getOutputFile();

    /**
     * @return Reference (e. g. URL) to the external resource that should be downloaded.
     */
    @NotNull
    String getLocator();

    long getCurrentStartPosition();

    /**
     * @return -1 if the length is unknown, remaining number of bytes to download otherwise.
     */
    long getRemainingLength();

    void incrementDownloadedBytesCount(long diff);

    /**
     * Sets total length of this chunk if it's unknown yet (e.g. was not returned in the initial HEAD request).
     * If total length is already known (i.e. positive), this method won't do anything.
     *
     * @param length Actual length
     */
    void updateTotalLength(long length);
}
