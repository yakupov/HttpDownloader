package org.iyakupov.downloader.core.impl;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.iyakupov.downloader.core.DownloadStatus;
import org.iyakupov.downloader.core.IDownloadableFilePart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.CopyOption;
import java.nio.file.Files;

import static org.iyakupov.downloader.core.DownloadStatus.*;

/**
 * Created by Ilia on 26.03.2016.
 */
public class DownloadableFilePart implements IDownloadableFilePart {
    private final File outputFile;
    private final URL url;
    private final long start;
    private final long length;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final HttpClient httpClient = HttpClients.createDefault();
    private final RequestConfig headRequestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(3000)
            .setConnectTimeout(3000)
            .setSocketTimeout(6000)
            .build(); //TODO: maybe parms, maybe constructor? Maybe single client for all?


    private DownloadStatus status = PENDING;

    public DownloadableFilePart(File outputFile, URL url, long start, long length) {
        this.outputFile = outputFile;
        this.url = url;
        this.start = start;
        this.length = length;
    }

    @Override
    public int getDownloadSpeed() {
        return 0; //FIXME
    }

    @Override
    public DownloadStatus getStatus() {
        return status;
    }

    @Override
    public double getProgress() {
        return 0; //FIXME
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
                httpRequest.addHeader(new BasicHeader("Range", "bytes=" + start +  "-" + end));
                httpRequest.setConfig(headRequestConfig);
                logger.trace("Executing request " + httpRequest.getURI());

                final HttpResponse response = httpClient.execute(httpRequest);

                logger.trace("HTTP response code: " + response.getStatusLine().getStatusCode());
                logger.trace("HTTP response reason: " + response.getStatusLine().getReasonPhrase());

                if (response.getEntity() != null) {

                    final InputStream inputStream = response.getEntity().getContent();
                    try (OutputStream outputFileStream = new FileOutputStream(outputFile);) {
                        long totalBytesCopied = 0;
                        final byte[] buf = new byte[1000];

                        int read;
                        while ((read = inputStream.read(buf)) >= 0) {
                            totalBytesCopied += read;
                            //logger.trace("Total bytes downloaded in this thread: " + totalBytesCopied);
                            outputFileStream.write(buf, 0, read);
                            outputFileStream.flush();
                        }

                        //response.getEntity().writeTo(outputFileStream);
                        //outputFileStream.flush();
                    }


                    //TODO: remove if exists
                   // Files.copy(inputStream, outputFile.toPath());
                    //TODO: copy using small portions, check status (maybe pause etc)
                    //TODO: controlled download
                    status = DONE;
                } else {
                    logger.error("No entity in response");
                }

            } catch (URISyntaxException | IOException e) {
                status = ERROR;

                logger.error("HTTP request processing failed", e);
                throw new RuntimeException(e); //FIXME
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
