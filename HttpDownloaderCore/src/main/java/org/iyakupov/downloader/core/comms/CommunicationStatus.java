package org.iyakupov.downloader.core.comms;

/**
 * List of possible statuses of a Communication
 */
public enum CommunicationStatus {
    NOT_STARTED,
    IN_PROGRESS,
    PARTIAL_CONTENT_OK,
    PARTIAL_CONTENT_NOK,
    OK,
    NO_RESOURCE,
    UNAVAILABLE,
    ERROR
}
