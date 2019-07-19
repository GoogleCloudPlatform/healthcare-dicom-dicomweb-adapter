package com.google.cloud.healthcare;

import com.github.danieln.multipart.MultipartInput;
import java.io.InputStream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Status;
import org.json.JSONArray;

public interface IDicomWebClient {

  MultipartInput wadoRs(String path) throws DicomWebException;

  JSONArray qidoRs(String path) throws DicomWebException;

  void stowRs(String path, InputStream in) throws DicomWebException;

  /**
   * An exception for errors returned by the DicomWeb server.
   */
  class DicomWebException extends Exception {

    private int status = Status.ProcessingFailure;

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

    public DicomWebException(String message) {
      super(message);
    }

    public DicomWebException(Throwable cause) {
      super(cause);
    }

    public int getStatus() {
      return status;
    }

    public Attributes getAttributes() {
      Attributes attrs = new Attributes();
      attrs.setString(Tag.ErrorComment, VR.LO, getMessage());
      return attrs;
    }
  }
}
