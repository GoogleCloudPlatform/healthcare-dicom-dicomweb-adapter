package com.google.cloud.healthcare;

import com.github.danieln.multipart.MultipartInput;
import java.io.InputStream;
import org.json.JSONArray;

public interface IDicomWebClient {

  MultipartInput wadoRs(String path) throws DicomWebException;

  JSONArray qidoRs(String path) throws DicomWebException;

  void stowRs(String path, InputStream in) throws DicomWebException;

  /**
   * An exception for errors returned by the DicomWeb server.
   */
  class DicomWebException extends Exception {

    public DicomWebException(String message) {
      super(message);
    }

    public DicomWebException(Throwable cause) {
      super(cause);
    }
  }
}
