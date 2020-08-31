package com.google.cloud.healthcare.imaging.dicomadapter.cstore.destination;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.DestinationFilter;
import org.dcm4che3.data.Attributes;
import java.util.Map;

public class SingleDestinationClientFactory extends DestinationClientFactory {
  public SingleDestinationClientFactory(Map<DestinationFilter, IDicomWebClient> destinationMap, IDicomWebClient defaultDicomWebClient) {
    super(destinationMap, defaultDicomWebClient);
  }

  @Override
  protected void selectAndPutDestinationClients(DestinationHolder destinationHolder, String callingAet, Attributes attrs) {
    for (DestinationFilter filter: destinationMap.keySet()) {
      if (filter.matches(callingAet, attrs)) {
        destinationHolder.setSingleDestination(destinationMap.get(filter));
        return;
      }
    }
  }
}
