package org.iyakupov.downloader.core.dispatch.impl;

import org.iyakupov.downloader.core.DownloadStatus;
import org.iyakupov.downloader.core.comms.ICommunicationAlgorithm;
import org.iyakupov.downloader.core.comms.ICommunicationComponent;
import org.iyakupov.downloader.core.comms.impl.HttpCommunicationComponent;
import org.iyakupov.downloader.core.comms.impl.HttpDownloadCheckCommunicationAlgorithm;
import org.iyakupov.downloader.core.comms.impl.HttpPartDownloadCommunicationAlgorithm;
import org.iyakupov.downloader.core.dispatch.IDispatchingQueue;
import org.iyakupov.downloader.core.file.IDownloadableFile;
import org.iyakupov.downloader.core.file.internal.IDownloadableFileInt;
import org.iyakupov.downloader.core.file.internal.IDownloadableFilePartInt;
import org.iyakupov.downloader.core.file.internal.impl.DownloadableFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Queued thread pool, designed to process file download requests
 */
public class DispatchingQueue implements IDispatchingQueue {
    public final static int DEFAULT_QUEUE_CAPACITY = 1000;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ICommunicationComponent communicationComponent = new HttpCommunicationComponent();
    private final Map<IDownloadableFilePartInt, IDownloadableFileInt> allTasks = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor;

    public DispatchingQueue(int maxNumberOfThreads) {
        this(maxNumberOfThreads, DEFAULT_QUEUE_CAPACITY);
    }

    public DispatchingQueue(int maxNumberOfThreads, int queueCapacity) {
        final BlockingQueue<Runnable> taskQueue = new PriorityBlockingQueue<>(queueCapacity, (o1, o2) -> {
            if (o1 == o2)
                return 0;
            else if (o1 == null)
                return -1;
            else if (o2 == null)
                return 1;
            else if (o1 instanceof ICommunicationAlgorithm && o2 instanceof ICommunicationAlgorithm)
                return Integer.compare(((ICommunicationAlgorithm) o2).getPriority(), ((ICommunicationAlgorithm) o1).getPriority());
            else
                return 0;
        });
        executor = new ThreadPoolExecutor(maxNumberOfThreads, maxNumberOfThreads, 60L, TimeUnit.SECONDS, taskQueue);
        executor.allowCoreThreadTimeOut(true);
    }

    @Override
    public IDownloadableFileInt getParentFile(IDownloadableFilePartInt part) {
        return allTasks.get(part);
    }

    private void submitDownloadRequest(IDownloadableFileInt file) {
        executor.execute(new HttpDownloadCheckCommunicationAlgorithm(this, communicationComponent, file));
    }

    @Override
    public void submitEvictedTask(IDownloadableFilePartInt part) {
        executor.execute(new HttpPartDownloadCommunicationAlgorithm(100, this, communicationComponent, part));
    }

    @Override
    public void submitTask(IDownloadableFileInt file, IDownloadableFilePartInt part) {
        allTasks.put(part, file);
        executor.execute(new HttpPartDownloadCommunicationAlgorithm(10, this, communicationComponent, part));
        //TODO: check for duplicates maybe?
    }

    @Override
    public synchronized void setThreadPoolSize(int newSize) {
        executor.setCorePoolSize(newSize);
        executor.setMaximumPoolSize(newSize);
        int tasksToEvict = executor.getActiveCount() - newSize;
        for (IDownloadableFilePartInt part: allTasks.keySet()) {
            if (tasksToEvict-- <= 0)
                break;
            if (part.getStatus() == DownloadStatus.DOWNLOADING) {
                logger.warn("Evicted task because of the shortage of threads: " + part.getLocator());
                part.suspend();
            }
        }
    }

    //public void markFileAsCompleted(IDownloadableFileInt file) {
        //TODO: remove from the Map or move to another storage... Or don't do anything.
        //Need to define methods for manipulations (e.g. resumeDownload, cancel etc.) with files, managed by this dispatcher.
        //Need to manage the entries of the parts map
    //}

    @Override
    public IDownloadableFile submitFile(String url, File outputDir, int nThreads) {
        final DownloadableFile downloadableFile = new DownloadableFile(url, outputDir, nThreads);
        submitDownloadRequest(downloadableFile);
        return downloadableFile;
    }
}
