package org.iyakupov.downloader.core.file.internal;

import org.iyakupov.downloader.core.file.IDownloadableFilePart;
import org.iyakupov.downloader.core.file.state.FilePartLengthState;
import org.jetbrains.annotations.NotNull;

/**
 * Internal interface of a downloadable file part.
 * It's methods should be called from the Dispatcher or from a Communication Algorithm.
 */
public interface IManagedDownloadableFilePart extends IDownloadableFilePart {
    /**
     * Set actual download speed of this chunk
     *
     * @param speed Download speed
     */
    void setDownloadSpeed(int speed);

    /**
     * Set the status of DONE to this download request
     *
     * @return Whether the status was changed. If not - possibly because the status was changed by another thread.
     */
    boolean completeSuccessfully();

    /**
     * Set the status FAILED and store the error message
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
     * @return Whether we know the remaining length of the file.
     */
    FilePartLengthState getLengthState();

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
     * Contract: the first call to this method returns true if the length was not defined upon
     * construction of this file part. {@link IManagedDownloadableFilePart#getLengthState}
     * may not return {@link FilePartLengthState#YET_UNKNOWN} for this part after this call.
     *
     * @param length Actual length
     * @return Whether the length was updated
     */
    boolean updateTotalLength(long length);

    /**
     * @return Whether the server supports partial downloads
     */
    boolean isDownloadResumeSupported();

    /**
     * Set the flag, indicating that the server does not support partial download
     */
    void setDownloadResumeNotSupported();

    /**
     * Make the task that was requested to be suspended ready to proceed with download.
     *
     * @return Whether the restart was successful
     */
    boolean confirmSuspendAndRestart();

    /**
     * Forcefully pause the download.
     *
     * @return Whether the status was changed. If not - possibly because the status was changed by another thread.
     */
    boolean suspend();

    /**
     * Make this task ready for download (set the status of PENDING)
     *
     * @return Whether the status was changed. If not - possibly because the status was changed by another thread.
     */
    boolean resume();

    /**
     * Confirm that the download was halted for this task, if the pause was previously requested
     *
     * @return Whether the status was changed. If not - possibly because the status was changed by another thread.
     */
    boolean confirmPause();
}
