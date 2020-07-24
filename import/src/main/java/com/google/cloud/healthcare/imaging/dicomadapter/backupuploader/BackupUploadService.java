package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.Event;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.MonitoringService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
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
  private String uploadFilePath;

  /**
   * Create BackupUploadService instance.
   * @param uploadFilePath path `where to save` backup files.
   * @param backupUploader DAO with simple write/read/remove operations.
   * @param delayCalculator util class for reSend tasks schedule delay calculation.
   */
  public BackupUploadService(
      String uploadFilePath, IBackupUploader backupUploader, DelayCalculator delayCalculator) {
    this.uploadFilePath = uploadFilePath;
    this.backupUploader = backupUploader;
    this.delayCalculator = delayCalculator;
    this.attemptsAmount = delayCalculator.getAttemptsAmount();
  }

  @Override
  public BackupState createBackup(byte[] backupData, String uniqueFileName)
      throws IBackupUploader.BackupException {
    backupUploader.doWriteBackup(backupData, uploadFilePath, uniqueFileName);
    log.debug("sopInstanceUID={}, backup saved.", uniqueFileName);
    return new BackupState(uploadFilePath, uniqueFileName, attemptsAmount);
  }

  @Override // todo: guard code from second method call
  public void startUploading(IDicomWebClient webClient, BackupState backupState)
      throws IBackupUploader.BackupException {
    byte[] bytes =
        backupUploader.doReadBackup(
            backupState.getDownloadFilePath(), backupState.getUniqueFileName());

    int uploadAttemptsCountdown = backupState.getAttemptsCountdown();
    if (uploadAttemptsCountdown > 0) {
      scheduleUploadWithDelay(webClient, bytes, backupState);
    }
  }

  @Override
  public void removeBackup(BackupState backupState) {
    try {
      backupUploader.removeBackup(backupState.getDownloadFilePath(), backupState.getUniqueFileName());
      log.debug("sopInstanceUID={}, removeBackup successful.", backupState.getUniqueFileName());
    } catch (IOException ex) {
      MonitoringService.addEvent(Event.CSTORE_BACKUP_ERROR);
      log.error("sopInstanceUID={}, removeBackup failed.", backupState.getUniqueFileName(), ex);
    }
  }

  private void scheduleUploadWithDelay(IDicomWebClient webClient, byte[] bytes, BackupState backupState) {
    String fileName = backupState.getUniqueFileName();
    if (backupState.decrement()) {
      int attemptNumber = attemptsAmount - backupState.getAttemptsCountdown();

      log.info("Trying to resend data, sopInstanceUID={}, attempt № {}. ", fileName, attemptNumber);
      CompletableFuture completableFuture =
          CompletableFuture.supplyAsync(
              () -> {
                try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                  webClient.stowRs(bais);
                  removeBackup(backupState);
                  log.debug(
                      "sopInstanceUID={}, bytes={},\n resend attempt № {}, - successful.", fileName, bytes, attemptNumber);
                } catch (IOException | IDicomWebClient.DicomWebException ex) {
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
                    scheduleUploadWithDelay(webClient, bytes, backupState);
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
