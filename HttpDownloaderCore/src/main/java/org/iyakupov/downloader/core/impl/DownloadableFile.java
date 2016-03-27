package org.iyakupov.downloader.core.impl;

import org.apache.commons.io.FilenameUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.iyakupov.downloader.core.DownloadStatus;
import org.iyakupov.downloader.core.IDownloadableFile;
import org.iyakupov.downloader.core.IDownloadableFilePart;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.iyakupov.downloader.core.DownloadStatus.*;

/**
 * Created by Ilia on 26.03.2016.
 */
public class DownloadableFile implements IDownloadableFile {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final URL url;
    private final File outputFile;
    private final int maxThreadCount;

    private final HttpClient httpClient = HttpClients.createDefault();
    private final RequestConfig headRequestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(3000)
            .setConnectTimeout(3000)
            .setSocketTimeout(6000)
            .build(); //TODO: maybe parms, maybe constructor?

    private final List<IDownloadableFilePart> fileParts = new ArrayList<>();
    private final Lock filePartsLock = new ReentrantLock();

    private volatile boolean isPartialDownloadSupported = false;
    private volatile boolean fatalErrorHappened = false;

    public DownloadableFile(URL url, File outputDir, int maxThreadCount) {
        this.url = url;
        if (!outputDir.isDirectory()) {
            throw new IllegalArgumentException("The given path is not a directory: " + outputDir);
        }
        final String fileName = FilenameUtils.getName(url.toString());
        if (fileName == null) {
            throw new IllegalArgumentException("The given URL has null file name: " + url);
        }
        this.outputFile = new File(outputDir, fileName);
        this.maxThreadCount = maxThreadCount;
    }

    @Override
    public int getDownloadSpeed() {
        return getDownloadableParts().stream()
                .mapToInt(IDownloadableFilePart::getDownloadSpeed)
                .sum();
    }

    @NotNull
    @Override
    //TODO: maybe this method is not needed? Or optimize...
    public DownloadStatus getStatus() {
        if (fatalErrorHappened) {
            return ERROR;
        } else if (getDownloadableParts().isEmpty()) {
            return INITIATED;
        } else {
            boolean isDownloading = false;
            boolean isSuspended = false;
            boolean isPaused = false;
            boolean isDone = true;
            for (IDownloadableFilePart part: getDownloadableParts()) {
                final DownloadStatus status = part.getStatus();
                if (status != DONE) {
                    isDone = false;
                    if (status == ERROR || status == CANCELLED) {
                        return status;
                    } else if (status == DOWNLOADING) {
                        isDownloading = true;
                    } else if (status == SUSPENDED) {
                        isSuspended = true;
                    } else if (status == PAUSED) {
                        isPaused = true;
                    }
                }
            }
            if (isDone)
                return DONE;
            if (isDownloading)
                return DOWNLOADING;
            else if (isPaused)
                return PAUSED;
            else if (isSuspended)
                return SUSPENDED;
            else
                return PENDING;
        }
    }

    @Override
    public int getActiveThreadCount() {
        return (int) getDownloadableParts().stream().filter(p -> p.getStatus() == DOWNLOADING).count();
    }

    @Override
    public int getMaxThreadCount() {
        return maxThreadCount;
    }

    @Override
    public double getProgress() {
        return getDownloadableParts().stream()
                .mapToDouble(IDownloadableFilePart::getProgress)
                .average()
                .orElse(0);
    }

    @Override
    public synchronized void pause() {
        getDownloadableParts().stream().forEach(IDownloadableFilePart::pause);
    }

    @Override
    public synchronized void start() {
        if (getDownloadableParts().isEmpty()) {
            createFileParts();
        } else {
            getDownloadableParts().stream().forEach(IDownloadableFilePart::start);
        }
    }

    private void createFileParts() {
        try {
            final HttpHead httpRequest = new HttpHead(url.toURI());
            httpRequest.addHeader(new BasicHeader("Range", "bytes=0-"));
            httpRequest.setConfig(headRequestConfig);
            logger.trace("Executing request " + httpRequest.getURI());

            final HttpResponse response = httpClient.execute(httpRequest);
            final int statusCode = response.getStatusLine().getStatusCode();
            logger.trace("HTTP response code: " + statusCode);
            logger.trace("HTTP response reason: " + response.getStatusLine().getReasonPhrase());

            if (statusCode >= 200 && statusCode < 300) { //Success
                final Header contentLengthHeader = response.getFirstHeader("content-length");
                final long contentLength;
                if (contentLengthHeader != null && contentLengthHeader.getValue() != null &&
                        contentLengthHeader.getValue().matches("[0-9]+")) {
                    logger.trace("Content length: " + contentLengthHeader.getValue());
                    contentLength = Long.parseLong(contentLengthHeader.getValue());
                } else {
                    logger.warn("Received a HTTP HEAD response without valid content-length. " +
                            "We will try to download it in a single thread");
                    contentLength = -1;
                }

                try {
                    filePartsLock.lock();
                    if (maxThreadCount > 1 && contentLength > 0 && statusCode == 206) {
                        isPartialDownloadSupported = true;

                        final long chunkSize = contentLength / maxThreadCount;
                        for (int i = 0; i < maxThreadCount; ++i) {
                            final DownloadableFilePart part = new DownloadableFilePart(
                                    new File(outputFile.getAbsolutePath() + "_part" + i),
                                    url,
                                    i * chunkSize,
                                    i == maxThreadCount - 1 ? -1 : chunkSize);
                            fileParts.add(part);
                        }
                    } else { //single thread
                        final DownloadableFilePart part = new DownloadableFilePart(
                                new File(outputFile.getAbsolutePath() + "_part" + 0),
                                url, 0, -1);
                        fileParts.add(part);

                        //TODO: no copy
                    }
                } finally {
                    filePartsLock.unlock();
                }
            } else {
                logger.error("Download failed - bad HTTP status code: " + statusCode);
                fatalErrorHappened = true;
            }
        } catch (IOException e) {
            logger.error("Connection to a remote server has failed", e);
            fatalErrorHappened = true;
            //TODO: store error message maybe?
        } catch (URISyntaxException e) {
            throw new RuntimeException("Incorrect URL", e); //FIXME: checked exception or log message
        }
    }

    @Override
    public synchronized void cancel() {
        getDownloadableParts().stream().forEach(IDownloadableFilePart::cancel);
    }

    @Override
    public boolean isPartialDownloadSupported() {
        return isPartialDownloadSupported;
    }

    @NotNull
    @Override
    public Collection<IDownloadableFilePart> getDownloadableParts() { //TODO: ensure correct order
        if (filePartsLock.tryLock()) {
            final List<IDownloadableFilePart> res = Collections.unmodifiableList(fileParts);
            filePartsLock.unlock();
            return res;
        } else {
            return Collections.emptyList();
        }
    }

    //FIXME using statuses
    boolean saved = false;

    @Override
    public synchronized boolean saveToDisk() throws IOException {
        if (saved) return false; //FIXME
        saved = true;

        try (OutputStream outputFileStream = new FileOutputStream(outputFile)) {
            for (IDownloadableFilePart part : getDownloadableParts()) {
                //TODO: Create a new file, maybe
                Files.copy(part.getOutputFile().toPath(), outputFileStream);
                Files.delete(part.getOutputFile().toPath());
            }
            return true;
        }
    }

    @NotNull
    @Override
    public File getOutputFile() {
        return outputFile;
    }
}
