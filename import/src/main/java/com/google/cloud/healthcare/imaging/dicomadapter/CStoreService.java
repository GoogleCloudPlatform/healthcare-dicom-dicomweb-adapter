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

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.IDicomWebClient.DicomWebException;
import com.google.cloud.healthcare.deid.redactor.DicomRedactor;
import com.google.cloud.healthcare.imaging.dicomadapter.backupuploader.BackupState;
import com.google.cloud.healthcare.imaging.dicomadapter.backupuploader.IBackupUploadService;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.Event;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.MonitoringService;
import com.google.common.io.CountingInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.atomic.AtomicReference;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.Transcoder;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to handle server-side C-STORE DICOM requests.
 */
public class CStoreService extends BasicCStoreSCP {

  private static Logger log = LoggerFactory.getLogger(CStoreService.class);

  private final IDicomWebClient defaultDicomWebClient;
  private final Map<DestinationFilter, IDicomWebClient> destinationMap;
  private final DicomRedactor redactor;
  private final String transcodeToSyntax;
  private final IBackupUploadService backupUploadService;

  CStoreService(IDicomWebClient defaultDicomWebClient,
      Map<DestinationFilter, IDicomWebClient> destinationMap,
      DicomRedactor redactor, String transcodeToSyntax, IBackupUploadService backupUploadService) {
    this.defaultDicomWebClient = defaultDicomWebClient;
    this.destinationMap =
        destinationMap != null && destinationMap.size() > 0 ? destinationMap : null;
    this.redactor = redactor;
    this.transcodeToSyntax =
        transcodeToSyntax != null && transcodeToSyntax.length() > 0 ? transcodeToSyntax : null;

    this.backupUploadService = backupUploadService;

    if(this.transcodeToSyntax != null) {
      log.info("Transcoding to: " + transcodeToSyntax);
    }
  }

  @Override
  protected void store(
      Association association,
      PresentationContext presentationContext,
      Attributes request,
      PDVInputStream inPdvStream,
      Attributes response)
      throws DicomServiceException, IOException {

      AtomicReference<BackupState> backupState = new AtomicReference<>();
      AtomicReference<IDicomWebClient> destinationClient = new AtomicReference<>();

      try {
        MonitoringService.addEvent(Event.CSTORE_REQUEST);

        String sopClassUID = request.getString(Tag.AffectedSOPClassUID);
        String sopInstanceUID = request.getString(Tag.AffectedSOPInstanceUID);
        String transferSyntax = presentationContext.getTransferSyntax();

        validateParam(sopClassUID, "AffectedSOPClassUID");
        validateParam(sopInstanceUID, "AffectedSOPInstanceUID");

        final CountingInputStream countingStream;

        if(destinationMap != null){
          DicomInputStream inDicomStream  = new DicomInputStream(inPdvStream);
          inDicomStream.mark(Integer.MAX_VALUE);
          Attributes attrs = inDicomStream.readDataset(-1, Tag.PixelData);
          inDicomStream.reset();

          countingStream = new CountingInputStream(inDicomStream);
          destinationClient.set(selectDestinationClient(association.getAAssociateAC().getCallingAET(), attrs));
        } else {
          countingStream = new CountingInputStream(inPdvStream);
          destinationClient.set(defaultDicomWebClient);
        }

        InputStream inWithHeader =
            DicomStreamUtil.dicomStreamWithFileMetaHeader(
                sopInstanceUID, sopClassUID, transferSyntax, countingStream);

        List<StreamProcessor> processorList = new ArrayList<>();
        if (redactor != null) {
          processorList.add(redactor::redact);
        }

        if(transcodeToSyntax != null && !transcodeToSyntax.equals(transferSyntax)) {
          processorList.add((inputStream, outputStream) -> {
            try (Transcoder transcoder = new Transcoder(inputStream)) {
              transcoder.setIncludeFileMetaInformation(true);
              transcoder.setDestinationTransferSyntax(transcodeToSyntax);
              transcoder.transcode((transcoder1, dataset) -> outputStream);
            }
          });
        }

        if (backupUploadService != null) {
          processorList.add((inputStream, outputStream) -> {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            StreamUtils.copy(inputStream, baos);
            byte[] bytes = baos.toByteArray();
            StreamUtils.copy(new ByteArrayInputStream(bytes), outputStream);

            backupState.set(backupUploadService.createBackup(bytes));
          });
        }

        processorList.add((inputStream, outputStream) -> {
          destinationClient.get().stowRs(inputStream);
        });

        processStream(association.getApplicationEntity().getDevice().getExecutor(),
            inWithHeader, processorList);

        response.setInt(Tag.Status, VR.US, Status.Success);
        MonitoringService.addEvent(Event.CSTORE_BYTES, countingStream.getCount());
      } catch (DicomWebException e) {
      if (backupUploadService != null) {
        backupUploadService.startUploading(destinationClient.get(), backupState.get());
        // todo: monitoring?
        // todo: logging?
      } else {
        reportError(e);
        DicomServiceException serviceException = new DicomServiceException(e.getStatus(), e);
        serviceException.setErrorComment(e.getMessage());
        throw serviceException;
      }
    } catch (DicomServiceException e) {
      reportError(e);
      throw e;
    } catch (Throwable e) {
      reportError(e);
      throw new DicomServiceException(Status.ProcessingFailure, e);
    }
  }

  private void reportError(Throwable e) {
    MonitoringService.addEvent(Event.CSTORE_ERROR);
    log.error("C-STORE request failed: ", e);
  }

  private void validateParam(String value, String name) throws DicomServiceException {
    if (value == null || value.trim().length() == 0) {
      throw new DicomServiceException(Status.CannotUnderstand, "Mandatory tag empty: " + name);
    }
  }

  private IDicomWebClient selectDestinationClient(String callingAet, Attributes attrs){
    for(DestinationFilter filter: destinationMap.keySet()){
      if(filter.matches(callingAet, attrs)){
        return destinationMap.get(filter);
      }
    }
    return defaultDicomWebClient;
  }

  private void processStream(Executor underlyingExecutor, InputStream inputStream,
      List<StreamProcessor> processorList) throws Throwable {
    if (processorList.size() == 1) {
      StreamProcessor singleProcessor = processorList.get(0);
      singleProcessor.process(inputStream, null);
    } else if (processorList.size() > 1) {
      List<StreamCallable> callableList = new ArrayList<>();

      PipedOutputStream pdvPipeOut = new PipedOutputStream();
      InputStream nextInputStream = new PipedInputStream(pdvPipeOut);
      for(int i=0; i < processorList.size(); i++){
        StreamProcessor processor = processorList.get(i);
        InputStream processorInput = nextInputStream;
        OutputStream processorOutput = null;

        if(i < processorList.size() - 1) {
          PipedOutputStream pipeOut = new PipedOutputStream();
          processorOutput = pipeOut;
          nextInputStream = new PipedInputStream(pipeOut);
        }

        callableList.add(new StreamCallable(processorInput, processorOutput, processor));
      }

      ExecutorCompletionService<Void> ecs = new ExecutorCompletionService<>(underlyingExecutor);
      for(StreamCallable callable : callableList){
        ecs.submit(callable);
      }

      try (pdvPipeOut) {
        // PDVInputStream is thread-locked
        StreamUtils.copy(inputStream, pdvPipeOut);
      } catch (IOException e) {
        // causes or is caused by exception in callables, no need to throw this up
        log.trace("Error copying inputStream to pdvPipeOut", e);
      }

      try {
        for (int i = 0; i < callableList.size(); i++) {
          ecs.take().get();
        }
      } catch (ExecutionException e) {
        // here we need to recognize that it`s right web Ex //todo
//        if (backupUploadService != null) {
//          backupUploadService.startUploading();
//        }
        throw e.getCause();
      }
    }
  }

  @FunctionalInterface
  private interface StreamProcessor {
    void process(InputStream inputStream, OutputStream outputStream) throws Exception;
  }

  private static class StreamCallable implements Callable<Void> {
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final StreamProcessor processor;

    public StreamCallable(InputStream inputStream, OutputStream outputStream,
        StreamProcessor processor) {
      this.inputStream = inputStream;
      this.outputStream = outputStream;
      this.processor = processor;
    }

    @Override
    public Void call() throws Exception {
      try (inputStream) {
        try (outputStream) {
          processor.process(inputStream, outputStream);
        }
      }
      return null;
    }
  }
}
