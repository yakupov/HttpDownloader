package org.iyakupov.downloader.core.dispatch;

import org.iyakupov.downloader.core.file.IDownloadableFile;
import org.iyakupov.downloader.core.file.internal.IDownloadableFileInt;
import org.iyakupov.downloader.core.file.internal.IDownloadableFilePartInt;

import java.io.File;
import java.util.Collection;

/**
 * Dispatcher that tracks, manages and executes file download requests and part download requests
 */
public interface IDispatchingQueue {
    /**
     * Get the parent file for this downloadable part
     *
     * @param part Download task
     * @return Parent file
     */
    IDownloadableFileInt getParentFile(IDownloadableFilePartInt part);

    /**
     * Submit file part download resume with high priority
     *
     * @param part Resumed download task
     */
    void submitEvictedTask(IDownloadableFilePartInt part);

    /**
     * Submit file part download with normal priority
     *
     * @param file Parent file for this downloadable part
     * @param part Downloadable part (part download task)
     */
    void submitNewTask(IDownloadableFileInt file, IDownloadableFilePartInt part);

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
