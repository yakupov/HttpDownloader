package org.iyakupov.downloader.core.dispatch;

import org.iyakupov.downloader.core.file.IDownloadableFile;
import org.iyakupov.downloader.core.file.internal.IManagedDownloadableFile;
import org.iyakupov.downloader.core.file.internal.IManagedDownloadableFilePart;

import java.io.Closeable;
import java.io.File;
import java.util.Collection;

/**
 * Dispatcher that tracks, manages and executes file download requests and part download requests
 */
public interface IDispatchingQueue extends Closeable {
    /**
     * Submit file part download resume with high numericValue
     *
     * @param file Parent file for this downloadable part
     * @param part Resumed download task
     */
    void reSubmitEvictedTask(IManagedDownloadableFile file, IManagedDownloadableFilePart part);

    /**
     * Submit file part download with normal numericValue
     *
     * @param file Parent file for this downloadable part
     * @param part Downloadable part (part download task)
     */
    void submitNewTask(IManagedDownloadableFile file, IManagedDownloadableFilePart part);

    /**
     * Sets the maximal number of download worker threads
     *
     * @param newSize           Number of threads. Must be positive.
     * @param evictNonResumable Whether tasks where server does not support partial download may be evicted.
     *                          If set to false, the total number of threads may stay greater than newSize.
     */
    void setThreadPoolSize(int newSize, boolean evictNonResumable);

    /**
     * Create a new file download request
     *
     * @param url       Location of this file in the remote location
     * @param outputDir Output file
     * @param nThreads  Number of pieces to cut this file into in order to perform parallel download
     * @return File download request
     */
    IDownloadableFile submitFile(String url, File outputDir, int nThreads);

    /**
     * @return The collection of file download requests that this Dispatcher knows about.
     */
    Collection<IDownloadableFile> getAllFiles();

    /**
     * Cancel file download and remove file download request from Dispatcher's data structures.
     *
     * @param file File download request
     * @return false if the Dispatcher did not knew about this file, true otherwise.
     */
    boolean forgetFile(IDownloadableFile file);

    /**
     * Resume the download of a paused file.
     *
     * @param file File download request
     * @return Whether the Dispatcher has managed to resume download
     */
    boolean resumeDownload(IDownloadableFile file);
}
