package org.iyakupov.downloader.core.file.internal;

import org.iyakupov.downloader.core.file.IDownloadableFilePart;
import org.jetbrains.annotations.NotNull;

/**
 * Internal interface of a downloadable file part.
 * It's methods should be called from the Dispatcher or from a Communication Algorithm.
 */
public interface IDownloadableFilePartInt extends IDownloadableFilePart {
    /**
     * Set actual download speed of this chunk
     *
     * @param speed Download speed
     */
    void setDownloadSpeed(int speed);

    /**
     * Set the status of DONE to this download request
     */
    void completeSuccessfully();

    /**
     * Set the status ERROR and store the error message
     *
     * @param errorText Error message
     */
    void completeWithError(@NotNull String errorText);

    /**
     * @return The position (number of byte) to resume (or start) the download from.
     */
    long getCurrentStartPosition();

    /**
     * @return -1 if the length is unknown, remaining number of bytes to download otherwise.
     */
    long getRemainingLength();

    /**
     * Increment the internal counter of downloaded bytes.
     * This counter is used for calculation of the current start position.
     *
     * @param diff Incrementer
     */
    void incrementDownloadedBytesCount(long diff);

    /**
     * Sets total length of this chunk if it's unknown yet (e.g. was not returned in the initial HEAD request).
     * If total length is already known (i.e. positive), this method won't do anything.
     *
     * @param length Actual length
     */
    void updateTotalLength(long length);
}
