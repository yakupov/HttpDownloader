package org.iyakupov.downloader.core.comms.impl;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.iyakupov.downloader.core.comms.CommunicationStatus;
import org.iyakupov.downloader.core.comms.ICommunicationComponent;
import org.iyakupov.downloader.core.comms.ICommunicationResult;
import org.iyakupov.downloader.core.exceptions.BadLocatorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Downloads files via HTTP
 */
public class HttpCommunicationComponent implements ICommunicationComponent {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final HttpClient httpClient;
    private final RequestConfig httpRequestConfig;

    public HttpCommunicationComponent() {
        final PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        // Increase max total connection to 200
        cm.setMaxTotal(200);
        // Increase default max connection per route to 20
        cm.setDefaultMaxPerRoute(200);
        /*// Increase max connections for localhost:80 to 50
        HttpHost localhost = new HttpHost("locahost", 80);
        cm.setMaxPerRoute(new HttpRoute(localhost), 50);
        */
        httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .build();

        httpRequestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(3000)
                .setConnectTimeout(3000)
                .setSocketTimeout(6000)
                .build();

        //TODO: parms
    }

    /**
     * Send HTTP HEAD request to an external server.
     *
     * @param locator URL of the requested resource.
     * @return Result of this request
     * @throws BadLocatorException wraps {@link URISyntaxException}
     */
    @Override
    public ICommunicationResult checkRemoteFile(String locator) throws BadLocatorException {
        final HttpCommunicationResult.Builder resultBuilder = HttpCommunicationResult.builder();
        try {
            final HttpHead httpRequest = new HttpHead(new URL(locator).toURI());
            httpRequest.addHeader(new BasicHeader("Range", "bytes=0-"));
            httpRequest.setConfig(httpRequestConfig);

            logger.trace("Executing request " + httpRequest.getURI());
            final HttpResponse response = httpClient.execute(httpRequest);
            final int statusCode = response.getStatusLine().getStatusCode();
            logger.trace("HTTP response code: " + statusCode + ", reason = " +
                    response.getStatusLine().getReasonPhrase());
            resultBuilder.setMessage(response.getStatusLine().getReasonPhrase());

            if (statusCode >= 200 && statusCode < 300) { //Success
                final Header contentLengthHeader = response.getFirstHeader("content-length");
                final long contentLength;
                if (contentLengthHeader != null && contentLengthHeader.getValue() != null &&
                        contentLengthHeader.getValue().matches("[0-9]+")) {
                    logger.trace("Content length: " + contentLengthHeader.getValue());
                    contentLength = Long.parseLong(contentLengthHeader.getValue());
                } else {
                    logger.warn("Received a HTTP HEAD response without valid content-length. " +
                            "We will try to download it in a single thread");
                    contentLength = -1;
                }

                resultBuilder.setSize(contentLength);
                if (contentLength > 0 && statusCode == 206) {
                    resultBuilder.setCommunicationStatus(CommunicationStatus.PARTIAL_CONTENT_OK);
                } else {
                    resultBuilder.setCommunicationStatus(CommunicationStatus.OK);
                }
            } else {
                logger.error("Download failed - bad HTTP status code: " + statusCode);
                resultBuilder.setCommunicationStatus(CommunicationStatus.ERROR); //TODO: learn to return NO_RESOURCE
            }
        } catch (IOException e) {
            logger.error("Connection to the remote server has failed", e);
            resultBuilder.setCommunicationStatus(CommunicationStatus.UNAVAILABLE);
        } catch (URISyntaxException e) {
            throw new BadLocatorException("Incorrect URL", e);
        }

        return resultBuilder.createHttpCommunicationResult();
    }

    /**
     * Download the given file using HTTP get from the start in one part
     *
     * @param locator URL of the requested resource.
     * @return Result of this request
     * @throws BadLocatorException wraps {@link URISyntaxException} and {@link MalformedURLException}
     */
    @Override
    public ICommunicationResult downloadRemoteFile(String locator) throws BadLocatorException {
        return downloadRemoteFile(locator, 0, -1);
    }

    /**
     * Download the given file using HTTP get from the start in one part
     *
     * @param locator URL of the requested resource.
     * @param start   Number of the first byte of the downloadable chunk. Set to zero for full download.
     * @param length  Length of the downloadable chunk. Set to negative for full download.
     * @return Result of this request
     * @throws BadLocatorException wraps {@link URISyntaxException} and {@link MalformedURLException}
     */
    @Override
    public ICommunicationResult downloadRemoteFile(String locator, long start, long length) throws BadLocatorException {
        final HttpCommunicationResult.Builder resultBuilder = HttpCommunicationResult.builder();
        try {
            final HttpGet httpRequest = new HttpGet(new URL(locator).toURI());
            final String end;
            if (length > 0) {
                end = String.valueOf(start + length - 1);
            } else {
                end = "";
            }
            httpRequest.addHeader(new BasicHeader("Range", "bytes=" + start + "-" + end));
            httpRequest.setConfig(httpRequestConfig);

            logger.trace("Executing request " + httpRequest.getURI());
            final HttpResponse response = httpClient.execute(httpRequest);
            final int statusCode = response.getStatusLine().getStatusCode();
            logger.trace("HTTP response code: " + statusCode + ", reason = " +
                    response.getStatusLine().getReasonPhrase());
            resultBuilder.setMessage(response.getStatusLine().getReasonPhrase());

            if (statusCode >= 200 && statusCode < 300) {
                if (response.getEntity() != null) {
                    if (statusCode != 206 && (start != 0 || length > 0)) {
                        logger.error("Successful RC partial content not supported (although requested)");
                        resultBuilder.setCommunicationStatus(CommunicationStatus.PARTIAL_CONTENT_NOK);
                    } else if (statusCode == 206) {
                        resultBuilder.setCommunicationStatus(CommunicationStatus.PARTIAL_CONTENT_OK);
                    } else {
                        resultBuilder.setCommunicationStatus(CommunicationStatus.OK);
                    }
                    resultBuilder.setSize(response.getEntity().getContentLength());
                    resultBuilder.setResponseDataStream(response.getEntity().getContent());
                } else {
                    logger.error("Successful RC but no HTTP response entity");
                    resultBuilder.setCommunicationStatus(CommunicationStatus.ERROR);
                }
            } else {
                logger.error("Download failed - bad HTTP status code: " + statusCode);
                resultBuilder.setCommunicationStatus(CommunicationStatus.ERROR); //TODO: learn to return NO_RESOURCE
            }
        } catch (URISyntaxException | MalformedURLException e) {
            throw new BadLocatorException("Incorrect URL", e);
        } catch (ClientProtocolException e) {
            logger.error("Unsupported HTTP protocol", e);
            resultBuilder.setCommunicationStatus(CommunicationStatus.ERROR);
        } catch (IOException e) {
            logger.error("Connection to the remote server has failed", e);
            resultBuilder.setCommunicationStatus(CommunicationStatus.UNAVAILABLE);
        }

        return resultBuilder.createHttpCommunicationResult();
    }
}