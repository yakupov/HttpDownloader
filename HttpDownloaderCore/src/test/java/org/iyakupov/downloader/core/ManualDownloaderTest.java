package org.iyakupov.downloader.core;

import org.apache.commons.io.FilenameUtils;
import org.iyakupov.downloader.core.impl.DownloadableFile;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by Ilia on 26.03.2016.
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
        try (Dispatcher dispatcher = new Dispatcher(20)) {
            final File outputDir = new File("C:\\temp\\dl");
            final DownloadableFile downloadableFile = new DownloadableFile(nv260MUrl, outputDir, 2);
            downloadableFile.start();
            dispatcher.submitTasks(downloadableFile.getDownloadableParts(), downloadableFile);
            while (downloadableFile.getStatus() != DownloadStatus.DONE && downloadableFile.getStatus() != DownloadStatus.ERROR) {
                System.out.println("Status: " + downloadableFile.getStatus());
                System.out.println("Speed: " + downloadableFile.getDownloadSpeed());
                System.out.println("Progress: " + downloadableFile.getProgress());
                Thread.sleep(3000);
            }
        }
    }
}
