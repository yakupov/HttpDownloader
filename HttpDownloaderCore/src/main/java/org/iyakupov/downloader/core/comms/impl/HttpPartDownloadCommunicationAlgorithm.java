package org.iyakupov.downloader.core.comms.impl;

import org.iyakupov.downloader.core.comms.CommunicationStatus;
import org.iyakupov.downloader.core.comms.ICommunicationAlgorithm;
import org.iyakupov.downloader.core.comms.ICommunicationComponent;
import org.iyakupov.downloader.core.comms.ICommunicationResult;
import org.iyakupov.downloader.core.dispatch.IDispatchingQueue;
import org.iyakupov.downloader.core.file.internal.IDownloadableFileInt;
import org.iyakupov.downloader.core.file.IDownloadableFilePart;
import org.iyakupov.downloader.core.file.internal.IDownloadableFilePartInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;

import static org.iyakupov.downloader.core.DownloadStatus.*;

/**
 * This algorithm downloads data from HTTP stream to a temporary file
 */
public class HttpPartDownloadCommunicationAlgorithm implements ICommunicationAlgorithm {
    private final static long SPEED_MEASURE_THRESHOLD = (long) 1e9; //in nS
    private final static int BUFFER_SIZE = 4 * 1024; //4KBytes

    private final static Logger logger = LoggerFactory.getLogger(HttpPartDownloadCommunicationAlgorithm.class);


    private final int priority;
    private final IDispatchingQueue dispatcher;
    private final ICommunicationComponent comm;
    private final IDownloadableFilePartInt filePart;

    public HttpPartDownloadCommunicationAlgorithm(int priority, IDispatchingQueue dispatcher, ICommunicationComponent comm, IDownloadableFilePartInt filePart) {
        this.priority = priority;
        this.dispatcher = dispatcher;
        this.comm = comm;
        this.filePart = filePart;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void run() {
        try {
            if (filePart.getStatus() != SUSPENDED && filePart.getStatus() != PENDING) {
                return;
            }
            logger.trace("Started task, file = " + filePart.getOutputFile());
            filePart.start();

            final ICommunicationResult communicationResult = comm.downloadRemoteFile(filePart.getLocator(),
                    filePart.getCurrentStartPosition(), filePart.getRemainingLength());
            if (filePart.getRemainingLength() < 0) {
                logger.trace("Updating total length of chunk " + filePart.getOutputFile() +
                        ". Now it's " + communicationResult.getSize());
                filePart.updateTotalLength(communicationResult.getSize());
            }

            if (communicationResult.getResponseDataStream() != null &&
                    (communicationResult.getResponseCode() == CommunicationStatus.OK ||
                            communicationResult.getResponseCode() == CommunicationStatus.PARTIAL_CONTENT_OK)) {

                final InputStream inputStream = communicationResult.getResponseDataStream();
                try (OutputStream outputFileStream = new FileOutputStream(filePart.getOutputFile(), true)) {
                    long bytesSinceLastMeasure = 0;
                    long lastMeasureTimestamp = System.nanoTime();
                    final byte[] buffer = new byte[BUFFER_SIZE];
                    int lastRead;
                    while ((lastRead = inputStream.read(buffer)) >= 0) {
                        //Copy
                        outputFileStream.write(buffer, 0, lastRead);
                        outputFileStream.flush();

                        //Increment counters
                        filePart.incrementDownloadedBytesCount(lastRead);
                        bytesSinceLastMeasure += lastRead;
                        final long currentTime = System.nanoTime();
                        if (currentTime - lastMeasureTimestamp > SPEED_MEASURE_THRESHOLD) {
                            final double interval = ((double) (currentTime - lastMeasureTimestamp)) / 1e9;
                            filePart.setDownloadSpeed((int) ((double) bytesSinceLastMeasure / interval));
                            bytesSinceLastMeasure = 0;
                            lastMeasureTimestamp = System.nanoTime();
                        }

                        //Check status
                        if (filePart.getStatus() == PAUSED || filePart.getStatus() == CANCELLED) {
                            return;
                        } else if (filePart.getStatus() == SUSPENDED) {
                            logger.info("Task " + filePart.getOutputFile() + " evicted, re-submitting");
                            dispatcher.submitEvictedTask(filePart);
                            return;
                        } else if (filePart.getStatus() != DOWNLOADING) {
                            logger.error("Running task was suspended with an unexpected status: " + filePart.getStatus());
                            return;
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            logger.error("Failed to write to temporary file", e);
            filePart.completeWithError("Failed to write to temporary file. File not found: " + e.getMessage());
            return;
        } catch (IOException e) {
            logger.error("Failed to read from HTTP stream or to write to the temporary file stream", e);
            filePart.completeWithError("IO Exception: " + e.getMessage());
            return;
        } finally {
            filePart.setDownloadSpeed(0);
        }

        try {
            if (filePart.getRemainingLength() <= 0) {
                final IDownloadableFileInt file = dispatcher.getParentFile(filePart);
                filePart.completeSuccessfully();
                if (file.decrementAndGetNonSuccessfullyDownloadedPartsCount() == 0) {
                    combineTemporaryFiles(file);
                }
            } else {
                filePart.completeWithError("Stream has ended, but remaining length is greater than zero");
            }
        } catch (IOException e) {
            logger.error("Failed to copy data from temporary file to final one", e);
            filePart.completeWithError("Failed to copy data from temporary file to final one: " + e.getMessage());
        }
    }

    /**
     * Combine all temporary files into one resulting file.
     *
     * @param file File download request
     * @throws IOException
     */
    private void combineTemporaryFiles(IDownloadableFileInt file) throws IOException {
        if (file.getDownloadableParts().size() <= 1) {
            file.markAsSaved();
            return; //Already in the resulting file
        }

        try (OutputStream outputFileStream = new FileOutputStream(file.getOutputFile())) {
            for (IDownloadableFilePart part : file.getDownloadableParts()) {
                logger.trace("Copy data from " + part.getOutputFile() + " to " + file.getOutputFile());

                //TODO: Create a new file if exists, maybe
                Files.copy(part.getOutputFile().toPath(), outputFileStream);
                Files.delete(part.getOutputFile().toPath());
            }

            file.markAsSaved();
        }
    }
}
