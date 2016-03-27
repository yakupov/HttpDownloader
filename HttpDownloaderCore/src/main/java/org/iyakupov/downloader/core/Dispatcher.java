package org.iyakupov.downloader.core;

import com.google.common.collect.Sets;
import org.iyakupov.downloader.core.impl.DownloadableFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.net.URL;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Ilia on 26.03.2016.
 */
public class Dispatcher implements Closeable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    //TODO: runnable?
    private class DownloadFilePartProxy implements Callable<IDownloadableFilePart> {
        private final IDownloadableFilePart filePart;

        public DownloadFilePartProxy(IDownloadableFilePart filePart) {
            this.filePart = filePart;
        }

        @Override
        public IDownloadableFilePart call() throws Exception {
            logger.info("Proxy start, file name = " + filePart.getOutputFile());

            filePart.start();
            final DownloadStatus status = filePart.getStatus();
            //logger.trace("Exit start() with status: " + status);
            switch (status) {
                case SUSPENDED:
                    evictedTasks.add(filePart);
                    break;
                case PAUSED:
                    pausedTasks.add(filePart);
                    break;
                case CANCELLED:
                    allTasks.remove(filePart);
                    break;
                case ERROR:
                    allTasks.remove(filePart).cancel();
                    break;
                case DONE:
                    final IDownloadableFile file = allTasks.remove(filePart);
                    if (file.getStatus() == DownloadStatus.DONE) {
                        //TODO: maybe decrement some counter. No getStatus() - too slow!
                        file.saveToDisk();
                    }
                    break;
                default:
                    throw new IllegalStateException("Unexpected state after filePart.start(): " + status);
            }

            activeTasks.remove(filePart);
            logger.trace("AT remove. Sz = " + activeTasks.size() + ", file name = " + filePart.getOutputFile());
            return filePart;
        }
    }

    private class DownloadDispatchThread implements Runnable {
        private boolean isStopped = false;

        public void stop() {
            isStopped = true;
        }

        @Override
        public void run() {
            while (!isStopped) {
                //activeTasksUpdateLock.lock();
                //TODO: ensure that the lock is not needed

                //TODO: maybe move this logic to Proxy and RequestProcessor?

                while (activeTasks.size() < maxThreads && !evictedTasks.isEmpty()) {
                    final IDownloadableFilePart part = evictedTasks.poll();
                    logger.info("Active tasks size: " + activeTasks.size());
                    logger.info("Awakening evicted task: " + part.getOutputFile());
                    activeTasks.add(part);
                    executorService.submit(new DownloadFilePartProxy(part));
                    logger.info("Submitted... AT size = " + activeTasks.size());
                }

                final Iterator<IDownloadableFilePart> pausedTaskIterator = pausedTasks.iterator();
                while (activeTasks.size() < maxThreads && pausedTaskIterator.hasNext()) {
                    final IDownloadableFilePart part = pausedTaskIterator.next();
                    if (part.getStatus() != DownloadStatus.PAUSED) {
                        activeTasks.add(part);
                        pausedTasks.remove(part);
                        executorService.submit(new DownloadFilePartProxy(part));
                    }
                }

                while (activeTasks.size() < maxThreads && !newTasks.isEmpty()) {
                    final IDownloadableFilePart part = newTasks.poll();
                    activeTasks.add(part);
                    executorService.submit(new DownloadFilePartProxy(part));
                }

                //activeTasksUpdateLock.unlock();

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    private class DownloadRequestProcessor implements Runnable {
        private boolean isStopped = false;

        public void stop() {
            isStopped = true;
        }

        @Override
        public void run() {
            while (!isStopped) {
                IDownloadableFile newFile;
                while ((newFile = newFileDownloadRequests.poll()) != null) {
                    logger.info("Registering new download request. Output file name: " + newFile.getOutputFile().toString());
                    newFile.start();
                    if (newFile.getStatus() != DownloadStatus.ERROR) {
                        newTasks.addAll(newFile.getDownloadableParts());
                        for (IDownloadableFilePart part : newFile.getDownloadableParts()) {
                            allTasks.putIfAbsent(part, newFile);
                        }
                    }
                }
            }
        }
    }

    private final Queue<IDownloadableFile> newFileDownloadRequests = new ConcurrentLinkedQueue<>();
    private final Queue<IDownloadableFilePart> newTasks = new ConcurrentLinkedQueue<>();
    private final Queue<IDownloadableFilePart> evictedTasks = new ConcurrentLinkedQueue<>();
    private final ConcurrentMap<IDownloadableFilePart, IDownloadableFile> allTasks = new ConcurrentHashMap<>();
    private final Set<IDownloadableFilePart> activeTasks = Sets.newConcurrentHashSet();
    private final Set<IDownloadableFilePart> pausedTasks = Sets.newConcurrentHashSet();

    private final ExecutorService executorService = Executors.newCachedThreadPool(); //TODO: maybe need to set pool size explicitly
    private final DownloadDispatchThread dispatchThread = new DownloadDispatchThread();
    private final DownloadRequestProcessor downloadRequestProcessor = new DownloadRequestProcessor();

    private volatile int maxThreads;

    //private final Lock activeTasksUpdateLock = new ReentrantLock();

    public Dispatcher(int maxThreads) {
        this.maxThreads = maxThreads;
        executorService.submit(dispatchThread);
        executorService.submit(downloadRequestProcessor);
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public void setMaxThreads(int maxThreads) {
        final Iterator<IDownloadableFilePart> activeTaskIterator = activeTasks.iterator();
        final int numberOfTasksToSuspend = activeTasks.size() - maxThreads;
        for (int i = 0; i < numberOfTasksToSuspend; ++i) {
            final IDownloadableFilePart part = activeTaskIterator.next();
            logger.warn("Temporarily evicted task because of shortage of download threads: " + part.getOutputFile().toString());
            part.suspend();
        }

        this.maxThreads = maxThreads;
    }

    public IDownloadableFile submitFile(URL url, File outputDir) {
        return submitFile(url, outputDir, 1);
    }

    public IDownloadableFile submitFile(URL url, File outputDir, int nThreads) {
        final IDownloadableFile downloadableFile = new DownloadableFile(url, outputDir, nThreads);
        newFileDownloadRequests.add(downloadableFile);
        return downloadableFile;
    }

    public void close() {
        dispatchThread.stop();
        downloadRequestProcessor.stop();
        executorService.shutdown();
    }
}
