package com.google.cloud.healthcare.imaging.dicomadapter.cstoresender;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.AetDictionary;
import java.io.Closeable;
import java.io.IOException;

public interface ICStoreSender extends Closeable {

  /**
   * Sends instance via c-store (or test stub) to target AET, returns bytes sent
   */
  long cstore(
      AetDictionary.Aet target,
      String studyUid,
      String seriesUid,
      String sopInstanceUid,
      String sopClassUid)
      throws IDicomWebClient.DicomWebException, IOException, InterruptedException;
}
