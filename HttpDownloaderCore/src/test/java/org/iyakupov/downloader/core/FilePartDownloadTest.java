package org.iyakupov.downloader.core;

import org.iyakupov.downloader.core.comms.CommunicationStatus;
import org.iyakupov.downloader.core.comms.ICommunicationAlgorithm;
import org.iyakupov.downloader.core.comms.ICommunicationComponent;
import org.iyakupov.downloader.core.comms.ICommunicationResult;
import org.iyakupov.downloader.core.comms.impl.HttpCommunicationResult;
import org.iyakupov.downloader.core.comms.impl.HttpPartDownloadCommunicationAlgorithm;
import org.iyakupov.downloader.core.dispatch.IDispatchingQueue;
import org.iyakupov.downloader.core.dispatch.TaskPriority;
import org.iyakupov.downloader.core.file.IDownloadableFile;
import org.iyakupov.downloader.core.file.internal.IDownloadableFileInt;
import org.iyakupov.downloader.core.file.internal.IDownloadableFilePartInt;
import org.iyakupov.downloader.core.file.internal.impl.DownloadableFile;
import org.iyakupov.downloader.core.file.internal.impl.DownloadableFilePart;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Arrays;
import java.util.function.Consumer;

import static org.iyakupov.downloader.core.DownloadStatus.ERROR;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.iyakupov.downloader.core.comms.CommunicationStatus.*;
import static org.iyakupov.downloader.core.DownloadStatus.*;

/**
 * UT for HttpPartDownloadCommunicationAlgorithm
 */
public class FilePartDownloadTest {
    private static class ExpectedState {
        @NotNull
        final CommunicationStatus communicationStatus;
        @NotNull
        final DownloadStatus partStatus;
        @NotNull
        final DownloadStatus fileStatusAfterThisStep;
        @Nullable
        final Consumer<IDownloadableFile> actionOnFile;

        public ExpectedState(@NotNull CommunicationStatus communicationStatus,
                             @NotNull DownloadStatus partStatus,
                             @NotNull DownloadStatus fileStatusAfterThisStep) {
            this(communicationStatus, partStatus, fileStatusAfterThisStep, null);
        }

        public ExpectedState(@NotNull CommunicationStatus communicationStatus,
                             @NotNull DownloadStatus partStatus,
                             @NotNull DownloadStatus fileStatusAfterThisStep,
                             @Nullable Consumer<IDownloadableFile> actionOnFile) {
            this.communicationStatus = communicationStatus;
            this.partStatus = partStatus;
            this.fileStatusAfterThisStep = fileStatusAfterThisStep;
            this.actionOnFile = actionOnFile;
        }
    }

    private final static Logger logger = LoggerFactory.getLogger(FilePartDownloadTest.class);

    /**
     * Manually create a file download request and its part download requests.
     * The number of parts will be the same as the number of expected states.
     * <p>
     * File parts are being processed step-by-step. ExpectedState defines the external RC that
     * will be returned by remote server on the corresponding step, expected statuses of the file
     * and the current part and action on this file that should be executed after the this step.
     *
     * @param fileSize       File size
     * @param expectedStates States. Number of states equals to the number of parts.
     */
    public void testCommon(final int fileSize,
                           final ExpectedState... expectedStates) {
        final String fileName = "file.bin";
        final String fileUrl = "http://my.site/" + fileName;

        final File outputDir = new File("target/ut_temp_dir/");
        logger.debug("Temporary file output dir: " + outputDir.getAbsolutePath());
        logger.debug("Deleting old tmpdir: " + outputDir.delete());
        logger.debug("Re-creating tmpdir: " + outputDir.mkdir());

        final int desiredPartsCount = expectedStates.length;
        final int chunkSize = fileSize / desiredPartsCount;

        final IDownloadableFileInt file = new DownloadableFile(fileUrl, outputDir, desiredPartsCount);
        assertEquals(INITIATED, file.getStatus());

        final IDispatchingQueue dispatchingQueue = mock(IDispatchingQueue.class);
        doNothing().when(dispatchingQueue).submitEvictedTask(any());
        when(dispatchingQueue.getParentFile(any())).thenReturn(file);

        final IDownloadableFilePartInt[] partsArray = new IDownloadableFilePartInt[desiredPartsCount];
        for (int i = 0; i < desiredPartsCount; ++i) {
            final File outputFile = new File(file.getOutputFile().getAbsolutePath() + "_part" + i);
            //noinspection ResultOfMethodCallIgnored
            outputFile.delete();
            final IDownloadableFilePartInt part = new DownloadableFilePart(
                    outputFile,
                    file.getLocator(),
                    i * chunkSize,
                    i < desiredPartsCount - 1 ? chunkSize : -1);
            file.addPart(part);
            partsArray[i] = part;
        }

        assertEquals(PENDING, file.getStatus());

        for (int i = 0; i < desiredPartsCount; ++i) {
            logger.trace("Executing download of the part #" + i +
                    ", expected communication status is " + expectedStates[i].communicationStatus);

            final ICommunicationComponent communicationComponent = mock(ICommunicationComponent.class);
            when(communicationComponent.downloadRemoteFile(anyString(), anyLong(), anyLong()))
                    .thenAnswer(createResponseGenerator(expectedStates[i].communicationStatus,
                            i < desiredPartsCount - 1 ? chunkSize : chunkSize + fileSize % chunkSize));

            final ICommunicationAlgorithm communicationAlgorithm =
                    new HttpPartDownloadCommunicationAlgorithm(TaskPriority.NEW_PART_DOWNLOAD.getPriority(),
                            dispatchingQueue, communicationComponent, partsArray[i]);
            communicationAlgorithm.run();

            assertEquals(expectedStates[i].partStatus, partsArray[i].getStatus());
            assertEquals(expectedStates[i].fileStatusAfterThisStep, file.getStatus());
            if (partsArray[i].getStatus() == DONE && i < desiredPartsCount - 1) {
                assertEquals(chunkSize, partsArray[i].getOutputFile().length());
            }

            if (expectedStates[i] != null) {
                final ExpectedState currentState = expectedStates[i];
                if (currentState != null && currentState.actionOnFile != null) {
                    currentState.actionOnFile.accept(file);
                }
            }
        }

        if (file.getStatus() == DONE)
            assertEquals(fileSize, file.getOutputFile().length());

        verify(dispatchingQueue, never()).submitEvictedTask(any());
    }

    /**
     * Defines the behavior of a {@link ICommunicationComponent} mock
     *
     * @param rc         {@link CommunicationStatus} to return
     * @param dataLength Number of bytes in the returned data stream
     * @return Mock behavior description for Mockito
     */
    @NotNull
    private Answer<ICommunicationResult> createResponseGenerator(CommunicationStatus rc, int dataLength) {
        return invocationOnMock -> {
            final Object[] invocationArguments = invocationOnMock.getArguments();
            assert (invocationArguments.length == 3);
            final byte[] res = new byte[dataLength];
            Arrays.fill(res, ((Long) invocationArguments[1]).byteValue());
            return new HttpCommunicationResult(rc, "Irrelevant", new ByteArrayInputStream(res), res.length);
        };
    }

    @Test
    public void testSuccessfulPartialDownload() {
        testCommon(100,
                new ExpectedState(PARTIAL_CONTENT_OK, DONE, PENDING),
                new ExpectedState(PARTIAL_CONTENT_OK, DONE, PENDING),
                new ExpectedState(PARTIAL_CONTENT_OK, DONE, DONE)
        );
    }

    @Test
    public void testWithError() {
        testCommon(100,
                new ExpectedState(PARTIAL_CONTENT_OK, DONE, PENDING),
                new ExpectedState(PARTIAL_CONTENT_NOK, ERROR, ERROR),
                new ExpectedState(PARTIAL_CONTENT_OK, DONE, ERROR)
        );
    }

    @Test
    public void testWithPartialContentError() {
        testCommon(100,
                new ExpectedState(PARTIAL_CONTENT_OK, DONE, PENDING),
                new ExpectedState(PARTIAL_CONTENT_OK, DONE, PENDING),
                new ExpectedState(OK, ERROR, ERROR)
        );
    }

    @Test
    public void testCancel() {
        testCommon(100,
                new ExpectedState(PARTIAL_CONTENT_OK, DONE, PENDING),
                new ExpectedState(PARTIAL_CONTENT_OK, DONE, PENDING, IDownloadableFile::cancel),
                new ExpectedState(PARTIAL_CONTENT_OK, CANCELLED, CANCELLED)
        );
    }

    @Test
    public void testPause() {
        testCommon(100,
                new ExpectedState(PARTIAL_CONTENT_OK, DONE, PENDING),
                new ExpectedState(PARTIAL_CONTENT_OK, DONE, PENDING, IDownloadableFile::pause),
                new ExpectedState(PARTIAL_CONTENT_OK, PAUSE_CONFIRMED, PAUSED)
        );
    }

    @Test
    public void testPauseThenCancel() {
        testCommon(100,
                new ExpectedState(PARTIAL_CONTENT_OK, DONE, PENDING),
                new ExpectedState(PARTIAL_CONTENT_OK, DONE, PENDING, IDownloadableFile::pause),
                new ExpectedState(PARTIAL_CONTENT_OK, PAUSE_CONFIRMED, PAUSED, IDownloadableFile::cancel),
                new ExpectedState(PARTIAL_CONTENT_OK, CANCELLED, CANCELLED)
        );
    }

    @Test
    public void testCancelThenPause() {
        testCommon(100,
                new ExpectedState(PARTIAL_CONTENT_OK, DONE, PENDING),
                new ExpectedState(PARTIAL_CONTENT_OK, DONE, PENDING, IDownloadableFile::cancel),
                new ExpectedState(PARTIAL_CONTENT_OK, CANCELLED, CANCELLED, IDownloadableFile::pause),
                new ExpectedState(PARTIAL_CONTENT_OK, CANCELLED, CANCELLED)
        );
    }


    @Test
    public void testErrorThenPause() {
        testCommon(100,
                new ExpectedState(PARTIAL_CONTENT_OK, DONE, PENDING),
                new ExpectedState(OK, ERROR, ERROR, IDownloadableFile::pause),
                new ExpectedState(PARTIAL_CONTENT_OK, PAUSE_CONFIRMED, ERROR)
        );
    }

    @Test
    public void testErrorThenCancel() {
        testCommon(100,
                new ExpectedState(PARTIAL_CONTENT_OK, DONE, PENDING),
                new ExpectedState(OK, ERROR, ERROR, IDownloadableFile::cancel),
                new ExpectedState(PARTIAL_CONTENT_OK, CANCELLED, CANCELLED)
        );
    }

    @Test
    public void testPauseAndResume() {
        testCommon(100,
                new ExpectedState(PARTIAL_CONTENT_OK, DONE, PENDING),
                new ExpectedState(PARTIAL_CONTENT_OK, DONE, PENDING, f -> {
                    f.pause();
                    f.getDownloadableParts().forEach(p -> ((IDownloadableFilePartInt) p).resumeDownload());
                }),
                new ExpectedState(PARTIAL_CONTENT_OK, DONE, DONE)
        );
    }

}
