package com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.AetDictionary;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.BackupState;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.IBackupUploadService;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.IBackupUploader.BackupException;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest.sender.CStoreSender;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest.sender.CStoreSenderFactory;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.Event;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.MonitoringService;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class MultipleDestinationUploadService implements IMultipleDestinationUploadService {

  private Logger log = LoggerFactory.getLogger(MultipleDestinationUploadService.class);

  private CStoreSenderFactory cStoreSenderFactory;
  private IBackupUploadService backupUploadService;
  private int attemptsAmount;

  public MultipleDestinationUploadService(CStoreSenderFactory cStoreSenderFactory, IBackupUploadService backupUploadService, int attemptsAmount) {
    this.cStoreSenderFactory = cStoreSenderFactory;
    this.backupUploadService = backupUploadService;
    this.attemptsAmount = attemptsAmount;
  }

  @Override
  public void start(ImmutableList<IDicomWebClient> healthcareDestinations,
                    ImmutableList<AetDictionary.Aet> dicomDestinations,
                    InputStream inputStream,
                    String sopClassUID,
                    String sopInstanceUID) throws MultipleDestinationUploadServiceException {
    if (backupUploadService == null) {
      throw new IllegalArgumentException("backupUploadService is null. Some flags not set.");
    }

    List<Throwable> asyncUploadProcessingExceptions = new ArrayList<>();

    try {
      backupUploadService.createBackup(inputStream, sopInstanceUID);
    } catch (BackupException be) {
      MonitoringService.addEvent(Event.CSTORE_BACKUP_ERROR);
      log.error("{} processing failed.", this.getClass().getSimpleName(), be);
      throw new MultipleDestinationUploadServiceException(be);
    }

    List<CompletableFuture> uploadFutures = new ArrayList<>();

    CompletableFuture healthcareUploadFuture;
    for (IDicomWebClient healthcareDest: healthcareDestinations) {
      try {
        healthcareUploadFuture = backupUploadService.startUploading(
            healthcareDest,
            new BackupState(
                sopInstanceUID,
                attemptsAmount));

        uploadFutures.add(healthcareUploadFuture);
      } catch (BackupException be) {
        log.error("Async upload to healthcareDest task not started.", be);
        asyncUploadProcessingExceptions.add(be);
      }
    }

    if (dicomDestinations.isEmpty() == false) {
      CStoreSender cStoreSender = cStoreSenderFactory.create();

      CompletableFuture dicomUploadFuture;
      for (AetDictionary.Aet dicomDest : dicomDestinations) {
        try {
          dicomUploadFuture =
              backupUploadService.startUploading(
                  cStoreSender,
                  dicomDest,
                  sopInstanceUID,
                  sopClassUID,
                  new BackupState(sopInstanceUID, attemptsAmount));

          uploadFutures.add(dicomUploadFuture);
        } catch (BackupException be) {
          log.error("Async upload to dicomDest task not started.", be);
          asyncUploadProcessingExceptions.add(be);
        }
      }
    }

    for (CompletableFuture uploadFuture: uploadFutures) {
      try {
        uploadFuture.get();
      } catch (ExecutionException eex) {
        log.error("Exception on asyncUpload Job processing.", eex);
        asyncUploadProcessingExceptions.add(eex.getCause());
      } catch (InterruptedException ie) {
        log.error("CStoreSender task interrupted. Upload tasks canceled.", ie);
        Thread.currentThread().interrupt();
        throw new MultipleDestinationUploadServiceException(ie);
      }
    }

    if (asyncUploadProcessingExceptions.isEmpty()) {
      backupUploadService.removeBackup(sopInstanceUID);
    } else {
      log.error("Exception messages of the upload async jobs:\n{}",
          asyncUploadProcessingExceptions.stream().map(t -> t.getMessage()).collect(Collectors.joining("\n")));;

      throw new MultipleDestinationUploadServiceException(asyncUploadProcessingExceptions.get(0));
    }
  }
}
