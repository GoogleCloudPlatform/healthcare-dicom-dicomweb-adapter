package com.google.cloud.healthcare;

/**
 * Thrown when a DICOMweb request path fails validation (e.g. path traversal or disallowed
 * characters). This is a permanent error: retrying the same input will never succeed.
 */
public class InvalidDicomWebPathException extends IllegalArgumentException {
  public InvalidDicomWebPathException(String message) {
    super(message);
  }

  public InvalidDicomWebPathException(String message, Throwable cause) {
    super(message, cause);
  }
}
