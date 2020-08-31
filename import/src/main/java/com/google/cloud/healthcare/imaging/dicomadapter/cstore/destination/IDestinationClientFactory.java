package com.google.cloud.healthcare.imaging.dicomadapter.cstore.destination;

import org.dcm4che3.net.PDVInputStream;

import java.io.IOException;

public interface IDestinationClientFactory {
  DestinationHolder create(String callingAet, PDVInputStream inPdvStream) throws IOException;
}
