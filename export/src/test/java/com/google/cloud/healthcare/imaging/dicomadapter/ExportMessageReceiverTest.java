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

import static com.google.common.truth.Truth.assertThat;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.testing.http.HttpTesting;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.healthcare.DicomWebClient;
import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.util.FakeWebServer;
import com.google.cloud.healthcare.util.TestUtils;
import com.google.cloud.healthcare.imaging.dicomadapter.util.PortUtil;
import com.google.cloud.healthcare.imaging.dicomadapter.util.StubCStoreService;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import java.io.IOException;
import java.io.InputStream;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.service.BasicCEchoSCP;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ExportMessageReceiverTest {

  private final String serverAET = "SERVER";
  private final String serverHost = "localhost";
  private final String clientAET = "CLIENT";
  private final String testPubsubPath = "/studies/1/series/2/instances/3";
  private final String testQidoResponse =
      "[{\"00020010\":{\"vr\":\"UI\",\"Value\":[\"1\"]},"
          + "\"00080016\":{\"vr\":\"UI\",\"Value\":[\"2\"]},"
          + "\"00080018\":{\"vr\":\"UI\",\"Value\":[\"3\"]}}]";
  private ApplicationEntity clientAE;
  private DicomInputStream dicom;

  @Before
  public void setUp() throws Exception {
    clientAE = new ApplicationEntity(clientAET);
    Connection conn = new Connection();
    DeviceUtil.createClientDevice(clientAE, conn);
    clientAE.addConnection(conn);

    dicom = new DicomInputStream(TestUtils.streamTestFile(TestUtils.TEST_MR_FILE));
  }

  private int createServerDevice(String sopClass, String transferSyntax, int wantResponseStatus)
      throws Exception {
    int serverPort = PortUtil.getFreePort();
    DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
    serviceRegistry.addDicomService(new BasicCEchoSCP());
    serviceRegistry.addDicomService(new StubCStoreService(wantResponseStatus));
    TransferCapability transferCapability =
        new TransferCapability(
            null /* commonName */, sopClass, TransferCapability.Role.SCP, transferSyntax);
    Device serverDevice =
        DeviceUtil.createServerDevice(serverAET, serverPort, serviceRegistry, transferCapability);
    serverDevice.bindConnections();
    return serverPort;
  }

  // StubAckReplyConsumer is a test helper used to check whether Pub/Sub message was ACK-ed
  // (representing a successful export).
  private class StubAckReplyConsumer implements AckReplyConsumer {

    private boolean isAck = false;

    @Override
    public void ack() {
      isAck = true;
    }

    @Override
    public void nack() {
    }

    public boolean isAck() {
      return isAck;
    }
  }

  // Creates an ExportMessageReceiver that uses C-STORE for use in unit-tests.
  ExportMessageReceiver testCStoreExportMessageReceiver(
      int serverPort, FakeWebServer fakeWebServer, StubAckReplyConsumer replyConsumer)
      throws Exception {
    return testCStoreExportMessageReceiverWithPath(
        serverPort, fakeWebServer, replyConsumer, testPubsubPath);
  }

  ExportMessageReceiver testCStoreExportMessageReceiverWithPath(
      int serverPort,
      FakeWebServer fakeWebServer,
      StubAckReplyConsumer replyConsumer,
      String pubsubPath)
      throws Exception {
    ByteString pubsubMessageBytes = ByteString.copyFromUtf8(pubsubPath);
    PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(pubsubMessageBytes).build();
    DicomWebClient dicomWebClient =
        new DicomWebClient(fakeWebServer.createRequestFactory(), HttpTesting.SIMPLE_URL, "/studies");
    DicomSender dicomSender =
        new CStoreSender(clientAE, serverAET, serverHost, serverPort, dicomWebClient);
    ExportMessageReceiver receiver = new ExportMessageReceiver(dicomSender);
    receiver.receiveMessage(pubsubMessage, replyConsumer);
    return receiver;
  }

  // Creates an ExportMessageReceiver that uses STOW-RS for use in unit-tests.
  ExportMessageReceiver testStowRsExportMessageReceiver(
      FakeWebServer fakeSourceDicomWebServer,
      FakeWebServer fakeSinkDicomWebServer,
      StubAckReplyConsumer replyConsumer)
      throws Exception {
    return testStowRsExportMessageReceiverWithPath(
        fakeSourceDicomWebServer, fakeSinkDicomWebServer, replyConsumer, testPubsubPath);
  }

  ExportMessageReceiver testStowRsExportMessageReceiverWithPath(
      FakeWebServer fakeSourceDicomWebServer,
      FakeWebServer fakeSinkDicomWebServer,
      StubAckReplyConsumer replyConsumer,
      String pubsubPath)
      throws Exception {
    ByteString pubsubMessageBytes = ByteString.copyFromUtf8(pubsubPath);
    PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(pubsubMessageBytes).build();
    DicomWebClient sourceDicomWebClient =
        new DicomWebClient(fakeSourceDicomWebServer.createRequestFactory(), HttpTesting.SIMPLE_URL, "/studies");
    DicomWebClient sinkDicomWebClient =
        new DicomWebClient(fakeSinkDicomWebServer.createRequestFactory(), HttpTesting.SIMPLE_URL, "/studies") {
          // This tests only ExportMessageReceiver and StowRsSender.
          // Properly mocking HTTP2 for low-level jetty stowrs implementation would be
          // significantly more difficult (but it's used during integration test)
          public void stowRs(InputStream in) throws IDicomWebClient.DicomWebException {
            try {
              requestFactory.buildGetRequest(new GenericUrl("http://nope")).execute();
            } catch (IOException e) {
              throw new DicomWebException(e);
            }
          }
        };
    DicomSender dicomSender = new StowRsSender(sourceDicomWebClient, sinkDicomWebClient);
    ExportMessageReceiver receiver = new ExportMessageReceiver(dicomSender);
    receiver.receiveMessage(pubsubMessage, replyConsumer);
    return receiver;
  }

  @Test
  public void ExportMessageReceiverTest_CStoreSuccess() throws Exception {
    int serverPort = createServerDevice("*", "*", Status.Success);
    FakeWebServer fakeWebServer = new FakeWebServer();
    fakeWebServer.addJsonResponse(testQidoResponse);
    fakeWebServer.addWadoResponse(TestUtils.readTestFile(TestUtils.TEST_MR_FILE));

    StubAckReplyConsumer replyConsumer = new StubAckReplyConsumer();
    ExportMessageReceiver receiver =
        testCStoreExportMessageReceiver(serverPort, fakeWebServer, replyConsumer);
    assertThat(replyConsumer.isAck()).isTrue();
  }

  @Test
  public void ExportMessageReceiverTest_CStoreInvalidQidoResponse() throws Exception {
    int serverPort = createServerDevice("*", "*", Status.Success);
    FakeWebServer fakeWebServer = new FakeWebServer();
    fakeWebServer.addJsonResponse("[{}]");

    StubAckReplyConsumer replyConsumer = new StubAckReplyConsumer();
    ExportMessageReceiver receiver =
        testCStoreExportMessageReceiver(serverPort, fakeWebServer, replyConsumer);
    assertThat(replyConsumer.isAck()).isFalse();
  }

  @Test
  public void ExportMessageReceiverTest_CStoreDicomWebConnectionError() throws Exception {
    int serverPort = createServerDevice("*", "*", Status.Success);
    FakeWebServer fakeWebServer = new FakeWebServer();
    fakeWebServer.addResponseWithStatusCode(403);

    StubAckReplyConsumer replyConsumer = new StubAckReplyConsumer();
    ExportMessageReceiver receiver =
        testCStoreExportMessageReceiver(serverPort, fakeWebServer, replyConsumer);
    assertThat(replyConsumer.isAck()).isFalse();
  }

  @Test
  public void ExportMessageReceiverTest_CStoreAssociationRejected() throws Exception {
    int serverPort = createServerDevice(UID.MRImageStorage, "*", Status.Success);
    FakeWebServer fakeWebServer = new FakeWebServer();
    fakeWebServer.addJsonResponse(testQidoResponse);
    fakeWebServer.addWadoResponse(TestUtils.readTestFile(TestUtils.TEST_MR_FILE));

    StubAckReplyConsumer replyConsumer = new StubAckReplyConsumer();
    ExportMessageReceiver receiver =
        testCStoreExportMessageReceiver(serverPort, fakeWebServer, replyConsumer);
    assertThat(replyConsumer.isAck()).isFalse();
  }

  @Test
  public void ExportMessageReceiverTest_CStoreError() throws Exception {
    int serverPort = createServerDevice("*", "*", Status.ProcessingFailure);
    FakeWebServer fakeWebServer = new FakeWebServer();
    fakeWebServer.addJsonResponse(testQidoResponse);
    fakeWebServer.addWadoResponse(TestUtils.readTestFile(TestUtils.TEST_MR_FILE));

    StubAckReplyConsumer replyConsumer = new StubAckReplyConsumer();
    ExportMessageReceiver receiver =
        testCStoreExportMessageReceiver(serverPort, fakeWebServer, replyConsumer);
    assertThat(replyConsumer.isAck()).isFalse();
  }

  @Test
  public void ExportMessageReceiverTest_StowRsSuccess() throws Exception {
    // Service that sources DICOM.
    FakeWebServer fakeSourceDicomWebServer = new FakeWebServer();
    fakeSourceDicomWebServer.addWadoResponse(TestUtils.readTestFile(TestUtils.TEST_MR_FILE));

    // Service that sinks DICOM.
    FakeWebServer fakeSinkDicomWebServer = new FakeWebServer();
    fakeSinkDicomWebServer.addResponseWithStatusCode(200);

    StubAckReplyConsumer replyConsumer = new StubAckReplyConsumer();
    ExportMessageReceiver receiver =
        testStowRsExportMessageReceiver(
            fakeSourceDicomWebServer, fakeSinkDicomWebServer, replyConsumer);
    assertThat(replyConsumer.isAck()).isTrue();
  }

  @Test
  public void ExportMessageReceiverTest_StowRsSourceWadoRequestError() throws Exception {
    // Service that sources DICOM.
    FakeWebServer fakeSourceDicomWebServer = new FakeWebServer();
    fakeSourceDicomWebServer.addResponseWithStatusCode(404);

    // Service that sinks DICOM.
    FakeWebServer fakeSinkDicomWebServer = new FakeWebServer();

    StubAckReplyConsumer replyConsumer = new StubAckReplyConsumer();
    ExportMessageReceiver receiver =
        testStowRsExportMessageReceiver(
            fakeSourceDicomWebServer, fakeSinkDicomWebServer, replyConsumer);
    assertThat(replyConsumer.isAck()).isFalse();
  }

  @Test
  public void ExportMessageReceiverTest_StowRsSinkStowRequestError() throws Exception {
    // Service that sources DICOM.
    FakeWebServer fakeSourceDicomWebServer = new FakeWebServer();
    fakeSourceDicomWebServer.addWadoResponse(TestUtils.readTestFile(TestUtils.TEST_MR_FILE));

    // Service that sinks DICOM.
    FakeWebServer fakeSinkDicomWebServer = new FakeWebServer();
    fakeSinkDicomWebServer.addResponseWithStatusCode(400);

    StubAckReplyConsumer replyConsumer = new StubAckReplyConsumer();
    ExportMessageReceiver receiver =
        testStowRsExportMessageReceiver(
            fakeSourceDicomWebServer, fakeSinkDicomWebServer, replyConsumer);
    assertThat(replyConsumer.isAck()).isFalse();
  }

  @Test
  public void selectPeerCredentials_defaultFalse_returnsNull() {
    Flags flags = new Flags();
    Flags.useGcpApplicationDefaultCredentials = false;
    GoogleCredentials credentials = GoogleCredentials.create(new AccessToken("test-token", null));

    assertThat(ExportAdapter.selectPeerCredentials(flags, credentials)).isNull();
  }

  @Test
  public void selectPeerCredentials_true_returnsCredentials() {
    Flags flags = new Flags();
    Flags.useGcpApplicationDefaultCredentials = true;
    GoogleCredentials credentials = GoogleCredentials.create(new AccessToken("test-token", null));

    try {
      assertThat(ExportAdapter.selectPeerCredentials(flags, credentials))
          .isSameInstanceAs(credentials);
    } finally {
      Flags.useGcpApplicationDefaultCredentials = false;
    }
  }

  @Test
  public void ExportMessageReceiverTest_StowRsRejectsPathTraversal() throws Exception {
    FakeWebServer fakeSourceDicomWebServer = new FakeWebServer();
    FakeWebServer fakeSinkDicomWebServer = new FakeWebServer();
    StubAckReplyConsumer replyConsumer = new StubAckReplyConsumer();

    testStowRsExportMessageReceiverWithPath(
        fakeSourceDicomWebServer,
        fakeSinkDicomWebServer,
        replyConsumer,
        "../../../../../../../../target-project/locations/us-central1/datasets/d/dicomStores/s/dicomWeb/studies/1/series/2/instances/3");

    assertThat(replyConsumer.isAck()).isFalse();
    assertThat(fakeSourceDicomWebServer.getRequests()).isEmpty();
    assertThat(fakeSinkDicomWebServer.getRequests()).isEmpty();
  }

  @Test
  public void ExportMessageReceiverTest_CStoreRejectsPathTraversal() throws Exception {
    int serverPort = createServerDevice("*", "*", Status.Success);
    FakeWebServer fakeWebServer = new FakeWebServer();
    StubAckReplyConsumer replyConsumer = new StubAckReplyConsumer();

    testCStoreExportMessageReceiverWithPath(
        serverPort,
        fakeWebServer,
        replyConsumer,
        "../../../../../../../../target-project/locations/us-central1/datasets/d/dicomStores/s/dicomWeb/studies/1/series/2/instances/3");

    assertThat(replyConsumer.isAck()).isFalse();
    assertThat(fakeWebServer.getRequests()).isEmpty();
  }

  @Test
  public void ExportMessageReceiverTest_StowRsResourceRelativePathSuccess() throws Exception {
    FakeWebServer fakeSourceDicomWebServer = new FakeWebServer();
    fakeSourceDicomWebServer.addWadoResponse(TestUtils.readTestFile(TestUtils.TEST_MR_FILE));
    FakeWebServer fakeSinkDicomWebServer = new FakeWebServer();
    fakeSinkDicomWebServer.addResponseWithStatusCode(200);
    StubAckReplyConsumer replyConsumer = new StubAckReplyConsumer();

    testStowRsExportMessageReceiverWithPath(
        fakeSourceDicomWebServer,
        fakeSinkDicomWebServer,
        replyConsumer,
        "projects/p/locations/l/datasets/d/dicomStores/s/dicomWeb/studies/1/series/2/instances/3");

    assertThat(replyConsumer.isAck()).isTrue();
  }
}
