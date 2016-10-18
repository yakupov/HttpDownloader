package org.iyakupov.downloader.core.comms.impl;

import org.iyakupov.downloader.core.AppSettings;
import org.iyakupov.downloader.core.comms.CommunicationStatus;
import org.iyakupov.downloader.core.comms.ICommunication;
import org.iyakupov.downloader.core.comms.ICommunicatingComponent;
import org.iyakupov.downloader.core.comms.ICommunicationResult;
import org.iyakupov.downloader.core.dispatch.IDispatchingQueue;
import org.iyakupov.downloader.core.dispatch.TaskPriority;
import org.iyakupov.downloader.core.file.internal.IManagedDownloadableFile;
import org.iyakupov.downloader.core.file.IDownloadableFilePart;
import org.iyakupov.downloader.core.file.internal.IManagedDownloadableFilePart;
import org.iyakupov.downloader.core.file.state.FilePartLengthState;
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
            //Paused task
            if (filePart.getStatus() == PAUSED) {
                return;
            } else if (filePart.getStatus() != PENDING) {
                logger.error("Failed to start task because of incorrect status" + filePart);
                return;
            }
            logger.debug("Started task, file = " + filePart.getOutputFile());
            filePart.start();

            //Unsaved file
            if (filePart.getRemainingLength() <= 0 && filePart.getLengthState() != FilePartLengthState.YET_UNKNOWN) {
                if (file.getNonSuccessfullyDownloadedPartsCount() == 0) {
                    try {
                        combineTemporaryFiles(file);
                    } catch (IOException e) {
                        error("Failed to copy data from temporary file to final one", e);
                    }
                } else {
                    error("File part with PENDING status of has zero remaining length, but is not the last one: " +
                            "incomplete parts count is greater then zero for the file. Part: " + filePart, null);
                }
                return;
            }

            //Need to download the data
            try (final ICommunicationResult communicationResult = comm.downloadRemoteFile(
                    filePart.getLocator(), filePart.getCurrentStartPosition(), filePart.getRemainingLength())) {

                if (filePart.getLengthState() == FilePartLengthState.YET_UNKNOWN) {
                    logger.debug("Updating total length of chunk " + filePart + ". Now it's " + communicationResult.getSize());
                    if (!filePart.updateTotalLength(communicationResult.getSize()))
                        logger.error("Failed to update the length of part " + filePart);
                }

                final boolean statusOk = communicationResult.getResponseCode() == CommunicationStatus.PARTIAL_CONTENT_OK ||
                        !filePart.isDownloadResumeSupported() && communicationResult.getResponseCode() == CommunicationStatus.OK;
                final InputStream responseDataStream = communicationResult.getResponseDataStream();
                if (responseDataStream != null && statusOk) {
                    try (OutputStream outputFileStream = new FileOutputStream(filePart.getOutputFile(), true)) {
                        long bytesSinceLastMeasure = 0;
                        long lastMeasureTimestamp = System.nanoTime();
                        final byte[] buffer = new byte[AppSettings.getDownloadBufferSize()];
                        int lastRead;

                        while ((lastRead = responseDataStream.read(buffer)) > 0) { //Timeout is set in the HTTP client
                            if (filePart.getLengthState() == FilePartLengthState.KNOWN && lastRead > filePart.getRemainingLength())
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
                            if (currentTime - lastMeasureTimestamp > AppSettings.getDownloadSpeedMeasureThreshold()) {
                                final double interval = ((double) (currentTime - lastMeasureTimestamp)) / 1e9;
                                filePart.setDownloadSpeed((int) ((double) bytesSinceLastMeasure / interval));
                                bytesSinceLastMeasure = 0;
                                lastMeasureTimestamp = System.nanoTime();
                            }

                            //Check status
                            if (filePart.getStatus() == CANCELLED) {
                                logger.debug("Task " + filePart + " cancelled, exiting worker");
                                return;
                            } else if (filePart.getRemainingLength() > 0) { //Download on halt
                                if (filePart.getStatus() == PAUSE_REQUESTED) {
                                    logger.debug("Task " + filePart + " paused, exiting worker");
                                    filePart.confirmPause();
                                    return;
                                } else if (filePart.getStatus() == SUSPEND_REQUESTED) {
                                    logger.info("Task " + filePart + " evicted, re-submitting");
                                    dispatcher.reSubmitEvictedTask(file, filePart);
                                    return;
                                } else if (filePart.getStatus() != DOWNLOADING) {
                                    logger.error("Running task was aborted with an unexpected status: " + filePart);
                                    return;
                                }
                            } else if (filePart.getLengthState() == FilePartLengthState.KNOWN){ //Seems to be completed
                                break;
                            }
                        }
                    }

                    //End of input stream
                    if (filePart.getLengthState() == FilePartLengthState.UNKNOWN ||
                            filePart.getRemainingLength() <= 0 && filePart.getLengthState() == FilePartLengthState.KNOWN) {
                        logger.debug("Finished downloading part  " + filePart);
                        filePart.completeSuccessfully();
                        if (file.decrementAndGetNonSuccessfullyDownloadedPartsCount() == 0) {
                            combineTemporaryFiles(file);
                        }
                    } else {
                        error("Stream has ended, but remaining length is greater than zero", null);
                    }
                } else if (communicationResult.getResponseCode() == CommunicationStatus.OK) {
                    error("Expected to be able to perform partial download of this file part, " +
                            "but the server has returned unsuitable response code", null);
                } else {
                    error("Bad response code: " + communicationResult.getResponseCode(), null);
                }
            }
        } catch (FileNotFoundException e) {
            error("Failed to write to a temporary file. File not found", e);
        } catch (IOException | IllegalStateException e) {
            if (filePart.getStatus() != CANCELLED) {
                error("Failed to read from HTTP stream or to write to the output file stream", e);
            }
        } finally {
            filePart.setDownloadSpeed(0);
        }
    }

    private void error(String errorMessage, Exception e) {
        if (e == null) {
            logger.error(errorMessage);
            filePart.completeWithError(errorMessage);
        } else {
            logger.error(errorMessage, e);
            filePart.completeWithError(errorMessage + "; Exception text: " + e);
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
                Files.copy(part.getOutputFile().toPath(), outputFileStream);
                Files.delete(part.getOutputFile().toPath());
            }
            file.markAsSaved();
        }
    }
}
