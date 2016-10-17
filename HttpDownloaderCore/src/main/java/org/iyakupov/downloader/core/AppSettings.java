package org.iyakupov.downloader.core;

/**
 * Downloader core configuration parameters holder
 */
public class AppSettings {
    //Application-level downloader parameters
    private static volatile int downloadBufferSize = 4 * 1024; //4KBytes
    private static volatile long downloadSpeedMeasureThreshold = (long) 1e9; //in nS

    //HTTP client parameters
    private static volatile int httpMaxConnections = 200;
    private static volatile int httpConnRqTimeout = 6000;
    private static volatile int httpConnTimeout = 6000;
    private static volatile int httpSocketTimeout = 30000;

    //Dispatcher
    private static volatile int dispatchingQueueCapacity = 1000;

    public static int getDownloadBufferSize() {
        return downloadBufferSize;
    }

    public static void setDownloadBufferSize(int downloadBufferSize) {
        AppSettings.downloadBufferSize = downloadBufferSize;
    }

    public static long getDownloadSpeedMeasureThreshold() {
        return downloadSpeedMeasureThreshold;
    }

    public static void setDownloadSpeedMeasureThreshold(long downloadSpeedMeasureThreshold) {
        AppSettings.downloadSpeedMeasureThreshold = downloadSpeedMeasureThreshold;
    }

    public static int getHttpMaxConnections() {
        return httpMaxConnections;
    }

    public static void setHttpMaxConnections(int httpMaxConnections) {
        AppSettings.httpMaxConnections = httpMaxConnections;
    }

    public static int getHttpConnRqTimeout() {
        return httpConnRqTimeout;
    }

    public static void setHttpConnRqTimeout(int httpConnRqTimeout) {
        AppSettings.httpConnRqTimeout = httpConnRqTimeout;
    }

    public static int getHttpConnTimeout() {
        return httpConnTimeout;
    }

    public static void setHttpConnTimeout(int httpConnTimeout) {
        AppSettings.httpConnTimeout = httpConnTimeout;
    }

    public static int getHttpSocketTimeout() {
        return httpSocketTimeout;
    }

    public static void setHttpSocketTimeout(int httpSocketTimeout) {
        AppSettings.httpSocketTimeout = httpSocketTimeout;
    }

    public static int getDispatchingQueueCapacity() {
        return dispatchingQueueCapacity;
    }

    public static void setDispatchingQueueCapacity(int dispatchingQueueCapacity) {
        AppSettings.dispatchingQueueCapacity = dispatchingQueueCapacity;
    }
}
