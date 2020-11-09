package com.google.cloud.healthcare.imaging.dicomadapter.cstore.destination;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.AetDictionary.Aet;
import com.google.cloud.healthcare.imaging.dicomadapter.DestinationFilter;
import com.google.cloud.healthcare.imaging.dicomadapter.ImportAdapter.Pair;
import com.google.common.collect.ImmutableList;
import org.dcm4che3.data.Attributes;

public class MultipleDestinationClientFactory extends DestinationClientFactory {

  private ImmutableList<Pair<DestinationFilter, Aet>> dicomDestinations;

  public MultipleDestinationClientFactory(ImmutableList<Pair<DestinationFilter, IDicomWebClient>> healthcareDestinations,
                                          ImmutableList<Pair<DestinationFilter, Aet>> dicomDestinations,
                                          IDicomWebClient defaultDicomWebClient) {
    super(healthcareDestinations, defaultDicomWebClient, dicomDestinations != null && !dicomDestinations.isEmpty());
    this.dicomDestinations = dicomDestinations;
  }

  @Override
  protected void selectAndPutDestinationClients(DestinationHolder destinationHolder, String callingAet, Attributes attrs) {
    ImmutableList.Builder<IDicomWebClient> filteredHealthcareWebClientsBuilder = ImmutableList.builder();
    if (healthcareDestinations != null) {
      for (Pair<DestinationFilter, IDicomWebClient> filterToDestination : healthcareDestinations) {
        if (filterToDestination.getLeft().matches(callingAet, attrs)) {
          filteredHealthcareWebClientsBuilder.add(filterToDestination.getRight());
        }
      }
      destinationHolder.setHealthcareDestinations(filteredHealthcareWebClientsBuilder.build());
    }

    if (dicomDestinations != null) {
      ImmutableList.Builder<Aet> filteredDicomDestinationsBuilder = ImmutableList.builder();
      for (Pair<DestinationFilter, Aet> filterToDestination : dicomDestinations) {
        if (filterToDestination.getLeft().matches(callingAet, attrs)) {
          filteredDicomDestinationsBuilder.add(filterToDestination.getRight());
        }
      }
      destinationHolder.setDicomDestinations(filteredDicomDestinationsBuilder.build());
    }
  }
}
