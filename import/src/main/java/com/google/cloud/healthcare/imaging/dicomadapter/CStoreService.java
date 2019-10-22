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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
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

  private final String path;
  private final IDicomWebClient dicomWebClient;
  private final DicomRedactor redactor;

  CStoreService(String path, IDicomWebClient dicomWebClient, DicomRedactor redactor) {
    this.path = path;
    this.dicomWebClient = dicomWebClient;
    this.redactor = redactor;
  }

  @Override
  protected void store(
      Association association,
      PresentationContext presentationContext,
      Attributes request,
      PDVInputStream inDicomStream,
      Attributes response)
      throws DicomServiceException, IOException {
    try {
      MonitoringService.addEvent(Event.CSTORE_REQUEST);

      String sopClassUID = request.getString(Tag.AffectedSOPClassUID);
      String sopInstanceUID = request.getString(Tag.AffectedSOPInstanceUID);
      String transferSyntax = presentationContext.getTransferSyntax();

      validateParam(sopClassUID, "AffectedSOPClassUID");
      validateParam(sopInstanceUID, "AffectedSOPInstanceUID");

      CountingInputStream countingStream = new CountingInputStream(inDicomStream);
      InputStream inBuffer =
          DicomStreamUtil.dicomStreamWithFileMetaHeader(
              sopInstanceUID, sopClassUID, transferSyntax, countingStream);

      if (redactor != null) {
        redactAndStow(association.getApplicationEntity().getDevice().getExecutor(), inBuffer);
      } else {
        dicomWebClient.stowRs(path, inBuffer);
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

  private void redactAndStow(Executor underlyingExecutor, InputStream inputStream)
      throws Throwable {
    ExecutorCompletionService<Void> ecs = new ExecutorCompletionService<>(underlyingExecutor);

    PipedOutputStream pdvPipeOut = new PipedOutputStream();
    PipedInputStream pdvPipeIn = new PipedInputStream(pdvPipeOut);
    PipedOutputStream redactedPipeOut = new PipedOutputStream();
    PipedInputStream redactedPipeIn = new PipedInputStream(redactedPipeOut);

    ecs.submit(() -> {
      dicomWebClient.stowRs(path, redactedPipeIn);
      return null;
    });

    ecs.submit(() -> {
      redactor.redact(pdvPipeIn, redactedPipeOut);
      return null;
    });

    try {
      // PDVInputStream is thread-locked
      StreamUtils.copy(inputStream, pdvPipeOut);
    } catch (IOException e) {
      // causes or is caused by exception in redactor.redact, no need to throw this up
      log.trace("Error copying inputStream to pdvPipeOut", e);
    }
    pdvPipeOut.close();

    try {
      for (int i = 0; i < 2; ++i) {
        ecs.take().get();
      }
    } catch (ExecutionException e) {
      throw e.getCause();
    }
  }
}
