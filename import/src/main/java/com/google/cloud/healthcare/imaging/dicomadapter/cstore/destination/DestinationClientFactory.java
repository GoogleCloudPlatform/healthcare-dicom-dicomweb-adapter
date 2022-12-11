package com.google.cloud.healthcare.imaging.dicomadapter.cstore.destination;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.DestinationFilter;
import com.google.cloud.healthcare.imaging.dicomadapter.ImportAdapter.Pair;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;

public abstract class DestinationClientFactory implements IDestinationClientFactory {

  protected final ImmutableList<Pair<DestinationFilter, IDicomWebClient>> healthcareDestinations;
  private final IDicomWebClient defaultDicomWebClient;
  private boolean dicomDestinationsNotEmpty;

  public DestinationClientFactory(ImmutableList<Pair<DestinationFilter, IDicomWebClient>> healthcareDestinations,
                                  IDicomWebClient defaultDicomWebClient) {
    this.healthcareDestinations = healthcareDestinations;
    this.defaultDicomWebClient = defaultDicomWebClient;
  }

  public DestinationClientFactory(ImmutableList<Pair<DestinationFilter, IDicomWebClient>> healthcareDestinations,
                                  IDicomWebClient defaultDicomWebClient, boolean dicomDestinationsNotEmpty) {
    this(healthcareDestinations, defaultDicomWebClient);
    this.dicomDestinationsNotEmpty = dicomDestinationsNotEmpty;
  }

  @Override
  public DestinationHolder create(String callingAet, String transferSyntax, InputStream inputStream) throws IOException {
    DestinationHolder destinationHolder;
    
    if ((healthcareDestinations != null && !healthcareDestinations.isEmpty()) || dicomDestinationsNotEmpty) {
      DicomInputStream inDicomStream = createDicomInputStream(transferSyntax, inputStream);
      Attributes attrs = getFilteringAttributes(inDicomStream);
      
      destinationHolder = new DestinationHolder(inDicomStream, defaultDicomWebClient);
      selectAndPutDestinationClients(destinationHolder, callingAet, attrs);
    } else {
      destinationHolder = new DestinationHolder(inputStream, defaultDicomWebClient);
    }

    return destinationHolder;
  }

  @VisibleForTesting
  DicomInputStream createDicomInputStream(String transferSyntax, InputStream inputStream) throws IOException {
    return new DicomInputStream(new BufferedInputStream(inputStream), transferSyntax);
  }

  private Attributes getFilteringAttributes(DicomInputStream inDicomStream) throws IOException {
    inDicomStream.mark(Integer.MAX_VALUE);
    Attributes attrs = inDicomStream.readDataset(-1, Tag.PixelData);
    inDicomStream.reset();
    return attrs;
  }

  protected abstract void selectAndPutDestinationClients(DestinationHolder destinationHolder, String callingAet, Attributes attrs);
}
