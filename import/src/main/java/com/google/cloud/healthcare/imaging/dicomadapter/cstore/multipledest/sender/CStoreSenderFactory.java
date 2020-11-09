package com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest.sender;

import com.google.cloud.healthcare.imaging.dicomadapter.DeviceUtil;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;

public class CStoreSenderFactory {

  private final String cstoreSubAet;

  public CStoreSenderFactory(String cstoreSubAet) {
    this.cstoreSubAet = cstoreSubAet;
  }

  public CStoreSender create() {
    ApplicationEntity subApplicationEntity = new ApplicationEntity(cstoreSubAet);
    Connection conn = new Connection();
    DeviceUtil.createClientDevice(subApplicationEntity, conn);
    subApplicationEntity.addConnection(conn);

    return new CStoreSender(subApplicationEntity);
  }
}
