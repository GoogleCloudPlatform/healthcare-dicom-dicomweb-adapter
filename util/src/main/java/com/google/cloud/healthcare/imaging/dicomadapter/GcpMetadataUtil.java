package com.google.cloud.healthcare.imaging.dicomadapter;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpStatusCodes;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GcpMetadataUtil {

  private static final String baseUrl = "http://metadata/computeMetadata/v1/";
  private static Logger log = LoggerFactory.getLogger(GcpMetadataUtil.class);

  /**
   * Retrieves metadata element specified by path in GCE environment
   * https://cloud.google.com/compute/docs/storing-retrieving-metadata
   * @param requestFactory
   * @param path metadata path
   */
  public static String get(HttpRequestFactory requestFactory, String path) {
    try {
      HttpRequest httpRequest =
          requestFactory.buildGetRequest(new GenericUrl(baseUrl + path));
      httpRequest.getHeaders().put("Metadata-Flavor", "Google");
      HttpResponse httpResponse = httpRequest.execute();

      if (!httpResponse.isSuccessStatusCode()
          || httpResponse.getStatusCode() == HttpStatusCodes.STATUS_CODE_NO_CONTENT) {
        log.error("Failed to get metadata for {} with response {}:{}",
            path, httpResponse.getStatusCode(), httpResponse.getStatusMessage());
        return null;
      }

      return CharStreams.toString(new InputStreamReader(
          httpResponse.getContent(), StandardCharsets.UTF_8));
    } catch (IOException | IllegalArgumentException e) {
      log.error("Failed to get metadata for {} with exception {}", path, e);
      return null;
    }
  }
}
