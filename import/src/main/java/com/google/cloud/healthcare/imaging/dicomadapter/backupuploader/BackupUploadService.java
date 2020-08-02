package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.Event;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.MonitoringService;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
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
    if (backupState.getAttemptsCountdown() > 0) {
      scheduleUploadWithDelay(webClient, backupState);
    }
  }

  @Override
  public void removeBackup(String fileName) {
    try {
      backupUploader.doRemoveBackup(fileName);
      log.debug("sopInstanceUID={}, removeBackup successful.", fileName);
    } catch (IOException ex) {
      MonitoringService.addEvent(Event.CSTORE_BACKUP_ERROR);
      log.error("sopInstanceUID={}, removeBackup failed.", fileName, ex);
    }
  }

  private void scheduleUploadWithDelay(IDicomWebClient webClient, BackupState backupState) throws IBackupUploader.BackupException {
    String uniqueFileName = backupState.getUniqueFileName();
    if (backupState.decrement()) {
      int attemptNumber = attemptsAmount - backupState.getAttemptsCountdown();

      log.info("Trying to resend data, sopInstanceUID={}, attempt № {}. ", uniqueFileName, attemptNumber);
      CompletableFuture completableFuture =
          CompletableFuture.runAsync(
              () -> {
                try {
                  InputStream inputStream = readBackupExceptionally(uniqueFileName);
                  webClient.stowRs(inputStream);
                  removeBackup(uniqueFileName);
                  log.debug("sopInstanceUID={}, resend attempt № {}, - successful.", uniqueFileName, attemptNumber);
                } catch (IDicomWebClient.DicomWebException ex) {
                  log.error("sopInstanceUID={}, resend attempt № {} - failed.", uniqueFileName, attemptNumber, ex);

                  if (backupState.getAttemptsCountdown() > 0 /* + todo: http_code_filter*/) {
                    scheduleUploadWithDelayExceptionally(webClient, backupState);
                    MonitoringService.addEvent(Event.CSTORE_BACKUP_ERROR);
                  } else {
                    throwOnNoResendAttemptsLeft(uniqueFileName);
                  }
                }
              },
              CompletableFuture.delayedExecutor(
                  delayCalculator.getExponentialDelayMillis(backupState.getAttemptsCountdown()),
                  TimeUnit.MILLISECONDS));

      try {
        completableFuture.get();
      } catch (InterruptedException ie) {
        log.error("scheduleUploadWithDelay Runnable task canceled.", ie);
        Thread.currentThread().interrupt();
      } catch (ExecutionException eex) {
        throw new IBackupUploader.BackupException(eex.getCause());
      }
    } else {
      throwOnNoResendAttemptsLeft(uniqueFileName);
    }
  }

  private void scheduleUploadWithDelayExceptionally(IDicomWebClient webClient, BackupState backupState) {
    try {
      scheduleUploadWithDelay(webClient, backupState);
      } catch (IBackupUploader.BackupException ex) {
      throw new CompletionException(ex);
    }
  }

  private void throwOnNoResendAttemptsLeft(String uniqueFileName) throws CompletionException {
    log.debug("sopInstanceUID={}, No resend attempt left.", uniqueFileName);
    throw new CompletionException(
        new IBackupUploader.BackupException("sopInstanceUID=" + uniqueFileName + ". No resend attempt left."));
  }

  private InputStream readBackupExceptionally(String fileName) throws CompletionException {
    try {
      return backupUploader.doReadBackup(fileName);
    } catch (IBackupUploader.BackupException ex) {
      log.error("sopInstanceUID={}, read backup failed.", fileName, ex.getCause());
      throw new CompletionException(ex);
    }
  }
}
