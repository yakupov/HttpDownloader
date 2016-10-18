package org.iyakupov.downloader.gui.settings;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Storage for application settings.
 */
public class SettingsModel implements ISettingsModel {
    public static final int DEFAULT_WORKER_THREADS_COUNT_PER_FILE = 10;

    private File defaultOutputFolder;
    private final IntegerProperty totalNumberOfThreads = new SimpleIntegerProperty(DEFAULT_WORKER_THREADS_COUNT_PER_FILE);
    private int defaultNumberOfThreadsPerFile = 1;

    @Override
    public File getDefaultOutputFolder() {
        return defaultOutputFolder;
    }

    void setDefaultOutputFolder(File defaultOutputFolder) {
        this.defaultOutputFolder = defaultOutputFolder;
    }

    @Override
    public int getTotalNumberOfThreads() {
        return totalNumberOfThreads.get();
    }

    @NotNull
    public IntegerProperty getTotalNumberOfThreadsProperty() {
        return totalNumberOfThreads;
    }

    void setTotalNumberOfThreads(int totalNumberOfThreads) {
        this.totalNumberOfThreads.set(totalNumberOfThreads);
    }

    @Override
    public int getDefaultNumberOfThreadsPerFile() {
        return defaultNumberOfThreadsPerFile;
    }

    void setDefaultNumberOfThreadsPerFile(int defaultNumberOfThreadsPerFile) {
        this.defaultNumberOfThreadsPerFile = defaultNumberOfThreadsPerFile;
    }
}
