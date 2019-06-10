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

import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.cloud.healthcare.imaging.dicomadapter.util.DimseRSPAssert;
import com.google.cloud.healthcare.imaging.dicomadapter.util.PortUtil;
import com.google.cloud.healthcare.util.TestUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executors;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.InputStreamDataWriter;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCEchoSCP;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CStoreServiceTest {

  // Server properties.
  private static String serverAET = "SERVER";
  private static String serverHostname = "localhost";

  // Client properties.
  ApplicationEntity clientAE;

  // Creates a HTTP request factory that returns given response code.
  private HttpRequestFactory createHttpRequestFactory(boolean connectError, int responseCode) {
    return new MockHttpTransport() {
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) {
        return new MockLowLevelHttpRequest() {
          @Override
          public LowLevelHttpResponse execute() throws IOException {
            if (connectError) {
              throw new IOException("connect error");
            }
            MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
            response.setStatusCode(responseCode);
            return response;
          }
        };
      }
    }.createRequestFactory();
  }

  private Association associate(
      String serverHostname, int serverPort, String sopClass, String syntax) throws Exception {
    AAssociateRQ rq = new AAssociateRQ();
    rq.addPresentationContext(new PresentationContext(1, sopClass, syntax));
    rq.setCalledAET(serverAET);
    Connection remoteConn = new Connection();
    remoteConn.setHostname(serverHostname);
    remoteConn.setPort(serverPort);
    return clientAE.connect(remoteConn, rq);
  }

  // Creates a DICOM service and returns the port it is listening on.
  private int createDicomServer(boolean connectError, int responseCode) throws Exception {
    int serverPort = PortUtil.getFreePort();
    DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
    serviceRegistry.addDicomService(new BasicCEchoSCP());
    HttpRequestFactory httpRequestFactory = createHttpRequestFactory(connectError, responseCode);
    CStoreService cStoreService =
        new CStoreService("https://localhost:443", "/studies", httpRequestFactory);
    serviceRegistry.addDicomService(cStoreService);
    Device serverDevice = DeviceUtil.createServerDevice(serverAET, serverPort, serviceRegistry);
    serverDevice.bindConnections();
    return serverPort;
  }

  @Before
  public void setUp() throws Exception {
    // Create the C-STORE client.
    String clientAET = "CSTORECLIENT";
    Device device = new Device(clientAET);
    Connection conn = new Connection();
    device.addConnection(conn);
    clientAE = new ApplicationEntity(clientAET);
    device.addApplicationEntity(clientAE);
    clientAE.addConnection(conn);
    device.setExecutor(Executors.newSingleThreadExecutor());
    device.setScheduledExecutor(Executors.newSingleThreadScheduledExecutor());
  }

  @Test
  public void testCStoreService_success() throws Exception {
    basicCStoreServiceTest(
        false,
        HttpStatusCodes.STATUS_CODE_OK,
        Status.Success);
  }

  @Test
  public void testCStoreService_connectionError() throws Exception {
    basicCStoreServiceTest(
        true,
        HttpStatusCodes.STATUS_CODE_OK, // won't be returned due to connectionError
        Status.ProcessingFailure);
  }

  @Test
  public void testCStoreService_unauthorized() throws Exception {
    basicCStoreServiceTest(
        false,
        HttpStatusCodes.STATUS_CODE_UNAUTHORIZED,
        Status.ProcessingFailure);
  }

  @Test
  public void testCStoreService_serviceUnavailable() throws Exception {
    basicCStoreServiceTest(
        false,
        HttpStatusCodes.STATUS_CODE_SERVICE_UNAVAILABLE,
        Status.OutOfResources);
  }

  @Test
  public void testCStoreService_noSopInstanceUid() throws Exception {
    basicCStoreServiceTest(
        true, // no stow-rs request will be made
        HttpStatusCodes.STATUS_CODE_OK,
        Status.CannotUnderstand,
        UID.MRImageStorage,
        null);
  }

  private void basicCStoreServiceTest(
      boolean connectionError,
      int httpStatus,
      int expectedDimseStatus) throws Exception {
    basicCStoreServiceTest(
        connectionError,
        httpStatus,
        expectedDimseStatus,
        UID.MRImageStorage,
        "1.0.0.0");
  }

  private void basicCStoreServiceTest(
      boolean connectionError,
      int httpStatus,
      int expectedDimseStatus,
      String sopClassUID,
      String sopInstanceUID) throws Exception {
    InputStream in =
        new DicomInputStream(TestUtils.streamDICOMStripHeaders(TestUtils.TEST_MR_FILE));
    InputStreamDataWriter data = new InputStreamDataWriter(in);

    // Create C-STORE DICOM server.
    // The server gets an unauthorized error upon invoking STOW-RS POST (which for now causes a
    // client error).
    int serverPort =
        createDicomServer(connectionError, httpStatus);

    // Associate with peer AE.
    Association association =
        associate(serverHostname, serverPort, sopClassUID, UID.ExplicitVRLittleEndian);

    // Send the DICOM file.
    DimseRSPAssert rspAssert = new DimseRSPAssert(association, expectedDimseStatus);
    association.cstore(
        sopClassUID,
        sopInstanceUID,
        1,
        data,
        UID.ExplicitVRLittleEndian,
        rspAssert);
    association.waitForOutstandingRSP();

    // Close the association.
    association.release();
    association.waitForSocketClose();

    rspAssert.assertResult();
  }

  // TODO(b/73252285): increase test coverage.
}
