package org.iyakupov.downloader.core.dispatch.impl;

import org.iyakupov.downloader.core.DownloadStatus;
import org.iyakupov.downloader.core.comms.ICommunicationAlgorithm;
import org.iyakupov.downloader.core.comms.ICommunicationComponent;
import org.iyakupov.downloader.core.comms.impl.HttpCommunicationComponent;
import org.iyakupov.downloader.core.comms.impl.HttpDownloadCheckCommunicationAlgorithm;
import org.iyakupov.downloader.core.comms.impl.HttpPartDownloadCommunicationAlgorithm;
import org.iyakupov.downloader.core.dispatch.IDispatchingQueue;
import org.iyakupov.downloader.core.file.IDownloadableFile;
import org.iyakupov.downloader.core.file.IDownloadableFilePart;
import org.iyakupov.downloader.core.file.impl.DownloadableFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Created by Ilia on 30.03.2016.
 */
public class DispatchingQueue implements IDispatchingQueue {
    private final static int QUEUE_SIZE = 1000; //TODO: parm

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ICommunicationComponent communicationComponent = new HttpCommunicationComponent();

    private final ThreadPoolExecutor executor;
    private final Map<IDownloadableFilePart, IDownloadableFile> allTasks = new ConcurrentHashMap<>();

    public DispatchingQueue(int maxNumberOfThreads) {
        final BlockingQueue<Runnable> taskQueue = new PriorityBlockingQueue<>(QUEUE_SIZE, (o1, o2) -> {
            if (o1 == o2)
                return 0;
            else if (o1 == null)
                return -1;
            else if (o2 == null)
                return 1;
            else if (o1 instanceof ICommunicationAlgorithm && o2 instanceof ICommunicationAlgorithm)
                return Integer.compare(((ICommunicationAlgorithm) o1).getPriority(), ((ICommunicationAlgorithm) o2).getPriority());
            else
                return 0;
        });
        executor = new ThreadPoolExecutor(maxNumberOfThreads, maxNumberOfThreads, 0, TimeUnit.SECONDS, taskQueue);

        // TODO: Thread pool DOES NOT GROW if the queue is not full. Sad but true.
        // Need better thread pool, which does not rely on the size of the underlying queue.
    }

    @Override
    public IDownloadableFile getParentFile(IDownloadableFilePart part) {
        return allTasks.get(part);
    }

    private void submitNewFile(IDownloadableFile file) {
        executor.execute(new HttpDownloadCheckCommunicationAlgorithm(this, communicationComponent, file));
    }

    @Override
    public void submitEvictedTask(IDownloadableFilePart part) {
        final HttpPartDownloadCommunicationAlgorithm task = new HttpPartDownloadCommunicationAlgorithm(100, this, communicationComponent, part);
        logger.trace("New task created... Status = " + part.getStatus());
        executor.execute(task);
        //taskQueue.offer(task);
        logger.trace("Submitted...");
    }

    @Override
    public void submitTask(IDownloadableFile file, IDownloadableFilePart part) {
        allTasks.put(part, file);
        executor.execute(new HttpPartDownloadCommunicationAlgorithm(10, this, communicationComponent, part));
    }

    @Override
    public synchronized void resize(int newSize) {
        executor.setCorePoolSize(newSize);
        executor.setMaximumPoolSize(newSize);
        int tasksToEvict = executor.getActiveCount() - newSize;
        for (IDownloadableFilePart part: allTasks.keySet()) {
            if (tasksToEvict-- <= 0)
                break;

            if (part.getStatus() == DownloadStatus.DOWNLOADING) {
                logger.warn("Evicted task because of the shortage of threads: " + part.getLocator());

                part.suspend();
                //submitEvictedTask(part);
            }
        }
        logger.trace("Resize finished");
    }

    @Override
    public void markFileAsCompleted(IDownloadableFile file) {
        //TODO: remove from the Map or move to another storage... Or don't do anything.
        //Need to define methods for manipulations (e.g. resumeDownload, cancel etc.) with files, managed by this dispatcher.
    }

    @Override
    public IDownloadableFile submitFile(URL url, File outputDir, int nThreads) {
        final IDownloadableFile downloadableFile = new DownloadableFile(url, outputDir, nThreads);
        submitNewFile(downloadableFile);
        return downloadableFile;
    }
}
