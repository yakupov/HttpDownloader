package org.iyakupov.downloader.core.file;

import org.iyakupov.downloader.core.file.state.FilePartDownloadState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Continuous chunk of a file that is downloaded and stored in a temporary file.
 */
public interface IDownloadableFilePart {
    /**
     * @return speed in bytes/s
     */
    int getDownloadSpeed();

    @NotNull
    FilePartDownloadState getStatus();

    /**
     * @return Percentage of the file that was already downloaded, possible values: [0, 1]
     */
    double getProgress();

    /**
     * Manually pause this download.
     *
     * @return Whether the status was changed. If not - possibly because the status was changed by another thread.
     */
    boolean pause();

    boolean start();

    boolean cancel();

    @Nullable
    String getErrorText();

    /**
     * @return {@link File} to the temporary file that stores this part
     */
    @NotNull
    File getOutputFile();

    /**
     * @return Reference (e. g. URL) to the external resource that should be downloaded.
     */
    @NotNull
    String getLocator();
}
