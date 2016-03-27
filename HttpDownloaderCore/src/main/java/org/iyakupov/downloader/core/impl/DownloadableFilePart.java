package org.iyakupov.downloader.core.impl;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.iyakupov.downloader.core.DownloadStatus;
import org.iyakupov.downloader.core.IDownloadableFilePart;
import org.jetbrains.annotations.NotNull;
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

    @NotNull
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
    public void pause() {
        if (status != ERROR && status != DONE) {
            status = PAUSED;
        }
    }

    @Override
    public void suspend() {
        if (status != ERROR && status != DONE) {
            status = SUSPENDED;
        }
    }

    @Override
    public synchronized void start() {
        boolean resume = false;

        switch (status) {
            case SUSPENDED:
            case PAUSED:
                resume = true;
                logger.trace("File part downloader awakened: " + getOutputFile().toString());
            case PENDING:
                status = DOWNLOADING;
                download(resume);
                return;
            case DOWNLOADING:
                return;
            default:
                throw new IllegalStateException("Failed to start download of a part of a file. Current status = " + status);
        }
    }

    private void download(boolean resume) {
        try {
            final HttpGet httpRequest = new HttpGet(url.toURI());
            final String end;
            if (length > 0) {
                end = String.valueOf(start + length - 1);
            } else {
                end = "";
            }
            httpRequest.addHeader(new BasicHeader("Range", "bytes=" + (start + downloadedBytesCount) + "-" + end));
            httpRequest.setConfig(headRequestConfig);
            logger.trace("Executing request " + httpRequest.getURI());
            final HttpResponse response = httpClient.execute(httpRequest);
            logger.trace("HTTP response code: " + response.getStatusLine().getStatusCode() +
                    ", reason = " + response.getStatusLine().getReasonPhrase());

            if (response.getEntity() != null) {
                //TODO: check that RC=206

                if (length <= 0) {
                    length = response.getEntity().getContentLength();
                }

                final InputStream inputStream = response.getEntity().getContent();
                try (OutputStream outputFileStream = new FileOutputStream(outputFile, resume)) {

                    final byte[] buffer = new byte[4096];
                    int lastRead;
                    int i = 0;
                    long lastMeasureTimestamp = System.nanoTime();
                    long lastMeasureBytesCount = downloadedBytesCount;
                    while ((lastRead = inputStream.read(buffer)) >= 0) {
                        //Update counters
                        downloadedBytesCount += lastRead;
                        if (i++ == 1000) {
                            final double interval = ((double) (System.nanoTime() - lastMeasureTimestamp)) / 1e9;
                            final double bytesCount = downloadedBytesCount - lastMeasureBytesCount;
                            downloadSpeed = (int) (bytesCount / interval);

                            lastMeasureTimestamp = System.nanoTime();
                            lastMeasureBytesCount = downloadedBytesCount;
                            i = 0;
                        }

                        //Copy
                        outputFileStream.write(buffer, 0, lastRead);
                        outputFileStream.flush();

                        if (status != DOWNLOADING) {
                            return;
                        }
                    }
                }
                status = DONE;
                logger.trace("File part downloader done: " + getOutputFile().toString());

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
    }

    @Override
    public void cancel() {
        status = CANCELLED;
    }

    @NotNull
    @Override
    public File getOutputFile() {
        return outputFile;
    }
}
