package org.iyakupov.downloader.core;

import org.iyakupov.downloader.core.dispatch.impl.DispatchingQueue;
import org.iyakupov.downloader.core.file.IDownloadableFile;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Temporary manual "integration test"
 */
@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class ManualDownloaderTest {
    private final URL nv260MUrl;
    private final URL slack600MUrl;
    private final URL vk70KUrl;

    public ManualDownloaderTest() throws MalformedURLException {
        slack600MUrl = new URL("http://mirrors.slackware.com/slackware/slackware-iso/slackware-14.1-iso/slackware-14.1-source-d6.iso");
        vk70KUrl = new URL("http://cs630616.vk.me/v630616252/27318/49e4sKOm_OU.jpg");
        nv260MUrl = new URL("http://ru.download.nvidia.com/Windows/364.51/364.51-desktop-win10-32bit-international-whql.exe");
    }

    @Test
    @Ignore
    public void downloaderTest() throws Exception {
        final DispatchingQueue dispatcher = new DispatchingQueue(20);

        final File outputDir = new File("C:\\temp\\dl");
        final IDownloadableFile downloadableFile = dispatcher.submitFile(slack600MUrl.toString(), outputDir, 10);
        IDownloadableFile downloadableFile2 = null;

        int i = 0;
        while (downloadableFile.getStatus() != DownloadStatus.DONE &&
                downloadableFile.getStatus() != DownloadStatus.ERROR &&
                downloadableFile.getStatus() != DownloadStatus.CANCELLED) {
            System.out.println("Status: " + downloadableFile.getStatus());
            System.out.println("Speed: " + downloadableFile.getDownloadSpeed());
            System.out.println("Progress: " + downloadableFile.getProgress());

            System.out.print("Parts statuses: ");
            downloadableFile.getDownloadableParts().forEach(p -> System.out.print(p.getStatus() + " "));
            System.out.println();

            if (i++ == 3) {
                System.out.println("Now try with 2 threads");
                dispatcher.setThreadPoolSize(2, false);
                downloadableFile2 = dispatcher.submitFile(nv260MUrl.toString(), outputDir, 3);
            }

            if (i == 7) {
                System.out.println("Now try with 5 threads");
                dispatcher.setThreadPoolSize(5, false);
            }

            if (i == 10) {
                System.out.println("Pause");
                downloadableFile.pause();
            }

            if (i == 13) {
                System.out.println("Resume");
                dispatcher.resumeDownload(downloadableFile);
            }

            if (i == 16) {
                System.out.println("File 1: forget");
                dispatcher.forgetFile(downloadableFile);
            }

            Thread.sleep(3000);
        }

        System.out.println("DONE, status = " + downloadableFile.getStatus());

        while (downloadableFile2 != null && downloadableFile2.getStatus() != DownloadStatus.DONE && downloadableFile2.getStatus() != DownloadStatus.ERROR) {
            System.out.println("Status: " + downloadableFile2.getStatus());
            System.out.println("Speed: " + downloadableFile2.getDownloadSpeed());
            System.out.println("Progress: " + downloadableFile2.getProgress());

            System.out.print("Parts statuses: ");
            downloadableFile2.getDownloadableParts().forEach(p -> System.out.print(p.getStatus() + " "));
            System.out.println();

            Thread.sleep(3000);
        }

        if (downloadableFile2 != null)
            System.out.println("DONE, status = " + downloadableFile2.getStatus());
    }
}
