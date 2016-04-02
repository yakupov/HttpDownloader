package org.iyakupov.downloader.core;

import org.iyakupov.downloader.core.comms.CommunicationStatus;
import org.iyakupov.downloader.core.comms.ICommunicationAlgorithm;
import org.iyakupov.downloader.core.comms.ICommunicationComponent;
import org.iyakupov.downloader.core.comms.impl.HttpCommunicationResult;
import org.iyakupov.downloader.core.comms.impl.HttpDownloadCheckCommunicationAlgorithm;
import org.iyakupov.downloader.core.dispatch.IDispatchingQueue;
import org.iyakupov.downloader.core.file.IDownloadableFilePart;
import org.iyakupov.downloader.core.file.internal.IDownloadableFileInt;
import org.iyakupov.downloader.core.file.internal.IDownloadableFilePartInt;
import org.iyakupov.downloader.core.file.internal.impl.DownloadableFile;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

/**
 * UT for HttpDownloadCheckCommunicationAlgorithm
 */
public class FilePartCreationTest {
    private final static Logger logger = LoggerFactory.getLogger(FilePartCreationTest.class);

    /**
     * Test the execution of HttpDownloadCheckCommunicationAlgorithm with different parameters
     *
     * @param fileSize           Size, returned by the CommunicationComponent
     * @param desiredPartsCount  Requested number of file pieces
     * @param expectedPartsCount Actual number of file pieces
     * @param rc                 Status, returned by the CommunicationComponent
     */
    public void testCommon(final int fileSize,
                           final int desiredPartsCount,
                           final int expectedPartsCount,
                           final CommunicationStatus rc) {
        logger.info(String.format("Started file part creation test with fileSize=%d, desiredPartsCount=%d, " +
                "expectedPartsCount=%d, rc=%s", fileSize, desiredPartsCount, expectedPartsCount, rc));

        final String fileName = "file.bin";
        final String fileUrl = "http://my.site/" + fileName;
        final File outputDir = spy(new File("/home/ilia/"));
        when(outputDir.isDirectory()).thenReturn(true);

        final ICommunicationComponent communicationComponent = mock(ICommunicationComponent.class);
        when(communicationComponent.checkRemoteFile(any())).thenReturn(
                new HttpCommunicationResult(rc, "Irrelevant", null, fileSize));

        final IDispatchingQueue dispatchingQueue = mock(IDispatchingQueue.class);
        doNothing().when(dispatchingQueue).submitNewTask(any(), any());

        final IDownloadableFileInt downloadableFile = new DownloadableFile(fileUrl, outputDir, desiredPartsCount);
        final ICommunicationAlgorithm downloadCheckAlgorithm =
                new HttpDownloadCheckCommunicationAlgorithm(dispatchingQueue, communicationComponent, downloadableFile);

        downloadCheckAlgorithm.run();
        verify(dispatchingQueue, times(expectedPartsCount)).submitNewTask(any(), any());

        final List<IDownloadableFilePart> parts = downloadableFile.getDownloadableParts();
        assertEquals(expectedPartsCount, parts.size());
        assertEquals(new File(outputDir, fileName), downloadableFile.getOutputFile());
        for (int i = 0; i < expectedPartsCount; ++i) {
            logger.trace("Checking part " + i);
            final IDownloadableFilePartInt part = (IDownloadableFilePartInt) parts.get(i);

            assertEquals(DownloadStatus.PENDING, part.getStatus());
            assertEquals(fileUrl, part.getLocator());

            if (i < expectedPartsCount - 1)
                assertEquals(fileSize / expectedPartsCount, part.getRemainingLength());
            else
                assertEquals(-1, part.getRemainingLength());

            if (parts.size() > 1)
                assertEquals(new File(outputDir.getAbsolutePath(), fileName + "_part" + i), part.getOutputFile());
            else
                assertEquals(downloadableFile.getOutputFile(), part.getOutputFile());
        }
    }

    @Test
    public void testWithPartialDownload() {
        testCommon(100, 10, 10, CommunicationStatus.PARTIAL_CONTENT_OK);
    }

    @Test
    public void testWithoutPartialDownload() {
        testCommon(100, 10, 1, CommunicationStatus.OK);
    }

    @Test
    public void testWithoutPartialDownloadWithoutLength() {
        testCommon(-1, 10, 1, CommunicationStatus.OK);
    }

    @Test
    public void testOnePartWithPartialDownload() {
        testCommon(100, 1, 1, CommunicationStatus.PARTIAL_CONTENT_OK);
    }

    @Test
    public void testOnePartWithoutPartialDownload() {
        testCommon(100, 1, 1, CommunicationStatus.OK);
    }

    @Test
    public void testBadRc() {
        testCommon(100, 11, 0, CommunicationStatus.ERROR);
    }
}
