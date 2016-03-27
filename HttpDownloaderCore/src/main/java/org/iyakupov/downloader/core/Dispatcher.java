package org.iyakupov.downloader.core;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.Collection;
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

    private class FilePartProxy implements Callable<IDownloadableFilePart> {
        private final IDownloadableFilePart filePart;

        public FilePartProxy(IDownloadableFilePart filePart) {
            this.filePart = filePart;
        }

        @Override
        public IDownloadableFilePart call() throws Exception {
            filePart.start();
            final DownloadStatus status = filePart.getStatus();
            switch (status) {
                case SUSPENDED:
                    evictedTasks.add(filePart);
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
                        //TODO: maybe decrement some counter. No getStatus()!
                        file.saveToDisk();
                    }
                    break;
                default:
                    throw new IllegalStateException("Unexpected state after filePart.start(): " + status);
            }

            activeTasks.remove(filePart);
            return filePart;
        }
    }

    private class DispatchThread implements Runnable {
        private boolean isStopped = false;

        public void stop() {
            isStopped = true;
        }

        @Override
        public void run() {
            while (!isStopped) {
                activeTasksUpdateLock.lock();

                while (activeTasks.size() < maxThreads && !evictedTasks.isEmpty()) {
                    final IDownloadableFilePart part = evictedTasks.poll();
                    activeTasks.add(part);
                    executorService.submit(new FilePartProxy(part));
                }

                while (activeTasks.size() < maxThreads && !newTasks.isEmpty()) {
                    final IDownloadableFilePart part = newTasks.poll();
                    activeTasks.add(part);
                    executorService.submit(new FilePartProxy(part));
                }

                activeTasksUpdateLock.unlock();

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    private final Queue<IDownloadableFilePart> newTasks = new ConcurrentLinkedQueue<>();
    private final Queue<IDownloadableFilePart> evictedTasks = new ConcurrentLinkedQueue<>();
    private final ConcurrentMap<IDownloadableFilePart, IDownloadableFile> allTasks = new ConcurrentHashMap<>();
    private final Set<IDownloadableFilePart> activeTasks = Sets.newConcurrentHashSet();

    private final ExecutorService executorService = Executors.newCachedThreadPool(); //TODO: maybe need to set pool size explicitly
    private final DispatchThread dispatchThread = new DispatchThread();

    private int maxThreads;

    private final Lock activeTasksUpdateLock = new ReentrantLock();

    public Dispatcher(int maxThreads) {
        this.maxThreads = maxThreads;
        executorService.submit(dispatchThread);
    }

    public synchronized void submitTasks(Collection<IDownloadableFilePart> tasks, IDownloadableFile parentFile) {
        newTasks.addAll(tasks);
        tasks.stream().forEach(t -> allTasks.putIfAbsent(t, parentFile));
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public void setMaxThreads(int maxThreads) {
        activeTasksUpdateLock.lock();

        try {
            final int evictedTasksCount = this.maxThreads - maxThreads;
            if (evictedTasksCount > 0) {
                final Iterator<IDownloadableFilePart> iterator = activeTasks.iterator();
                for (int i = 0; i < evictedTasksCount && iterator.hasNext(); ++i) {
                    final IDownloadableFilePart part = iterator.next();
                    part.pause();
                    activeTasks.remove(part);
                    evictedTasks.add(part);
                }
            }

            this.maxThreads = maxThreads;
        } finally {
            activeTasksUpdateLock.unlock();
        }
    }

    public void close() {
        dispatchThread.stop();
        executorService.shutdown();
    }
}
