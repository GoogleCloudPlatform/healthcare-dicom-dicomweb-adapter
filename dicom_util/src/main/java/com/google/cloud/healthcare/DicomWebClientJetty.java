package com.google.cloud.healthcare;

import com.google.auth.oauth2.OAuth2Credentials;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.Status;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http.MetaData.Response;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.api.Stream;
import org.eclipse.jetty.http2.api.server.ServerSessionListener;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.frames.DataFrame;
import org.eclipse.jetty.http2.frames.HeadersFrame;
import org.eclipse.jetty.http2.frames.ResetFrame;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.FuturePromise;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DicomWebClientJetty implements IDicomWebClient {

  private static Logger log = LoggerFactory.getLogger(DicomWebClientJetty.class);

  private static final int CONNECT_PORT = 443;

  private final Boolean useStowOverwrite;
  private final String stowPath;
  private final OAuth2Credentials credentials;

  public DicomWebClientJetty(
      OAuth2Credentials credentials,
      String stowPath,
      Boolean useStowOverwrite) {
    this.credentials = credentials;
    this.stowPath = StringUtil.trim(stowPath);
    this.useStowOverwrite = useStowOverwrite;
  }

  @Override
  public InputStream wadoRs(String path) throws DicomWebException {
    throw new UnsupportedOperationException("Not Implemented, use DicomWebClient");
  }

  @Override
  public JSONArray qidoRs(String path) throws DicomWebException {
    throw new UnsupportedOperationException("Not Implemented, use DicomWebClient");
  }

  @Override
  public void stowRs(InputStream in) throws DicomWebException {
    try {
      log.debug("STOW-RS to: " + stowPath);

      HTTP2Client client = new HTTP2Client();
      SslContextFactory sslContextFactory = new SslContextFactory.Client();
      client.addBean(sslContextFactory);
      client.start();

      HttpURI uri = new HttpURI(stowPath);

      FuturePromise<Session> sessionPromise = new FuturePromise<>();
      client.connect(sslContextFactory, new InetSocketAddress(uri.getHost(), CONNECT_PORT),
          new ServerSessionListener.Adapter(), sessionPromise);
      // Having a low timeout here causes flakyness when used with in transit compression.
      // This is likely due to the Stow thread sleeping and then waking up again between
      // the connect call and the session promise.get() call.
      // Reference: https://github.com/GoogleCloudPlatform/healthcare-dicom-dicomweb-adapter/issues/108
      Session session = sessionPromise.get(300, TimeUnit.SECONDS);

      // Prepare the request
      HttpFields requestFields = new HttpFields();
      if (credentials != null) {
        credentials.getRequestMetadata();
        requestFields.add(HttpHeader.AUTHORIZATION,
            "Bearer " + credentials.getAccessToken().getTokenValue());
      }
      requestFields.add(HttpHeader.CONTENT_TYPE,
          "application/dicom");
      requestFields.add(HttpHeader.ACCEPT,
          "application/dicom+json");
      MetaData.Request request = new MetaData.Request("POST", uri, HttpVersion.HTTP_2,
          requestFields);
      HeadersFrame headersFrame = new HeadersFrame(request, null, false);

      // Prepare the listener to receive the HTTP response frames.
      final StringBuilder resultBuilder = new StringBuilder();
      final CompletableFuture<Integer> responseCodeFuture = new CompletableFuture<>();
      final CompletableFuture<Boolean> doneFuture = new CompletableFuture<>();
      Stream.Listener responseListener = new Stream.Listener.Adapter() {
        @Override
        public void onReset(Stream stream, ResetFrame frame) {
          doneFuture.complete(false);
        }

        @Override
        public void onHeaders(Stream stream, HeadersFrame frame) {
          if (frame.getMetaData() instanceof Response) {
            responseCodeFuture.complete(((Response) frame.getMetaData()).getStatus());
          }
        }

        @Override
        public void onData(Stream stream, DataFrame frame, Callback callback) {
          byte[] bytes = new byte[frame.getData().remaining()];
          frame.getData().get(bytes);
          resultBuilder.append(new String(bytes, StandardCharsets.UTF_8));

          if (frame.isEndStream()) {
            doneFuture.complete(true);
          }

          callback.succeeded();
        }
      };

      FuturePromise<Stream> streamPromise = new FuturePromise<>();
      session.newStream(headersFrame, streamPromise, responseListener);
      Stream stream = streamPromise.get();

      DataStream dataStream = new DataStream(stream, in);
      try {
        dataStream.send();
      } catch (IOException e) {
        if (!doneFuture.isDone()) {
          throw e;
        }
      }

      doneFuture.get();
      client.stop();

      int httpStatus = responseCodeFuture.get();
      if (httpStatus != HttpStatus.OK_200) {
        String resp = resultBuilder.toString();
        throw new DicomWebException(
            "Http_" + httpStatus + ": " + resp, httpStatus, Status.ProcessingFailure);
      }
    } catch (Exception e) {
      if (e instanceof DicomWebException) {
        throw (DicomWebException) e;
      }
      throw new DicomWebException(e);
    }
  }

  /** Retry the STOW-RS on an HTTP409, after DELETE. */
  @Override
  public Boolean getStowOverwrite() {
    return this.useStowOverwrite;
  }

  /**
   * Deletes the resource and returns void.
   *
   * @param path The resource URL path to delete.
   */
  @Override
  public void delete(String path) throws IDicomWebClient.DicomWebException {
    try {
      String deletePath = String.format("%s/%s", stowPath, StringUtil.trim(path));
      log.debug("DELETE to: " + deletePath);

      HTTP2Client client = new HTTP2Client();
      SslContextFactory sslContextFactory = new SslContextFactory.Client();
      client.addBean(sslContextFactory);
      client.start();

      HttpURI uri = new HttpURI(deletePath);

      FuturePromise<Session> sessionPromise = new FuturePromise<>();
      client.connect(
          sslContextFactory,
          new InetSocketAddress(uri.getHost(), CONNECT_PORT),
          new ServerSessionListener.Adapter(),
          sessionPromise);

      // Applied based on logic in the STOW request.
      Session session = sessionPromise.get(300, TimeUnit.SECONDS);

      // Prepare the request
      HttpFields requestFields = new HttpFields();
      if (credentials != null) {
        credentials.getRequestMetadata();
        requestFields.add(
            HttpHeader.AUTHORIZATION, "Bearer " + credentials.getAccessToken().getTokenValue());
      }

      MetaData.Request request =
          new MetaData.Request("DELETE", uri, HttpVersion.HTTP_2, requestFields);
      HeadersFrame headersFrame = new HeadersFrame(request, null, true);

      // Prepare the listener to receive the HTTP response frames.
      final StringBuilder resultBuilder = new StringBuilder();
      final CompletableFuture<Integer> responseCodeFuture = new CompletableFuture<>();
      final CompletableFuture<Boolean> doneFuture = new CompletableFuture<>();

      session.newStream(
          headersFrame,
          new FuturePromise<>(),
          new Stream.Listener.Adapter() {
            @Override
            public void onReset(Stream stream, ResetFrame frame) {
              doneFuture.complete(false);
            }

            @Override
            public void onHeaders(Stream stream, HeadersFrame frame) {
              if (frame.getMetaData() instanceof Response) {
                responseCodeFuture.complete(((Response) frame.getMetaData()).getStatus());
              }
            }

            @Override
            public void onData(Stream stream, DataFrame frame, Callback callback) {
              byte[] bytes = new byte[frame.getData().remaining()];
              frame.getData().get(bytes);
              resultBuilder.append(new String(bytes, StandardCharsets.UTF_8));

              if (frame.isEndStream()) {
                doneFuture.complete(true);
              }

              callback.succeeded();
            }
          });

      doneFuture.get();
      client.stop();

      int httpStatus = responseCodeFuture.get();
      if (httpStatus != HttpStatus.OK_200) {
        String resp = resultBuilder.toString();
        throw new DicomWebException(
            "Http_" + httpStatus + ": " + resp, httpStatus, Status.ProcessingFailure);
      }
    } catch (Exception e) {
      if (e instanceof DicomWebException) {
        throw (DicomWebException) e;
      }
      throw new DicomWebException(e);
    }
  }

  /**
   * Reads the resource to figure out the study/series/instance UID path, deletes it and returns
   * void.
   *
   * @param stream The unread stream containing the DICOM instance.
   */
  @Override
  public void delete(InputStream stream) throws IDicomWebClient.DicomWebException {
    try {
      DicomInputStream dis = new DicomInputStream(stream);
      Attributes attrs = dis.readDataset(-1, Tag.PixelData);
      String instanceUrl =
          String.format(
              "%s/series/%s/instances/%s", // NOTE: /studies/ already in stowPath
              StringUtil.getTagValueAsStringOrException(attrs, Tag.StudyInstanceUID),
              StringUtil.getTagValueAsStringOrException(attrs, Tag.SeriesInstanceUID),
              StringUtil.getTagValueAsStringOrException(attrs, Tag.SOPInstanceUID));
      this.delete(instanceUrl);
    } catch (IOException e) {
      throw new IDicomWebClient.DicomWebException(e);
    }
  }

  private static class DataStream {

    private static final int BUFFER_SIZE = 8192;

    private final byte[] buffer = new byte[BUFFER_SIZE];
    private final InputStream in;
    private final Stream stream;

    private boolean endStream = false;

    public DataStream(Stream http2stream, InputStream inputStream) {
      this.stream = http2stream;
      this.in = inputStream;
    }

    public boolean hasNextFrame() {
      return !endStream;
    }

    public DataFrame nextFrame() throws IOException {
      int read = in.read(buffer);
      if (read == -1) {
        endStream = true;
        return new DataFrame(stream.getId(),
            ByteBuffer.wrap(buffer, 0, 0), true);
      } else {
        return new DataFrame(stream.getId(),
            ByteBuffer.wrap(buffer, 0, read), false);
      }
    }

    public void send() throws IOException {
      CheckerCallback callback = new CheckerCallback();
      while (hasNextFrame()) {
        stream.data(nextFrame(), callback);
        callback.checkAndReset();
      }
    }
  }

  private static class CheckerCallback implements Callback {

    private CompletableFuture<Throwable> result = new CompletableFuture<Throwable>();

    @Override
    public void succeeded() {
      result.complete(null);
    }

    @Override
    public void failed(Throwable x) {
      result.complete(x);
    }

    public void checkAndReset() throws IOException {
      try {
        Throwable x = result.get();
        result = new CompletableFuture<>();
        if (x != null) {
          throw new IOException(x);
        }
      } catch (InterruptedException | ExecutionException e) {
        throw new IOException(e);
      }
    }
  }
}
