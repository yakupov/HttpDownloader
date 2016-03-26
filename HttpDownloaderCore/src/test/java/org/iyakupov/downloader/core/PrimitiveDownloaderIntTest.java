package org.iyakupov.downloader.core;

import org.iyakupov.downloader.core.impl.DownloadableFile;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.net.URL;

/**
 * Created by Ilia on 26.03.2016.
 */
public class PrimitiveDownloaderIntTest {
    @Test
    @Ignore
    public void downloaderTest() throws Exception {
        /*final Downloader downloader = new Downloader(4);
        final URL url = new URL("http://mirrors.slackware.com/slackware/slackware-iso/slackware-14.1-iso/slackware-14.1-source-d61.iso");
        downloader.submitFile(url);*/

        try (Dispatcher dispatcher = new Dispatcher(20)) {
            //final URL url = new URL("http://mirrors.slackware.com/slackware/slackware-iso/slackware-14.1-iso/slackware-14.1-source-d6.iso");
            //final URL url = new URL("http://cs630616.vk.me/v630616252/27318/49e4sKOm_OU.jpg");
            final URL url = new URL("http://ru.download.nvidia.com/Windows/364.51/364.51-desktop-win10-32bit-international-whql.exe");
            System.out.println(url.getFile());
            final File outFile = new File("C:\\temp\\dl\\test.jpg");
            final DownloadableFile downloadableFile = new DownloadableFile(url, outFile, 10);
            downloadableFile.start();
            dispatcher.submitTasks(downloadableFile.getDownloadableParts(), downloadableFile);
            while (downloadableFile.getStatus() != DownloadStatus.DONE && downloadableFile.getStatus() != DownloadStatus.ERROR) {
                System.out.println("Status: " + downloadableFile.getStatus());
                Thread.sleep(10000);
            }
        }
    }
}
