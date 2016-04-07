package org.iyakupov.downloader.gui.settings;

import java.io.File;

/**
 * Interface to the settings model
 */
public interface ISettingsModel {
    File getDefaultOutputFolder();

    int getTotalNumberOfThreads();

    int getDefaultNumberOfThreadsPerFile();
}
