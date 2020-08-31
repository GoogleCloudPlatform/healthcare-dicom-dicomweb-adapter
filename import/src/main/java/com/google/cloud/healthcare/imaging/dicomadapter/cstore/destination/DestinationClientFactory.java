package com.google.cloud.healthcare.imaging.dicomadapter.cstore.destination;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.DestinationFilter;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.PDVInputStream;
import java.io.IOException;
import java.util.Map;

public abstract class DestinationClientFactory implements IDestinationClientFactory {

  protected final Map<DestinationFilter, IDicomWebClient> destinationMap;
  private final IDicomWebClient defaultDicomWebClient;

  public DestinationClientFactory(Map<DestinationFilter, IDicomWebClient> destinationMap,
                                  IDicomWebClient defaultDicomWebClient) {
    this.destinationMap = destinationMap;
    this.defaultDicomWebClient = defaultDicomWebClient;
  }

  @Override
  public DestinationHolder create(String callingAet, PDVInputStream inPdvStream) throws IOException {
    DestinationHolder destinationHolder;

    if (destinationMap != null) {
      DicomInputStream inDicomStream = new DicomInputStream(inPdvStream);
      Attributes attrs = getFilteringAttributes(inDicomStream);

      destinationHolder = new DestinationHolder(inDicomStream, defaultDicomWebClient);
      selectAndPutDestinationClients(destinationHolder, callingAet, attrs);
    } else {
      destinationHolder = new DestinationHolder(inPdvStream, defaultDicomWebClient);
    }

    return destinationHolder;
  }

  private Attributes getFilteringAttributes(DicomInputStream inDicomStream) throws IOException {
    inDicomStream.mark(Integer.MAX_VALUE);
    Attributes attrs = inDicomStream.readDataset(-1, Tag.PixelData);
    inDicomStream.reset();
    return attrs;
  }

  protected abstract void selectAndPutDestinationClients(DestinationHolder destinationHolder, String callingAet, Attributes attrs);
}
