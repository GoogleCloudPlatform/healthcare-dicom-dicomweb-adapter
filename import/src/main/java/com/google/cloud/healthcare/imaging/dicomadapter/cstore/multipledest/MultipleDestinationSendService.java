package com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.AetDictionary;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.BackupState;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.BackupUploadService;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.IBackupUploader.BackupException;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest.sender.CStoreSender;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest.sender.CStoreSenderFactory;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class MultipleDestinationSendService implements IMultipleDestinationSendService {

  private Logger log = LoggerFactory.getLogger(MultipleDestinationSendService.class);

  private CStoreSenderFactory cStoreSenderFactory;
  private BackupUploadService backupUploadService;
  private int attemptsAmount;

  public MultipleDestinationSendService(CStoreSenderFactory cStoreSenderFactory, BackupUploadService backupUploadService, int attemptsAmount) {
    this.cStoreSenderFactory = cStoreSenderFactory;
    this.backupUploadService = backupUploadService;
    this.attemptsAmount = attemptsAmount;
  }

  @Override
  public void start(ImmutableList<IDicomWebClient> healthcareDestinations,
                    ImmutableList<AetDictionary.Aet> dicomDestinations,
                    InputStream inputStream,
                    String sopClassUID,
                    String backupFileName) throws BackupException, MultipleDestinationSendServiceException {
    CStoreSender cStoreSender = cStoreSenderFactory.create();

    if (backupUploadService == null) {
      throw new IllegalArgumentException("backupUploadService is null. Some flags not set.");
    }

    List<Throwable> asyncSendProcessingExceptions = new ArrayList<>();

    try {
      backupUploadService.createBackup(inputStream, backupFileName);
    } catch (BackupException be) {
      log.error("MultipleDestinationSendService processing failed.", be);
      throw be;
    }

    List<CompletableFuture> sendFutures = new ArrayList<>();

    CompletableFuture healthcareSendFuture;
    for (IDicomWebClient healthcareDest: healthcareDestinations) {
      try {
        healthcareSendFuture = backupUploadService.startUploading(
            healthcareDest,
            new BackupState(
                backupFileName,
                attemptsAmount));

        sendFutures.add(healthcareSendFuture);
      } catch (BackupException be) {
        log.error("Async send to healthcareDest task not started.", be);
        asyncSendProcessingExceptions.add(be);
      }

    }

    CompletableFuture dicomSendFuture;
    for (AetDictionary.Aet dicomDest: dicomDestinations) {
      try {
        dicomSendFuture = backupUploadService.startUploading(
            cStoreSender,
            dicomDest,
            backupFileName,
            sopClassUID,
            new BackupState(
                backupFileName,
                attemptsAmount));

        sendFutures.add(dicomSendFuture);
      } catch (BackupException be) {
        log.error("Async send to dicomDest task not started.", be);
        asyncSendProcessingExceptions.add(be);
      }
    }

    for (CompletableFuture sendFuture: sendFutures) {
      try {
        sendFuture.get();
      } catch (ExecutionException eex) {
        log.error("Exception on asyncSend Job processing.", eex);
        asyncSendProcessingExceptions.add(eex.getCause());
      } catch (InterruptedException ie) {
        log.error("CStoreSender task interrupted. Send tasks canceled.", ie);
        Thread.currentThread().interrupt();
        throw new MultipleDestinationSendServiceException(ie);
      }
    }

    if (asyncSendProcessingExceptions.isEmpty()) {
      backupUploadService.removeBackup(backupFileName);
    } else {
      log.error("Exception messages of the send async jobs:\n{}",
          asyncSendProcessingExceptions.stream().map(t -> t.getMessage()).collect(Collectors.joining("\n")));;

      throw new MultipleDestinationSendServiceException(asyncSendProcessingExceptions.get(0));
    }
  }

  public static class MultipleDestinationSendServiceException extends Exception {
    public MultipleDestinationSendServiceException(Throwable cause) {
      super(cause);
    }
  }
}
