package com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest.sender;

import com.google.cloud.healthcare.imaging.dicomadapter.AetDictionary.Aet;
import com.google.cloud.healthcare.imaging.dicomadapter.DicomClient;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import org.dcm4che3.net.ApplicationEntity;

public class CStoreSender implements Closeable {

  private final ApplicationEntity applicationEntity;

  public CStoreSender(ApplicationEntity applicationEntity) {
    this.applicationEntity = applicationEntity;
  }

  public void cstore(Aet target,
                    String sopInstanceUid,
                    String sopClassUid,
                    InputStream inputStream)
      throws IOException, InterruptedException {
    DicomClient.connectAndCstore(
        sopClassUid,
        sopInstanceUid,
        inputStream,
        applicationEntity,
        target.getName(),
        target.getHost(),
        target.getPort());
  }

  @Override
  public void close() {
    applicationEntity.getDevice().unbindConnections();
  }
}
