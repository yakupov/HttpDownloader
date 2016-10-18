package org.iyakupov.downloader.gui.new_download;

import org.jetbrains.annotations.NotNull;

import java.io.File;

public class DownloadRequest {
    @NotNull
    private final File outputDir;
    @NotNull
    private final String url;
    private final int numberOfThreads;

    DownloadRequest(@NotNull File outputDir, @NotNull String url, int numberOfThreads) {
        this.outputDir = outputDir;
        this.url = url;
        this.numberOfThreads = numberOfThreads;
    }

    @NotNull
    public File getOutputDir() {
        return outputDir;
    }

    @NotNull
    public String getUrl() {
        return url;
    }

    public int getNumberOfThreads() {
        return numberOfThreads;
    }

    @Override
    public String toString() {
        return "DownloadRequest{" +
                "outputDir=" + outputDir +
                ", url='" + url + '\'' +
                ", numberOfThreads=" + numberOfThreads +
                '}';
    }
}
