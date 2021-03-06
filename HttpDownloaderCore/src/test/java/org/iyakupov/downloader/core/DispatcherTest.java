package org.iyakupov.downloader.core;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.iyakupov.downloader.core.comms.CommunicationStatus;
import org.iyakupov.downloader.core.comms.ICommunicatingComponent;
import org.iyakupov.downloader.core.comms.impl.HttpCommunicationResult;
import org.iyakupov.downloader.core.dispatch.IDispatchingQueue;
import org.iyakupov.downloader.core.dispatch.impl.DispatchingQueue;
import org.iyakupov.downloader.core.file.IDownloadableFile;
import org.iyakupov.downloader.core.file.state.FileDownloadState;
import org.iyakupov.downloader.core.file.state.FilePartDownloadState;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.iyakupov.downloader.core.comms.CommunicationStatus.PARTIAL_CONTENT_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

/**
 * Functional tests for the Dispatcher
 */
public class DispatcherTest {
    private final static Logger logger = LoggerFactory.getLogger(FilePartDownloadTest.class);

    /**
     * Real world-emulating input stream
     */
    private static class SleepyByteArrayInputStream extends ByteArrayInputStream {
        private final int delay;

        SleepyByteArrayInputStream(byte[] buf, int delay) {
            super(buf);
            this.delay = delay;
        }

        @Override
        public int read(@NotNull byte[] readBuffer) throws IOException {
            safeSleep(delay);
            return super.read(readBuffer);
        }
    }

    /**
     * Sleep without worrying about the {@link InterruptedException}
     *
     * @param taskProcessDelay Time to sleep (in milliseconds)
     */
    private static void safeSleep(int taskProcessDelay) {
        try {
            Thread.sleep(taskProcessDelay);
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * Create a dispatcher with mocked CommunicationComponent
     *
     * @param chunkSize                       Default size of a file chunk. The size that will be returned in a response to a
     *                                        Check operation can be calculated as chunkSize * expectedNumberOfChunks
     * @param expectedNumberOfChunks          Number of chunks that each file will be split in
     * @param checkRc                         CommunicationStatus that will be returned in a response to a Check operation
     * @param partDownloadRc                  CommunicationStatus that will be returned in a response to a PartDownload operation
     * @param readDelay                       Time to sleep (in milliseconds) before each read
     * @param defaultDispatcherThreadPoolSize Maximal number of simultaneous workers
     * @return Dispatcher
     */
    @NotNull
    private IDispatchingQueue createDispatcher(int chunkSize,
                                               int expectedNumberOfChunks,
                                               CommunicationStatus checkRc,
                                               CommunicationStatus partDownloadRc,
                                               int readDelay,
                                               int defaultDispatcherThreadPoolSize) {
        final ICommunicatingComponent communicationComponent = mock(ICommunicatingComponent.class);
        when(communicationComponent.checkRemoteFile(any()))
                .thenReturn(new HttpCommunicationResult(checkRc, "Irrelevant", null, chunkSize * expectedNumberOfChunks));

        when(communicationComponent.downloadRemoteFile(anyString(), anyLong(), anyLong())).thenAnswer(invocationOnMock -> {
            final Object[] invocationArguments = invocationOnMock.getArguments();
            assert (invocationArguments.length == 3);

            final int dataLength = ((Long) invocationArguments[2]).intValue();
            logger.debug("Creating new SleepyStream, dataLength = " + dataLength);
            final byte[] res = new byte[dataLength >= 0 ? dataLength : chunkSize];
            Arrays.fill(res, ((Long) invocationArguments[1]).byteValue());

            final HttpEntity responseEntity = mock(HttpEntity.class);
            final SleepyByteArrayInputStream dataStream = new SleepyByteArrayInputStream(res, readDelay);
            when(responseEntity.getContent()).thenReturn(dataStream);

            final CloseableHttpResponse response = mock(CloseableHttpResponse.class);
            when(response.getEntity()).thenReturn(responseEntity);
            doAnswer(invocationOnMock1 -> {
                dataStream.close();
                logger.trace("Closed mocked HTTP response");
                return null;
            }).when(response).close();

            return new HttpCommunicationResult(partDownloadRc, "Irrelevant", response, res.length);
        });

        return new DispatchingQueue(defaultDispatcherThreadPoolSize, AppSettings.getDispatchingQueueCapacity(), communicationComponent);
    }

    /*
     * TESTS
     */

    @NotNull
    private final File outputDir = new File("target/ut_temp_dir/");
    private IDispatchingQueue dispatcher;

    @Before
    public void ensureCleanTempDir() {
        logger.debug("Temporary file output dir: " + outputDir.getAbsolutePath());
        if (!outputDir.exists()) {
            if (!outputDir.mkdir())
                logger.error("Failed to create the output directory");
            return;
        }

        logger.debug("Deleting old tmpdir");
        boolean deleted = false;
        final long startTime = System.nanoTime();
        while (!deleted && (System.nanoTime() - startTime) < 10e9) {
            try {
                FileUtils.forceDelete(outputDir);
                deleted = true;
            } catch (IOException e) {
                logger.error("TMPDIR deletion failed", e);
                safeSleep(1000);
                logger.info("Trying to delete again");
            }
        }
        logger.debug("Re-creating tmpdir: " + outputDir.mkdir());
    }

    @After
    public void cleanup() throws IOException {
        dispatcher.close();
    }

    @Test(timeout = 15000)
    public void testOneFileEnoughThreads() throws IOException {
        final int chunkSize = 1000;
        final int numberOfThreads = 20;
        final int downloadablePartsPerFile = 3;
        final int readDelay = 1000;

        dispatcher = createDispatcher(chunkSize, downloadablePartsPerFile,
                PARTIAL_CONTENT_OK, PARTIAL_CONTENT_OK, readDelay, numberOfThreads);
        assert (dispatcher.getAllFiles().isEmpty());

        final IDownloadableFile file1 = dispatcher.submitFile("http://a.b/file.dmp", outputDir, downloadablePartsPerFile);
        assertEquals(1, dispatcher.getAllFiles().size());

        while (file1.getDownloadableParts().isEmpty())
            safeSleep(100);

        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());
        file1.getDownloadableParts().forEach(p -> assertThat(p.getStatus(),
                anyOf(is(FilePartDownloadState.DONE), is(FilePartDownloadState.PENDING), is(FilePartDownloadState.DOWNLOADING))));

        while (file1.getStatus() != FileDownloadState.DONE)
            safeSleep(100);

        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());
        file1.getDownloadableParts().forEach(p -> assertEquals(FilePartDownloadState.DONE, p.getStatus()));
        assertEquals(chunkSize * downloadablePartsPerFile, file1.getOutputFile().length());
    }

    @Test(timeout = 20000)
    public void testOneFileNotEnoughThreads() throws IOException {
        final int chunkSize = 2 * AppSettings.getDownloadBufferSize();
        final int numberOfThreads = 1;
        final int downloadablePartsPerFile = 3;
        final int readDelay = 1000;

        dispatcher = createDispatcher(chunkSize, downloadablePartsPerFile,
                PARTIAL_CONTENT_OK, PARTIAL_CONTENT_OK, readDelay, numberOfThreads);
        assert (dispatcher.getAllFiles().isEmpty());

        final IDownloadableFile file1 = dispatcher.submitFile("http://a.b/file.dmp", outputDir, downloadablePartsPerFile);
        assertEquals(1, dispatcher.getAllFiles().size());

        while (file1.getDownloadableParts().isEmpty())
            safeSleep(100);

        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());

        while (file1.getStatus() != FileDownloadState.DONE) {
            file1.getDownloadableParts().forEach(p -> assertThat(p.getStatus(),
                    anyOf(is(FilePartDownloadState.DONE), is(FilePartDownloadState.PENDING), is(FilePartDownloadState.DOWNLOADING))));
            final long downloadingPartsCount = file1.getDownloadableParts().stream().filter(p -> p.getStatus() == FilePartDownloadState.DOWNLOADING).count();
            if (downloadingPartsCount > numberOfThreads) {
                logger.warn("Mismatch between the current number of downloading tasks (" + downloadingPartsCount +
                        ") and expected one (" + numberOfThreads + "). Please check the logs if there are " +
                        "too many of such warnings");
            }
            safeSleep(100);
        }

        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());
        file1.getDownloadableParts().forEach(p -> assertEquals(FilePartDownloadState.DONE, p.getStatus()));
        assertEquals(chunkSize * downloadablePartsPerFile, file1.getOutputFile().length());
    }

    @Test(timeout = 20000)
    public void testTwoFilesEnoughThreads() throws IOException {
        final int chunkSize = 1000;
        final int numberOfThreads = 20;
        final int downloadablePartsPerFile = 3;
        final int readDelay = 1000;

        dispatcher = createDispatcher(chunkSize, downloadablePartsPerFile,
                PARTIAL_CONTENT_OK, PARTIAL_CONTENT_OK, readDelay, numberOfThreads);
        assert (dispatcher.getAllFiles().isEmpty());

        final IDownloadableFile file1 = dispatcher.submitFile("http://a.b/file1.dmp", outputDir, downloadablePartsPerFile);
        final IDownloadableFile file2 = dispatcher.submitFile("http://a.b/file2.dmp", outputDir, downloadablePartsPerFile);
        assertEquals(2, dispatcher.getAllFiles().size());

        while (file1.getDownloadableParts().isEmpty() || file2.getDownloadableParts().isEmpty())
            safeSleep(100);

        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());
        assertEquals(downloadablePartsPerFile, file2.getDownloadableParts().size());
        file1.getDownloadableParts().forEach(p -> assertThat(p.getStatus(),
                anyOf(is(FilePartDownloadState.DONE), is(FilePartDownloadState.PENDING), is(FilePartDownloadState.DOWNLOADING))));
        file2.getDownloadableParts().forEach(p -> assertThat(p.getStatus(),
                anyOf(is(FilePartDownloadState.DONE), is(FilePartDownloadState.PENDING), is(FilePartDownloadState.DOWNLOADING))));

        while (file1.getStatus() != FileDownloadState.DONE)
            safeSleep(100);

        file1.getDownloadableParts().forEach(p -> assertEquals(FilePartDownloadState.DONE, p.getStatus()));
        file2.getDownloadableParts().forEach(p -> assertEquals(FilePartDownloadState.DONE, p.getStatus()));
        assertEquals(chunkSize * downloadablePartsPerFile, file1.getOutputFile().length());
        assertEquals(chunkSize * downloadablePartsPerFile, file2.getOutputFile().length());
    }

    @Test(timeout = 30000)
    public void testTwoFilesNotEnoughThreads() throws IOException {
        final int chunkSize = 1000;
        final int numberOfThreads = 3;
        final int downloadablePartsPerFile = 3;
        final int readDelay = 1000;

        dispatcher = createDispatcher(chunkSize, downloadablePartsPerFile,
                PARTIAL_CONTENT_OK, PARTIAL_CONTENT_OK, readDelay, numberOfThreads);
        assert (dispatcher.getAllFiles().isEmpty());

        final IDownloadableFile file1 = dispatcher.submitFile("http://a.b/file1.dmp", outputDir, downloadablePartsPerFile);
        final IDownloadableFile file2 = dispatcher.submitFile("http://a.b/file2.dmp", outputDir, downloadablePartsPerFile);
        assertEquals(2, dispatcher.getAllFiles().size());

        while (file1.getDownloadableParts().isEmpty() || file2.getDownloadableParts().isEmpty())
            safeSleep(100);

        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());
        assertEquals(downloadablePartsPerFile, file2.getDownloadableParts().size());

        while (file1.getStatus() != FileDownloadState.DONE || file2.getStatus() != FileDownloadState.DONE) {
            file1.getDownloadableParts().forEach(p -> assertThat(p.getStatus(),
                    anyOf(is(FilePartDownloadState.DONE), is(FilePartDownloadState.PENDING), is(FilePartDownloadState.DOWNLOADING))));
            file2.getDownloadableParts().forEach(p -> assertThat(p.getStatus(),
                    anyOf(is(FilePartDownloadState.DONE), is(FilePartDownloadState.PENDING), is(FilePartDownloadState.DOWNLOADING))));

            final long downloadingPartsCount =
                    file1.getDownloadableParts().stream().filter(p -> p.getStatus() == FilePartDownloadState.DOWNLOADING).count() +
                            file2.getDownloadableParts().stream().filter(p -> p.getStatus() == FilePartDownloadState.DOWNLOADING).count();
            if (downloadingPartsCount > numberOfThreads) {
                logger.warn("Mismatch between the current number of downloading tasks (" + downloadingPartsCount +
                        ") and expected one (" + numberOfThreads + "). Please check the logs if there are " +
                        "too many of such warnings");
            }
            safeSleep(100);
        }

        file1.getDownloadableParts().forEach(p -> assertEquals(FilePartDownloadState.DONE, p.getStatus()));
        file2.getDownloadableParts().forEach(p -> assertEquals(FilePartDownloadState.DONE, p.getStatus()));
        assertEquals(chunkSize * downloadablePartsPerFile, file1.getOutputFile().length());
        assertEquals(chunkSize * downloadablePartsPerFile, file2.getOutputFile().length());
    }

    @Test(timeout = 60000)
    public void testOneFileWithEviction() throws IOException {
        final int numberOfThreads = 20;
        final int newNumberOfThreads = 1;
        final int downloadablePartsPerFile = 3;
        final int chunkSize = 5 * AppSettings.getDownloadBufferSize(); //5 reads
        final int readDelay = 1000;
        final int allowedDelaysForASingleIteration = 1000;

        dispatcher = createDispatcher(chunkSize, downloadablePartsPerFile,
                PARTIAL_CONTENT_OK, PARTIAL_CONTENT_OK, readDelay, numberOfThreads);
        assert (dispatcher.getAllFiles().isEmpty());

        final IDownloadableFile file1 = dispatcher.submitFile("http://a.b/file.dmp", outputDir, downloadablePartsPerFile);
        assertEquals(1, dispatcher.getAllFiles().size());

        while (file1.getDownloadableParts().isEmpty())
            safeSleep(100);
        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());

        // Wait to start download of each part. We expect to start them 'simultaneously', a long time before any of the
        // tasks may complete all five reads (100 ms to sleep << 5000 ms for 5 reads)
        long counter;
        while ((counter = file1.getDownloadableParts().stream()
                .filter(p -> p.getStatus() == FilePartDownloadState.DOWNLOADING).count()) < downloadablePartsPerFile) {
            logger.trace("Not enough downloading parts: " + counter);
            safeSleep(300);
        }

        dispatcher.setThreadPoolSize(newNumberOfThreads, true);
        safeSleep(readDelay + allowedDelaysForASingleIteration); //ensure completion of a read operation

        while (file1.getStatus() != FileDownloadState.DONE) {
            file1.getDownloadableParts().forEach(p -> assertThat(p.getStatus(),
                    anyOf(is(FilePartDownloadState.DONE), is(FilePartDownloadState.SUSPEND_REQUESTED),
                            is(FilePartDownloadState.PENDING), is(FilePartDownloadState.DOWNLOADING))));
            final long downloadingPartsCount = file1.getDownloadableParts().stream().filter(p -> p.getStatus() == FilePartDownloadState.DOWNLOADING).count();
            if (downloadingPartsCount > numberOfThreads) {
                logger.warn("Mismatch between the current number of downloading tasks (" + downloadingPartsCount +
                        ") and expected one (" + numberOfThreads + "). Please check the logs if there are " +
                        "too many of such warnings");
            }
            safeSleep(100);
        }

        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());
        file1.getDownloadableParts().forEach(p -> assertEquals(FilePartDownloadState.DONE, p.getStatus()));
        assertEquals(chunkSize * downloadablePartsPerFile, file1.getOutputFile().length());
    }

    @Test(timeout = 60000)
    public void testAdditionAfterEviction() throws IOException {
        final int numberOfThreads = 20;
        final int newNumberOfThreads = 1;
        final int downloadablePartsPerFile = 3;
        final int chunkSize = 3 * AppSettings.getDownloadBufferSize(); //5 reads
        final int readDelay = 1000;

        dispatcher = createDispatcher(chunkSize, downloadablePartsPerFile,
                PARTIAL_CONTENT_OK, PARTIAL_CONTENT_OK, readDelay, numberOfThreads);
        assert (dispatcher.getAllFiles().isEmpty());

        final IDownloadableFile file1 = dispatcher.submitFile("http://a.b/file1.dmp", outputDir, downloadablePartsPerFile);
        assertEquals(1, dispatcher.getAllFiles().size());

        while (file1.getDownloadableParts().isEmpty())
            safeSleep(100);
        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());

        // Wait to start download of each part. We expect to start them 'simultaneously', a long time before any of the
        // tasks may complete all five reads (100 ms to sleep << 3000 ms for 3 reads)
        while (file1.getDownloadableParts().stream()
                .filter(p -> p.getStatus() == FilePartDownloadState.DOWNLOADING).count() < downloadablePartsPerFile)
            safeSleep(100);

        dispatcher.setThreadPoolSize(newNumberOfThreads, true);

        final IDownloadableFile file2 = dispatcher.submitFile("http://a.b/file2.dmp", outputDir, downloadablePartsPerFile);
        assertEquals(FileDownloadState.INITIATED, file2.getStatus());

        while (file1.getStatus() != FileDownloadState.DONE|| file2.getStatus() != FileDownloadState.DONE) {
            if (file2.getStatus() != FileDownloadState.INITIATED) {
                assert (file1.getStatus() == FileDownloadState.DONE || file1.getStatus() == FileDownloadState.UNSAVED);
                assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().stream().filter(p -> p.getStatus() == FilePartDownloadState.DONE).count());
            } else {
                file1.getDownloadableParts().forEach(p -> assertThat(p.getStatus(),
                        anyOf(is(FilePartDownloadState.DONE), is(FilePartDownloadState.SUSPEND_REQUESTED),
                                is(FilePartDownloadState.PENDING), is(FilePartDownloadState.DOWNLOADING))));
                final long downloadingPartsCount = file2.getDownloadableParts().stream().filter(p -> p.getStatus() == FilePartDownloadState.DOWNLOADING).count();
                if (downloadingPartsCount > numberOfThreads) {
                    logger.warn("Mismatch between the current number of downloading tasks (" + downloadingPartsCount +
                            ") and expected one (" + newNumberOfThreads + "). Please check the logs if there are " +
                            "too many of such warnings");
                }
            }
            safeSleep(100);
        }

        file1.getDownloadableParts().forEach(p -> assertEquals(FilePartDownloadState.DONE, p.getStatus()));
        file2.getDownloadableParts().forEach(p -> assertEquals(FilePartDownloadState.DONE, p.getStatus()));
        assertEquals(chunkSize * downloadablePartsPerFile, file1.getOutputFile().length());
        assertEquals(chunkSize * downloadablePartsPerFile, file2.getOutputFile().length());
    }

    @Test(timeout = 30000)
    public void testOneFileWithExtension() throws IOException {
        final int numberOfThreads = 1;
        final int newNumberOfThreads = 20;
        final int downloadablePartsPerFile = 3;
        final int chunkSize = 5 * AppSettings.getDownloadBufferSize(); //5 reads
        final int readDelay = 1000;

        dispatcher = createDispatcher(chunkSize, downloadablePartsPerFile,
                PARTIAL_CONTENT_OK, PARTIAL_CONTENT_OK, readDelay, numberOfThreads);
        assert (dispatcher.getAllFiles().isEmpty());

        final IDownloadableFile file1 = dispatcher.submitFile("http://a.b/file.dmp", outputDir, downloadablePartsPerFile);
        assertEquals(1, dispatcher.getAllFiles().size());

        while (file1.getDownloadableParts().isEmpty())
            safeSleep(100);
        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());

        while (file1.getStatus() != FileDownloadState.DOWNLOADING)
            safeSleep(100);
        assertEquals(numberOfThreads, file1.getDownloadableParts().stream()
                .filter(p -> p.getStatus() == FilePartDownloadState.DOWNLOADING).count());
        assertEquals(downloadablePartsPerFile - numberOfThreads, file1.getDownloadableParts().stream()
                .filter(p -> p.getStatus() == FilePartDownloadState.PENDING).count());

        dispatcher.setThreadPoolSize(newNumberOfThreads, true);

        // We expect the Thread Pool to allocate enough threads to process all parts 'simultaneously' a long time
        // before the tasks may complete all five reads (100 ms to sleep << 5000 ms for 5 reads)
        while (file1.getDownloadableParts().stream().filter(p -> p.getStatus() == FilePartDownloadState.DOWNLOADING).count() < downloadablePartsPerFile)
            safeSleep(100);

        while (file1.getStatus() != FileDownloadState.DONE)
            safeSleep(100);

        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());
        file1.getDownloadableParts().forEach(p -> assertEquals(FilePartDownloadState.DONE, p.getStatus()));
        assertEquals(chunkSize * downloadablePartsPerFile, file1.getOutputFile().length());
    }

    @Test(timeout = 15000)
    public void testOneFileCancel() throws IOException {
        final int chunkSize = 3 * AppSettings.getDownloadBufferSize();
        final int numberOfThreads = 20;
        final int downloadablePartsPerFile = 3;
        final int readDelay = 1000;

        dispatcher = createDispatcher(chunkSize, downloadablePartsPerFile,
                PARTIAL_CONTENT_OK, PARTIAL_CONTENT_OK, readDelay, numberOfThreads);
        assert (dispatcher.getAllFiles().isEmpty());

        final IDownloadableFile file1 = dispatcher.submitFile("http://a.b/file.dmp", outputDir, downloadablePartsPerFile);
        assertEquals(1, dispatcher.getAllFiles().size());

        while (file1.getDownloadableParts().isEmpty())
            safeSleep(100);

        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());
        file1.getDownloadableParts().forEach(p -> assertThat(p.getStatus(),
                anyOf(is(FilePartDownloadState.DONE), is(FilePartDownloadState.PENDING), is(FilePartDownloadState.DOWNLOADING))));

        file1.cancel();
        while (file1.getStatus() != FileDownloadState.CANCELLED)
            safeSleep(100);

        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());
        file1.getDownloadableParts().forEach(p -> assertEquals(FilePartDownloadState.CANCELLED, p.getStatus()));
    }

    @Test(timeout = 20000)
    public void testOneFilePauseResume() throws IOException {
        final int chunkSize = 3 * AppSettings.getDownloadBufferSize();
        final int numberOfThreads = 20;
        final int downloadablePartsPerFile = 3;
        final int readDelay = 1000;

        dispatcher = createDispatcher(chunkSize, downloadablePartsPerFile,
                PARTIAL_CONTENT_OK, PARTIAL_CONTENT_OK, readDelay, numberOfThreads);
        assert (dispatcher.getAllFiles().isEmpty());

        final IDownloadableFile file1 = dispatcher.submitFile("http://a.b/file.dmp", outputDir, downloadablePartsPerFile);
        assertEquals(1, dispatcher.getAllFiles().size());

        while (file1.getDownloadableParts().isEmpty())
            safeSleep(100);

        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());
        file1.getDownloadableParts().forEach(p -> assertThat(p.getStatus(),
                anyOf(is(FilePartDownloadState.DONE), is(FilePartDownloadState.PENDING), is(FilePartDownloadState.DOWNLOADING))));

        file1.pause();
        while (file1.getStatus() != FileDownloadState.PAUSED) {
            logger.debug("Wait until all parts will halt. Current file status: " + file1.getStatus());
            safeSleep(300);
        }
        file1.getDownloadableParts().forEach(p -> assertThat(p.getStatus(), anyOf(is(FilePartDownloadState.DONE), is(FilePartDownloadState.PAUSED))));

        dispatcher.resumeDownload(file1);
        while (file1.getStatus() != FileDownloadState.DONE)
            safeSleep(100);

        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());
        file1.getDownloadableParts().forEach(p -> assertEquals(FilePartDownloadState.DONE, p.getStatus()));
        assertEquals(chunkSize * downloadablePartsPerFile, file1.getOutputFile().length());
    }

    @Test(timeout = 10000)
    public void testOneFileForget() throws IOException {
        final int chunkSize = 1000;
        final int numberOfThreads = 20;
        final int downloadablePartsPerFile = 3;
        final int readDelay = 1000;

        dispatcher = createDispatcher(chunkSize, downloadablePartsPerFile,
                PARTIAL_CONTENT_OK, PARTIAL_CONTENT_OK, readDelay, numberOfThreads);
        assert (dispatcher.getAllFiles().isEmpty());

        final IDownloadableFile file1 = dispatcher.submitFile("http://a.b/file.dmp", outputDir, downloadablePartsPerFile);
        assertEquals(1, dispatcher.getAllFiles().size());

        while (file1.getDownloadableParts().isEmpty())
            safeSleep(100);

        dispatcher.forgetFile(file1);

        assertEquals(0, dispatcher.getAllFiles().size());
        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());
        file1.getDownloadableParts().forEach(p -> assertEquals(FilePartDownloadState.CANCELLED, p.getStatus()));
        assertEquals(FileDownloadState.CANCELLED, file1.getStatus());
    }
}
