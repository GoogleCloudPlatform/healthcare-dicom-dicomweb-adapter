package com.google.cloud.healthcare.imaging.dicomadapter.cstore.destination;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.AetDictionary;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CountingInputStream;
import java.io.InputStream;

public class DestinationHolder {

  private IDicomWebClient singleDestination;
  private ImmutableList<IDicomWebClient> healthcareDestinations;
  private ImmutableList<AetDictionary.Aet> dicomDestinations;
  private CountingInputStream countingInputStream;

  public DestinationHolder(InputStream destinationInputStream, IDicomWebClient defaultDestination) {
    this.countingInputStream = new CountingInputStream(destinationInputStream);
    //default values
    this.singleDestination = defaultDestination;
    this.healthcareDestinations = ImmutableList.of(defaultDestination);
    this.dicomDestinations = ImmutableList.of();
  }

  public CountingInputStream getCountingInputStream() {
    return countingInputStream;
  }

  public void setSingleDestination(IDicomWebClient dicomWebClient) {
    this.singleDestination = dicomWebClient;
  }

  public IDicomWebClient getSingleDestination() {
    return singleDestination;
  }

  public void setHealthcareDestinations(ImmutableList<IDicomWebClient> healthcareDestinations) {
    this.healthcareDestinations = healthcareDestinations;
  }

  public void setDicomDestinations(ImmutableList<AetDictionary.Aet> dicomDestinations) {
    this.dicomDestinations = dicomDestinations;
  }

  public ImmutableList<IDicomWebClient> getHealthcareDestinations() {
    return healthcareDestinations;
  }

  public ImmutableList<AetDictionary.Aet> getDicomDestinations() {
    return dicomDestinations;
  }
}
