package org.iyakupov.downloader.core;

import org.iyakupov.downloader.core.impl.DownloadableFile;

import java.net.URL;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by Ilia on 26.03.2016.
 */
//TODO: extract interface maybe
public class Downloader {

    private int maxNumberOfThreads;
    private final ConcurrentLinkedQueue<IDownloadableFile> downloadRequestQueue = new ConcurrentLinkedQueue<>();


    public Downloader(int maxNumberOfThreads) {
        this.maxNumberOfThreads = maxNumberOfThreads;
    }
/*
    public IDownloadableFile submitFile(URL url) {
        return submitFile(url, 1);
    }

    public IDownloadableFile submitFile(URL url, int nThreads) {
        final DownloadableFile downloadableFile = new DownloadableFile(nThreads, url); //TODO: arg order
        downloadableFile.start();
        return downloadableFile;
    }
*/
}
