package org.iyakupov.downloader.core.impl;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.iyakupov.downloader.core.DownloadStatus;
import org.iyakupov.downloader.core.IDownloadableFilePart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;

import static org.iyakupov.downloader.core.DownloadStatus.*;

/**
 * Created by Ilia on 26.03.2016.
 */
public class DownloadableFilePart implements IDownloadableFilePart {
    private final File outputFile;
    private final URL url;
    private final long start;
    private long length;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final HttpClient httpClient = HttpClients.createDefault();
    private final RequestConfig headRequestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(3000)
            .setConnectTimeout(3000)
            .setSocketTimeout(6000)
            .build(); //TODO: maybe parms, maybe constructor? Maybe single client for all?


    private volatile DownloadStatus status = PENDING;
    private volatile int downloadSpeed = 0;
    private volatile long downloadedBytesCount = 0;

    public DownloadableFilePart(File outputFile, URL url, long start, long length) {
        this.outputFile = outputFile;
        this.url = url;
        this.start = start;
        this.length = length;
    }

    @Override
    public int getDownloadSpeed() {
        return downloadSpeed;
    }

    @Override
    public DownloadStatus getStatus() {
        return status;
    }

    @Override
    public double getProgress() {
        if (length <= 0) {
            //TODO: process properly
            return 0;
        } else {
            return (double) downloadedBytesCount / length;
        }
    }

    @Override
    public synchronized void pause() {
        status = SUSPENDED;
    }

    @Override
    public synchronized void start() {
        if (status == PENDING) {
            status = DOWNLOADING;

            try {
                final HttpGet httpRequest = new HttpGet(url.toURI());
                final String end;
                if (length > 0) {
                    end = String.valueOf(start + length - 1);
                } else {
                    end = "";
                }
                httpRequest.addHeader(new BasicHeader("Range", "bytes=" + start + "-" + end));
                httpRequest.setConfig(headRequestConfig);
                logger.trace("Executing request " + httpRequest.getURI());
                final HttpResponse response = httpClient.execute(httpRequest);
                logger.trace("HTTP response code: " + response.getStatusLine().getStatusCode());
                logger.trace("HTTP response reason: " + response.getStatusLine().getReasonPhrase());

                if (response.getEntity() != null) {
                    if (length <= 0) {
                        length = response.getEntity().getContentLength();
                    }

                    try (OutputStream outputFileStream = new FileOutputStream(outputFile);
                         final InputStream inputStream = response.getEntity().getContent()) {

                        final long beforeDownloadTimestamp = System.nanoTime();
                        final long beforeDownloadProgress = downloadedBytesCount;
                        final byte[] buffer = new byte[4096];
                        int lastRead;
                        while ((lastRead = inputStream.read(buffer)) >= 0) {
                            //Update counters
                            downloadedBytesCount += lastRead;
                            final double downloadDuration = ((double) (System.nanoTime() - beforeDownloadTimestamp)) / 1e9;
                            downloadSpeed = (int) ((double) (downloadedBytesCount - beforeDownloadProgress) / downloadDuration); //FIXME: write more carefully
                            //FIXME: maybe we actually need instant speed... Current algo causes problem when some of the threads have finished their work

                            //logger.trace("Total bytes downloaded in this thread: " + downloadedBytesCount);

                            //Copy
                            outputFileStream.write(buffer, 0, lastRead);
                            outputFileStream.flush();

                            //TODO: react on change of the status
                        }
                    }
                    status = DONE;
                } else {
                    status = ERROR;
                    logger.error("Received response without content");
                }
            } catch (URISyntaxException | IOException e) {
                status = ERROR;
                logger.error("HTTP request processing failed", e);
                throw new RuntimeException(e); //FIXME
            } finally {
                downloadSpeed = 0;
            }

        } else {
            //TODO: support resume
        }


    }

    @Override
    public synchronized void cancel() {
        status = CANCELLED;
    }

    @Override
    public File getOutputFile() {
        return outputFile;
    }
}
