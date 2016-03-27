package org.iyakupov.downloader.core;

import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Created by Ilia on 26.03.2016.
 */
public interface IDownloadableFilePart {
    /**
     *
     * @return speed in bytes/s
     */
    int getDownloadSpeed();

    @NotNull
    DownloadStatus getStatus();

    /**
     * @return Percentage of the file that was already downloaded, possible values: [0, 1]
     */
    double getProgress();

    /**
     * Manually pause this download.
     */
    void pause();

    /**
     * Automatically suspend task because of the shortage of available threads
     */
    void suspend();

    void start();

    void cancel();

    @NotNull
    File getOutputFile();
}
