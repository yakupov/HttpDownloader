package org.iyakupov.downloader.core.dispatch.impl;

import com.google.common.collect.Sets;
import org.iyakupov.downloader.core.file.state.FileDownloadState;
import org.iyakupov.downloader.core.comms.ICommunication;
import org.iyakupov.downloader.core.comms.ICommunicatingComponent;
import org.iyakupov.downloader.core.comms.impl.HttpCommunicatingComponent;
import org.iyakupov.downloader.core.comms.impl.HttpDownloadCheckCommunication;
import org.iyakupov.downloader.core.comms.impl.HttpPartDownloadCommunication;
import org.iyakupov.downloader.core.dispatch.IDispatchingQueue;
import org.iyakupov.downloader.core.dispatch.TaskPriority;
import org.iyakupov.downloader.core.file.IDownloadableFile;
import org.iyakupov.downloader.core.file.IDownloadableFilePart;
import org.iyakupov.downloader.core.file.internal.IManagedDownloadableFile;
import org.iyakupov.downloader.core.file.internal.IManagedDownloadableFilePart;
import org.iyakupov.downloader.core.file.internal.impl.DownloadableFile;
import org.iyakupov.downloader.core.file.state.FilePartDownloadState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Queued thread pool, designed to process file download requests
 */
public class DispatchingQueue implements IDispatchingQueue {
    public final static int DEFAULT_QUEUE_CAPACITY = 1000;

    private final Logger logger = LoggerFactory.getLogger(DispatchingQueue.class);

    private final Set<IManagedDownloadableFile> knownFiles = Sets.newConcurrentHashSet(); //concurrent for getAllFiles to work
    private final ExecutorService trashRemovalExecutor = Executors.newCachedThreadPool();
    private final ThreadPoolExecutor executor;
    private final ICommunicatingComponent communicationComponent;

    public DispatchingQueue(int maxNumberOfThreads) {
        this(maxNumberOfThreads, DEFAULT_QUEUE_CAPACITY);
    }

    private DispatchingQueue(int maxNumberOfThreads, int queueCapacity) {
        this(maxNumberOfThreads, queueCapacity, new HttpCommunicatingComponent());
    }

    public DispatchingQueue(int maxNumberOfThreads, int queueCapacity, ICommunicatingComponent communicationComponent) {
        final BlockingQueue<Runnable> taskQueue = new PriorityBlockingQueue<>(queueCapacity, (o1, o2) -> {
            if (o1 instanceof ICommunication && o2 instanceof ICommunication)
                return Integer.compare(((ICommunication) o2).getPriority(), ((ICommunication) o1).getPriority());
            else
                return 0;
        });

        executor = new ThreadPoolExecutor(maxNumberOfThreads, maxNumberOfThreads, 60L, TimeUnit.SECONDS, taskQueue);
        executor.allowCoreThreadTimeOut(true);

        this.communicationComponent = communicationComponent;

        logger.info("Started DispatchingQueue with thread pool size of " + maxNumberOfThreads);
    }

    @Override
    public synchronized void reSubmitEvictedTask(IManagedDownloadableFile file, IManagedDownloadableFilePart part) {
        if (part.confirmSuspendAndRestart()) {
            executor.execute(new HttpPartDownloadCommunication(
                    TaskPriority.EVICTED_TASK, this, communicationComponent, file, part));
        } else {
            logger.warn("Failed to re-submit evicted task - incorrect status or concurrent modification by user? " +
                    "Status: " + part.getStatus() + ", URL: " + part.getLocator() + ", file = " + part.getOutputFile());
        }
    }

    @Override
    public synchronized void submitNewTask(IManagedDownloadableFile file, IManagedDownloadableFilePart part) {
        if (!fileIsKnown(file)) {
            logger.error("Failed to submit a part download task: parent file is not known. " +
                    "Already deleted? Task: " + part);
        } else {
            executor.execute(new HttpPartDownloadCommunication(TaskPriority.NEW_PART_DOWNLOAD, this, communicationComponent, file, part));
        }
    }

    @Override
    public synchronized void setThreadPoolSize(int newSize, boolean evictNonResumable) {
        logger.info("Set new core and max pool size: " + newSize);
        executor.setCorePoolSize(newSize);
        executor.setMaximumPoolSize(newSize);

        //FIXME: it looks like we can't properly increase the pool size, need to test & fix
        /*
           Reproduce on 1 file:
             1) maxThreads = 5, parts = 15
             2) Set maxThreads = 20
             3) There are only 8 active parts
         */

        final int activeCount = executor.getActiveCount();
        int tasksToEvict = activeCount - newSize;
        if (tasksToEvict > 0) {
            logger.debug("Current active thread count: " + activeCount + ", tasks to evict: " + tasksToEvict);
            tasksToEvict = suspendSomeTasks(tasksToEvict, false);
            if (tasksToEvict > 0)
                suspendSomeTasks(tasksToEvict, true);
        }
    }

    /**
     * Forcefully pause some tasks
     *
     * @param tasksToEvict Number of tasks to suspend
     * @param evictNonResumable Whether to suspend tasks where download resume is not possible
     * @return Remaining number of tasks to evict
     */
    private synchronized int suspendSomeTasks(int tasksToEvict, boolean evictNonResumable) {
        for (IManagedDownloadableFile file: knownFiles) {
            for (IManagedDownloadableFilePart part: file.getDownloadableParts()) {
                if (part.getStatus() == FilePartDownloadState.DOWNLOADING &&
                        (part.isDownloadResumeSupported() || evictNonResumable)) {
                    if (tasksToEvict-- <= 0)
                        break;

                    logger.warn("Evicted task because of the shortage of threads: "
                            + part.getLocator() + ", file = " + part.getOutputFile());
                    part.suspend();
                }
            }
        }
        return tasksToEvict;
    }

    public synchronized IDownloadableFile submitFile(String url, File outputDir, int nThreads) {
        final DownloadableFile downloadableFile = new DownloadableFile(url, outputDir, nThreads);
        if (fileIsKnown(downloadableFile))
            throw new RuntimeException("Download request with this URL is already submitted: " + url);
        knownFiles.forEach(f -> {
            if (f.getOutputFile().equals(downloadableFile.getOutputFile()))
                throw new RuntimeException("Download request with this output file name already exists: " +
                        downloadableFile.getOutputFile());
        });

        knownFiles.add(downloadableFile);
        executor.execute(new HttpDownloadCheckCommunication(this, communicationComponent, downloadableFile));
        return downloadableFile;
    }

    @Override
    public Collection<IDownloadableFile> getAllFiles() {
        return Collections.unmodifiableSet(knownFiles);
    }

    //TODO: cancellation should be thread-safe, maybe don't need sync. Possible issues with knownFiles
    @Override
    public synchronized boolean forgetFile(IDownloadableFile file) {
        if (fileIsKnown(file)) {
            final boolean shouldCancel = file.getStatus() != FileDownloadState.DONE;
            //noinspection SuspiciousMethodCalls
            knownFiles.remove(file);
            if (shouldCancel && file.cancel()) {
                file.getDownloadableParts().stream().map(IDownloadableFilePart::getOutputFile)
                        .forEach(f -> trashRemovalExecutor.submit(new FileRemovalTask(f)));
                return !trashRemovalExecutor.submit(new FileRemovalTask(file.getOutputFile())).isCancelled(); //Findbugs does not like ignored return value
            }
        } else {
            logger.error("Asked to forget an unknown file: " + file);
        }
        return false;
    }

    private boolean fileIsKnown(IDownloadableFile file) {
        return file instanceof IManagedDownloadableFile && knownFiles.contains(file);
    }

    @Override
    public synchronized boolean resumeDownload(IDownloadableFile file) {
        //FIXME: if file save has failed, but the download has finished,
        //resume() breaks everything. It downloads more data (duplicate) and does not assemble the output file

        if (file.getStatus() == FileDownloadState.PAUSED ||
                file.getStatus() == FileDownloadState.FAILED && file.getDownloadableParts().size() > 0) {
            logger.debug("Resuming download of file " + file.getOutputFile());
            if (!fileIsKnown(file)) {
                logger.error("Trying to resume download of a forgotten file. File is already deleted?");
                return false;
            }
            file.getDownloadableParts().stream()
                    .filter(p -> p.getStatus() == FilePartDownloadState.PAUSED || p.getStatus() == FilePartDownloadState.FAILED)
                    .forEach(p -> {
                        final IManagedDownloadableFilePart partInt = (IManagedDownloadableFilePart) p;
                        partInt.resume();
                        logger.debug("Resuming part " + p.getOutputFile());
                        executor.execute(new HttpPartDownloadCommunication(
                                TaskPriority.PAUSED_TASK, this, communicationComponent, (IManagedDownloadableFile) file, partInt));
                    });
            return true;
        } else {
            logger.error("Failed to resume download of the file " + file);
            return false;
        }
    }

    @Override
    public synchronized void close() throws IOException {
        knownFiles.forEach(IDownloadableFile::cancel);
        executor.shutdownNow();
        trashRemovalExecutor.shutdownNow();
        communicationComponent.close();

        knownFiles.forEach(file -> {
            file.getDownloadableParts().stream().map(IDownloadableFilePart::getOutputFile).forEach(f -> {
                if (!f.delete())
                    f.deleteOnExit();
            });
            if (!file.getOutputFile().delete())
                file.getOutputFile().deleteOnExit();
        });
    }
}
