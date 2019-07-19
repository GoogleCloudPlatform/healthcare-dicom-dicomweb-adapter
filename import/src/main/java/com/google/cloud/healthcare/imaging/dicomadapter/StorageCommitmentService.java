package com.google.cloud.healthcare.imaging.dicomadapter;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.AetDictionary.Aet;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.Event;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.MonitoringService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Commands;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.FutureDimseRSP;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.pdu.RoleSelection;
import org.dcm4che3.net.service.AbstractDicomService;
import org.dcm4che3.net.service.DicomServiceException;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StorageCommitmentService extends AbstractDicomService {

  private static Logger log = LoggerFactory.getLogger(StorageCommitmentService.class);

  private final IDicomWebClient dicomWebClient;
  private final AetDictionary aets;

  StorageCommitmentService(IDicomWebClient dicomWebClient, AetDictionary aets) {
    super(UID.StorageCommitmentPushModelSOPClass);
    this.dicomWebClient = dicomWebClient;
    this.aets = aets;
  }

  @Override
  protected void onDimseRQ(Association as, PresentationContext pc, Dimse dimse, Attributes cmd,
      Attributes data) throws IOException {
    if (dimse != Dimse.N_ACTION_RQ ||
        !cmd.getString(Tag.RequestedSOPClassUID).equals(UID.StorageCommitmentPushModelSOPClass) ||
        !cmd.getString(Tag.RequestedSOPInstanceUID)
            .equals(UID.StorageCommitmentPushModelSOPInstance)) {
      throw new DicomServiceException(Status.UnrecognizedOperation);
    }

    MonitoringService.addEvent(Event.COMMITMENT_REQUEST);

    Aet remoteAet = aets.getAet(as.getRemoteAET());
    if (remoteAet == null) {
      MonitoringService.addEvent(Event.COMMITMENT_ERROR);

      Attributes rspAttrs = new Attributes();
      rspAttrs.setString(Tag.ErrorComment, VR.LO, "Unknown AET: " + as.getRemoteAET());
      as.writeDimseRSP(pc, Commands.mkNActionRSP(cmd, Status.ProcessingFailure), rspAttrs);
      return;
    }

    CommitmentReportTask task = new CommitmentReportTask(as.getApplicationEntity(),
        data, remoteAet);
    as.getApplicationEntity().getDevice().execute(task);
    as.writeDimseRSP(pc, Commands.mkNActionRSP(cmd, Status.Success));
  }

  private class CommitmentReportTask implements Runnable {

    private final Attributes data;
    private final ApplicationEntity applicationEntity;
    private final Aet remoteAet;

    CommitmentReportTask(ApplicationEntity applicationEntity, Attributes data, Aet remoteAet) {
      this.applicationEntity = applicationEntity;
      this.data = data;
      this.remoteAet = remoteAet;
    }

    @Override
    public void run() {
      List<String> presentInstances = new ArrayList<>();
      List<String> absentInstances = new ArrayList<>();

      Sequence sopSequence = data.getSequence(Tag.ReferencedSOPSequence);
      for (Attributes item : sopSequence) {
        Attributes queryAttributes = new Attributes();
        String instanceUID = item.getString(Tag.ReferencedSOPInstanceUID);
        queryAttributes
            .setString(Tag.SOPInstanceUID, VR.UI, instanceUID);
        queryAttributes
            .setString(Tag.SOPClassUID, VR.UI, item.getStrings(Tag.ReferencedSOPClassUID));
        queryAttributes.setString(Tag.QueryRetrieveLevel, VR.CS, "IMAGE");
        try {
          String qidoPath = AttributesUtil.attributesToQidoPath(queryAttributes);

          try {
            JSONArray qidoResult = dicomWebClient.qidoRs(qidoPath);
            if (qidoResult == null || qidoResult.length() == 0) {
              absentInstances.add(instanceUID);
            } else {
              presentInstances.add(instanceUID);
            }
          } catch (IDicomWebClient.DicomWebException e) {
            log.error("Commitment QidoRs error: ", e);
          }

        } catch (DicomServiceException e) {
          log.error("Commitment QidoPath error: ", e);
        }
      }

      DicomClient dicomClient;
      try {
        dicomClient = DicomClient.associatePeer(applicationEntity,
            remoteAet.getHost(), remoteAet.getPort(), makeAAssociateRQ());
      } catch (Exception e) {
        log.error("associatePeer exception: ", e);
        return;
      }

      Association association = dicomClient.getAssociation();
      try {
        FutureDimseRSP handler = new FutureDimseRSP(association.nextMessageID());

        association.neventReport(UID.StorageCommitmentPushModelSOPClass,
            UID.StorageCommitmentPushModelSOPInstance,
            1, // TODO
            makeDataset(presentInstances, absentInstances),
            UID.ExplicitVRLittleEndian,
            handler);

        handler.next();
        int dimseStatus = handler.getCommand().getInt(Tag.Status, /* default status */ -1);
        // TODO expect N-EVENT-RECORD-RSP
        if (dimseStatus != Status.Success) {
          throw new IllegalArgumentException(
              "Commitment Report failed with status code: " + dimseStatus);
        }
      } catch (IOException | InterruptedException e) {
        log.error("neventReport error: ", e);
      } finally {
        try {
          association.release();
          association.waitForSocketClose();
        } catch (Exception e) {
          log.warn("Send Commitment Report successfully, but failed to close association: ", e);
        }
      }
    }

    private AAssociateRQ makeAAssociateRQ() {
      AAssociateRQ aarq = new AAssociateRQ();
      aarq.setCallingAET(applicationEntity.getAETitle());
      aarq.setCalledAET(remoteAet.getName());
      TransferCapability tc = applicationEntity.getTransferCapabilityFor(
          UID.StorageCommitmentPushModelSOPClass,
          TransferCapability.Role.SCP);
      aarq.addPresentationContext(
          new PresentationContext(
              1,
              UID.StorageCommitmentPushModelSOPClass,
              UID.ExplicitVRLittleEndian));
      aarq.addRoleSelection(
          new RoleSelection(UID.StorageCommitmentPushModelSOPClass, false, true));
      return aarq;
    }

    private Attributes makeDataset(List<String> presentInstances, List<String> absentInstances) {
      Attributes result = new Attributes();
      result.setString(Tag.TransactionUID, VR.UI, data.getString(Tag.TransactionUID));

      if (absentInstances.size() > 0) {
        Sequence absentSeq = result.newSequence(Tag.FailedSOPSequence, absentInstances.size());
        for (String absentInstanceUid : absentInstances) {
          Attributes seqElementAttributes = new Attributes();
          seqElementAttributes.setString(Tag.SOPInstanceUID, VR.UI, absentInstanceUid);
          // sopClassUID?
          absentSeq.add(seqElementAttributes);
        }
      }

      if (presentInstances.size() > 0) {
        Sequence presentSeq = result.newSequence(Tag.ReferencedSOPSequence, absentInstances.size());
        for (String presentInstanceUid : presentInstances) {
          Attributes seqElementAttributes = new Attributes();
          seqElementAttributes.setString(Tag.SOPInstanceUID, VR.UI, presentInstanceUid);
          // sopClassUID?
          presentSeq.add(seqElementAttributes);
        }
      }

      return result;
    }
  }
}
