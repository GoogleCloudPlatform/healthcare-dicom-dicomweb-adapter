package com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.IDicomWebClient.DicomWebException;
import com.google.cloud.healthcare.imaging.dicomadapter.AetDictionary;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.BackupState;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.IBackupUploader;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest.sender.CStoreSender;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.Event;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.MonitoringService;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.IBackupUploader.BackupException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackupUploadService implements IBackupUploadService {

  private final DelayCalculator delayCalculator;
  private final IBackupUploader backupUploader;
  private final ImmutableList<Integer> httpErrorCodesToRetry;
  private final int attemptsAmount;

  private Logger log = LoggerFactory.getLogger(this.getClass());

  /**
   * Create BackupUploadService instance.
   * @param backupUploader DAO with simple write/read/remove operations.
   * @param delayCalculator util class for reSend tasks schedule delay calculation.
   */
  public BackupUploadService(IBackupUploader backupUploader, Integer attemptsAmount, ImmutableList<Integer> httpErrorCodesToRetry,
      DelayCalculator delayCalculator) {
    this.backupUploader = backupUploader;
    this.attemptsAmount = attemptsAmount;
    this.httpErrorCodesToRetry = httpErrorCodesToRetry;
    this.delayCalculator = delayCalculator;
  }

  @Override
  public void createBackup(InputStream inputStream, String uniqueFileName) throws BackupException {
    backupUploader.doWriteBackup(inputStream, uniqueFileName);
    log.debug("sopInstanceUID={}, backup saved.", uniqueFileName);
  }

  @Override
  public CompletableFuture startUploading(IDicomWebClient webClient, BackupState backupState) throws BackupException {
    return scheduleUploadWithDelay(
        backupState,
        new HealthcareDestinationUploadAsyncJob(
            webClient,
            backupState),
        0);
  }

  @Override
  public CompletableFuture startUploading(CStoreSender cStoreSender, AetDictionary.Aet target, String sopInstanceUid, String sopClassUidBackupState,
                                          BackupState backupState) throws BackupException {
    return scheduleUploadWithDelay(
        backupState,
        new DicomDestinationUploadAsyncJob(
            cStoreSender,
            backupState,
            target,
            sopInstanceUid,
            sopClassUidBackupState),
        0);
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

  public abstract class UploadAsyncJob implements Runnable {
    protected BackupState backupState;
    protected String uniqueFileName;
    protected int attemptNumber;

    public UploadAsyncJob(BackupState backupState) {
      this.backupState = backupState;
      this.uniqueFileName = backupState.getUniqueFileName();
      this.attemptNumber = attemptsAmount - backupState.getAttemptsCountdown();
    }

    protected void logUploadFailed(Exception e) {
      log.error("sopInstanceUID={}, resend attempt {} - failed.", uniqueFileName, attemptNumber + 1, e);
    }

    protected void logSuccessUpload() {
      log.debug("sopInstanceUID={}, resend attempt {}, - successful.", uniqueFileName, attemptNumber + 1);
    }

    protected InputStream readBackupExceptionally() throws CompletionException {
      try {
        return backupUploader.doReadBackup(uniqueFileName);
      } catch (BackupException ex) {
        log.error("sopInstanceUID={}, read backup failed.", uniqueFileName, ex.getCause());
        throw new CompletionException(ex);
      }
    }
  }

  public class DicomDestinationUploadAsyncJob extends UploadAsyncJob {

    private CStoreSender cStoreSender;
    private AetDictionary.Aet target;
    private String sopInstanceUid;
    private String sopClassUidBackupState;

    public DicomDestinationUploadAsyncJob(
        CStoreSender cStoreSender,
        BackupState backupState,
        AetDictionary.Aet target,
        String sopInstanceUid,
        String sopClassUidBackupState) {
      super(backupState);
      this.cStoreSender = cStoreSender;
      this.target = target;
      this.sopInstanceUid = sopInstanceUid;
      this.sopClassUidBackupState = sopClassUidBackupState;
    }

    @Override
    public void run() {
      try {
        InputStream inputStream = readBackupExceptionally();
        cStoreSender.cstore(target, sopInstanceUid, sopClassUidBackupState, inputStream);
        logSuccessUpload();
      } catch (IOException io) {
        logUploadFailed(io);

        if (backupState.getAttemptsCountdown() > 0) {
            try {
              scheduleUploadWithDelay(
                  backupState,
                  new DicomDestinationUploadAsyncJob(
                      cStoreSender,
                      backupState,
                      target,
                      sopInstanceUid,
                      sopClassUidBackupState),
                  delayCalculator.getExponentialDelayMillis(
                      backupState.getAttemptsCountdown(),
                      attemptsAmount))
                  .get();

            } catch (BackupException | ExecutionException | InterruptedException ex) {
              throw new CompletionException(ex);
            }
            MonitoringService.addEvent(Event.CSTORE_BACKUP_ERROR);
          } else {
            throwOnNoResendAttemptsLeft(null, uniqueFileName);
          }
      } catch (InterruptedException ie) {
        log.error("cStoreSender.cstore interrupted. Runnable task canceled.", ie);
        Thread.currentThread().interrupt();
        throw new CompletionException(new BackupException(ie));
      }
    }
  }

  public class HealthcareDestinationUploadAsyncJob extends UploadAsyncJob {

    private IDicomWebClient webClient;

    public HealthcareDestinationUploadAsyncJob(IDicomWebClient webClient, BackupState backupState) {
      super(backupState);
      this.webClient = webClient;
    }

    @Override
    public void run() {
      try {
        InputStream inputStream = readBackupExceptionally();
        webClient.stowRs(inputStream);
        logSuccessUpload();
      } catch (DicomWebException dwe) {
        logUploadFailed(dwe);

        if (filterHttpCode(dwe.getHttpStatus())) {
          if (backupState.getAttemptsCountdown() > 0) {
            try {
              scheduleUploadWithDelay(
                  backupState,
                  new HealthcareDestinationUploadAsyncJob(webClient, backupState),
                  delayCalculator.getExponentialDelayMillis(backupState.getAttemptsCountdown(), attemptsAmount))
                      .get();

            } catch (BackupException | ExecutionException | InterruptedException ex) {
              throw new CompletionException(ex);
            }
            MonitoringService.addEvent(Event.CSTORE_BACKUP_ERROR);
          } else {
            throwOnNoResendAttemptsLeft(dwe, uniqueFileName);
          }
        } else {
          throwOnHttpFilterFail(dwe, dwe.getHttpStatus());
        }
      }
    }
    private boolean filterHttpCode(Integer actualHttpStatus) {
      return actualHttpStatus >= 500 || httpErrorCodesToRetry.contains(actualHttpStatus);
    }

    private void throwOnHttpFilterFail(DicomWebException dwe, int httpCode) throws CompletionException {
      String errorMessage = "Not retried due to HTTP code=" + httpCode;
      log.debug(errorMessage);
      throw new CompletionException(new BackupException(dwe.getStatus(), dwe, errorMessage));
    }
  }

  private CompletableFuture scheduleUploadWithDelay(BackupState backupState, Runnable uploadJob, long delayMillis) throws BackupException {
    String uniqueFileName = backupState.getUniqueFileName();
    if (backupState.decrement()) {
      log.info("Trying to send data, sopInstanceUID={}, attempt № {}. ",
          uniqueFileName,
          1 + attemptsAmount - backupState.getAttemptsCountdown());

      return CompletableFuture.runAsync(
          uploadJob,
          CompletableFuture.delayedExecutor(
              delayMillis,
              TimeUnit.MILLISECONDS));
    } else {
      throw getNoResendAttemptLeftException(null, uniqueFileName);
    }
  }

  private void throwOnNoResendAttemptsLeft(DicomWebException dwe, String uniqueFileName) throws CompletionException {
    throw new CompletionException(getNoResendAttemptLeftException(dwe, uniqueFileName));
  }

  private BackupException getNoResendAttemptLeftException(DicomWebException dwe, String uniqueFileName) {
    String errorMessage = "sopInstanceUID=" + uniqueFileName + ". No resend attempt left.";
    log.debug(errorMessage);
    if (dwe != null) {
      return new BackupException(dwe.getStatus(), dwe, errorMessage);
    } else {
      return new BackupException(errorMessage);
    }
  }
}
