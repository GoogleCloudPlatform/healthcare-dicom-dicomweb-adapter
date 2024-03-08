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

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.LogUtil;
import com.google.cloud.healthcare.imaging.dicomadapter.util.DimseRSPAssert;
import com.google.cloud.healthcare.imaging.dicomadapter.util.PortUtil;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCEchoSCP;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CFindServiceTest {

  // Server properties.
  final static String serverAET = "SERVER";
  final static String serverHostname = "localhost";

  final static String clientAET = "CLIENT";
  
  // Flags
  Flags cFINDFlags = new Flags();

  // Client properties.
  ApplicationEntity clientAE;

  @Before
  public void setUp() throws Exception {
    LogUtil.Log4jToStdout("DEBUG");

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
  public void testCFindService_success() throws Exception {
    basicCFindServiceTest(new TestUtils.DicomWebClientTestBase() {
      @Override
      public JSONArray qidoRs(String path) throws DicomWebException {
        JSONArray instances = new JSONArray();
        instances.put(TestUtils.dummyQidorsInstance());
        return instances;
      }
    }, Status.Success);
  }

  @Test
  public void testCFindService_cancel() throws Exception {
    basicCFindServiceTest(new TestUtils.DicomWebClientTestBase() {
      @Override
      public JSONArray qidoRs(String path) throws DicomWebException {
        // that's not how it really happens
        throw new CancellationException();
      }
    }, Status.Cancel);
  }

  @Test
  public void testCFindService_processingFailure() throws Exception {
    basicCFindServiceTest(new TestUtils.DicomWebClientTestBase() {
      @Override
      public JSONArray qidoRs(String path) throws DicomWebException {
        throw new NullPointerException();
      }
    }, Status.ProcessingFailure);
  }

  @Test
  public void testCFindService_outOfResources() throws Exception {
    basicCFindServiceTest(new TestUtils.DicomWebClientTestBase() {
      @Override
      public JSONArray qidoRs(String path) throws DicomWebException {
        throw new DicomWebException("test-generated exception", Status.OutOfResources);
      }
    }, Status.OutOfResources);
  }

  @Test
  public void testCFindService_notAuthorized() throws Exception {
    basicCFindServiceTest(new TestUtils.DicomWebClientTestBase() {
      @Override
      public JSONArray qidoRs(String path) throws DicomWebException {
        throw new DicomWebException("test-generated exception", Status.NotAuthorized);
      }
    }, Status.NotAuthorized);
  }
  
  @Test
  public void testCFindService_withFuzzyMatching() throws Exception {
	cFINDFlags.fuzzyMatching = true;
    basicCFindServiceTest(new TestUtils.DicomWebClientTestBase() {
      @Override
      public JSONArray qidoRs(String path) throws DicomWebException {
	assertThat(path).contains("fuzzymatching=true");
        JSONArray instances = new JSONArray();
        instances.put(TestUtils.dummyQidorsInstance());
        return instances;
      }
    }, Status.Success);
  }
  
  @Test
  public void testCFindService_withoutFuzzyMatching() throws Exception {
	cFINDFlags.fuzzyMatching = false;
    basicCFindServiceTest(new TestUtils.DicomWebClientTestBase() {
      @Override
      public JSONArray qidoRs(String path) throws DicomWebException {
	assertThat(path).doesNotContain("fuzzymatching");
        JSONArray instances = new JSONArray();
        instances.put(TestUtils.dummyQidorsInstance());
        return instances;
      }
    }, Status.Success);
  }

  public void basicCFindServiceTest(IDicomWebClient serverDicomWebClient,
      int expectedStatus) throws Exception {
    // Create C-STORE DICOM server.
    int serverPort = createDicomServer(serverDicomWebClient);

    // Associate with peer AE.
    Association association =
        associate(serverHostname, serverPort,
            UID.StudyRootQueryRetrieveInformationModelFind, UID.ExplicitVRLittleEndian);

    Attributes findData = new Attributes();
    findData.setString(Tag.QueryRetrieveLevel, VR.CS, "IMAGE");

    // Issue CFIND
    DimseRSPAssert rspAssert = new DimseRSPAssert(association, expectedStatus);
    association.cfind(
        UID.StudyRootQueryRetrieveInformationModelFind,
        1,
        findData, // I mock IDicomWebClient anyway, AttributesUtil will be tested separately
        UID.ExplicitVRLittleEndian,
        rspAssert
    );
    association.waitForOutstandingRSP();

    // Close the association.
    association.release();
    association.waitForSocketClose();

    // since assert is here, only final status matters and PENDING ones are ignored
    rspAssert.assertResult();
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
  private int createDicomServer(IDicomWebClient dicomWebClient) throws Exception {
    int serverPort = PortUtil.getFreePort();
    DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
    serviceRegistry.addDicomService(new BasicCEchoSCP());
    CFindService cFindService = new CFindService(dicomWebClient, cFINDFlags);
    serviceRegistry.addDicomService(cFindService);
    Device serverDevice = DeviceUtil.createServerDevice(serverAET, serverPort, serviceRegistry);
    serverDevice.bindConnections();
    return serverPort;
  }
}
