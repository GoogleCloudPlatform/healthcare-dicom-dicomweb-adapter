package com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest.sender;

import com.google.cloud.healthcare.imaging.dicomadapter.*;
import com.google.cloud.healthcare.imaging.dicomadapter.util.PortUtil;
import com.google.cloud.healthcare.imaging.dicomadapter.util.StubCStoreService;
import com.google.common.io.CountingInputStream;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.*;
import org.dcm4che3.net.service.BasicCEchoSCP;
import org.dcm4che3.net.service.DicomService;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class CStoreSenderTest {
  // Server properties.
  private final static String SERVER_AET = "SERVER";
  private final static String SERVER_HOST_NAME = "localhost";
  private final static String DESTINATION_HOSTNAME = "localhost";

  // Client properties.
  private final static String SOP_INSTANCE_ID = "1.2.840.113543.6.6.1.1.2.2415947926921624359.201235357587280";
  private final static Path PATH_TO_DCM_FILE = Paths.get("src", "test", "resources", SOP_INSTANCE_ID);
  private final static String CLIENT_AET = "CLIENT";

  private CStoreSender cStoreSender;
  private int dicomDestinationPort;

  private DicomService dicomServiceStub;

  @Rule
  public MockitoRule rule = MockitoJUnit.rule();

  @Before
  public void setUp() throws Exception {
    CStoreSenderFactory cStoreSenderFactory = new CStoreSenderFactory(CLIENT_AET);
    cStoreSender = cStoreSenderFactory.create();
    dicomDestinationPort = createDicomServer();
  }

  @Test
  public void sendFile() throws IOException, InterruptedException {
    byte[] actualDcmFileSize = Files.readAllBytes(PATH_TO_DCM_FILE);
    try (
        InputStream inputStream = Files.newInputStream(PATH_TO_DCM_FILE);
        CountingInputStream countingStream = new CountingInputStream(inputStream);
        DicomInputStream dicomInputStream = new DicomInputStream(countingStream)) {

      AetDictionary.Aet validAet = new AetDictionary.Aet(SERVER_AET, SERVER_HOST_NAME, dicomDestinationPort);

      cStoreSender.cstore(validAet,
          SOP_INSTANCE_ID,
          UID.ExplicitVRLittleEndian,
          dicomInputStream);

      List<Byte> actualReceivedBytesByServer = getByteObjectArray(
          ((StubCStoreService) dicomServiceStub).getReceivedBytes());
      List<Byte> expectedBytes = getByteObjectArray(Files.readAllBytes(PATH_TO_DCM_FILE));

      assertThat(expectedBytes).containsAtLeastElementsIn(actualReceivedBytesByServer);
      assertThat(actualDcmFileSize.length).isNotEqualTo(0);
      assertThat(countingStream.getCount()).isEqualTo(actualDcmFileSize.length);
    }
  }

  private ArrayList<Byte> getByteObjectArray(byte[] bytes) {
    ArrayList<Byte> objectByteList = new ArrayList<>();
    for(byte b: bytes) {
      objectByteList.add(b);
    }
    return objectByteList;
  }

  @After
  public void tearDown() {
    cStoreSender.close();
  }

  // Creates a DICOM service and returns the port it is listening on.
  private int createDicomServer() throws Exception {
    int serverPort = PortUtil.getFreePort();
    DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
    serviceRegistry.addDicomService(new BasicCEchoSCP());

    AetDictionary aetDict = new AetDictionary(new AetDictionary.Aet[]{
        new AetDictionary.Aet(SERVER_AET, DESTINATION_HOSTNAME, 0)});

    dicomServiceStub = new StubCStoreService(Status.Success);
    serviceRegistry.addDicomService(dicomServiceStub);
    TransferCapability transferCapability =
        new TransferCapability(null /* commonName */, "*", TransferCapability.Role.SCP, "*");
    Device serverDevice = DeviceUtil.createServerDevice(SERVER_AET, serverPort, serviceRegistry, transferCapability);
    serverDevice.bindConnections();
    return serverPort;
  }
}
