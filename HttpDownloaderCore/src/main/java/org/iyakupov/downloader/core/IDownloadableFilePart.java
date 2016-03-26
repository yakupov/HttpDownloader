package org.iyakupov.downloader.core;

import java.io.File;

/**
 * Created by Ilia on 26.03.2016.
 */
public interface IDownloadableFilePart {//} extends Runnable { //TODO: maybe don't extend?
    /**
     *
     * @return speed in bytes/s
     */
    int getDownloadSpeed();

    DownloadStatus getStatus();

    /**
     * @return Percentage of the file that was already downloaded, possible values: [0, 1]
     */
    double getProgress();

    void pause();

    void start();

    void cancel();

    File getOutputFile();
}
