package org.iyakupov.downloader.core.comms;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Represents the results of communication with an external resource (e.g. via a network).
 */
public interface ICommunicationResult extends Closeable {
    /**
     * @return Response code, indicating the status of this request.
     */
    @NotNull
    CommunicationStatus getResponseCode();

    /**
     * @return Response text (e.g. error message)
     */
    @Nullable
    String getMessage();

    /**
     * Returns response data. If this Communication does not store the data in memory, null will be returned.
     *
     * @return Response data.
     */
    @Nullable
    ByteBuffer getResponseData();

    /**
     * Returns a stream of response data. If this Communication contains no data
     * or stores it in memory, null will be returned.
     *
     * @return Response data stream.
     */
    @Nullable
    InputStream getResponseDataStream() throws IOException;

    /**
     * @return Size of the whole file or of the downloadable chunk.
     * Negative value means that that it's not possible to obtain the length.
     */
    long getSize();
}
