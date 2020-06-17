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

package com.google.cloud.healthcare;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.eclipse.jetty.http.MultiPartParser;
import com.google.api.client.http.HttpMediaType;
import com.google.api.client.testing.http.HttpTesting;
import com.google.cloud.healthcare.util.FakeWebServer;
import com.google.cloud.healthcare.util.TestUtils;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DicomWebClientTest {

  private FakeWebServer fakeDicomWebServer;
  private DicomWebClient client;

  @Before
  public void setUp() {
    fakeDicomWebServer = new FakeWebServer();
    client = new DicomWebClient(fakeDicomWebServer.createRequestFactory(), HttpTesting.SIMPLE_URL, "/studies");
  }

  @Test
  public void testDicomWebClient_Wado() throws Exception {
    byte[] dicomInstance = TestUtils.readTestFile(TestUtils.TEST_MR_FILE);

    fakeDicomWebServer.addWadoResponse(dicomInstance);
    InputStream responseStream = client.wadoRs("instanceName");

    assertNotNull(responseStream);

    byte[] actual = ByteStreams.toByteArray(responseStream);
    if (!Arrays.equals(dicomInstance, actual)) {
      fail("wadoRs returned unexpected DICOM bytes");
    }
  }

  @Test
  public void testDicomWebClient_WadoError() throws Exception {
    fakeDicomWebServer.addResponseWithStatusCode(404);
    assertThrows(IDicomWebClient.DicomWebException.class, () -> client.wadoRs("instanceName"));
  }

  @Test
  public void testDicomWebClient_Qido() throws Exception {
    String qidoResponse =
        "[{\"0020000E\":{\"vr\":\"UI\",\"Value\":[\"1.2.840.113619.2.176.3596.3364818.7819.1259708454.108\"]}}]";
    fakeDicomWebServer.addJsonResponse(qidoResponse);
    JSONArray jsonArray = client.qidoRs("query");
    assertThat(jsonArray.length()).isEqualTo(1);
    JSONObject tag = jsonArray.getJSONObject(0).getJSONObject("0020000E");
    JSONArray tagArray = tag.getJSONArray("Value");
    assertThat(tagArray.length()).isEqualTo(1);
    String tagValue = tagArray.getString(0);
    assertThat(tagValue).isEqualTo("1.2.840.113619.2.176.3596.3364818.7819.1259708454.108");
  }

  @Test
  public void testDicomWebClient_QidoError() throws Exception {
    fakeDicomWebServer.addResponseWithStatusCode(404);
    assertThrows(IDicomWebClient.DicomWebException.class, () -> client.qidoRs("query"));
  }

  @Test
  public void testDicomWebClient_NothingInjected() throws Exception {
    assertThrows(IDicomWebClient.DicomWebException.class, () -> client.qidoRs("query"));
  }

  // This interface isn't well documented, but you can find the implementation on GitHub
  // at https://github.com/eclipse/jetty.project/blob/jetty-9.4.20.v20190813/jetty-http/src/main/java/org/eclipse/jetty/http/MultiPartParser.java.
  private static class MultipartHandler implements MultiPartParser.Handler {
    ByteBuffer lastItem;

    @Override
    public boolean content​(ByteBuffer item, boolean last) {
      lastItem = item;

      // Returning true seems to trigger early abort of the parser.
      return false;
    }
  }

  @Test
  public void testDicomWebClient_Stow() throws Exception {
    fakeDicomWebServer.addResponseWithStatusCode(200);

    String data = "some data";
    client.stowRs(new ByteArrayInputStream(data.getBytes()));

    FakeWebServer.Request req = fakeDicomWebServer.getRequests().firstElement();

    ByteArrayOutputStream body = new ByteArrayOutputStream();
    req.request.getStreamingContent().writeTo(body);

    String contentType = req.request.getContentType();
    HttpMediaType mediaType = new HttpMediaType(contentType);
    String boundary = mediaType.getParameter("boundary");

    MultipartHandler handler = new MultipartHandler();
    MultiPartParser parser = new MultiPartParser(handler,boundary);
    parser.parse(ByteBuffer.wrap(body.toByteArray()), true);

    assertThat(StandardCharsets.UTF_8.decode(handler.lastItem).toString()).isEqualTo(data);
  }

  @Test
  public void testDicomWebClient_StowError() throws Exception {
    fakeDicomWebServer.addResponseWithStatusCode(404);
    assertThrows(
        IDicomWebClient.DicomWebException.class,
        () -> client.stowRs(new ByteArrayInputStream(new byte[0])));
  }
}
