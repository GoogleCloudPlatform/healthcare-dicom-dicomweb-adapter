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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.backupuploader.BackupFlags;
import com.google.cloud.healthcare.imaging.dicomadapter.backupuploader.BackupUploadService;
import com.google.cloud.healthcare.imaging.dicomadapter.backupuploader.DelayCalculator;
import com.google.cloud.healthcare.imaging.dicomadapter.backupuploader.IBackupUploader;
import com.google.cloud.healthcare.imaging.dicomadapter.util.DimseRSPAssert;
import com.google.cloud.healthcare.imaging.dicomadapter.util.PortUtil;
import com.google.cloud.healthcare.util.TestUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.eclipse.jetty.http.HttpStatus;
import org.json.JSONArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class CStoreServiceTest {

  // Server properties.
  private static String serverAET = "SERVER";
  private static String serverHostname = "localhost";
  private static String SOP_INSTANCE_UID = "1.0.0.0";

  @Rule
  public MockitoRule rule = MockitoJUnit.rule();

  @Mock
  IBackupUploader mockBackupUploader;

  @Mock
  DelayCalculator mockDelayCalculator;

  @Mock
  private BackupFlags backupFlagsMock;

  // Client properties.
  private ApplicationEntity clientAE;
  private CStoreService cStoreService;

  public void assertProcessingRequestsDeltaIsZero() {
    assertThat(cStoreService.getProcessingRequestsNowDelta().get()).isEqualTo(0);
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
  private int createDicomServer(
      MockDestinationConfig[] destinationConfigs,
      String transcodeToSyntax,
      BackupUploadService backupUploadService,
      IDicomWebClient dicomWebClient) throws Exception {
    int serverPort = PortUtil.getFreePort();
    DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
    serviceRegistry.addDicomService(new BasicCEchoSCP());
    Map<DestinationFilter, IDicomWebClient> destinationMap = new LinkedHashMap<>();
    if(destinationConfigs != null) {
      for (MockDestinationConfig conf : destinationConfigs) {
        destinationMap.put(
            new DestinationFilter(conf.filter),
            new MockStowClient(conf.connectError, conf.httpResponseCode)
        );
      }
    }
    cStoreService = new CStoreService(dicomWebClient, destinationMap, null, transcodeToSyntax, backupUploadService);
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

    when(mockBackupUploader.doReadBackup(anyString())).thenReturn(new ByteArrayInputStream(new byte []{1,2,3,4}));
  }

  @Test
  public void testCStoreService_success() throws Exception {
    basicCStoreServiceTest(
        false,
        HttpStatusCodes.STATUS_CODE_OK,
        Status.Success);
    assertProcessingRequestsDeltaIsZero();
  }

  @Test
  public void testCStoreService_connectionError() throws Exception {
    basicCStoreServiceTest(
        true,
        HttpStatusCodes.STATUS_CODE_OK, // won't be returned due to connectionError
        Status.ProcessingFailure);
    assertProcessingRequestsDeltaIsZero();
  }

  @Test
  public void testCStoreService_unauthorized() throws Exception {
    basicCStoreServiceTest(
        false,
        HttpStatusCodes.STATUS_CODE_UNAUTHORIZED,
        Status.NotAuthorized);
    assertProcessingRequestsDeltaIsZero();
  }

  @Test
  public void testCStoreService_serviceUnavailable() throws Exception {
    basicCStoreServiceTest(
        false,
        HttpStatusCodes.STATUS_CODE_SERVICE_UNAVAILABLE,
        Status.OutOfResources);
    assertProcessingRequestsDeltaIsZero();
  }

  @Test
  public void testCStoreService_noSopInstanceUid() throws Exception {
    basicCStoreServiceTest(
        true, // no stow-rs request will be made
        HttpStatusCodes.STATUS_CODE_OK,
        Status.CannotUnderstand,
        null,
        UID.MRImageStorage,
        null,
        TestUtils.TEST_MR_FILE,
        null,
        null);
    assertProcessingRequestsDeltaIsZero();
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
    assertProcessingRequestsDeltaIsZero();
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
    assertProcessingRequestsDeltaIsZero();
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
    assertProcessingRequestsDeltaIsZero();
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
    assertProcessingRequestsDeltaIsZero();
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
    assertProcessingRequestsDeltaIsZero();
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
    assertProcessingRequestsDeltaIsZero();
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
    assertProcessingRequestsDeltaIsZero();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCStoreService_map_invalidFilter_noSuchTag() throws Exception {
    basicCStoreServiceTest(
        false,
        HttpStatusCodes.STATUS_CODE_OK,
        Status.Success,
        new MockDestinationConfig[] {
            new MockDestinationConfig("NoSuchTag=NoSuchValue",
                true, HttpStatusCodes.STATUS_CODE_SERVER_ERROR),
        });
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCStoreService_map_invalidFilter_format() throws Exception {
    basicCStoreServiceTest(
        false,
        HttpStatusCodes.STATUS_CODE_OK,
        Status.Success,
        new MockDestinationConfig[] {
            new MockDestinationConfig("StudyDate=19921012=19921012",
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
    assertProcessingRequestsDeltaIsZero();
  }

  @Test
  public void testCStoreService_transcodeToJpeg2k() throws Exception {
    basicCStoreServiceTest(
        false,
        HttpStatusCodes.STATUS_CODE_OK,
        Status.Success,
        null,
        UID.SecondaryCaptureImageStorage,
        "1.2.276.0.7230010.3.1.4.1784944219.230771.1519337370.699151",
        TestUtils.TEST_IMG_FILE,
        UID.JPEG2000,
        null);
    assertProcessingRequestsDeltaIsZero();
  }

  @Test
  public void testBackupUploadService_ExceptionOnWriteToBackup() throws Exception {
    MockStowClient spyStowClient = spy(new MockStowClient(
        false,
        HttpStatusCodes.STATUS_CODE_OK));

    doThrow(new IBackupUploader.BackupException("errMsg")).when(mockBackupUploader).doWriteBackup(any(InputStream.class), anyString());
    doNothing().when(spyStowClient).stowRs(any(InputStream.class));

    BackupUploadService backupUploadService = new BackupUploadService(mockBackupUploader, backupFlagsMock, mockDelayCalculator);

    basicCStoreServiceTest(
        Status.ProcessingFailure,
        backupUploadService,
        spyStowClient);
    assertProcessingRequestsDeltaIsZero();

    verify(mockBackupUploader, never()).doReadBackup(anyString());
    verify(mockBackupUploader, never()).doRemoveBackup(anyString());
  }

  @Test
  public void testBackupUploadService_httpCode409_failed() throws Exception {
    MockStowClient spyStowClient = spy(new MockStowClient(
        false,
        HttpStatusCodes.STATUS_CODE_BAD_REQUEST));

    doThrow(new IDicomWebClient.DicomWebException("conflictTestCode409", HttpStatus.CONFLICT_409, Status.ProcessingFailure))
        .when(spyStowClient).stowRs(any(InputStream.class));

    BackupUploadService backupUploadService = new BackupUploadService(mockBackupUploader, backupFlagsMock, mockDelayCalculator);

    basicCStoreServiceTest(
        Status.ProcessingFailure,
        backupUploadService,
        spyStowClient);
    assertProcessingRequestsDeltaIsZero();

    verify(mockBackupUploader).doWriteBackup(any(InputStream.class), anyString());
    verify(mockBackupUploader).doReadBackup(anyString());
    verify(spyStowClient).stowRs(any(InputStream.class));
    verify(mockBackupUploader, never()).doRemoveBackup(anyString());
  }

  @Test
  public void testBackupUploadService_retryOn_DicomWebException408Code_Success() throws Exception {
    MockStowClient spyStowClient = spy(new MockStowClient(
        false,
        HttpStatusCodes.STATUS_CODE_BAD_REQUEST));

    when(backupFlagsMock.getHttpErrorCodesToRetry()).thenReturn(List.of(408));
    when(backupFlagsMock.getAttemptsAmount()).thenReturn(2);

    doThrow(new IDicomWebClient.DicomWebException(" Request Timeout 408", HttpStatus.REQUEST_TIMEOUT_408, Status.ProcessingFailure))
        .doNothing()
        .when(spyStowClient).stowRs(any(InputStream.class));

    BackupUploadService backupUploadService = new BackupUploadService(mockBackupUploader, backupFlagsMock, mockDelayCalculator);

    basicCStoreServiceTest(
        Status.Success,
        backupUploadService,
        spyStowClient);
    assertProcessingRequestsDeltaIsZero();

    verify(mockBackupUploader).doWriteBackup(any(InputStream.class), anyString());
    verify(mockBackupUploader, times(2)).doReadBackup(anyString());
    verify(spyStowClient, times(2)).stowRs(any(InputStream.class));
    verify(mockBackupUploader).doRemoveBackup(anyString());
  }

  @Test
  public void testBackupUploadService_httpCode500_ThirdTrySuccess() throws Exception {
    MockStowClient spyStowClient = spy(new MockStowClient(
        false,
        HttpStatusCodes.STATUS_CODE_BAD_REQUEST));

    when(backupFlagsMock.getAttemptsAmount()).thenReturn(3);
    doThrow(new IDicomWebClient.DicomWebException("testCode500", HttpStatus.INTERNAL_SERVER_ERROR_500, HttpStatusCodes.STATUS_CODE_SERVER_ERROR))
        .doThrow(new IDicomWebClient.DicomWebException("testCode502", HttpStatus.BAD_GATEWAY_502, HttpStatusCodes.STATUS_CODE_BAD_GATEWAY))
        .doNothing()
        .when(spyStowClient).stowRs(any(InputStream.class));

    BackupUploadService backupUploadService = new BackupUploadService(mockBackupUploader, backupFlagsMock, mockDelayCalculator);

    basicCStoreServiceTest(
        Status.Success,
        backupUploadService,
        spyStowClient);
    assertProcessingRequestsDeltaIsZero();

    verify(mockBackupUploader).doWriteBackup(any(InputStream.class), anyString());
    verify(mockBackupUploader, times(3)).doReadBackup(anyString());
    verify(spyStowClient, times(3)).stowRs(any(InputStream.class));
    verify(mockBackupUploader).doRemoveBackup(anyString());
  }

  @Test
  public void testBackupUploadService_backupWriteAndReadSuccess() throws Exception {
    MockStowClient spyStowClient = spy(new MockStowClient(
        false,
        HttpStatusCodes.STATUS_CODE_OK));

    BackupUploadService backupUploadService = new BackupUploadService(mockBackupUploader, backupFlagsMock, mockDelayCalculator);

    basicCStoreServiceTest(
        Status.Success,
        backupUploadService,
        spyStowClient);
    assertProcessingRequestsDeltaIsZero();

    verify(mockBackupUploader).doWriteBackup(any(InputStream.class), eq(SOP_INSTANCE_UID));
    verify(mockBackupUploader).doReadBackup(eq(SOP_INSTANCE_UID));
    verify(spyStowClient).stowRs(any(InputStream.class));
    verify(mockBackupUploader).doRemoveBackup(eq(SOP_INSTANCE_UID));
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
        SOP_INSTANCE_UID,
        TestUtils.TEST_MR_FILE,
        null,
        null);
  }

  private void basicCStoreServiceTest(
      int expectedDimseStatus,
      BackupUploadService backupUploadService,
      IDicomWebClient dicomWebClient) throws Exception {
    basicCStoreServiceTest(
        expectedDimseStatus,
        null,
        UID.MRImageStorage,
        SOP_INSTANCE_UID,
        TestUtils.TEST_MR_FILE,
        null,
        backupUploadService,
        dicomWebClient);
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
        SOP_INSTANCE_UID,
        TestUtils.TEST_MR_FILE,
        null,
        null);
  }

  private void basicCStoreServiceTest(
      boolean connectionError,
      int httpStatus,
      int expectedDimseStatus,
      MockDestinationConfig[] destinationConfigs,
      String sopClassUID,
      String sopInstanceUID,
      String testFile,
      String transcodeToSyntax,
      BackupUploadService backupUploadService)
      throws Exception {
    basicCStoreServiceTest(
        expectedDimseStatus,
        destinationConfigs,
        sopClassUID,
        sopInstanceUID,
        testFile,
        transcodeToSyntax,
        backupUploadService,
        new MockStowClient(connectionError, httpStatus));
  }

  private void basicCStoreServiceTest(
      int expectedDimseStatus,
      MockDestinationConfig[] destinationConfigs,
      String sopClassUID,
      String sopInstanceUID,
      String testFile,
      String transcodeToSyntax,
      BackupUploadService backupUploadService,
      IDicomWebClient dicomWebClient)
      throws Exception {
    DicomInputStream in = (DicomInputStream) TestUtils.streamDICOMStripHeaders(testFile);
    InputStreamDataWriter data = new InputStreamDataWriter(in);

    // Create C-STORE DICOM server.
    int serverPort =
        createDicomServer(destinationConfigs, transcodeToSyntax, backupUploadService, dicomWebClient);

    // Associate with peer AE.
    Association association =
        associate(serverHostname, serverPort, sopClassUID, in.getTransferSyntax());

    // Send the DICOM file.
    DimseRSPAssert rspAssert = new DimseRSPAssert(association, expectedDimseStatus);
    association.cstore(
        sopClassUID,
        sopInstanceUID,
        1,
        data,
        in.getTransferSyntax(),
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
    public InputStream wadoRs(String path) throws DicomWebException {
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

      try {
        in.readAllBytes();
      } catch (IOException e) {
        throw new DicomWebException(e);
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
