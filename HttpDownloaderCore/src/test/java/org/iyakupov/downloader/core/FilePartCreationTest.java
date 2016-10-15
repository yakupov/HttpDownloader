package org.iyakupov.downloader.core;

import org.iyakupov.downloader.core.comms.CommunicationStatus;
import org.iyakupov.downloader.core.comms.ICommunicatingComponent;
import org.iyakupov.downloader.core.comms.ICommunication;
import org.iyakupov.downloader.core.comms.impl.HttpCommunicationResult;
import org.iyakupov.downloader.core.comms.impl.HttpDownloadCheckCommunication;
import org.iyakupov.downloader.core.dispatch.IDispatchingQueue;
import org.iyakupov.downloader.core.file.internal.IManagedDownloadableFile;
import org.iyakupov.downloader.core.file.internal.IManagedDownloadableFilePart;
import org.iyakupov.downloader.core.file.internal.impl.DownloadableFile;
import org.iyakupov.downloader.core.file.state.FilePartDownloadState;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

/**
 * UT for HttpDownloadCheckCommunication
 */
public class FilePartCreationTest {
    private final static Logger logger = LoggerFactory.getLogger(FilePartCreationTest.class);

    /**
     * Test the execution of HttpDownloadCheckCommunication with different parameters
     *
     * @param fileSize           Size, returned by the CommunicationComponent
     * @param desiredPartsCount  Requested number of file pieces
     * @param expectedPartsCount Actual number of file pieces
     * @param rc                 Status, returned by the CommunicationComponent
     */
    private void testCommon(final int fileSize,
                            final int desiredPartsCount,
                            final int expectedPartsCount,
                            final CommunicationStatus rc) {
        logger.info(String.format("Started file part creation test with fileSize=%d, desiredPartsCount=%d, " +
                "expectedPartsCount=%d, rc=%s", fileSize, desiredPartsCount, expectedPartsCount, rc));

        final String fileName = "file.bin";
        final String fileUrl = "http://my.site/" + fileName;
        final File outputDir = spy(new File("/home/ilia/"));
        when(outputDir.isDirectory()).thenReturn(true);

        final ICommunicatingComponent communicationComponent = mock(ICommunicatingComponent.class);
        when(communicationComponent.checkRemoteFile(any())).thenReturn(
                new HttpCommunicationResult(rc, "Irrelevant", null, fileSize));

        final IDispatchingQueue dispatchingQueue = mock(IDispatchingQueue.class);
        doNothing().when(dispatchingQueue).submitNewTask(any(), any());

        final IManagedDownloadableFile downloadableFile = new DownloadableFile(fileUrl, outputDir, desiredPartsCount);
        final ICommunication downloadCheckAlgorithm =
                new HttpDownloadCheckCommunication(dispatchingQueue, communicationComponent, downloadableFile);

        downloadCheckAlgorithm.run();
        verify(dispatchingQueue, times(expectedPartsCount)).submitNewTask(any(), any());

        final List<IManagedDownloadableFilePart> parts = new ArrayList<>(downloadableFile.getDownloadableParts());
        Collections.sort(parts, new Comparator<IManagedDownloadableFilePart>() {
            @Override
            public int compare(IManagedDownloadableFilePart o1, IManagedDownloadableFilePart o2) {
                return o1.getOutputFile().getName().compareTo(o2.getOutputFile().getName()); //FIXME: stupid test
            }
        });
        assertEquals(expectedPartsCount, parts.size());
        assertEquals(new File(outputDir, fileName), downloadableFile.getOutputFile());
        for (int i = 0; i < expectedPartsCount; ++i) {
            logger.trace("Checking part " + i);
            final IManagedDownloadableFilePart part = parts.get(i);

            assertEquals(FilePartDownloadState.PENDING, part.getStatus());
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
