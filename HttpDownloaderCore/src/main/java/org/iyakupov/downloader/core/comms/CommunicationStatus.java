package org.iyakupov.downloader.core.comms;

/**
 * List of possible statuses of a Communication
 */
public enum CommunicationStatus {
    NOT_STARTED,
    PARTIAL_CONTENT_OK,
    PARTIAL_CONTENT_NOK,
    OK,
    UNAVAILABLE,
    ERROR
}
