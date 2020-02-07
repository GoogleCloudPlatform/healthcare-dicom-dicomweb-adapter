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

import com.github.danieln.multipart.MultipartInput;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.util.DimseRSPAssert;
import com.google.cloud.healthcare.imaging.dicomadapter.util.PortUtil;
import com.google.cloud.healthcare.util.TestUtils;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import org.dcm4che3.data.Tag;
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
import org.dcm4che3.util.TagUtils;
import org.json.JSONArray;
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
  private int createDicomServer(
      boolean connectError,
      int responseCode,
      MockDestinationConfig[] destinationConfigs) throws Exception {
    int serverPort = PortUtil.getFreePort();
    DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
    serviceRegistry.addDicomService(new BasicCEchoSCP());
    IDicomWebClient dicomWebClient =
        new MockStowClient(connectError, responseCode);
    Map<DestinationFilter, IDicomWebClient> destinationMap = new LinkedHashMap<>();
    if(destinationConfigs != null) {
      for (MockDestinationConfig conf : destinationConfigs) {
        destinationMap.put(
            new DestinationFilter(conf.filter),
            new MockStowClient(conf.connectError, conf.httpResponseCode)
        );
      }
    }
    CStoreService cStoreService =
        new CStoreService(dicomWebClient, destinationMap, null);
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
        Status.NotAuthorized);
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
        null,
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
        null,
        UID.MRImageStorage,
        "1.0.0.0");
  }

  @Test
  public void testCStoreService_map_success() throws Exception {
    basicCStoreServiceTest(
        true,
        HttpStatusCodes.STATUS_CODE_SERVER_ERROR,
        Status.Success,
        new MockDestinationConfig[] {
            new MockDestinationConfig("StudyDate=19921012&SOPInstanceUID=1.0.0.0",
                false, HttpStatusCodes.STATUS_CODE_OK)
        });
  }

  @Test
  public void testCStoreService_map_byAet() throws Exception {
    basicCStoreServiceTest(
        true,
        HttpStatusCodes.STATUS_CODE_SERVER_ERROR,
        Status.Success,
        new MockDestinationConfig[] {
            new MockDestinationConfig("StudyDate=19921012&AETitle=CSTORECLIENT",
                false, HttpStatusCodes.STATUS_CODE_OK)
        });
  }

  @Test
  public void testCStoreService_map_connectionError() throws Exception {
    basicCStoreServiceTest(
        false,
        HttpStatusCodes.STATUS_CODE_OK,
        Status.ProcessingFailure,
        new MockDestinationConfig[] {
            new MockDestinationConfig("StudyDate=19921012&SOPInstanceUID=1.0.0.0",
                true, HttpStatusCodes.STATUS_CODE_OK)
        });
  }

  @Test
  public void testCStoreService_map_filterOrder_successFirst() throws Exception {
    basicCStoreServiceTest(
        true,
        HttpStatusCodes.STATUS_CODE_SERVER_ERROR,
        Status.Success,
        new MockDestinationConfig[] {
            new MockDestinationConfig("StudyDate=19921012&SOPInstanceUID=1.0.0.0",
                false, HttpStatusCodes.STATUS_CODE_OK),
            new MockDestinationConfig("StudyDate=19921012",
                true, HttpStatusCodes.STATUS_CODE_SERVER_ERROR),
        });
  }

  @Test
  public void testCStoreService_map_filterOrder_failFirst() throws Exception {
    basicCStoreServiceTest(
        false,
        HttpStatusCodes.STATUS_CODE_OK,
        Status.ProcessingFailure,
        new MockDestinationConfig[] {
            new MockDestinationConfig("StudyDate=19921012&SOPInstanceUID=1.0.0.0",
                true, HttpStatusCodes.STATUS_CODE_SERVER_ERROR),
            new MockDestinationConfig("StudyDate=19921012",
                false, HttpStatusCodes.STATUS_CODE_OK),
        });
  }

  @Test
  public void testCStoreService_map_emptyFilterMatches() throws Exception {
    basicCStoreServiceTest(
        true,
        HttpStatusCodes.STATUS_CODE_SERVER_ERROR,
        Status.Success,
        new MockDestinationConfig[] {
            new MockDestinationConfig("", false, HttpStatusCodes.STATUS_CODE_OK)
        });
  }

  @Test
  public void testCStoreService_map_defaultClientOnNoMatch() throws Exception {
    basicCStoreServiceTest(
        false,
        HttpStatusCodes.STATUS_CODE_OK,
        Status.Success,
        new MockDestinationConfig[] {
            new MockDestinationConfig("StudyDate=NoSuchValue",
                true, HttpStatusCodes.STATUS_CODE_SERVER_ERROR),
            new MockDestinationConfig("SOPInstanceUID=NoSuchValue",
                true, HttpStatusCodes.STATUS_CODE_SERVER_ERROR),
        });
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCStoreService_map_invalidFilter() throws Exception {
    basicCStoreServiceTest(
        false,
        HttpStatusCodes.STATUS_CODE_OK,
        Status.Success,
        new MockDestinationConfig[] {
            new MockDestinationConfig("NoSuchTag=NoSuchValue",
                true, HttpStatusCodes.STATUS_CODE_SERVER_ERROR),
        });
  }

  @Test
  public void testCStoreService_map_tagByHex() throws Exception {
    basicCStoreServiceTest(
        true,
        HttpStatusCodes.STATUS_CODE_SERVER_ERROR,
        Status.Success,
        new MockDestinationConfig[] {
            new MockDestinationConfig(TagUtils.toHexString(Tag.StudyDate) + "=19921012",
                false, HttpStatusCodes.STATUS_CODE_OK)
        });
  }


  private void basicCStoreServiceTest(
      boolean connectionError,
      int httpStatus,
      int expectedDimseStatus,
      MockDestinationConfig[] destinationConfigs) throws Exception {
    basicCStoreServiceTest(
        connectionError,
        httpStatus,
        expectedDimseStatus,
        destinationConfigs,
        UID.MRImageStorage,
        "1.0.0.0");
  }

  private void basicCStoreServiceTest(
      boolean connectionError,
      int httpStatus,
      int expectedDimseStatus,
      MockDestinationConfig[] destinationConfigs,
      String sopClassUID,
      String sopInstanceUID) throws Exception {
    InputStream in =
        new DicomInputStream(TestUtils.streamDICOMStripHeaders(TestUtils.TEST_MR_FILE));
    InputStreamDataWriter data = new InputStreamDataWriter(in);

    // Create C-STORE DICOM server.
    int serverPort =
        createDicomServer(connectionError, httpStatus, destinationConfigs);

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

  private class MockStowClient implements IDicomWebClient {

    private boolean connectError;
    private int httpResponseCode;

    public MockStowClient(boolean connectError, int httpResponseCode) {
      this.connectError = connectError;
      this.httpResponseCode = httpResponseCode;
    }

    @Override
    public MultipartInput wadoRs(String path) throws DicomWebException {
      throw new UnsupportedOperationException();
    }

    @Override
    public JSONArray qidoRs(String path) throws DicomWebException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void stowRs(InputStream in) throws DicomWebException {
      if (connectError) {
        throw new DicomWebException("connect error");
      }
      if (httpResponseCode != HttpStatusCodes.STATUS_CODE_OK) {
        throw new DicomWebException("mock error", httpResponseCode, Status.ProcessingFailure);
      }
    }
  }

  private class MockDestinationConfig {
    private final String filter;
    private final boolean connectError;
    private final int httpResponseCode;

    public MockDestinationConfig(String filter, boolean connectError, int httpResponseCode) {
      this.filter = filter;
      this.connectError = connectError;
      this.httpResponseCode = httpResponseCode;
    }
  }

  // TODO(b/73252285): increase test coverage.
}
