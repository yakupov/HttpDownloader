package org.iyakupov.downloader.core.exceptions;

/**
 * In the current implementation it's born to wrap a {@link java.net.MalformedURLException}
 */
public class BadLocatorException extends RuntimeException {
    public BadLocatorException(String message, Throwable cause) {
        super(message, cause);
    }
}
