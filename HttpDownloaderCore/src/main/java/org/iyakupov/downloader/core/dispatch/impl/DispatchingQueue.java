package org.iyakupov.downloader.core.dispatch.impl;

import com.google.common.collect.Sets;
import org.iyakupov.downloader.core.DownloadStatus;
import org.iyakupov.downloader.core.comms.ICommunicationAlgorithm;
import org.iyakupov.downloader.core.comms.ICommunicationComponent;
import org.iyakupov.downloader.core.comms.impl.HttpCommunicationComponent;
import org.iyakupov.downloader.core.comms.impl.HttpDownloadCheckCommunicationAlgorithm;
import org.iyakupov.downloader.core.comms.impl.HttpPartDownloadCommunicationAlgorithm;
import org.iyakupov.downloader.core.dispatch.IDispatchingQueue;
import org.iyakupov.downloader.core.file.IDownloadableFile;
import org.iyakupov.downloader.core.file.IDownloadableFilePart;
import org.iyakupov.downloader.core.file.internal.IDownloadableFileInt;
import org.iyakupov.downloader.core.file.internal.IDownloadableFilePartInt;
import org.iyakupov.downloader.core.file.internal.impl.DownloadableFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Queued thread pool, designed to process file download requests
 */
public class DispatchingQueue implements IDispatchingQueue {
    public final static int DEFAULT_QUEUE_CAPACITY = 1000;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ThreadPoolExecutor executor;
    private final ICommunicationComponent communicationComponent = new HttpCommunicationComponent();

    private final Lock taskAddLock = new ReentrantLock();
    private final Map<IDownloadableFilePartInt, IDownloadableFileInt> partDownloadTasks = new ConcurrentHashMap<>();
    private final Set<IDownloadableFile> knownFiles = Sets.newConcurrentHashSet();

    public DispatchingQueue(int maxNumberOfThreads) {
        this(maxNumberOfThreads, DEFAULT_QUEUE_CAPACITY);
    }

    public DispatchingQueue(int maxNumberOfThreads, int queueCapacity) {
        final BlockingQueue<Runnable> taskQueue = new PriorityBlockingQueue<>(queueCapacity, (o1, o2) -> {
            if (o1 instanceof ICommunicationAlgorithm && o2 instanceof ICommunicationAlgorithm)
                return Integer.compare(((ICommunicationAlgorithm) o2).getPriority(), ((ICommunicationAlgorithm) o1).getPriority());
            else
                return 0;
        });

        executor = new ThreadPoolExecutor(maxNumberOfThreads, maxNumberOfThreads, 60L, TimeUnit.SECONDS, taskQueue);
        executor.allowCoreThreadTimeOut(true);
    }

    @Override
    public IDownloadableFileInt getParentFile(IDownloadableFilePartInt part) {
        return partDownloadTasks.get(part);
    }

    private void submitDownloadRequest(IDownloadableFileInt file) {
        knownFiles.add(file);
        executor.execute(new HttpDownloadCheckCommunicationAlgorithm(this, communicationComponent, file));
    }

    @Override
    public void submitEvictedTask(IDownloadableFilePartInt part) {
        executor.execute(new HttpPartDownloadCommunicationAlgorithm(100, this, communicationComponent, part));
    }

    @Override
    public void submitNewTask(IDownloadableFileInt file, IDownloadableFilePartInt part) {
        if (partDownloadTasks.containsKey(part)) {
            throw new IllegalStateException("Trying to submit existing file part download task");
        } else {
            taskAddLock.lock();
            //idea: even if we add a part download task at the same time as we delete the corresponding
            //file download request, this task will get the status of CANCELLED.
            //See another usage of this lock in forgetFile
            if (knownFiles.contains(file)) {
                partDownloadTasks.put(part, file);
            } else {
                logger.error("Failed to submit a part download task: parent file is not known. " +
                        "Already deleted? File: " + file.getOutputFile());
                return;
            }
            taskAddLock.unlock();
            executor.execute(new HttpPartDownloadCommunicationAlgorithm(10, this, communicationComponent, part));
        }
    }

    @Override
    public synchronized void setThreadPoolSize(int newSize, boolean evictNonResumable) {
        executor.setCorePoolSize(newSize);
        executor.setMaximumPoolSize(newSize);

        int tasksToEvict = executor.getActiveCount() - newSize;

        for (IDownloadableFilePartInt part: partDownloadTasks.keySet()) {
            if (tasksToEvict-- <= 0)
                break;
            if (part.getStatus() == DownloadStatus.DOWNLOADING && part.isDownloadResumeSupported()) {
                logger.warn("Evicted task because of the shortage of threads: " + part.getLocator());
                part.suspend();
            }
        }

        if (evictNonResumable && tasksToEvict > 0) {
            for (IDownloadableFilePartInt part: partDownloadTasks.keySet()) {
                if (tasksToEvict-- <= 0)
                    break;
                if (part.getStatus() == DownloadStatus.DOWNLOADING) {
                    logger.warn("Evicted task because of the shortage of threads: " + part.getLocator());
                    part.suspend();
                }
            }
        }
    }

    @Override
    public IDownloadableFile submitFile(String url, File outputDir, int nThreads) {
        final DownloadableFile downloadableFile = new DownloadableFile(url, outputDir, nThreads);
        submitDownloadRequest(downloadableFile);
        return downloadableFile;
    }

    @Override
    public Collection<IDownloadableFile> getAllFiles() {
        return Collections.unmodifiableSet(knownFiles);
    }

    @Override
    public boolean forgetFile(IDownloadableFile file) {
        final boolean shouldCancel = file.getStatus() != DownloadStatus.DONE;

        taskAddLock.lock();
        if (shouldCancel)
            file.cancel();
        final boolean removed = knownFiles.remove(file);
        taskAddLock.unlock();

        if (removed) {
            file.getDownloadableParts().stream().forEach(partDownloadTasks::remove);
            if (shouldCancel) {
                file.getDownloadableParts().stream().map(IDownloadableFilePart::getOutputFile).forEach(File::delete);
                //noinspection ResultOfMethodCallIgnored
                file.getOutputFile().delete();
            }
        }
        return removed;
    }

    @Override
    public boolean resumeDownload(IDownloadableFile file) {
        if (file.getStatus() == DownloadStatus.PAUSED) {
            if (!knownFiles.contains(file)) {
                logger.error("Trying to resume download of a forgotten file. File is already deleted?");
                return false;
            }
            file.getDownloadableParts().stream()
                    .forEach(p -> {
                                final IDownloadableFilePartInt partInt = (IDownloadableFilePartInt) p;
                                partInt.resumeDownload();
                                executor.execute(new HttpPartDownloadCommunicationAlgorithm(
                                        5, this, communicationComponent, partInt));
                            });
            return true;
        } else {
            logger.error("Failed to resume - unexpected file status: " + file.getStatus());
            return false;
        }
    }
}
