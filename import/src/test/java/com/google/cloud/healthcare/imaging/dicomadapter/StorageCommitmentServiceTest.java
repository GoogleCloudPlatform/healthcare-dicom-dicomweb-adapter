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
import com.google.cloud.healthcare.imaging.dicomadapter.util.DimseRSPAssert;
import com.google.cloud.healthcare.imaging.dicomadapter.util.PortUtil;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.AssociationStateException;
import org.dcm4che3.net.Commands;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.AbstractDicomService;
import org.dcm4che3.net.service.BasicCEchoSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class StorageCommitmentServiceTest {

  // Server properties.
  final static String serverAET = "SERVER";
  final static String serverHostname = "localhost";

  final static String clientAET = "CLIENT";
  final static String clientHostname = "localhost";

  // Default negotiated transfer syntax
  final static String transferSyntax = UID.ImplicitVRLittleEndian;


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
  public void testCommitmentService_found() throws Exception {
    Attributes rqAttrs = new Attributes();
    rqAttrs.setString(Tag.TransactionUID, VR.UI, "1");
    Sequence sequence = rqAttrs.newSequence(Tag.ReferencedSOPSequence, 1);
    Attributes seqItem = new Attributes();
    seqItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, "1");
    seqItem.setString(Tag.ReferencedSOPClassUID, VR.UI, "1");
    sequence.add(seqItem);

    Attributes expectReportAttrs = new Attributes(rqAttrs);
    expectReportAttrs.setString(Tag.RetrieveAETitle, VR.AE, serverAET);

    basicCommitmentServiceTest(new TestUtils.DicomWebClientTestBase() {
      @Override
      public JSONArray qidoRs(String path) throws DicomWebException {
        JSONArray instances = new JSONArray();
        instances.put(TestUtils.dummyQidorsInstance());
        return instances;
      }
    }, rqAttrs, Status.Success, expectReportAttrs);
  }

  @Test
  public void testCommitmentService_notFound() throws Exception {
    Attributes rqAttrs = new Attributes();
    rqAttrs.setString(Tag.TransactionUID, VR.UI, "1");
    Sequence sequence = rqAttrs.newSequence(Tag.ReferencedSOPSequence, 1);
    Attributes seqItem = new Attributes();
    seqItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, "1");
    seqItem.setString(Tag.ReferencedSOPClassUID, VR.UI, "1");
    sequence.add(seqItem);

    Attributes expectReportAttrs = new Attributes();
    expectReportAttrs.setString(Tag.RetrieveAETitle, VR.AE, serverAET);
    expectReportAttrs.setString(Tag.TransactionUID, VR.UI, "1");
    sequence = expectReportAttrs.newSequence(Tag.FailedSOPSequence, 1);
    seqItem = new Attributes();
    seqItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, "1");
    seqItem.setString(Tag.ReferencedSOPClassUID, VR.UI, "1");
    seqItem.setInt(Tag.FailureReason, VR.US, Status.NoSuchObjectInstance);
    sequence.add(seqItem);

    basicCommitmentServiceTest(new TestUtils.DicomWebClientTestBase() {
      @Override
      public JSONArray qidoRs(String path) throws DicomWebException {
        return new JSONArray();
      }
    }, rqAttrs, Status.Success, expectReportAttrs);
  }

  public void basicCommitmentServiceTest(IDicomWebClient serverDicomWebClient,
      Attributes requestData,
      int expectedStatus,
      Attributes expectReportAttrs) throws Exception {
    // Create C-STORE DICOM server.
    int scuPort = PortUtil.getFreePort();
    CompletableFuture<Boolean> checkFuture = new CompletableFuture<>();
    createSCUDevice(scuPort, expectReportAttrs, checkFuture);
    int serverPort = createDicomServer(serverDicomWebClient, scuPort);

    // Associate with peer AE.
    Association association =
        associate(serverHostname, serverPort,
            UID.StorageCommitmentPushModel, transferSyntax);

    // Issue N-ACTION
    DimseRSPAssert rspAssert = new DimseRSPAssert(association, expectedStatus);
    association.naction(
        UID.StorageCommitmentPushModel,
        UID.StorageCommitmentPushModelInstance,
        1,
        requestData,
        transferSyntax,
        rspAssert);
    association.waitForOutstandingRSP();

    // Close the association.
    association.release();
    association.waitForSocketClose();
    rspAssert.assertResult();

    // check that N-EVENT-REPORT data matches expected expectReportAttrs
    Truth.assertThat(checkFuture.get(5, TimeUnit.SECONDS)).isTrue();
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
  private int createDicomServer(IDicomWebClient dicomWebClient, int scuPort) throws Exception {
    int serverPort = PortUtil.getFreePort();

    DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
    serviceRegistry.addDicomService(new BasicCEchoSCP());
    AetDictionary aetDict = new AetDictionary(new AetDictionary.Aet[]{
        new AetDictionary.Aet(clientAET, clientHostname, scuPort)});
    StorageCommitmentService cmtService = new StorageCommitmentService(dicomWebClient, aetDict);
    serviceRegistry.addDicomService(cmtService);

    Device serverDevice = DeviceUtil.createServerDevice(serverAET, serverPort, serviceRegistry);
    serverDevice.bindConnections();
    return serverPort;
  }

  // Creates a SCU service and returns the port it is listening on.
  private Device createSCUDevice(int listenerPort, Attributes expectReportAttrs,
      CompletableFuture<Boolean> checkFuture) throws Exception {
    DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
    serviceRegistry.addDicomService(new BasicCEchoSCP());
    StorageCommitmentSCUService scuService = new StorageCommitmentSCUService(expectReportAttrs,
        checkFuture);
    serviceRegistry.addDicomService(scuService);

    Device serverDevice = DeviceUtil.createServerDevice(clientAET, listenerPort, serviceRegistry,
        new TransferCapability(null,
            UID.StorageCommitmentPushModel,
            TransferCapability.Role.SCU,
            transferSyntax));
    serverDevice.bindConnections();
    return serverDevice;
  }

  private class StorageCommitmentSCUService extends AbstractDicomService {

    private final Attributes expectReportAttrs;
    private final CompletableFuture<Boolean> checkFuture;

    public StorageCommitmentSCUService(Attributes expectReportAttrs,
        CompletableFuture<Boolean> checkFuture) {
      super(UID.StorageCommitmentPushModel);
      this.expectReportAttrs = expectReportAttrs;
      this.checkFuture = checkFuture;
    }

    @Override
    protected void onDimseRQ(Association as, PresentationContext pc, Dimse dimse, Attributes cmd,
        Attributes data) throws IOException {

      // Throwing exceptions here is only meaningful in sense that it prevents checkFuture
      // completion. Might as well checkFuture.complete(false) instead.

      if (dimse != Dimse.N_EVENT_REPORT_RQ) {
        throw new DicomServiceException(Status.UnrecognizedOperation);
      }

      if (!cmd.getString(Tag.AffectedSOPClassUID).equals(UID.StorageCommitmentPushModel)) {
        throw new DicomServiceException(Status.NoSuchSOPclass);
      }

      if (!cmd.getString(Tag.AffectedSOPInstanceUID)
          .equals(UID.StorageCommitmentPushModelInstance)) {
        throw new DicomServiceException(Status.NoSuchObjectInstance);
      }

      int eventTypeID = cmd.getInt(Tag.EventTypeID, 0);
      Sequence presentSeq = data.getSequence(Tag.ReferencedSOPSequence);
      Sequence absentSeq = data.getSequence(Tag.FailedSOPSequence);
      switch (eventTypeID) {
        case 1:
          if (presentSeq == null || presentSeq.size() == 0 || absentSeq != null) {
            throw new DicomServiceException(Status.ProcessingFailure);
          }
          break;
        case 2:
          if (absentSeq == null || absentSeq.size() == 0) {
            throw new DicomServiceException(Status.ProcessingFailure);
          }
          break;
        default:
          throw new DicomServiceException(Status.NoSuchEventType)
              .setEventTypeID(eventTypeID);
      }
      try {
        checkFuture.complete(data.equals(expectReportAttrs));

        Attributes rsp = Commands.mkNEventReportRSP(cmd, Status.Success);
        as.writeDimseRSP(pc, rsp);
      } catch (AssociationStateException e) {
        e.printStackTrace();
      }
    }
  }
}
