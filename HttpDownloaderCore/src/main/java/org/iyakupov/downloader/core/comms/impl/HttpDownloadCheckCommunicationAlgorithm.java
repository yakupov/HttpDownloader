package org.iyakupov.downloader.core.comms.impl;

import org.iyakupov.downloader.core.comms.CommunicationStatus;
import org.iyakupov.downloader.core.comms.ICommunicationAlgorithm;
import org.iyakupov.downloader.core.comms.ICommunicationComponent;
import org.iyakupov.downloader.core.comms.ICommunicationResult;
import org.iyakupov.downloader.core.dispatch.IDispatchingQueue;
import org.iyakupov.downloader.core.file.internal.IDownloadableFileInt;
import org.iyakupov.downloader.core.file.internal.impl.DownloadableFilePart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Algorithm that sends HTTP HEAD to server, obtains file size and checks, whether partial download is possible.
 */
public class HttpDownloadCheckCommunicationAlgorithm implements ICommunicationAlgorithm {
    private final static Logger logger = LoggerFactory.getLogger(HttpPartDownloadCommunicationAlgorithm.class);

    private final IDispatchingQueue dispatcher;
    private final ICommunicationComponent comm;
    private final IDownloadableFileInt file;

    public HttpDownloadCheckCommunicationAlgorithm(IDispatchingQueue dispatcher, ICommunicationComponent comm, IDownloadableFileInt file) {
        this.dispatcher = dispatcher;
        this.comm = comm;
        this.file = file;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public void run() {
        logger.trace("Started " + getClass().getSimpleName() + " for task " + file.getLocator());
        final ICommunicationResult communicationResult = comm.checkRemoteFile(file.getLocator());

        final int maxThreadCount = file.getMaxThreadCount();
        if (maxThreadCount > 1 && communicationResult.getResponseCode() == CommunicationStatus.PARTIAL_CONTENT_OK) {
            final long chunkSize = communicationResult.getSize() / maxThreadCount;
            for (int i = 0; i < maxThreadCount; ++i) {
                logger.trace("Trying to add part " + i + " of " + maxThreadCount + "...");
                final File outputFile = new File(file.getOutputFile().getAbsolutePath() + "_part" + i);
                //noinspection ResultOfMethodCallIgnored
                outputFile.delete();
                final DownloadableFilePart part = new DownloadableFilePart(
                        outputFile,
                        file.getLocator(),
                        i * chunkSize,
                        i == maxThreadCount - 1 ? -1 : chunkSize);
                file.addPart(part);
                dispatcher.submitTask(file, part);
            }
        } else { //single thread
            //noinspection ResultOfMethodCallIgnored
            file.getOutputFile().delete(); //TODO: create new file maybe?
            final DownloadableFilePart part = new DownloadableFilePart(file.getOutputFile(), file.getLocator(), 0, -1);
            if (communicationResult.getResponseCode() != CommunicationStatus.PARTIAL_CONTENT_OK) {
                part.setDownloadResumeNotSupported();
            }
            file.addPart(part);
            dispatcher.submitTask(file, part);
        }
    }
}
