package com.google.cloud.healthcare.imaging.dicomadapter.cstoresender;

import com.github.danieln.multipart.MultipartInput;
import com.github.danieln.multipart.PartInput;
import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.AetDictionary;
import com.google.cloud.healthcare.imaging.dicomadapter.CMoveService;
import com.google.cloud.healthcare.imaging.dicomadapter.DicomClient;
import com.google.common.io.CountingInputStream;
import java.io.IOException;
import org.dcm4che3.net.ApplicationEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CStoreSender implements ICStoreSender {

  private static Logger log = LoggerFactory.getLogger(CMoveService.class);

  private final ApplicationEntity applicationEntity;
  private final IDicomWebClient dicomWebClient;

  public CStoreSender(ApplicationEntity applicationEntity, IDicomWebClient dicomWebClient) {
    this.applicationEntity = applicationEntity;
    this.dicomWebClient = dicomWebClient;
  }

  @Override
  public long cstore(AetDictionary.Aet target,
      String studyUid,
      String seriesUid,
      String sopInstanceUid,
      String sopClassUid)
      throws IDicomWebClient.DicomWebException, IOException, InterruptedException {
    String wadoUri =
        String.format("studies/%s/series/%s/instances/%s", studyUid, seriesUid, sopInstanceUid);
    log.info("CStore wadoUri : " + wadoUri);

    MultipartInput resp = dicomWebClient.wadoRs(wadoUri);
    PartInput part = resp.nextPart();
    if (part == null) {
      throw new IllegalArgumentException("WadoRS response has no parts");
    }

    CountingInputStream countingStream = new CountingInputStream(part.getInputStream());
    DicomClient.connectAndCstore(sopClassUid, sopInstanceUid, countingStream,
        applicationEntity, target.getName(), target.getHost(), target.getPort());
    return countingStream.getCount();
  }

  @Override
  public void close() throws IOException {
    applicationEntity.getDevice().unbindConnections();
  }
}
