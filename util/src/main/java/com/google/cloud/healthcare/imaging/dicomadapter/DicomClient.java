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

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.DimseRSPHandler;
import org.dcm4che3.net.FutureDimseRSP;
import org.dcm4che3.net.IncompatibleConnectionException;
import org.dcm4che3.net.InputStreamDataWriter;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;

/**
 * DicomClient is used to handle client-side legacy DICOM requests on given association.
 */
public class DicomClient {

  private Association association;

  public DicomClient(Association association) {
    this.association = association;
  }

  /**
   * Creates a new DicomClient by creating an association to the given peer.
   */
  public static DicomClient associatePeer(
      ApplicationEntity clientAE,
      String peerAET,
      String peerHostname,
      int peerPort,
      PresentationContext pc)
      throws IOException, InterruptedException, IncompatibleConnectionException, GeneralSecurityException {
    AAssociateRQ rq = new AAssociateRQ();
    rq.addPresentationContext(pc);
    rq.setCalledAET(peerAET);
    Connection remoteConn = new Connection();
    remoteConn.setHostname(peerHostname);
    remoteConn.setPort(peerPort);
    Association association = clientAE.connect(remoteConn, rq);
    return new DicomClient(association);
  }

  public static void connectAndCstore(
      String sopClassUid,
      String sopInstanceUid,
      InputStream in,
      ApplicationEntity applicationEntity,
      String dimsePeerAet,
      String dimsePeerHost,
      int dimsePeerPort) throws IOException, InterruptedException {
    DicomInputStream din = new DicomInputStream(in);
    din.readFileMetaInformation();

    PresentationContext pc = new PresentationContext(1, sopClassUid, din.getTransferSyntax());
    DicomClient dicomClient;
    try {
      dicomClient = DicomClient.associatePeer(applicationEntity,
          dimsePeerAet, dimsePeerHost, dimsePeerPort, pc);
    } catch (IOException | IncompatibleConnectionException | GeneralSecurityException e) {
      // calling code doesn't need to distinguish these
      throw new IOException(e);
    }

    Association association = dicomClient.getAssociation();
    try {
      FutureDimseRSP handler = new FutureDimseRSP(association.nextMessageID());
      dicomClient.cstore(
          sopClassUid, sopInstanceUid, din.getTransferSyntax(), din, handler);
      handler.next();
      int dimseStatus = handler.getCommand().getInt(Tag.Status, /* default status */ -1);
      if (dimseStatus != Status.Success) {
        throw new IllegalArgumentException("C-STORE failed with status code: " + dimseStatus);
      }
    } finally {
      try {
        association.release();
        association.waitForSocketClose();
      } catch (Exception e) {
        System.err.println("Send C-STORE successfully, but failed to close association");
        e.printStackTrace();
      }
    }
  }

  public void cstore(
      String sopClassUid,
      String sopInstanceUid,
      String transferSyntaxUid,
      DicomInputStream din,
      DimseRSPHandler responseHandler)
      throws IOException, InterruptedException {
    InputStreamDataWriter data = new InputStreamDataWriter(din);
    association.cstore(
        sopClassUid, sopInstanceUid, /* priority */ 1, data, transferSyntaxUid, responseHandler);
  }

  public Association getAssociation() {
    return association;
  }
}
