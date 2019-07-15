package com.google.cloud.healthcare.imaging.dicomadapter.cstoresender;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.DeviceUtil;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CStoreSenderFactory implements ICStoreSenderFactory {

  private static Logger log = LoggerFactory.getLogger(CStoreSenderFactory.class);

  private final String cstoreSubAet;
  private final IDicomWebClient dicomWebClient;


  public CStoreSenderFactory(String cstoreSubAet, IDicomWebClient dicomWebClient) {
    this.cstoreSubAet = cstoreSubAet;
    this.dicomWebClient = dicomWebClient;
  }

  @Override
  public ICStoreSender create() {
    ApplicationEntity subApplicationEntity = new ApplicationEntity(cstoreSubAet);
    Connection conn = new Connection();
    DeviceUtil.createClientDevice(subApplicationEntity, conn);
    subApplicationEntity.addConnection(conn);

    return new CStoreSender(subApplicationEntity, dicomWebClient);
  }
}
