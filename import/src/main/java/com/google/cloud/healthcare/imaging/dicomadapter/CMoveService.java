package com.google.cloud.healthcare.imaging.dicomadapter;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.cstoresender.ICStoreSender;
import com.google.cloud.healthcare.imaging.dicomadapter.cstoresender.ICStoreSenderFactory;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.Event;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.MonitoringService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Commands;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCMoveSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.TagUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CMoveService extends BasicCMoveSCP {

  private static Logger log = LoggerFactory.getLogger(CMoveService.class);
  private final IDicomWebClient dicomWebClient;
  private final AetDictionary aets;
  private final ICStoreSenderFactory cstoreSenderFactory;

  CMoveService(
      IDicomWebClient dicomWebClient,
      AetDictionary aets,
      ICStoreSenderFactory cstoreSenderFactory) {
    super(UID.StudyRootQueryRetrieveInformationModelMOVE);
    this.dicomWebClient = dicomWebClient;
    this.aets = aets;
    this.cstoreSenderFactory = cstoreSenderFactory;
  }

  @Override
  public void onDimseRQ(Association as, PresentationContext pc, Dimse dimse,
      Attributes cmd, Attributes keys) throws IOException {
    if (dimse != Dimse.C_MOVE_RQ) {
      throw new DicomServiceException(Status.UnrecognizedOperation);
    }

    MonitoringService.addEvent(Event.CMOVE_REQUEST);

    CMoveTask task = new CMoveTask(as, pc, cmd, keys);
    as.getApplicationEntity().getDevice().execute(task);
  }

  private class CMoveTask extends DimseTask {

    private final Attributes keys;

    private CMoveTask(Association as, PresentationContext pc,
        Attributes cmd, Attributes keys) {
      super(as, pc, cmd);

      this.keys = keys;
    }

    @Override
    public void run() {
      List<String> failedInstanceUids = new ArrayList<>();
      ICStoreSender cstoreSender = null;
      try {
        if (canceled) {
          throw new CancellationException();
        }
        runThread = Thread.currentThread();

        AetDictionary.Aet cstoreTarget = aets.getAet(cmd.getString(Tag.MoveDestination));
        if (cstoreTarget == null) {
          sendErrorResponse(Status.MoveDestinationUnknown,
              "Unknown AET: " + cmd.getString(Tag.MoveDestination));
          return;
        }

        // need to get instances belonging to series/study
        Attributes keysCopy = new Attributes(keys);
        keysCopy.setString(Tag.QueryRetrieveLevel, VR.CS, "IMAGE");
        String qidoPath;
        try {
          qidoPath = AttributesUtil.attributesToQidoPath(keysCopy,
              TagUtils.toHexString(Tag.TransferSyntaxUID));
          log.info("CMove QidoPath: " + qidoPath);
        } catch (DicomServiceException e) {
          log.error("CMove QidoPath error");
          sendErrorResponse(e.getStatus(), e.getMessage(), e.getDataset(), null);
          return;
        }

        JSONArray qidoResult;
        try {
          MonitoringService.addEvent(Event.CMOVE_QIDORS_REQUEST);
          qidoResult = dicomWebClient.qidoRs(qidoPath);
          if (qidoResult == null || qidoResult.length() == 0) {
            throw new IDicomWebClient.DicomWebException("No instances to move");
          }
        } catch (IDicomWebClient.DicomWebException e) {
          MonitoringService.addEvent(Event.CMOVE_QIDORS_ERROR);
          log.error("Failed QidoRs for CMove", e);
          sendErrorResponse(Status.UnableToCalculateNumberOfMatches, e.getMessage());
          return;
        }

        cstoreSender = cstoreSenderFactory.create();

        int successfullInstances = 0;
        int remainingInstances = qidoResult.length();
        for (Object instance : qidoResult) {
          sendPendingResponse(remainingInstances, successfullInstances, failedInstanceUids.size());

          if (canceled) {
            throw new CancellationException();
          }

          JSONObject instanceJson = (JSONObject) instance;
          String studyUid = AttributesUtil.getTagValue(instanceJson,
              TagUtils.toHexString(Tag.StudyInstanceUID));
          String seriesUid = AttributesUtil.getTagValue(instanceJson,
              TagUtils.toHexString(Tag.SeriesInstanceUID));
          String instanceUid = AttributesUtil.getTagValue(instanceJson,
              TagUtils.toHexString(Tag.SOPInstanceUID));
          String classUid = AttributesUtil.getTagValue(instanceJson,
              TagUtils.toHexString(Tag.SOPClassUID));
          String tsuid = AttributesUtil.getTagValueOrNull(instanceJson,
              TagUtils.toHexString(Tag.TransferSyntaxUID));
          if (tsuid == null) {
            tsuid = UID.ExplicitVRLittleEndian;
          }

          try {
            MonitoringService.addEvent(Event.CMOVE_CSTORE_REQUEST);
            long bytesSent = cstoreSender.cstore(cstoreTarget, studyUid, seriesUid,
                instanceUid, classUid, tsuid);
            successfullInstances++;
            MonitoringService.addEvent(Event.CMOVE_CSTORE_BYTES, bytesSent);
          } catch (IDicomWebClient.DicomWebException | IOException e) {
            MonitoringService.addEvent(Event.CMOVE_CSTORE_ERROR);
            log.error("Failed CStore within CMove", e);
            failedInstanceUids.add(instanceUid);
          }

          remainingInstances--;
        }

        if (failedInstanceUids.isEmpty()) {
          as.tryWriteDimseRSP(pc, Commands.mkCMoveRSP(cmd, Status.Success));
        } else {
          int status = successfullInstances > 0 ?
              Status.OneOrMoreFailures : Status.UnableToPerformSubOperations;
          sendErrorResponse(status, failedInstanceUids);
        }
      } catch (CancellationException | InterruptedException e) {
        log.info("Canceled CMove", e);
        sendErrorResponse(Status.Cancel, failedInstanceUids);
      } catch (Throwable e) {
        log.error("Failure processing CMove", e);
        sendErrorResponse(Status.ProcessingFailure, e.getMessage());
      } finally {
        synchronized (this) {
          runThread = null;
        }
        int msgId = cmd.getInt(Tag.MessageID, -1);
        as.removeCancelRQHandler(msgId);

        if (cstoreSender != null) {
          try {
            cstoreSender.close();
          } catch (IOException e) {
            log.error("Failure closing cstoreSender: ", e);
          }
        }
      }
    }

    // It seems WEASIS (at least, GINKGO/AESKULAP don't cancel at all) doesn't just send cancel-rq,
    // when it wants to cancel.
    // Instead it replies with cancel-rq to any move-rsp, including pending
    // (which I can spam as much as I want).
    // Which while not contradicting the standard, is weird (Pending responses are optional).
    private void sendPendingResponse(
        int remainingInstances,
        int successfullInstances,
        int failedInstances)
        throws CancellationException {
      Attributes attributes = new Attributes();
      attributes.setInt(Tag.NumberOfRemainingSuboperations, VR.US, remainingInstances);
      attributes.setInt(Tag.NumberOfCompletedSuboperations, VR.US, successfullInstances);
      attributes.setInt(Tag.NumberOfFailedSuboperations, VR.US, failedInstances);
      // no code path for warnings
      attributes.setInt(Tag.NumberOfWarningSuboperations, VR.US, 0);
      as.tryWriteDimseRSP(pc, Commands.mkCMoveRSP(cmd, Status.Pending), attributes);
    }

    private void sendErrorResponse(int status, String message) {
      sendErrorResponse(status, message, null, null);
    }

    private void sendErrorResponse(int status, List<String> failedInstanceUids) {
      sendErrorResponse(status, null, null, failedInstanceUids);
    }

    private void sendErrorResponse(int status, String message, Attributes dataset,
        List<String> failedInstanceUids) {
      switch (status) {
        case Status.Cancel:
          MonitoringService.addEvent(Event.CMOVE_CANCEL);
          break;
        case Status.OneOrMoreFailures:
          MonitoringService.addEvent(Event.CMOVE_WARNING);
          break;
        default:
          MonitoringService.addEvent(Event.CMOVE_ERROR);
      }

      if (message == null && (failedInstanceUids == null || failedInstanceUids.size() == 0)) {
        as.tryWriteDimseRSP(pc, Commands.mkCMoveRSP(cmd, status));
        return;
      }

      Attributes attributes = dataset == null ? new Attributes() : dataset;
      if (message != null) {
        attributes.setString(Tag.ErrorComment, VR.LO, message);
      }
      if (failedInstanceUids != null && failedInstanceUids.size() > 0) {
        attributes.setString(Tag.FailedSOPInstanceUIDList, VR.UI,
            failedInstanceUids.toArray(new String[]{}));
      }
      as.tryWriteDimseRSP(pc, Commands.mkCMoveRSP(cmd, status), attributes);
    }
  }
}
