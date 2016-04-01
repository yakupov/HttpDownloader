package org.iyakupov.downloader.core.dispatch;

import org.iyakupov.downloader.core.file.IDownloadableFile;
import org.iyakupov.downloader.core.file.internal.IDownloadableFileInt;
import org.iyakupov.downloader.core.file.internal.IDownloadableFilePartInt;

import java.io.File;

/**
 * This is an interface to a queued thread pool, designed to process file download requests.
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
    void submitTask(IDownloadableFileInt file, IDownloadableFilePartInt part);

    /**
     * Sets the maximal number of download worker threads
     *
     * @param newSize Number of threads. Must be positive.
     */
    void setThreadPoolSize(int newSize);

    /**
     * Create a new file download request
     *
     * @param url       Location of this file in the remote location
     * @param outputDir Output file
     * @param nThreads  Number of pieces to cut this file into in order to perform parallel download
     * @return File download request
     */
    IDownloadableFile submitFile(String url, File outputDir, int nThreads);
}
