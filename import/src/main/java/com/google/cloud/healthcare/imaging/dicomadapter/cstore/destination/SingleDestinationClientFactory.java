package com.google.cloud.healthcare.imaging.dicomadapter.cstore.destination;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.DestinationFilter;
import com.google.cloud.healthcare.imaging.dicomadapter.ImportAdapter.Pair;
import com.google.common.collect.ImmutableList;
import org.dcm4che3.data.Attributes;

public class SingleDestinationClientFactory extends DestinationClientFactory {
  public SingleDestinationClientFactory(ImmutableList<Pair<DestinationFilter, IDicomWebClient>> healthDestinationPairList, IDicomWebClient defaultDicomWebClient) {
    super(healthDestinationPairList, defaultDicomWebClient);
  }

  @Override
  protected void selectAndPutDestinationClients(DestinationHolder destinationHolder, String callingAet, Attributes attrs) {
    for (Pair<DestinationFilter, IDicomWebClient> filterToDestination: healthDestinations) {
      if (filterToDestination.getLeft().matches(callingAet, attrs)) {
        destinationHolder.setSingleDestination(filterToDestination.getRight());
        return;
      }
    }
  }
}
