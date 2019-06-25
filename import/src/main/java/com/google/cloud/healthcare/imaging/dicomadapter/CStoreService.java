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

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpMediaType;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.MultipartContent;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.Event;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.MonitoringService;
import com.google.common.io.CountingInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to handle server-side C-STORE DICOM requests.
 */
public class CStoreService extends BasicCStoreSCP {

  private static Logger log = LoggerFactory.getLogger(CStoreService.class);

  private String apiAddr;
  private String path;
  private HttpRequestFactory requestFactory;

  CStoreService(String apiAddr, String path, HttpRequestFactory requestFactory) {
    this.apiAddr = apiAddr;
    this.path = path;
    this.requestFactory = requestFactory;
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
      String remoteAeTitle = association.getCallingAET();

      validateParam(sopClassUID, "AffectedSOPClassUID");
      validateParam(sopInstanceUID, "AffectedSOPInstanceUID");

      CountingInputStream countingStream = new CountingInputStream(inDicomStream);
      InputStream inBuffer =
          DicomStreamUtil.dicomStreamWithFileMetaHeader(
              sopInstanceUID, sopClassUID, transferSyntax, countingStream);

      MultipartContent content = new MultipartContent();
      content.setMediaType(new HttpMediaType("multipart/related; type=\"application/dicom\""));
      content.setBoundary(UUID.randomUUID().toString());
      InputStreamContent dicomStream = new InputStreamContent("application/dicom", inBuffer);
      content.addPart(new MultipartContent.Part(dicomStream));

      GenericUrl url = new GenericUrl(apiAddr + path);
      try {
        HttpRequest httpRequest = requestFactory.buildPostRequest(url, content);
        httpRequest.execute();
      } catch (IOException e) {
        if (e instanceof HttpResponseException &&
            ((HttpResponseException) e).getStatusCode() ==
                HttpStatusCodes.STATUS_CODE_SERVICE_UNAVAILABLE) {
          throw new DicomServiceException(Status.OutOfResources, e);
        } else {
          throw e;
        }
      }

      log.info("Received C-STORE for association {}, SOP class {}, TS {}, remote AE {}",
          association.toString(), sopClassUID, transferSyntax, remoteAeTitle);
      response.setInt(Tag.Status, VR.US, Status.Success);

      MonitoringService.addEvent(Event.CSTORE_BYTES, countingStream.getCount());
    } catch (Throwable e) {
      MonitoringService.addEvent(Event.CSTORE_ERROR);
      if (e instanceof DicomServiceException) {
        throw e;
      } else {
        throw new DicomServiceException(Status.ProcessingFailure, e);
      }
    }
  }

  private void validateParam(String value, String name) throws DicomServiceException {
    if (value == null || value.trim().length() == 0) {
      throw new DicomServiceException(Status.CannotUnderstand, "Mandatory tag empty: " + name);
    }
  }

  @Override
  public void onClose(Association association) {
    // Handle any exceptions that may have been caused by aborts or C-Store request processing.
    String associationName = association.toString();
    if (association.getException() != null) {
      log.error("Exception while handling association " + associationName,
          association.getException());
    } else {
      log.info("Association {} finished successfully.", associationName);
    }
  }
}
