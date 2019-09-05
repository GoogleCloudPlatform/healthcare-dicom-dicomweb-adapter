// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.healthcare.imaging.dicomadapter;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpStatusCodes;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GcpMetadataUtil {

  private static final String baseUrl = "http://metadata/computeMetadata/v1/";
  private static Logger log = LoggerFactory.getLogger(GcpMetadataUtil.class);

  /**
   * Retrieves metadata element specified by path in GCE environment https://cloud.google.com/compute/docs/storing-retrieving-metadata
   *
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
        log.warn("Failed to get metadata for {} with response {}:{}",
            path, httpResponse.getStatusCode(), httpResponse.getStatusMessage());
        return null;
      }

      return CharStreams.toString(new InputStreamReader(
          httpResponse.getContent(), StandardCharsets.UTF_8));
    } catch (UnknownHostException e) {
      log.trace("Not GCP environment, failed to get metadata for {} with exception {}", path, e);
      return null;
    } catch (IOException | IllegalArgumentException e) {
      log.warn("Failed to get metadata for {} with exception {}", path, e);
      return null;
    }
  }
}
