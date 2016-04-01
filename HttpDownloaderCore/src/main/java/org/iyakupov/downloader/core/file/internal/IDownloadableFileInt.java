package org.iyakupov.downloader.core.file.internal;

import org.iyakupov.downloader.core.file.IDownloadableFile;
import org.jetbrains.annotations.NotNull;

/**
 * This is the internal interface of a file download request.
 * The methods of this interface may only be called by the Dispatcher
 * or by one of the Communication Algorithms.
 */
public interface IDownloadableFileInt extends IDownloadableFile {
    /**
     * Register downloadable piece of this file
     *
     * @param part downloadable part
     */
    void addPart(@NotNull IDownloadableFilePartInt part);

    /**
     * Sets status = DONE, if the current status is UNSAVED.
     */
    void markAsSaved();

    /**
     * This method should be called upon successful completion of download of a part of this file.
     * Two calls of this method may not return the same value.
     *
     * @return Count of the parts of the file that are not downloaded yet, or have statuses of ERROR or CANCELLED.
     */
    int decrementAndGetNonSuccessfullyDownloadedPartsCount();
}
