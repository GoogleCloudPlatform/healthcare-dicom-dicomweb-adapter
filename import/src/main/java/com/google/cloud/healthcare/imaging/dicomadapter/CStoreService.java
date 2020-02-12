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
import com.google.cloud.healthcare.IDicomWebClient.DicomWebException;
import com.google.cloud.healthcare.deid.redactor.DicomRedactor;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.Event;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.MonitoringService;
import com.google.common.io.CountingInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to handle server-side C-STORE DICOM requests.
 */
public class CStoreService extends BasicCStoreSCP {

  private static Logger log = LoggerFactory.getLogger(CStoreService.class);

  private final IDicomWebClient defaultDicomWebClient;
  private final Map<DestinationFilter, IDicomWebClient> destinationMap;
  private final DicomRedactor redactor;

  CStoreService(IDicomWebClient defaultDicomWebClient,
      Map<DestinationFilter, IDicomWebClient> destinationMap,
      DicomRedactor redactor) {
    this.defaultDicomWebClient = defaultDicomWebClient;
    this.destinationMap = destinationMap;
    this.redactor = redactor;
  }

  @Override
  protected void store(
      Association association,
      PresentationContext presentationContext,
      Attributes request,
      PDVInputStream inPdvStream,
      Attributes response)
      throws DicomServiceException, IOException {
    try {
      MonitoringService.addEvent(Event.CSTORE_REQUEST);

      String sopClassUID = request.getString(Tag.AffectedSOPClassUID);
      String sopInstanceUID = request.getString(Tag.AffectedSOPInstanceUID);
      String transferSyntax = presentationContext.getTransferSyntax();

      validateParam(sopClassUID, "AffectedSOPClassUID");
      validateParam(sopInstanceUID, "AffectedSOPInstanceUID");

      CountingInputStream countingStream;
      IDicomWebClient destinationClient;
      if(destinationMap != null && destinationMap.size() > 0){
        DicomInputStream inDicomStream  = new DicomInputStream(inPdvStream);
        inDicomStream.mark(Integer.MAX_VALUE);
        Attributes attrs = inDicomStream.readDataset(-1, Tag.PixelData);
        inDicomStream.reset();

        countingStream = new CountingInputStream(inDicomStream);
        destinationClient = selectDestinationClient(association.getAAssociateAC().getCallingAET(), attrs);
      } else {
        countingStream = new CountingInputStream(inPdvStream);
        destinationClient = defaultDicomWebClient;
      }

      InputStream inWithHeader =
          DicomStreamUtil.dicomStreamWithFileMetaHeader(
              sopInstanceUID, sopClassUID, transferSyntax, countingStream);

      if (redactor != null) {
        redactAndStow(association.getApplicationEntity().getDevice().getExecutor(), inWithHeader, destinationClient);
      } else {
        destinationClient.stowRs(inWithHeader);
      }

      response.setInt(Tag.Status, VR.US, Status.Success);
      MonitoringService.addEvent(Event.CSTORE_BYTES, countingStream.getCount());
    } catch (DicomWebException e) {
      MonitoringService.addEvent(Event.CSTORE_ERROR);
      throw new DicomServiceException(e.getStatus(), e.getMessage(), e);
    } catch (DicomServiceException e) {
      MonitoringService.addEvent(Event.CSTORE_ERROR);
      throw e;
    } catch (Throwable e) {
      MonitoringService.addEvent(Event.CSTORE_ERROR);
      throw new DicomServiceException(Status.ProcessingFailure, e);
    }
  }

  private void validateParam(String value, String name) throws DicomServiceException {
    if (value == null || value.trim().length() == 0) {
      throw new DicomServiceException(Status.CannotUnderstand, "Mandatory tag empty: " + name);
    }
  }

  private void redactAndStow(Executor underlyingExecutor, InputStream inputStream,
      IDicomWebClient dicomWebClient) throws Throwable {
    ExecutorCompletionService<Void> ecs = new ExecutorCompletionService<>(underlyingExecutor);

    PipedOutputStream pdvPipeOut = new PipedOutputStream();
    PipedInputStream pdvPipeIn = new PipedInputStream(pdvPipeOut);
    PipedOutputStream redactedPipeOut = new PipedOutputStream();
    PipedInputStream redactedPipeIn = new PipedInputStream(redactedPipeOut);

    ecs.submit(() -> {
      try (redactedPipeIn) {
        dicomWebClient.stowRs(redactedPipeIn);
      }
      return null;
    });

    ecs.submit(() -> {
      try (pdvPipeIn) {
        try (redactedPipeOut) {
          redactor.redact(pdvPipeIn, redactedPipeOut);
        }
      }
      return null;
    });

    try (pdvPipeOut) {
      // PDVInputStream is thread-locked
      StreamUtils.copy(inputStream, pdvPipeOut);
    } catch (IOException e) {
      // causes or is caused by exception in redactor.redact, no need to throw this up
      log.trace("Error copying inputStream to pdvPipeOut", e);
    }

    try {
      for (int i = 0; i < 2; ++i) {
        ecs.take().get();
      }
    } catch (ExecutionException e) {
      throw e.getCause();
    }
  }

  private IDicomWebClient selectDestinationClient(String callingAet, Attributes attrs){
    for(DestinationFilter filter: destinationMap.keySet()){
      if(filter.matches(callingAet, attrs)){
        return destinationMap.get(filter);
      }
    }
    return defaultDicomWebClient;
  }
}
