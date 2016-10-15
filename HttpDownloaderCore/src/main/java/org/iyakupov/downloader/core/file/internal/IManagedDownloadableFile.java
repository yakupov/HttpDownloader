package org.iyakupov.downloader.core.file.internal;

import org.iyakupov.downloader.core.file.IDownloadableFile;
import org.iyakupov.downloader.core.file.IDownloadableFilePart;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * This is the internal interface of a file download request.
 * The methods of this interface may only be called by the Dispatcher
 * or by one of the Communication Algorithms.
 */
public interface IManagedDownloadableFile extends IDownloadableFile {
    /**
     * Register downloadable piece of this file
     *
     * @param part downloadable part
     */
    void addPart(@NotNull IManagedDownloadableFilePart part);

    /**
     * Sets status = DONE, if the current status is UNSAVED.
     */
    void markAsSaved();

    /**
     * Sets status = FAILED
     */
    void errorHappened();

    /**
     * This method should be called upon successful completion of download of a part of this file.
     * Two calls of this method may not return the same value.
     *
     * @return Count of the parts of the file that are not downloaded yet, or have statuses of FAILED or CANCELLED.
     */
    int decrementAndGetNonSuccessfullyDownloadedPartsCount();

    /**
     * @return Ordered list of downloadable pieces of this file.
     */
    @NotNull
    @Override
    Collection<IManagedDownloadableFilePart> getDownloadableParts();
}
