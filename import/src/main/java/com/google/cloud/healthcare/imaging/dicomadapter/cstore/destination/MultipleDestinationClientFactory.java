package com.google.cloud.healthcare.imaging.dicomadapter.cstore.destination;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.AetDictionary;
import com.google.cloud.healthcare.imaging.dicomadapter.DestinationFilter;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableList;
import org.dcm4che3.data.Attributes;
import java.util.Map;

public class MultipleDestinationClientFactory extends DestinationClientFactory {

  private Map<DestinationFilter, AetDictionary.Aet> dicomDestinations;

  public MultipleDestinationClientFactory(Map<DestinationFilter, IDicomWebClient> healthcareDestinations,
                                          Map<DestinationFilter, AetDictionary.Aet> dicomDestinations,
                                          IDicomWebClient defaultDicomWebClient) {
    super(healthcareDestinations, defaultDicomWebClient);
    this.dicomDestinations = dicomDestinations;
  }

  @Override
  protected void selectAndPutDestinationClients(DestinationHolder destinationHolder, String callingAet, Attributes attrs) {
    Builder<IDicomWebClient> filteredHealthcareWebClientsBuilder = ImmutableList.builder();
    for (DestinationFilter filter: destinationMap.keySet()) {
      if (filter.matches(callingAet, attrs)) {
        filteredHealthcareWebClientsBuilder.add(destinationMap.get(filter));
      }
    }

    Builder<AetDictionary.Aet> filteredDicomDestinationsBuilder = ImmutableList.builder();
    for (DestinationFilter filter: dicomDestinations.keySet()) {
      if (filter.matches(callingAet, attrs)) {
        filteredDicomDestinationsBuilder.add(dicomDestinations.get(filter));
      }
    }

    destinationHolder.setHealthcareDestinations(filteredHealthcareWebClientsBuilder.build());
    destinationHolder.setDicomDestinations(filteredDicomDestinationsBuilder.build());
  }
}
