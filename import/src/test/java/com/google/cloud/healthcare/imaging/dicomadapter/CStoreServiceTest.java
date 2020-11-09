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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.BackupUploadService;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.DelayCalculator;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.IBackupUploader;
import com.google.cloud.healthcare.imaging.dicomadapter.ImportAdapter.Pair;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.destination.IDestinationClientFactory;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.destination.MultipleDestinationClientFactory;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.destination.SingleDestinationClientFactory;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest.MultipleDestinationUploadService;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest.sender.CStoreSender;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest.sender.CStoreSenderFactory;
import com.google.cloud.healthcare.imaging.dicomadapter.util.DimseRSPAssert;
import com.google.cloud.healthcare.imaging.dicomadapter.util.PortUtil;
import com.google.cloud.healthcare.util.TestUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import com.google.common.collect.ImmutableList;
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
  private static final Integer RETRY_ATTEMPTS_AMOUNT_ZERO = 0;
  private final int RETRY_ATTEMPTS_AMOUNT_ONE = 1;
  private final int RETRY_ATTEMPTS_AMOUNT_TWO = 2;
  private final String DEFAULT_DESTINATION_CONFIG_FILTER = "StudyDate=19921012&SOPInstanceUID=1.0.0.0";

  private final String VALID_HOST = "192.168.0.1";
  private final String VALID_NAME = "DEVICE_B";
  private final int VALID_PORT = 11114;

  private ImmutableList<Integer> EMPTY_HTTP_ERROR_CODES_TO_RETRY_FLAG = ImmutableList.copyOf(new Flags().httpErrorCodesToRetry);

  @Rule
  public MockitoRule rule = MockitoJUnit.rule();

  @Mock
  IBackupUploader backupUploaderMock;

  @Mock
  DelayCalculator delayCalculatorMock;

  @Mock
  private CStoreSenderFactory cStoreSenderFactoryMock;

  @Mock
  private CStoreSender cStoreSenderMock;

  private BackupUploadService backupUploadService;
  private MultipleDestinationUploadService multipleDestinationSendService;

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
      MockDestinationConfig[] destinationConfigs,
      String transcodeToSyntax,
      MultipleDestinationUploadService multipleDestinationSendService,
      IDestinationClientFactory destinationClientFactory,
      IDicomWebClient dicomWebClient) throws Exception {
    int serverPort = PortUtil.getFreePort();
    DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
    serviceRegistry.addDicomService(new BasicCEchoSCP());
    ImmutableList.Builder<Pair<DestinationFilter, IDicomWebClient>> destPairsBuilder = ImmutableList.builder();
    if (destinationConfigs != null) {
      for (MockDestinationConfig conf : destinationConfigs) {
        destPairsBuilder.add(
            new Pair(
                new DestinationFilter(conf.filter),
                new MockStowClient(conf.connectError, conf.httpResponseCode)
        ));
      }
    }

    if (destinationClientFactory == null) {
      destinationClientFactory = new SingleDestinationClientFactory(destPairsBuilder.build(), dicomWebClient);
    }

    CStoreService cStoreService =
        new CStoreService(
            destinationClientFactory,
            null,
            transcodeToSyntax,
            multipleDestinationSendService);

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

    when(backupUploaderMock.doReadBackup(anyString())).thenReturn(new ByteArrayInputStream(new byte []{1,2,3,4}));
    when(cStoreSenderFactoryMock.create()).thenReturn(cStoreSenderMock);
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
        null,
        TestUtils.TEST_MR_FILE,
        null,
        null,
        null);
  }

  @Test
  public void testCStoreService_map_success() throws Exception {
    basicCStoreServiceTest(
        true,
        HttpStatusCodes.STATUS_CODE_SERVER_ERROR,
        Status.Success,
        new MockDestinationConfig[] {
            new MockDestinationConfig(DEFAULT_DESTINATION_CONFIG_FILTER,
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
            new MockDestinationConfig(DEFAULT_DESTINATION_CONFIG_FILTER,
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
            new MockDestinationConfig(DEFAULT_DESTINATION_CONFIG_FILTER,
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
            new MockDestinationConfig(DEFAULT_DESTINATION_CONFIG_FILTER,
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
            new MockDestinationConfig(DEFAULT_DESTINATION_CONFIG_FILTER,
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
        null,
        null);
  }

  @Test
  public void testBackupUploadService_ExceptionOnWriteToBackup() throws Exception {
    MockStowClient spyStowClient = spy(new MockStowClient(
        false,
        HttpStatusCodes.STATUS_CODE_OK));

    backupUploadService = new BackupUploadService(
        backupUploaderMock,
        RETRY_ATTEMPTS_AMOUNT_ZERO,
        EMPTY_HTTP_ERROR_CODES_TO_RETRY_FLAG,
        delayCalculatorMock);

    multipleDestinationSendService = new MultipleDestinationUploadService(
        cStoreSenderFactoryMock,
        backupUploadService,
        RETRY_ATTEMPTS_AMOUNT_ZERO);


    doThrow(new IBackupUploader.BackupException("errMsg")).when(backupUploaderMock).doWriteBackup(any(InputStream.class), anyString());
    doNothing().when(spyStowClient).stowRs(any(InputStream.class));

    basicCStoreServiceTest(
        Status.ProcessingFailure,
        multipleDestinationSendService,
        spyStowClient,
        null);

    verify(backupUploaderMock, never()).doReadBackup(anyString());
    verify(backupUploaderMock, never()).doRemoveBackup(anyString());
  }

  @Test
  public void testBackupUploadService_httpCode409_failed() throws Exception {
    MockStowClient spyStowClient = spy(new MockStowClient(
        false,
        HttpStatusCodes.STATUS_CODE_BAD_REQUEST));

    backupUploadService = new BackupUploadService(
        backupUploaderMock,
        RETRY_ATTEMPTS_AMOUNT_ZERO,
        EMPTY_HTTP_ERROR_CODES_TO_RETRY_FLAG,
        delayCalculatorMock);

    multipleDestinationSendService = new MultipleDestinationUploadService(
        cStoreSenderFactoryMock,
        backupUploadService,
        RETRY_ATTEMPTS_AMOUNT_ZERO);

    doThrow(new IDicomWebClient.DicomWebException("conflictTestCode409", HttpStatus.CONFLICT_409, Status.ProcessingFailure))
        .when(spyStowClient).stowRs(any(InputStream.class));

    basicCStoreServiceTest(
        Status.ProcessingFailure,
        multipleDestinationSendService,
        spyStowClient,
        null);

    verify(backupUploaderMock).doWriteBackup(any(InputStream.class), anyString());
    verify(backupUploaderMock).doReadBackup(anyString());
    verify(spyStowClient).stowRs(any(InputStream.class));
    verify(backupUploaderMock, never()).doRemoveBackup(anyString());
  }

  @Test
  public void testBackupUploadService_retryOn_DicomWebException408Code_Success() throws Exception {
    MockStowClient spyStowClient = spy(new MockStowClient(
        false,
        HttpStatusCodes.STATUS_CODE_BAD_REQUEST));

    backupUploadService = new BackupUploadService(
        backupUploaderMock,
        RETRY_ATTEMPTS_AMOUNT_ONE,
        ImmutableList.of(408),
        delayCalculatorMock);

    multipleDestinationSendService = new MultipleDestinationUploadService(
        cStoreSenderFactoryMock,
        backupUploadService,
        RETRY_ATTEMPTS_AMOUNT_ONE);

    doThrow(new IDicomWebClient.DicomWebException(" Request Timeout 408", HttpStatus.REQUEST_TIMEOUT_408, Status.ProcessingFailure))
        .doNothing()
        .when(spyStowClient).stowRs(any(InputStream.class));

    basicCStoreServiceTest(
        Status.Success,
        multipleDestinationSendService,
        spyStowClient,
        null);

    verify(backupUploaderMock).doWriteBackup(any(InputStream.class), anyString());
    verify(backupUploaderMock, times(2)).doReadBackup(anyString());
    verify(spyStowClient, atLeast(1)).stowRs(any(InputStream.class));
    verify(backupUploaderMock).doRemoveBackup(anyString());
  }

  @Test
  public void multipleDestinationUploadService_twoTypesDestinations_Success() throws Exception {
    MockStowClient spyStowClient = spy(new MockStowClient(
        false,
        HttpStatusCodes.STATUS_CODE_OK));

    backupUploadService = new BackupUploadService(
        backupUploaderMock,
        RETRY_ATTEMPTS_AMOUNT_ONE,
        ImmutableList.of(),
        delayCalculatorMock);

    multipleDestinationSendService = new MultipleDestinationUploadService(
        cStoreSenderFactoryMock,
        backupUploadService,
        RETRY_ATTEMPTS_AMOUNT_ONE);

        doNothing()
        .when(spyStowClient).stowRs(any(InputStream.class));

    ImmutableList<Pair<DestinationFilter, IDicomWebClient>> healthcareDestinations = ImmutableList.of(
        new ImportAdapter.Pair(new DestinationFilter(DEFAULT_DESTINATION_CONFIG_FILTER), spyStowClient));

    AetDictionary.Aet target = new AetDictionary.Aet(VALID_NAME, VALID_HOST, VALID_PORT);
    ImmutableList<Pair<DestinationFilter, AetDictionary.Aet>> dicomDestinations = ImmutableList.of(
        new Pair(new DestinationFilter(DEFAULT_DESTINATION_CONFIG_FILTER), target));

    MultipleDestinationClientFactory multipleDestinationClientFactory = new MultipleDestinationClientFactory(
      healthcareDestinations,
      dicomDestinations,
      spyStowClient);

    basicCStoreServiceTest(
        Status.Success,
        multipleDestinationSendService,
        spyStowClient,
        multipleDestinationClientFactory);

    verify(backupUploaderMock).doWriteBackup(any(InputStream.class), anyString());
    verify(backupUploaderMock, times(2)).doReadBackup(anyString());
    verify(spyStowClient).stowRs(any(InputStream.class));
    verify(cStoreSenderMock).cstore(eq(target), eq(SOP_INSTANCE_UID), eq("1.2.840.10008.5.1.4.1.1.4"), any(InputStream.class));
    verify(backupUploaderMock).doRemoveBackup(eq(SOP_INSTANCE_UID));
  }

  @Test
  public void testBackupUploadService_httpCode500_ThirdTrySuccess() throws Exception {
    MockStowClient spyStowClient = spy(new MockStowClient(
        false,
        HttpStatusCodes.STATUS_CODE_BAD_REQUEST));

    backupUploadService = new BackupUploadService(
        backupUploaderMock,
        RETRY_ATTEMPTS_AMOUNT_TWO,
        EMPTY_HTTP_ERROR_CODES_TO_RETRY_FLAG,
        delayCalculatorMock);

    multipleDestinationSendService = new MultipleDestinationUploadService(
        cStoreSenderFactoryMock,
        backupUploadService,
        RETRY_ATTEMPTS_AMOUNT_TWO);


    doThrow(new IDicomWebClient.DicomWebException("testCode500", HttpStatus.INTERNAL_SERVER_ERROR_500, HttpStatusCodes.STATUS_CODE_SERVER_ERROR))
        .doThrow(new IDicomWebClient.DicomWebException("testCode502", HttpStatus.BAD_GATEWAY_502, HttpStatusCodes.STATUS_CODE_BAD_GATEWAY))
        .doNothing()
        .when(spyStowClient).stowRs(any(InputStream.class));

    basicCStoreServiceTest(
        Status.Success,
        multipleDestinationSendService,
        spyStowClient,
        null);

    verify(backupUploaderMock).doWriteBackup(any(InputStream.class), anyString());
    verify(backupUploaderMock, times(3)).doReadBackup(anyString());
    verify(spyStowClient, times(3)).stowRs(any(InputStream.class));
    verify(backupUploaderMock).doRemoveBackup(anyString());
  }

  @Test
  public void testBackupUploadService_backupWriteAndReadSuccess() throws Exception {
    MockStowClient spyStowClient = spy(new MockStowClient(
        false,
        HttpStatusCodes.STATUS_CODE_OK));

    backupUploadService = new BackupUploadService(
        backupUploaderMock,
        RETRY_ATTEMPTS_AMOUNT_ZERO,
        EMPTY_HTTP_ERROR_CODES_TO_RETRY_FLAG,
        delayCalculatorMock);

    multipleDestinationSendService = new MultipleDestinationUploadService(
        cStoreSenderFactoryMock,
        backupUploadService,
        RETRY_ATTEMPTS_AMOUNT_ZERO);

    basicCStoreServiceTest(
        Status.Success,
        multipleDestinationSendService,
        spyStowClient,
        null);

    verify(backupUploaderMock).doWriteBackup(any(InputStream.class), eq(SOP_INSTANCE_UID));
    verify(backupUploaderMock).doReadBackup(eq(SOP_INSTANCE_UID));
    verify(spyStowClient).stowRs(any(InputStream.class));
    verify(backupUploaderMock).doRemoveBackup(eq(SOP_INSTANCE_UID));
  }

  private void basicCStoreServiceTest(
      int expectedDimseStatus,
      MultipleDestinationUploadService multipleDestinationSendService,
      IDicomWebClient dicomWebClient,
      IDestinationClientFactory destinationClientFactory) throws Exception {
    basicCStoreServiceTest(
        expectedDimseStatus,
        null,
        UID.MRImageStorage,
        SOP_INSTANCE_UID,
        TestUtils.TEST_MR_FILE,
        null,
        multipleDestinationSendService,
        destinationClientFactory,
        dicomWebClient);
  }

  private void basicCStoreServiceTest(
      boolean connectionError,
      int httpStatus,
      int expectedDimseStatus) throws Exception {
    basicCStoreServiceTest(
        connectionError,
        httpStatus,
        expectedDimseStatus,
        null);
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
      MultipleDestinationUploadService multipleDestinationSendService,
      IDestinationClientFactory destinationClientFactory)
      throws Exception {
    basicCStoreServiceTest(
        expectedDimseStatus,
        destinationConfigs,
        sopClassUID,
        sopInstanceUID,
        testFile,
        transcodeToSyntax,
        multipleDestinationSendService,
        destinationClientFactory,
        new MockStowClient(connectionError, httpStatus));
  }

  private void basicCStoreServiceTest(
      int expectedDimseStatus,
      MockDestinationConfig[] destinationConfigs,
      String sopClassUID,
      String sopInstanceUID,
      String testFile,
      String transcodeToSyntax,
      MultipleDestinationUploadService multipleDestinationSendService,
      IDestinationClientFactory destinationClientFactory,
      IDicomWebClient dicomWebClient)
      throws Exception {
    DicomInputStream in = (DicomInputStream) TestUtils.streamDICOMStripHeaders(testFile);
    InputStreamDataWriter data = new InputStreamDataWriter(in);

    // Create C-STORE DICOM server.
    int serverPort =
        createDicomServer(destinationConfigs, transcodeToSyntax, multipleDestinationSendService, destinationClientFactory, dicomWebClient);

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