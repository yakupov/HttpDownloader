package org.iyakupov.downloader.core;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.iyakupov.downloader.core.comms.CommunicationStatus;
import org.iyakupov.downloader.core.comms.ICommunication;
import org.iyakupov.downloader.core.comms.ICommunicatingComponent;
import org.iyakupov.downloader.core.comms.ICommunicationResult;
import org.iyakupov.downloader.core.comms.impl.HttpCommunicationResult;
import org.iyakupov.downloader.core.comms.impl.HttpPartDownloadCommunication;
import org.iyakupov.downloader.core.dispatch.IDispatchingQueue;
import org.iyakupov.downloader.core.dispatch.TaskPriority;
import org.iyakupov.downloader.core.file.IDownloadableFile;
import org.iyakupov.downloader.core.file.internal.IManagedDownloadableFile;
import org.iyakupov.downloader.core.file.internal.IManagedDownloadableFilePart;
import org.iyakupov.downloader.core.file.internal.impl.DownloadableFile;
import org.iyakupov.downloader.core.file.internal.impl.DownloadableFilePart;
import org.iyakupov.downloader.core.file.state.FileDownloadState;
import org.iyakupov.downloader.core.file.state.FilePartDownloadState;
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

import static org.iyakupov.downloader.core.file.state.FileDownloadState.*;
import static org.iyakupov.downloader.core.comms.CommunicationStatus.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

/**
 * UT for HttpPartDownloadCommunication
 */
//FIXME: this test of state machine (or WTF is this) seems too complicated/useless
@SuppressWarnings("WeakerAccess") //TODO: remove suppression
public class FilePartDownloadTest {
    private static class ExpectedState {
        @NotNull
        final CommunicationStatus communicationStatus;
        @NotNull
        final FilePartDownloadState partStatus;
        @NotNull
        final FileDownloadState fileStatusAfterThisStep;
        @Nullable
        final Consumer<IDownloadableFile> actionOnFile;

        public ExpectedState(@NotNull CommunicationStatus communicationStatus,
                             @NotNull FilePartDownloadState partStatus,
                             @NotNull FileDownloadState fileStatusAfterThisStep) {
            this(communicationStatus, partStatus, fileStatusAfterThisStep, null);
        }

        public ExpectedState(@NotNull CommunicationStatus communicationStatus,
                             @NotNull FilePartDownloadState partStatus,
                             @NotNull FileDownloadState fileStatusAfterThisStep,
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

        final IManagedDownloadableFile file = new DownloadableFile(fileUrl, outputDir, desiredPartsCount);
        assertEquals(INITIATED, file.getStatus());

        final IDispatchingQueue dispatchingQueue = mock(IDispatchingQueue.class);
       // doNothing().when(dispatchingQueue).reSubmitEvictedTask(any());
       // when(dispatchingQueue.getParentFile(any())).thenReturn(file); //TODO: delete or rethink

        final IManagedDownloadableFilePart[] partsArray = new IManagedDownloadableFilePart[desiredPartsCount];
        for (int i = 0; i < desiredPartsCount; ++i) {
            final File outputFile = new File(file.getOutputFile().getAbsolutePath() + "_part" + i);
            //noinspection ResultOfMethodCallIgnored
            outputFile.delete();
            final IManagedDownloadableFilePart part = new DownloadableFilePart(
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

            final ICommunicatingComponent communicationComponent = mock(ICommunicatingComponent.class);
            when(communicationComponent.downloadRemoteFile(anyString(), anyLong(), anyLong()))
                    .thenAnswer(createResponseGenerator(expectedStates[i].communicationStatus,
                            i < desiredPartsCount - 1 ? chunkSize : chunkSize + fileSize % chunkSize));

            final ICommunication communicationAlgorithm =
                    new HttpPartDownloadCommunication(TaskPriority.NEW_PART_DOWNLOAD,
                            dispatchingQueue, communicationComponent, file, partsArray[i]);
            communicationAlgorithm.run();

            assertEquals(expectedStates[i].partStatus, partsArray[i].getStatus());
            assertEquals(expectedStates[i].fileStatusAfterThisStep, file.getStatus());
            if (partsArray[i].getStatus() == FilePartDownloadState.DONE && i < desiredPartsCount - 1) {
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

        verify(dispatchingQueue, never()).reSubmitEvictedTask(any(), any());
    }

    /**
     * Defines the behavior of a {@link ICommunicatingComponent} mock
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

            final HttpEntity responseEntity = mock(HttpEntity.class);
            when(responseEntity.getContent()).thenReturn(new ByteArrayInputStream(res));

            final CloseableHttpResponse response = mock(CloseableHttpResponse.class);
            when(response.getEntity()).thenReturn(responseEntity);
            doNothing().when(response).close();

            return new HttpCommunicationResult(rc, "Irrelevant", response, res.length);
        };
    }

    @Test
    public void testSuccessfulPartialDownload() {
        testCommon(100,
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.DONE, PENDING),
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.DONE, PENDING),
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.DONE, DONE)
        );
    }

    @Test
    public void testWithError() {
        testCommon(100,
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.DONE, PENDING),
                new ExpectedState(PARTIAL_CONTENT_NOK, FilePartDownloadState.FAILED, FAILED),
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.DONE, FAILED)
        );
    }

    @Test
    public void testWithPartialContentError() {
        testCommon(100,
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.DONE, PENDING),
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.DONE, PENDING),
                new ExpectedState(OK, FilePartDownloadState.FAILED, FAILED)
        );
    }

    @Test
    public void testCancel() {
        testCommon(100,
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.DONE, PENDING),
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.DONE, PENDING, IDownloadableFile::cancel),
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.CANCELLED, CANCELLED)
        );
    }

    @Test
    public void testPause() {
        testCommon(100,
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.DONE, PENDING),
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.DONE, PENDING, IDownloadableFile::pause),
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.PAUSED, PAUSED)
        );
    }

    @Test
    public void testPauseThenCancel() {
        testCommon(100,
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.DONE, PENDING),
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.DONE, PENDING, IDownloadableFile::pause),
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.PAUSED, PAUSED, IDownloadableFile::cancel),
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.CANCELLED, CANCELLED)
        );
    }

    @Test
    public void testCancelThenPause() {
        testCommon(100,
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.DONE, PENDING),
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.DONE, PENDING, IDownloadableFile::cancel),
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.CANCELLED, CANCELLED, IDownloadableFile::pause),
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.CANCELLED, CANCELLED)
        );
    }


    @Test
    public void testErrorThenPause() {
        testCommon(100,
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.DONE, PENDING),
                new ExpectedState(OK, FilePartDownloadState.FAILED, FAILED, IDownloadableFile::pause),
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.PAUSED, FAILED)
        );
    }

    @Test
    public void testErrorThenCancel() {
        testCommon(100,
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.DONE, PENDING),
                new ExpectedState(OK, FilePartDownloadState.FAILED, FAILED, IDownloadableFile::cancel),
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.CANCELLED, CANCELLED)
        );
    }

    @Test
    public void testPauseAndResume() {
        testCommon(100,
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.DONE, PENDING),
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.DONE, PENDING, f -> {
                    f.pause();
                    f.getDownloadableParts().forEach(p -> ((IManagedDownloadableFilePart) p).resume());
                }),
                new ExpectedState(PARTIAL_CONTENT_OK, FilePartDownloadState.DONE, DONE)
        );
    }

}
