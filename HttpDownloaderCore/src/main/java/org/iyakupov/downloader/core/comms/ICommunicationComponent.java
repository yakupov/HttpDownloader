package org.iyakupov.downloader.core.comms;

import org.iyakupov.downloader.core.exceptions.BadLocatorException;

import java.io.Closeable;

/**
 * Abstraction for performance of communications with external systems
 */
public interface ICommunicationComponent extends Closeable {
    /**
     * Try to connect to external resource but don't download the file.
     *
     * @param locator Locator (identifier) of the requested resource.
     * @return Result of this request
     * @throws BadLocatorException If failed to parse the locator.
     */
    ICommunicationResult checkRemoteFile(String locator) throws BadLocatorException;

    /**
     * Try to connect to external resource and start downloading it.
     *
     * @param locator Locator (identifier) of the requested resource.
     * @return Result of this request
     * @throws BadLocatorException If failed to parse the locator.
     */
    ICommunicationResult downloadRemoteFile(String locator) throws BadLocatorException;

    /**
     * Try to connect to external resource and start downloading a part of it.
     *
     * @param locator Locator (identifier) of the requested resource.
     * @param start   Number of the first byte of the file to download.
     * @param size    Length of the downloadable chunk.
     * @return Result of this request
     * @throws BadLocatorException If failed to parse the locator.
     */
    ICommunicationResult downloadRemoteFile(String locator, long start, long size) throws BadLocatorException;
}
