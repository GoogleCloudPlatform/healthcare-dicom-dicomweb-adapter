package com.google.cloud.healthcare.imaging.dicomadapter;

import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.Event;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.MonitoringService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.dcm4che3.net.service.BasicCFindSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.TagUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CFindService extends BasicCFindSCP {

  private static Logger log = LoggerFactory.getLogger(CFindService.class);

  private IDicomWebClient dicomWebClient;

  CFindService(IDicomWebClient dicomWebClient) {
    super(UID.StudyRootQueryRetrieveInformationModelFIND);
    this.dicomWebClient = dicomWebClient;
  }

  private static HashMap<String, JSONObject> uniqueResults(List<JSONArray> responses) {
    HashMap<String, JSONObject> uniqueResults = new HashMap<>();
    for (JSONArray response : responses) {
      for (Object result : response) {
        JSONObject resultJson = (JSONObject) result;
        String key = getResultKey(resultJson);
        if (!uniqueResults.containsKey(key)) {
          uniqueResults.put(key, resultJson);
        }
      }
    }
    return uniqueResults;
  }

  private static String getResultKey(JSONObject jsonObject) {
    return AttributesUtil.getTagValueOrNull(jsonObject,
        TagUtils.toHexString(Tag.StudyInstanceUID)) + "_" +
        AttributesUtil.getTagValueOrNull(jsonObject,
            TagUtils.toHexString(Tag.SeriesInstanceUID)) + "_" +
        AttributesUtil.getTagValueOrNull(jsonObject,
            TagUtils.toHexString(Tag.SOPInstanceUID));
  }

  @Override
  public void onDimseRQ(Association association,
      PresentationContext presentationContext,
      Dimse dimse,
      Attributes request,
      Attributes keys) throws IOException {
    if (dimse != Dimse.C_FIND_RQ) {
      throw new DicomServiceException(Status.UnrecognizedOperation);
    }

    MonitoringService.addEvent(Event.CFIND_REQUEST);

    CFindTask task = new CFindTask(association, presentationContext, request, keys);
    association.getApplicationEntity().getDevice().execute(task);
  }

  private class CFindTask extends DimseTask {

    private final Attributes keys;

    private CFindTask(Association as, PresentationContext pc,
        Attributes cmd, Attributes keys) {
      super(as, pc, cmd);

      this.keys = keys;
    }

    @Override
    public void run() {
      try {
        if (canceled) {
          throw new CancellationException();
        }
        runThread = Thread.currentThread();

        String[] qidoPaths = AttributesUtil.attributesToQidoPathArray(keys);
        List<JSONArray> qidoResults = new ArrayList<>();
        for (String qidoPath : qidoPaths) {
          if (canceled) {
            throw new CancellationException();
          }
          log.info("CFind QidoPath: " + qidoPath);
          try {
            MonitoringService.addEvent(Event.CFIND_SUB_QIDORS_REQUEST);
            JSONArray qidoResult = dicomWebClient.qidoRs(qidoPath);
            qidoResults.add(qidoResult);
          } catch (IDicomWebClient.DicomWebException e) {
            MonitoringService.addEvent(Event.CFIND_SUB_QIDORS_ERROR);
            if (e.getCause() instanceof HttpResponseException &&
                ((HttpResponseException) e.getCause()).getStatusCode() ==
                    HttpStatusCodes.STATUS_CODE_SERVICE_UNAVAILABLE) {
              MonitoringService.addEvent(Event.CFIND_ERROR);
              as.tryWriteDimseRSP(pc, Commands.mkCFindRSP(cmd, Status.OutOfResources));
              return;
            } else {
              throw e;
            }
          }
        }
        HashMap<String, JSONObject> uniqueResults = uniqueResults(qidoResults);

        for (JSONObject obj : uniqueResults.values()) {
          if (canceled) {
            throw new CancellationException();
          }
          Attributes attrs = AttributesUtil.jsonToAttributes(obj);
          as.writeDimseRSP(pc, Commands.mkCFindRSP(cmd, Status.Pending), attrs);
        }
        as.writeDimseRSP(pc, Commands.mkCFindRSP(cmd, Status.Success));
      } catch (CancellationException e) {
        log.info("Canceled CFind", e);
        MonitoringService.addEvent(Event.CFIND_CANCEL);
        as.tryWriteDimseRSP(pc, Commands.mkCFindRSP(cmd, Status.Cancel));
      } catch (Throwable e) {
        log.error("Failure processing CFind", e);
        MonitoringService.addEvent(Event.CFIND_ERROR);
        Attributes attrs = new Attributes();
        attrs.setString(Tag.ErrorComment, VR.LO, e.getMessage());
        as.tryWriteDimseRSP(pc, Commands.mkCFindRSP(cmd, Status.ProcessingFailure), attrs);
      } finally {
        synchronized (this) {
          runThread = null;
        }
        int msgId = cmd.getInt(Tag.MessageID, -1);
        as.removeCancelRQHandler(msgId);
      }
    }
  }
}