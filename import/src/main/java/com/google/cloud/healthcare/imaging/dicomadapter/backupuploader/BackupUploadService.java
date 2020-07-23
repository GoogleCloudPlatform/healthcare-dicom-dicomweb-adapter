package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.Event;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.MonitoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

public class BackupUploadService implements IBackupUploadService {

  private final DelayCalculator delayCalculator;
  private final IBackupUploader backupUploader;
  private final int attemptsAmount;

  private Logger log = LoggerFactory.getLogger(this.getClass());
  private String uploadFilePath;

  public BackupUploadService(
      String uploadFilePath, IBackupUploader backupUploader, DelayCalculator delayCalculator) {
    this.uploadFilePath = uploadFilePath;
    this.backupUploader = backupUploader;
    this.delayCalculator = delayCalculator;
    this.attemptsAmount = delayCalculator.getAttemptsAmount();
  }

  @Override
  public BackupState createBackup(byte[] backupData, String uniqueFileName)
      throws IBackupUploader.BackupExeption {
    backupUploader.doWriteBackup(backupData, uploadFilePath, uniqueFileName);
    log.debug("sopInstanceUID={}, backup saved.");
    return new BackupState(uploadFilePath, uniqueFileName, attemptsAmount);
  }

  @Override // todo: guard code from second method call
  public void startUploading(IDicomWebClient webClient, BackupState backupState)
      throws IBackupUploader.BackupExeption {
    byte[] bytes =
        backupUploader.doReadBackup(
            backupState.getDownloadFilePath(), backupState.getUniqueFileName());

    int uploadAttemptsCountdown = backupState.getAttemptsCountdown();
    if (uploadAttemptsCountdown > 0) {
      scheduleUploadWithDelay(webClient, bytes, backupState);
    }
  }

  private void scheduleUploadWithDelay(
      IDicomWebClient webClient, byte[] bytes, BackupState backupState) {
    String fileName = backupState.getUniqueFileName();
    if (backupState.decrement()) {
      int attemptNumber = attemptsAmount - backupState.getAttemptsCountdown();

      log.info("Trying to resend data, sopInstanceUID={}, attempt № {}. ", fileName, attemptNumber);
      CompletableFuture<Void> completableFuture =
          CompletableFuture.supplyAsync(
              () -> {
                try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                  webClient.stowRs(bais);
                  log.debug(
                      "sopInstanceUID={}, resend attempt № {}, - successful.",
                      fileName,
                      attemptNumber);
                } catch (IOException | IDicomWebClient.DicomWebException ex) {
                  log.error(
                      "sopInstanceUID={}, resend attempt № {} - failed.",
                      fileName,
                      attemptsAmount - backupState.getAttemptsCountdown(),
                      ex);
                  throw new CompletionException(ex);
                }
                return null;
              },
              CompletableFuture.delayedExecutor(
                  delayCalculator.getExponentialDelayMillis(backupState.getAttemptsCountdown()),
                  TimeUnit.MILLISECONDS))
          .exceptionally(
              ex -> {
                if (ex.getCause() instanceof IDicomWebClient.DicomWebException
                    && backupState.getAttemptsCountdown() > 0) {
                  scheduleUploadWithDelay(webClient, bytes, backupState);
                } else {
                  MonitoringService.addEvent(Event.CSTORE_BACKUP_ERROR);
                  log.error("sopInstanceUID={}, read backup failed.", fileName, ex.getCause());
                }
                return null;
              })
          .thenAccept(
              action -> {
                try {
                  backupUploader.removeBackup(
                      backupState.getDownloadFilePath(), backupState.getUniqueFileName());
                } catch (IOException ioex) {
                  throw new CompletionException(ioex);
                }
              })
          .exceptionally(
              removeEx -> {
                MonitoringService.addEvent(Event.CSTORE_BACKUP_ERROR);
                log.error(
                    "sopInstanceUID={}, removeBackup failed.", fileName, removeEx.getCause());
                return null;
              });
    } else {
      log.info("sopInstanceUID={}, Backup resend attempts exhausted.", fileName);
    }
  }
}
