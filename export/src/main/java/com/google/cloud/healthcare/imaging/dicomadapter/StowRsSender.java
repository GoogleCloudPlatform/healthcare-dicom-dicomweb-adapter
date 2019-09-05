// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.healthcare.imaging.dicomadapter;

import com.github.danieln.multipart.MultipartInput;
import com.github.danieln.multipart.PartInput;
import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.Event;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.MonitoringService;
import com.google.common.io.CountingInputStream;
import com.google.pubsub.v1.PubsubMessage;

// StowRsSender sends DICOM to peer using DicomWeb STOW-RS protocol.
public class StowRsSender implements DicomSender {
  private IDicomWebClient sourceDicomWebClient;
  private IDicomWebClient sinkDicomWebClient;
  private String sinkDicomWebPath;

  StowRsSender(
      IDicomWebClient sourceDicomWebClient,
      IDicomWebClient sinkDicomWebClient,
      String sinkDicomWebPath) {
    this.sourceDicomWebClient = sourceDicomWebClient;
    this.sinkDicomWebClient = sinkDicomWebClient;
    this.sinkDicomWebPath = sinkDicomWebPath;
  }

  @Override
  public void send(PubsubMessage message) throws Exception {
    // Invoke WADO-RS to get bulk DICOM.
    String wadoUri = message.getData().toStringUtf8();
    MultipartInput resp = sourceDicomWebClient.wadoRs(wadoUri);
    PartInput part = resp.nextPart();
    if (part == null) {
      throw new IllegalArgumentException("WadoRS response has no parts");
    }

    // Send the STOW-RS request to peer DicomWeb service.
    CountingInputStream countingStream = new CountingInputStream(part.getInputStream());
    sinkDicomWebClient.stowRs(sinkDicomWebPath, countingStream);
    MonitoringService.addEvent(Event.BYTES, countingStream.getCount());
    if (resp.nextPart() != null) {
      System.err.println("WadoRS response had more than one part, ignoring other parts");
    }
  }
}
