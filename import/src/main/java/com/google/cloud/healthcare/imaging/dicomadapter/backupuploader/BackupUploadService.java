package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.Event;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.MonitoringService;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BackupUploadService implements IBackupUploadService {

  private final DelayCalculator delayCalculator;
  private final IBackupUploader backupUploader;
  private final int attemptsAmount;

  private Logger log = LoggerFactory.getLogger(this.getClass());

  /**
   * Create BackupUploadService instance.
   * @param backupUploader DAO with simple write/read/remove operations.
   * @param delayCalculator util class for reSend tasks schedule delay calculation.
   */
  public BackupUploadService(IBackupUploader backupUploader, DelayCalculator delayCalculator) {
    this.backupUploader = backupUploader;
    this.delayCalculator = delayCalculator;
    this.attemptsAmount = delayCalculator.getAttemptsAmount();
  }

  @Override
  public BackupState createBackup(InputStream inputStream, String uniqueFileName) throws IBackupUploader.BackupException {
    backupUploader.doWriteBackup(inputStream, uniqueFileName);
    log.debug("sopInstanceUID={}, backup saved.", uniqueFileName);
    return new BackupState(uniqueFileName, attemptsAmount);
  }

  @Override
  public InputStream getBackupStream(String uniqueFileName) throws IBackupUploader.BackupException {
    return backupUploader.doReadBackup(uniqueFileName);
  }

  @Override // todo: guard code from second method call
  public void startUploading(IDicomWebClient webClient, BackupState backupState) throws IBackupUploader.BackupException {
    int uploadAttemptsCountdown = backupState.getAttemptsCountdown();
    if (uploadAttemptsCountdown > 0) {
      scheduleUploadWithDelay(webClient, backupState);
    }
  }

  @Override
  public void removeBackup(BackupState backupState) {
    try {
      backupUploader.doRemoveBackup(backupState.getUniqueFileName());
      log.debug("sopInstanceUID={}, removeBackup successful.", backupState.getUniqueFileName());
    } catch (IOException ex) {
      MonitoringService.addEvent(Event.CSTORE_BACKUP_ERROR);
      log.error("sopInstanceUID={}, removeBackup failed.", backupState.getUniqueFileName(), ex);
    }
  }

  private void scheduleUploadWithDelay(IDicomWebClient webClient, BackupState backupState) {
    String fileName = backupState.getUniqueFileName();
    if (backupState.decrement()) {
      int attemptNumber = attemptsAmount - backupState.getAttemptsCountdown();

      log.info("Trying to resend data, sopInstanceUID={}, attempt № {}. ", fileName, attemptNumber);
      CompletableFuture completableFuture =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  InputStream inputStream = backupUploader.doReadBackup(backupState.getUniqueFileName());
                  webClient.stowRs(inputStream);
                  removeBackup(backupState);
                  log.debug(
                      "sopInstanceUID={}, resend attempt № {}, - successful.", fileName, attemptNumber);
                } catch (IBackupUploader.BackupException | IDicomWebClient.DicomWebException ex) {
                  log.error("sopInstanceUID={}, resend attempt № {} - failed.",
                      fileName, attemptsAmount - backupState.getAttemptsCountdown(), ex);
                  throw new CompletionException(ex);
                }
                return null;
              },
              CompletableFuture.delayedExecutor(
                  delayCalculator.getExponentialDelayMillis(backupState.getAttemptsCountdown()),
                  TimeUnit.MILLISECONDS))
          .exceptionally(
              ex -> {
                if (ex.getCause() instanceof IDicomWebClient.DicomWebException) {
                  if (backupState.getAttemptsCountdown() > 0) {
                    scheduleUploadWithDelay(webClient, backupState);
                  } else {
                    log.debug("sopInstanceUID={}, No resend attempt left.", fileName);
                  }
                } else {
                  MonitoringService.addEvent(Event.CSTORE_BACKUP_ERROR);
                  log.error("sopInstanceUID={}, read backup failed.", fileName, ex.getCause());
                }
                return null;
              });
    } else {
      log.info("sopInstanceUID={}, Backup resend attempts exhausted.", fileName);
    }
  }
}
