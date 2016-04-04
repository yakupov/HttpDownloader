package org.iyakupov.downloader.core;

import org.apache.commons.io.FileUtils;
import org.iyakupov.downloader.core.comms.CommunicationStatus;
import org.iyakupov.downloader.core.comms.ICommunicationComponent;
import org.iyakupov.downloader.core.comms.impl.HttpCommunicationResult;
import org.iyakupov.downloader.core.comms.impl.HttpPartDownloadCommunicationAlgorithm;
import org.iyakupov.downloader.core.dispatch.IDispatchingQueue;
import org.iyakupov.downloader.core.dispatch.impl.DispatchingQueue;
import org.iyakupov.downloader.core.file.IDownloadableFile;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static org.iyakupov.downloader.core.DownloadStatus.*;
import static org.iyakupov.downloader.core.comms.CommunicationStatus.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Functional tests for the Dispatcher
 */
public class DispatcherTest {
    private final static Logger logger = LoggerFactory.getLogger(FilePartDownloadTest.class);

    /*
     * UTILS
     */

    /**
     * Real world-emulating input stream
     */
    static class SleepyByteArrayInputStream extends ByteArrayInputStream {
        private final int delay;

        public SleepyByteArrayInputStream(byte[] buf, int delay) {
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
     * @param returnedSize                    Size that will be returned in a response to a Check operation
     * @param checkRc                         CommunicationStatus that will be returned in a response to a Check operation
     * @param partDownloadRc                  CommunicationStatus that will be returned in a response to a PartDownload operation
     * @param readDelay                       Time to sleep (in milliseconds) before each read
     * @param defaultDispatcherThreadPoolSize Maximal number of simultaneous workers
     * @return Dispatcher
     */
    @NotNull
    private IDispatchingQueue createDispatcher(int returnedSize,
                                               CommunicationStatus checkRc,
                                               CommunicationStatus partDownloadRc,
                                               int readDelay,
                                               int defaultDispatcherThreadPoolSize) {
        final ICommunicationComponent communicationComponent = mock(ICommunicationComponent.class);
        when(communicationComponent.checkRemoteFile(any()))
                .thenReturn(new HttpCommunicationResult(checkRc, "Irrelevant", null, returnedSize));

        when(communicationComponent.downloadRemoteFile(anyString(), anyLong(), anyLong())).thenAnswer(invocationOnMock -> {
            final Object[] invocationArguments = invocationOnMock.getArguments();
            assert (invocationArguments.length == 3);
            final int dataLength = ((Long) invocationArguments[2]).intValue();
            final byte[] res = new byte[dataLength > 0 ? dataLength : 1];
            Arrays.fill(res, ((Long) invocationArguments[1]).byteValue());
            return new HttpCommunicationResult(partDownloadRc, "Irrelevant",
                    new SleepyByteArrayInputStream(res, readDelay), res.length);
        });

        return new DispatchingQueue(defaultDispatcherThreadPoolSize, DispatchingQueue.DEFAULT_QUEUE_CAPACITY, communicationComponent);
    }


    private int calculateExpectedFileSizeOnDisk(int headerSize, int numberOfParts) {
        if (numberOfParts == 1)
            return headerSize;
        else
            return (headerSize / numberOfParts) * (numberOfParts - 1) + 1;
    }

    /*
     * TESTS
     */

    @NotNull
    final File outputDir = new File("target/ut_temp_dir/");

    @Before
    public void ensureCleanTempDir() throws IOException {
        logger.trace("Temporary file output dir: " + outputDir.getAbsolutePath());
        logger.trace("Deleting old tmpdir");
        FileUtils.deleteDirectory(outputDir);
        logger.trace("Re-creating tmpdir: " + outputDir.mkdir());
    }

    @Test
    public void testOneFileEnoughThreads() throws IOException {
        final int size = 1000;
        final int numberOfThreads = 20;
        final int downloadablePartsPerFile = 3;
        final int readDelay = 1000;
        final int taskProcessDelay = 500;
        final int taskCompletionDelay = 2500;

        //noinspection ConstantConditions
        assert (size < HttpPartDownloadCommunicationAlgorithm.BUFFER_SIZE); //Ensure that the data will be retrieved in one read

        final IDispatchingQueue dispatcher = createDispatcher(size, PARTIAL_CONTENT_OK, PARTIAL_CONTENT_OK, readDelay, numberOfThreads);
        assert (dispatcher.getAllFiles().isEmpty());

        final IDownloadableFile file1 = dispatcher.submitFile("http://a.b/file.dmp", outputDir, downloadablePartsPerFile);
        assertEquals(1, dispatcher.getAllFiles().size());

        safeSleep(taskProcessDelay);

        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());
        file1.getDownloadableParts().forEach(p -> assertEquals(DOWNLOADING, p.getStatus()));

        safeSleep(taskCompletionDelay);

        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());
        file1.getDownloadableParts().forEach(p -> assertEquals(DONE, p.getStatus()));
        assertEquals(DONE, file1.getStatus());

        assertEquals(calculateExpectedFileSizeOnDisk(size, downloadablePartsPerFile), file1.getOutputFile().length());
    }

    @Test
    public void testOneFileNotEnoughThreads() throws IOException {
        final int size = 1000;
        final int numberOfThreads = 1;
        final int downloadablePartsPerFile = 3;
        final int readDelay = 1000;
        final int taskProcessDelay = 500;

        //noinspection ConstantConditions
        assert (size < HttpPartDownloadCommunicationAlgorithm.BUFFER_SIZE); //Ensure that the data will be retrieved in one read

        final IDispatchingQueue dispatcher = createDispatcher(size, PARTIAL_CONTENT_OK, PARTIAL_CONTENT_OK, readDelay, numberOfThreads);
        assert (dispatcher.getAllFiles().isEmpty());

        final IDownloadableFile file1 = dispatcher.submitFile("http://a.b/file.dmp", outputDir, downloadablePartsPerFile);
        assertEquals(1, dispatcher.getAllFiles().size());

        safeSleep(taskProcessDelay);
        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());

        while (file1.getDownloadableParts().stream().filter(p -> p.getStatus() == DONE).count() < downloadablePartsPerFile) {
            assertEquals(DOWNLOADING, file1.getStatus());
            final long downloadingPartsCount = file1.getDownloadableParts().stream().filter(p -> p.getStatus() == DOWNLOADING).count();
            if (downloadingPartsCount != numberOfThreads) {
                logger.warn("Mismatch between the current number of downloading tasks (" + downloadingPartsCount +
                        ") and expected one (" + numberOfThreads + ")");
            }
            safeSleep(taskProcessDelay);
        }
        safeSleep(taskProcessDelay); //save to disk

        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());
        file1.getDownloadableParts().forEach(p -> assertEquals(DONE, p.getStatus()));
        assertEquals(DONE, file1.getStatus());
        assertEquals(calculateExpectedFileSizeOnDisk(size, downloadablePartsPerFile), file1.getOutputFile().length());
    }

    @Test
    public void testTwoFilesEnoughThreads() throws IOException {
        final int size = 1000;
        final int numberOfThreads = 20;
        final int downloadablePartsPerFile = 3;
        final int readDelay = 1000;
        final int taskProcessDelay = 500;
        final int taskCompletionDelay = 2500;

        //noinspection ConstantConditions
        assert (size < HttpPartDownloadCommunicationAlgorithm.BUFFER_SIZE); //Ensure that the data will be retrieved in one read

        final IDispatchingQueue dispatcher = createDispatcher(size, PARTIAL_CONTENT_OK, PARTIAL_CONTENT_OK, readDelay, numberOfThreads);
        assert (dispatcher.getAllFiles().isEmpty());

        final IDownloadableFile file1 = dispatcher.submitFile("http://a.b/file1.dmp", outputDir, downloadablePartsPerFile);
        final IDownloadableFile file2 = dispatcher.submitFile("http://a.b/file2.dmp", outputDir, downloadablePartsPerFile);
        assertEquals(2, dispatcher.getAllFiles().size());

        safeSleep(taskProcessDelay);

        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());
        assertEquals(downloadablePartsPerFile, file2.getDownloadableParts().size());
        file1.getDownloadableParts().forEach(p -> assertEquals(DOWNLOADING, p.getStatus()));
        file2.getDownloadableParts().forEach(p -> assertEquals(DOWNLOADING, p.getStatus()));

        safeSleep(taskCompletionDelay);

        file1.getDownloadableParts().forEach(p -> assertEquals(DONE, p.getStatus()));
        file2.getDownloadableParts().forEach(p -> assertEquals(DONE, p.getStatus()));
        assertEquals(DONE, file1.getStatus());
        assertEquals(DONE, file2.getStatus());
        assertEquals(calculateExpectedFileSizeOnDisk(size, downloadablePartsPerFile), file1.getOutputFile().length());
        assertEquals(calculateExpectedFileSizeOnDisk(size, downloadablePartsPerFile), file2.getOutputFile().length());
    }

    @Test
    public void testTwoFilesNotEnoughThreads() throws IOException {
        final int size = 1000;
        final int numberOfThreads = 3;
        final int downloadablePartsPerFile = 3;
        final int readDelay = 1000;
        final int taskProcessDelay = 500;

        //noinspection ConstantConditions
        assert (size < HttpPartDownloadCommunicationAlgorithm.BUFFER_SIZE); //Ensure that the data will be retrieved in one read

        final IDispatchingQueue dispatcher = createDispatcher(size, PARTIAL_CONTENT_OK, PARTIAL_CONTENT_OK, readDelay, numberOfThreads);
        assert (dispatcher.getAllFiles().isEmpty());

        final IDownloadableFile file1 = dispatcher.submitFile("http://a.b/file1.dmp", outputDir, downloadablePartsPerFile);
        final IDownloadableFile file2 = dispatcher.submitFile("http://a.b/file2.dmp", outputDir, downloadablePartsPerFile);
        assertEquals(2, dispatcher.getAllFiles().size());

        safeSleep(taskProcessDelay);

        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());
        assertEquals(downloadablePartsPerFile, file2.getDownloadableParts().size());
        assertEquals(true, (file1.getStatus() == DOWNLOADING || file1.getStatus() == PENDING));
        assertEquals(true, (file2.getStatus() == DOWNLOADING || file2.getStatus() == PENDING));
        file1.getDownloadableParts().forEach(p -> assertEquals(true, (p.getStatus() == DOWNLOADING || p.getStatus() == PENDING)));
        file2.getDownloadableParts().forEach(p -> assertEquals(true, (p.getStatus() == DOWNLOADING || p.getStatus() == PENDING)));

        while (!(file1.getStatus() == DONE && file2.getStatus() == DONE)) {
            final long downloadingPartsCount =
                    file1.getDownloadableParts().stream().filter(p -> p.getStatus() == DOWNLOADING).count() +
                    file2.getDownloadableParts().stream().filter(p -> p.getStatus() == DOWNLOADING).count();
            if (downloadingPartsCount != numberOfThreads) {
                logger.warn("Mismatch between the current number of downloading tasks (" + downloadingPartsCount +
                        ") and expected one (" + numberOfThreads + ")");
            }
            //assert(file1.getStatus() == DOWNLOADING || file2.getStatus() == DOWNLOADING ||
            //        file1.getStatus() == UNSAVED || file2.getStatus() == UNSAVED);
            safeSleep(taskProcessDelay);
        }
        safeSleep(taskProcessDelay); //save to disk

        file1.getDownloadableParts().forEach(p -> assertEquals(DONE, p.getStatus()));
        file2.getDownloadableParts().forEach(p -> assertEquals(DONE, p.getStatus()));
        assertEquals(DONE, file1.getStatus());
        assertEquals(DONE, file2.getStatus());
        assertEquals(calculateExpectedFileSizeOnDisk(size, downloadablePartsPerFile), file1.getOutputFile().length());
        assertEquals(calculateExpectedFileSizeOnDisk(size, downloadablePartsPerFile), file2.getOutputFile().length());
    }

    @Test
    public void testOneFileWithEviction() throws IOException {
        final int numberOfThreads = 20;
        final int newNumberOfThreads = 1;
        final int downloadablePartsPerFile = 3;
        final int size = (3 * HttpPartDownloadCommunicationAlgorithm.BUFFER_SIZE) * downloadablePartsPerFile + 1; //3 reads
        final int readDelay = 1000;
        final int taskProcessDelay = 500;
        final int poolResizeExpectedDelay = 2500;

        final IDispatchingQueue dispatcher = createDispatcher(size, PARTIAL_CONTENT_OK, PARTIAL_CONTENT_OK, readDelay, numberOfThreads);
        assert (dispatcher.getAllFiles().isEmpty());

        final IDownloadableFile file1 = dispatcher.submitFile("http://a.b/file.dmp", outputDir, downloadablePartsPerFile);
        assertEquals(1, dispatcher.getAllFiles().size());

        safeSleep(taskProcessDelay);

        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());
        file1.getDownloadableParts().forEach(p -> assertEquals(DOWNLOADING, p.getStatus()));

        dispatcher.setThreadPoolSize(newNumberOfThreads, true);
        safeSleep(poolResizeExpectedDelay); //ensure completion of a read operation

        while (file1.getStatus() != DONE) {
            //assert (file1.getStatus() == DOWNLOADING || file1.getStatus() == UNSAVED); //may be pending
            final long downloadingPartsCount = file1.getDownloadableParts().stream().filter(p -> p.getStatus() == DOWNLOADING).count();
            if (downloadingPartsCount != newNumberOfThreads) {
                logger.warn("Mismatch between the current number of downloading tasks (" + downloadingPartsCount +
                        ") and expected one (" + newNumberOfThreads + ")");
            }
            safeSleep(taskProcessDelay);
        }

        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());
        file1.getDownloadableParts().forEach(p -> assertEquals(DONE, p.getStatus()));
        assertEquals(DONE, file1.getStatus());
        assertEquals(calculateExpectedFileSizeOnDisk(size, downloadablePartsPerFile), file1.getOutputFile().length());
    }

    @Test
    public void testAdditionAfterEviction() throws IOException {
        final int numberOfThreads = 20;
        final int newNumberOfThreads = 1;
        final int downloadablePartsPerFile = 3;
        final int size = (3 * HttpPartDownloadCommunicationAlgorithm.BUFFER_SIZE) * downloadablePartsPerFile + 1; //3 reads
        final int readDelay = 1000;
        final int taskProcessDelay = 500;
        final int poolResizeExpectedDelay = 2500;

        final IDispatchingQueue dispatcher = createDispatcher(size, PARTIAL_CONTENT_OK, PARTIAL_CONTENT_OK, readDelay, numberOfThreads);
        assert (dispatcher.getAllFiles().isEmpty());

        final IDownloadableFile file1 = dispatcher.submitFile("http://a.b/file1.dmp", outputDir, downloadablePartsPerFile);
        assertEquals(1, dispatcher.getAllFiles().size());

        safeSleep(taskProcessDelay);

        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());
        file1.getDownloadableParts().forEach(p -> assertEquals(DOWNLOADING, p.getStatus()));

        dispatcher.setThreadPoolSize(newNumberOfThreads, true);
        safeSleep(poolResizeExpectedDelay); //ensure completion of a read operation

        final IDownloadableFile file2 = dispatcher.submitFile("http://a.b/file2.dmp", outputDir, downloadablePartsPerFile);
        safeSleep(taskProcessDelay);
        assertEquals(INITIATED, file2.getStatus());

        while (file1.getStatus() != DONE || file2.getStatus() != DONE) {
            if (file2.getStatus() != INITIATED) {
                assert (file1.getStatus() == DONE || file1.getStatus() == UNSAVED);
                assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().stream().filter(p -> p.getStatus() == DONE).count());
            } else {
                final long downloadingPartsCount = file2.getDownloadableParts().stream().filter(p -> p.getStatus() == DOWNLOADING).count();
                if (downloadingPartsCount != newNumberOfThreads) {
                    logger.warn("Mismatch between the current number of downloading tasks (" + downloadingPartsCount +
                            ") and expected one (" + newNumberOfThreads + ")");
                }
            }
            safeSleep(taskProcessDelay);
        }

        file1.getDownloadableParts().forEach(p -> assertEquals(DONE, p.getStatus()));
        file2.getDownloadableParts().forEach(p -> assertEquals(DONE, p.getStatus()));
        assertEquals(DONE, file1.getStatus());
        assertEquals(DONE, file2.getStatus());
        assertEquals(calculateExpectedFileSizeOnDisk(size, downloadablePartsPerFile), file1.getOutputFile().length());
        assertEquals(calculateExpectedFileSizeOnDisk(size, downloadablePartsPerFile), file2.getOutputFile().length());
    }

    @Test
    public void testOneFileWithExtension() throws IOException {
        final int numberOfThreads = 1;
        final int newNumberOfThreads = 20;
        final int downloadablePartsPerFile = 3;
        final int size = (3 * HttpPartDownloadCommunicationAlgorithm.BUFFER_SIZE) * downloadablePartsPerFile + 1; //3 reads
        final int readDelay = 1000;
        final int taskProcessDelay = 500;
        final int poolResizeExpectedDelay = 2500;

        final IDispatchingQueue dispatcher = createDispatcher(size, PARTIAL_CONTENT_OK, PARTIAL_CONTENT_OK, readDelay, numberOfThreads);
        assert (dispatcher.getAllFiles().isEmpty());

        final IDownloadableFile file1 = dispatcher.submitFile("http://a.b/file.dmp", outputDir, downloadablePartsPerFile);
        assertEquals(1, dispatcher.getAllFiles().size());

        safeSleep(taskProcessDelay);

        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());
        assertEquals(numberOfThreads, file1.getDownloadableParts().stream().filter(p -> p.getStatus() == DOWNLOADING).count());
        assertEquals(downloadablePartsPerFile - numberOfThreads, file1.getDownloadableParts().stream().filter(p -> p.getStatus() == PENDING).count());

        dispatcher.setThreadPoolSize(newNumberOfThreads, true);
        safeSleep(poolResizeExpectedDelay);

        //Last part always has the size of one
        assertEquals(downloadablePartsPerFile - 1, file1.getDownloadableParts().stream().filter(p -> p.getStatus() == DOWNLOADING).count());
        assertEquals(1, file1.getDownloadableParts().stream().filter(p -> p.getStatus() == DONE).count());

        while (file1.getStatus() != DONE) {
            //assert(file1.getStatus() == DOWNLOADING || file1.getStatus() == UNSAVED);
            file1.getDownloadableParts().forEach(p -> assertEquals(true, (p.getStatus() == DOWNLOADING ||
                    p.getStatus() == PENDING || p.getStatus() == DONE)));
            safeSleep(taskProcessDelay);
        }

        assertEquals(downloadablePartsPerFile, file1.getDownloadableParts().size());
        file1.getDownloadableParts().forEach(p -> assertEquals(DONE, p.getStatus()));
        assertEquals(DONE, file1.getStatus());
        assertEquals(calculateExpectedFileSizeOnDisk(size, downloadablePartsPerFile), file1.getOutputFile().length());
    }

    //TODO: UTs for cancel, resume, forget
}
