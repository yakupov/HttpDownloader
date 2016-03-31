package org.iyakupov.downloader.core.comms.impl;

import org.iyakupov.downloader.core.comms.CommunicationStatus;
import org.iyakupov.downloader.core.comms.ICommunicationResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Results of a communication via HTTP.
 */
public class HttpCommunicationResult implements ICommunicationResult {
    private CommunicationStatus communicationStatus = CommunicationStatus.NOT_STARTED;
    private String message = null;
    private InputStream responseDataStream = null;
    private long size = -1;

    public HttpCommunicationResult(CommunicationStatus communicationStatus, String message, InputStream responseDataStream, long size) {
        this.communicationStatus = communicationStatus;
        this.message = message;
        this.responseDataStream = responseDataStream;
        this.size = size;
    }

    @NotNull
    @Override
    public CommunicationStatus getResponseCode() {
        return communicationStatus;
    }

    @Nullable
    @Override
    public String getMessage() {
        return message;
    }

    @Nullable
    @Override
    public ByteBuffer getResponseData() {
        return null;
    }

    @Nullable
    @Override
    public InputStream getResponseDataStream() {
        return responseDataStream;
    }

    @Override
    public long getSize() {
        return size;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private CommunicationStatus communicationStatus;
        private String message;
        private InputStream responseDataStream;
        private long size;

        public Builder setCommunicationStatus(CommunicationStatus communicationStatus) {
            this.communicationStatus = communicationStatus;
            return this;
        }

        public Builder setMessage(String message) {
            this.message = message;
            return this;
        }

        public Builder setResponseDataStream(InputStream responseDataStream) {
            this.responseDataStream = responseDataStream;
            return this;
        }

        public Builder setSize(long size) {
            this.size = size;
            return this;
        }

        public HttpCommunicationResult createHttpCommunicationResult() {
            return new HttpCommunicationResult(communicationStatus, message, responseDataStream, size);
        }
    }
}
