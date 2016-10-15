package org.iyakupov.downloader.core.dispatch.impl;

import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Task for save and asynchronous file deletion
 */
class FileRemovalTask implements Runnable {
    private static final long DEFAULT_TIMEOUT = (long) 10e9; //10 seconds in ns

    @NotNull
    private final File file;
    private final long timeout;

    FileRemovalTask(@NotNull File file) {
        this(file, DEFAULT_TIMEOUT);
    }

    private FileRemovalTask(@NotNull File file, long timeout) {
        this.file = file;
        this.timeout = timeout;
    }

    @Override
    public void run() {
        final long startTime = System.nanoTime();

        while (file.exists() && !file.delete()) {
            if (System.nanoTime() - startTime > timeout) {
                file.deleteOnExit();
                return;
            } else {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
}

