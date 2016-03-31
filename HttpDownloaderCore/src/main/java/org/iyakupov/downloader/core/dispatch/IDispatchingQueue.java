package org.iyakupov.downloader.core.dispatch;

import org.iyakupov.downloader.core.file.IDownloadableFile;
import org.iyakupov.downloader.core.file.IDownloadableFilePart;

import java.io.File;
import java.net.URL;

/**
 * Created by Ilia on 30.03.2016.
 */
public interface IDispatchingQueue {
    IDownloadableFile getParentFile(IDownloadableFilePart part);

    void submitEvictedTask(IDownloadableFilePart part);

    void submitTask(IDownloadableFile file, IDownloadableFilePart part);

    void resize(int newSize);

    void markFileAsCompleted(IDownloadableFile file);

    IDownloadableFile submitFile(URL url, File outputDir, int nThreads);
}
