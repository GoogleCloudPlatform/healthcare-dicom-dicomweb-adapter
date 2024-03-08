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

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.LogUtil;
import com.google.cloud.healthcare.imaging.dicomadapter.cmove.ISender;
import com.google.cloud.healthcare.imaging.dicomadapter.cmove.ISenderFactory;
import com.google.cloud.healthcare.imaging.dicomadapter.util.DimseRSPAssert;
import com.google.cloud.healthcare.imaging.dicomadapter.util.PortUtil;
import java.io.IOException;
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
public final class CMoveServiceTest {

  // Server properties.
  final static String serverAET = "SERVER";
  final static String serverHostname = "localhost";

  final static String clientAET = "CLIENT";

  final static String moveDestinationAET = "MOVE_DESTINATION";
  final static String moveDestinationHostname = "localhost";

  // Client properties.
  ApplicationEntity clientAE;

  @Before
  public void setUp() throws Exception {
    LogUtil.Log4jToStdout("DEBUG");

    // Create the C-STORE client.
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
  public void testCMoveService_success() throws Exception {
    basicCMoveServiceTest(new TestUtils.DicomWebClientTestBase() {
      @Override
      public JSONArray qidoRs(String path) throws DicomWebException {
        JSONArray instances = new JSONArray();
        instances.put(TestUtils.dummyQidorsInstance());
        return instances;
      }
    }, Status.Success);
  }

  @Test
  public void testCMoveService_processingFailure() throws Exception {
    basicCMoveServiceTest(new TestUtils.DicomWebClientTestBase() {
      @Override
      public JSONArray qidoRs(String path) throws DicomWebException {
        throw new NullPointerException();
      }
    }, Status.ProcessingFailure);
  }

  @Test
  public void testCMoveService_unknownDestinationAet() throws Exception {
    basicCMoveServiceTest(
        new TestUtils.DicomWebClientTestBase() {
          @Override
          public JSONArray qidoRs(String path) throws DicomWebException {
            JSONArray instances = new JSONArray();
            instances.put(TestUtils.dummyQidorsInstance());
            return instances;
          }
        },
        () -> new CStoreSenderTest(),
        Status.MoveDestinationUnknown,
        "UnknownDestionationAET_Literally");
  }

  @Test
  public void testCMoveService_qidoRs_genericFail() throws Exception {
    basicCMoveServiceTest(new TestUtils.DicomWebClientTestBase() {
      @Override
      public JSONArray qidoRs(String path) throws DicomWebException {
        throw new IDicomWebClient.DicomWebException("QidoRs Fail",
            Status.UnableToCalculateNumberOfMatches);
      }
    }, Status.UnableToCalculateNumberOfMatches);
  }

  @Test
  public void testCMoveService_qidoRs_notAuthorized() throws Exception {
    basicCMoveServiceTest(new TestUtils.DicomWebClientTestBase() {
      @Override
      public JSONArray qidoRs(String path) throws DicomWebException {
        throw new IDicomWebClient.DicomWebException("QidoRs Fail", Status.NotAuthorized);
      }
    }, Status.NotAuthorized);
  }

  @Test
  public void testCMoveService_noInstancesToMove() throws Exception {
    basicCMoveServiceTest(new TestUtils.DicomWebClientTestBase() {
      @Override
      public JSONArray qidoRs(String path) throws DicomWebException {
        return new JSONArray();
      }
    }, Status.UnableToCalculateNumberOfMatches);
  }

  @Test
  public void testCMoveService_cstoreFail() throws Exception {
    basicCMoveServiceTest(
        new TestUtils.DicomWebClientTestBase() {
          @Override
          public JSONArray qidoRs(String path) throws DicomWebException {
            JSONArray instances = new JSONArray();
            instances.put(TestUtils.dummyQidorsInstance());
            return instances;
          }
        },
        () -> new ISender() {
          @Override
          public long cmove(AetDictionary.Aet target, String studyUid, String seriesUid,
                            String sopInstanceUid, String sopClassUid)
              throws IDicomWebClient.DicomWebException, IOException, InterruptedException {
            throw new IDicomWebClient.DicomWebException("CStore Fail");
          }

          @Override
          public void close() throws IOException {

          }
        }
        , Status.UnableToPerformSubOperations
        , moveDestinationAET);
  }

  @Test
  public void testCMoveService_cstorePartialFail() throws Exception {
    basicCMoveServiceTest(
        new TestUtils.DicomWebClientTestBase() {
          @Override
          public JSONArray qidoRs(String path) throws DicomWebException {
            JSONArray instances = new JSONArray();
            instances.put(TestUtils.dummyQidorsInstance());
            instances.put(TestUtils.dummyQidorsInstance());
            return instances;
          }
        },
        () -> new ISender() {
          private int attempts = 0;

          @Override
          public long cmove(AetDictionary.Aet target,
              String studyUid,
              String seriesUid,
              String sopInstanceUid,
              String sopClassUid)
              throws IDicomWebClient.DicomWebException, IOException, InterruptedException {
            if (++attempts >= 2) {
              throw new IDicomWebClient.DicomWebException("CStore Fail");
            }
            return 0;
          }

          @Override
          public void close() throws IOException {

          }
        }
        , Status.OneOrMoreFailures
        , moveDestinationAET);
  }

  @Test
  public void testCMoveService_cancel() throws Exception {
    basicCMoveServiceTest(
        new TestUtils.DicomWebClientTestBase() {
          @Override
          public JSONArray qidoRs(String path) throws DicomWebException {
            JSONArray instances = new JSONArray();
            instances.put(TestUtils.dummyQidorsInstance());
            return instances;
          }
        },
        () -> new ISender() {
          @Override
          public long cmove(AetDictionary.Aet target, String studyUid, String seriesUid,
              String sopInstanceUid, String sopClassUid)
              throws IDicomWebClient.DicomWebException, IOException, InterruptedException {
            throw new InterruptedException();
          }

          @Override
          public void close() throws IOException {

          }
        }
        , Status.Cancel
        , moveDestinationAET);
  }

  public void basicCMoveServiceTest(IDicomWebClient serverDicomWebClient,
      int expectedStatus) throws Exception {
    basicCMoveServiceTest(serverDicomWebClient,
        () -> new CStoreSenderTest(),
        expectedStatus,
        moveDestinationAET);
  }

  public void basicCMoveServiceTest(IDicomWebClient serverDicomWebClient,
      ISenderFactory senderFactory,
      int expectedStatus,
      String moveDestinationAET) throws Exception {
    // Create C-STORE DICOM server.
    int serverPort = createDicomServer(serverDicomWebClient, senderFactory);

    // Associate with peer AE.
    Association association =
        associate(serverHostname, serverPort,
            UID.StudyRootQueryRetrieveInformationModelMove, UID.ExplicitVRLittleEndian);

    Attributes moveDataset = new Attributes();
    moveDataset.setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
    moveDataset.setString(Tag.StudyInstanceUID, VR.UI, "");

    // Issue CMOVE
    DimseRSPAssert rspAssert = new DimseRSPAssert(association, expectedStatus);
    association.cmove(
        UID.StudyRootQueryRetrieveInformationModelMove,
        1,
        moveDataset,
        UID.ExplicitVRLittleEndian,
        moveDestinationAET,
        rspAssert);
    association.waitForOutstandingRSP();

    // Close the association.
    association.release();
    association.waitForSocketClose();

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
  private int createDicomServer(
      IDicomWebClient dicomWebClient,
      ISenderFactory senderFactory) throws Exception {
    int serverPort = PortUtil.getFreePort();
    DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
    serviceRegistry.addDicomService(new BasicCEchoSCP());

    AetDictionary aetDict = new AetDictionary(new AetDictionary.Aet[]{
        new AetDictionary.Aet(moveDestinationAET, moveDestinationHostname, 0)});

    CMoveService cMoveService = new CMoveService(dicomWebClient, aetDict, senderFactory);
    serviceRegistry.addDicomService(cMoveService);
    Device serverDevice = DeviceUtil.createServerDevice(serverAET, serverPort, serviceRegistry);
    serverDevice.bindConnections();
    return serverPort;
  }

  private class CStoreSenderTest implements ISender {

    @Override
    public long cmove(AetDictionary.Aet target, String studyUid, String seriesUid,
        String sopInstanceUid, String sopClassUid)
        throws IDicomWebClient.DicomWebException, IOException, InterruptedException {
      return 0;
    }

    @Override
    public void close() throws IOException {

    }
  }
}
