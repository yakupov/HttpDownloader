package org.iyakupov.downloader.core;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Created by Ilia on 26.03.2016.
 */
public interface IDownloadableFile {
    //TODO: maybe it's actually a Future?

    /**
     *
     * @return speed in bytes/s
     */
    int getDownloadSpeed();

    @NotNull
    DownloadStatus getStatus();

    int getActiveThreadCount();

    int getMaxThreadCount();

    /**
     * @return Percentage of the file that was already downloaded, possible values: [0, 1]
     */
    double getProgress();

    void pause();

    void start();

    void cancel();

    boolean isPartialDownloadSupported();

    @NotNull
    Collection<IDownloadableFilePart> getDownloadableParts();

    boolean saveToDisk() throws IOException;

    @NotNull
    File getOutputFile();
}
