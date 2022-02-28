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

package com.google.cloud.healthcare;

import com.google.api.client.http.HttpStatusCodes;
import java.io.InputStream;
import org.dcm4che3.net.Status;
import org.json.JSONArray;

public interface IDicomWebClient {

  InputStream wadoRs(String path) throws DicomWebException;

  JSONArray qidoRs(String path) throws DicomWebException;

  void stowRs(InputStream in) throws DicomWebException;

  Boolean getStowOverwrite();

  void delete(String path) throws DicomWebException;

  void delete(InputStream stream) throws DicomWebException;

  /**
   * An exception for errors returned by the DicomWeb server.
   */
  class DicomWebException extends Exception {

    private int status = Status.ProcessingFailure;
    private int httpStatus;

    public DicomWebException(String message, int status) {
      super(message);
      this.status = status;
    }

    public DicomWebException(Throwable cause, int status) {
      super(cause);
      this.status = status;
    }

    public DicomWebException(String message, Throwable cause, int status) {
      super(message, cause);
      this.status = status;
    }

    public DicomWebException(
        String message,
        int httpStatus,
        int defaultDicomStatus) {
      super(message);
      this.status = httpStatusToDicomStatus(httpStatus, defaultDicomStatus);
      this.httpStatus = httpStatus;
    }

    public DicomWebException(
        String message,
        Throwable cause,
        int httpStatus,
        int defaultDicomStatus) {
      super(message, cause);
      this.status = httpStatusToDicomStatus(httpStatus, defaultDicomStatus);
      this.httpStatus = httpStatus;
    }

    public DicomWebException(String message) {
      super(message);
    }

    public DicomWebException(Throwable cause) {
      super(cause);
    }

    public int getStatus() {
      return status;
    }

    public int getHttpStatus() {
      return httpStatus;
    }

    private int httpStatusToDicomStatus(int httpStatus, int defaultStatus) {
      switch (httpStatus) {
        case HttpStatusCodes.STATUS_CODE_SERVICE_UNAVAILABLE:
          return Status.OutOfResources;
        case HttpStatusCodes.STATUS_CODE_UNAUTHORIZED:
          return Status.NotAuthorized;
        default:
          return defaultStatus;
      }
    }

  }
}
