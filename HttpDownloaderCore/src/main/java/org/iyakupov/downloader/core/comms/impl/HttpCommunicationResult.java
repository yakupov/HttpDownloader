package org.iyakupov.downloader.core.comms.impl;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.iyakupov.downloader.core.comms.CommunicationStatus;
import org.iyakupov.downloader.core.comms.ICommunicationResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Results of a communication via HTTP.
 */
public class HttpCommunicationResult implements ICommunicationResult {
    private CommunicationStatus communicationStatus = CommunicationStatus.NOT_STARTED;
    private String message = null;
    private CloseableHttpResponse httpResponse = null;
    private long size = -1;

    public HttpCommunicationResult(CommunicationStatus communicationStatus, String message, CloseableHttpResponse httpResponse, long size) {
        this.communicationStatus = communicationStatus;
        this.message = message;
        this.httpResponse = httpResponse;
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

    /**
     * Returns a stream of response data. If this Communication contains no data
     * or stores it in memory, null will be returned.
     *
     * @return Data stream.
     * IMPORTANT. This stream is managed by the HttpClient. Closing it explicitly will harm the performance.
     *
     * @throws IOException In case of any problems in HttpClient
     */
    @Nullable
    @Override
    public InputStream getResponseDataStream() throws IOException {
        if (httpResponse != null && httpResponse.getEntity() != null) {
            return httpResponse.getEntity().getContent();
        } else {
            return null;
        }
    }

    @Override
    public long getSize() {
        return size;
    }

    static Builder builder() {
        return new Builder();
    }

    @Override
    public void close() throws IOException {
        if (httpResponse != null)
            httpResponse.close();
    }

    static class Builder {
        private CommunicationStatus communicationStatus;
        private String message;
        private CloseableHttpResponse httpResponse;
        private long size;

        Builder setCommunicationStatus(CommunicationStatus communicationStatus) {
            this.communicationStatus = communicationStatus;
            return this;
        }

        Builder setMessage(String message) {
            this.message = message;
            return this;
        }

        Builder setHttpResponse(CloseableHttpResponse httpResponse) {
            this.httpResponse = httpResponse;
            return this;
        }

        Builder setSize(long size) {
            this.size = size;
            return this;
        }

        HttpCommunicationResult createHttpCommunicationResult() {
            return new HttpCommunicationResult(communicationStatus, message, httpResponse, size);
        }
    }
}
