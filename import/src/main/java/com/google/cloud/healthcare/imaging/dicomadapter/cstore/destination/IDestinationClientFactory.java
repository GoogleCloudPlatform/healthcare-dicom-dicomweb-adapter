package com.google.cloud.healthcare.imaging.dicomadapter.cstore.destination;

import java.io.IOException;
import java.io.InputStream;

public interface IDestinationClientFactory {
  DestinationHolder create(String callingAet, InputStream inPdvStream) throws IOException;
}
