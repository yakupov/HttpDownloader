package org.iyakupov.downloader.core.comms.impl;

import org.iyakupov.downloader.core.comms.CommunicationStatus;
import org.iyakupov.downloader.core.comms.ICommunication;
import org.iyakupov.downloader.core.comms.ICommunicatingComponent;
import org.iyakupov.downloader.core.comms.ICommunicationResult;
import org.iyakupov.downloader.core.dispatch.IDispatchingQueue;
import org.iyakupov.downloader.core.dispatch.TaskPriority;
import org.iyakupov.downloader.core.file.internal.IManagedDownloadableFile;
import org.iyakupov.downloader.core.file.IDownloadableFilePart;
import org.iyakupov.downloader.core.file.internal.IManagedDownloadableFilePart;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;

import static org.iyakupov.downloader.core.file.state.FilePartDownloadState.*;

/**
 * This algorithm downloads data from HTTP stream to a temporary file
 */
public class HttpPartDownloadCommunication implements ICommunication {
    //TODO: parameters
    public final static long SPEED_MEASURE_THRESHOLD = (long) 1e9; //in nS
    public final static int BUFFER_SIZE = 4 * 1024; //4KBytes

    private final Logger logger = LoggerFactory.getLogger(HttpPartDownloadCommunication.class);

    @NotNull
    private final TaskPriority priority;

    @NotNull
    private final IDispatchingQueue dispatcher;

    @NotNull
    private final ICommunicatingComponent comm;

    @NotNull
    private final IManagedDownloadableFile file;

    @NotNull
    private final IManagedDownloadableFilePart filePart;

    public HttpPartDownloadCommunication(@NotNull TaskPriority priority,
                                         @NotNull IDispatchingQueue dispatcher,
                                         @NotNull ICommunicatingComponent comm,
                                         @NotNull IManagedDownloadableFile file,
                                         @NotNull IManagedDownloadableFilePart filePart) {
        this.priority = priority;
        this.dispatcher = dispatcher;
        this.comm = comm;
        this.file = file;
        this.filePart = filePart;
    }

    @Override
    public int getPriority() {
        return priority.getNumericValue();
    }

    @Override
    public void run() {
        try {
            //FIXME
            if (filePart.getStatus() != PENDING) {
                logger.error("Failed to start task " + filePart.getOutputFile() + ". Incorrect status: " + filePart.getStatus());
                return;
            }
            logger.debug("Started task, file = " + filePart.getOutputFile());
            filePart.start();

            try (final ICommunicationResult communicationResult = comm.downloadRemoteFile(
                    filePart.getLocator(), filePart.getCurrentStartPosition(), filePart.getRemainingLength())) {
                if (filePart.getRemainingLength() < 0) {
                    logger.debug("Updating total length of chunk " + filePart.getOutputFile() +
                            ". Now it's " + communicationResult.getSize());
                    if (!filePart.updateTotalLength(communicationResult.getSize()))
                        logger.error("Failed to update the length of part " + filePart +
                                ". Unexpected concurrent modification");
                }

                final boolean statusOk = communicationResult.getResponseCode() == CommunicationStatus.PARTIAL_CONTENT_OK ||
                        !filePart.isDownloadResumeSupported() && communicationResult.getResponseCode() == CommunicationStatus.OK;

                final InputStream responseDataStream = communicationResult.getResponseDataStream();
                if (responseDataStream != null && statusOk) {
                    try (OutputStream outputFileStream = new FileOutputStream(filePart.getOutputFile(), true)) {
                        long bytesSinceLastMeasure = 0;
                        long lastMeasureTimestamp = System.nanoTime();
                        final byte[] buffer = new byte[BUFFER_SIZE];
                        int lastRead;
                        while ((lastRead = responseDataStream.read(buffer)) > 0) {
                            if (filePart.getRemainingLength() >= 0 && lastRead > filePart.getRemainingLength())
                                logger.warn("End of file was expected (basing on content-length), but the stream " +
                                        "has not ended. Continuing download...");

                            //Copy
                            logger.trace("Wrote " + lastRead + " bytes to " + filePart.getOutputFile());
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
                            if (filePart.getStatus() == CANCELLED) {
                                logger.debug("Task " + filePart.getOutputFile() + " cancelled, exiting worker");
                                return;
                            } else if (filePart.getRemainingLength() > 0) {
                                if (filePart.getStatus() == PAUSE_REQUESTED) {
                                    logger.debug("Task " + filePart.getOutputFile() + " paused, exiting worker");
                                    filePart.confirmPause();
                                    return;
                                } else if (filePart.getStatus() == SUSPEND_REQUESTED) {
                                    logger.info("Task " + filePart.getOutputFile() + " evicted, re-submitting");
                                    dispatcher.reSubmitEvictedTask(file, filePart);
                                    return;
                                } else if (filePart.getStatus() != DOWNLOADING) {
                                    logger.error("Running task was aborted with an unexpected status: " + filePart.getStatus());
                                    return;
                                }
                            }
                        }
                    }
                } else if (communicationResult.getResponseCode() == CommunicationStatus.OK) {
                    final String errorMessage = "Expected to be able to perform partial download of this file part, " +
                            "but the server has returned unsuitable response code";
                    logger.error(errorMessage);
                    filePart.completeWithError(errorMessage);
                    return;
                } else {
                    final String errorMessage = "Bad response code: " + communicationResult.getResponseCode();
                    logger.error(errorMessage);
                    filePart.completeWithError(errorMessage);
                    return;
                }
            }
        } catch (FileNotFoundException e) {
            logger.error("Failed to write to a temporary file. File not found.", e);
            filePart.completeWithError("Failed to write to a temporary file. File not found: " + e.getMessage());
            return;
        } catch (IOException | IllegalStateException e) {
            if (filePart.getStatus() != CANCELLED) {
                logger.error("Failed to read from HTTP stream or to write to the temporary file stream", e);
                filePart.completeWithError("IO Exception: " + e.getMessage());
            }
            return;
        } finally {
            filePart.setDownloadSpeed(0);
        }

        logger.debug("Finished downloading part " + filePart.getOutputFile());

        try {
            if (filePart.getRemainingLength() <= 0) {
                filePart.completeSuccessfully();
                if (file.decrementAndGetNonSuccessfullyDownloadedPartsCount() == 0) {
                    combineTemporaryFiles(file);
                }
            } else {
                logger.error("Stream has ended, but remaining length is greater than zero");
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
     * @throws IOException in case of any problems reading or writing files
     */
    private void combineTemporaryFiles(IManagedDownloadableFile file) throws IOException {
        if (file.getDownloadableParts().size() <= 1) {
            file.markAsSaved();
            return; //Already in the resulting file
        }

        try (OutputStream outputFileStream = new FileOutputStream(file.getOutputFile())) {
            for (IDownloadableFilePart part : file.getDownloadableParts()) {
                logger.debug("Copy data from " + part.getOutputFile() + " to " + file.getOutputFile());

                //TODO: Create a new file if exists, maybe
                Files.copy(part.getOutputFile().toPath(), outputFileStream);
                Files.delete(part.getOutputFile().toPath());
            }

            file.markAsSaved();
        }
    }
}
