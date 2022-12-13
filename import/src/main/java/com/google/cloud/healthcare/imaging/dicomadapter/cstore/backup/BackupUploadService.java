package com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup;

import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.IDicomWebClient.DicomWebException;
import com.google.cloud.healthcare.imaging.dicomadapter.AetDictionary;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.IBackupUploader.BackupException;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest.sender.CStoreSender;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.Event;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.MonitoringService;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackupUploadService implements IBackupUploadService {
  private final static Executor uploadThreadPool = Executors.newCachedThreadPool();
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
    log.debug("fileName={}, backup saved.", uniqueFileName);
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
  public CompletableFuture startUploading(CStoreSender cStoreSender, AetDictionary.Aet target, String sopInstanceUid, String sopClassUid,
                                          BackupState backupState) throws BackupException {
    return scheduleUploadWithDelay(
        backupState,
        new DicomDestinationUploadAsyncJob(
            cStoreSender,
            backupState,
            target,
            sopInstanceUid,
            sopClassUid),
        0);
  }


  @Override
  public void removeBackup(String fileName) {
    try {
      backupUploader.doRemoveBackup(fileName);
      log.debug("fileName={}, removeBackup successful.", fileName);
    } catch (IOException ex) {
      MonitoringService.addEvent(Event.CSTORE_BACKUP_ERROR);
      log.error("fileName={}, removeBackup failed.", fileName, ex);
    }
  }

  public abstract class UploadAsyncJob implements Runnable {
    protected BackupState backupState;
    protected String uniqueFileName;
    protected int attemptNumber;

    public UploadAsyncJob(BackupState backupState) {
      this.backupState = backupState;
      this.uniqueFileName = backupState.getUniqueFileName();
      this.attemptNumber = attemptsAmount + 2 - backupState.getAttemptsCountdown();
    }

    protected void logUploadFailed(Exception e) {
      log.error(
          "fileName={}, upload attempt № {} - failed.", uniqueFileName, attemptNumber, e);
    }

    protected void logSuccessUpload() {
      log.debug(
          "fileName={}, upload attempt № {}, - successful.", uniqueFileName, attemptNumber);
    }

    protected InputStream readBackupExceptionally() throws CompletionException {
      try {
        return backupUploader.doReadBackup(uniqueFileName);
      } catch (BackupException ex) {
        MonitoringService.addEvent(Event.CSTORE_BACKUP_ERROR);
        log.error("fileName={}, read backup failed.", uniqueFileName, ex.getCause());
        throw new CompletionException(ex);
      }
    }
  }

  public class DicomDestinationUploadAsyncJob extends UploadAsyncJob {

    private CStoreSender cStoreSender;
    private AetDictionary.Aet target;
    private String sopInstanceUid;
    private String sopClassUid;

    public DicomDestinationUploadAsyncJob(
        CStoreSender cStoreSender,
        BackupState backupState,
        AetDictionary.Aet target,
        String sopInstanceUid,
        String sopClassUid) {
      super(backupState);
      this.cStoreSender = cStoreSender;
      this.target = target;
      this.sopInstanceUid = sopInstanceUid;
      this.sopClassUid = sopClassUid;
    }

    @Override
    public void run() {
      try {
        InputStream inputStream = readBackupExceptionally();
        cStoreSender.cstore(target, sopInstanceUid, sopClassUid, inputStream);
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
                    sopClassUid),
                delayCalculator.getExponentialDelayMillis(
                    backupState.getAttemptsCountdown(),
                    attemptsAmount))
                .get();
          } catch (BackupException | ExecutionException | InterruptedException ex) {
            throw new CompletionException(ex);
          }
        } else {
          MonitoringService.addEvent(Event.CSTORE_ERROR);
          throwOnNoResendAttemptsLeft(null, uniqueFileName);
        }
      } catch (InterruptedException ie) {
        log.error("cStoreSender.cstore interrupted. Runnable task canceled.", ie);
        Thread.currentThread().interrupt();
        throw new CompletionException(ie);
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

        // If we get a conflict, and want to overwrite, then delete and retry. This
        // could run several times if the instance is being recreated by others (non-atomic).
        // Note that if the md5 of the instances match, we will get a HTTP 200. We will only get
        // an HTTP 409 when there is a difference in the MD5 between the instances
        int httpCode = dwe.getHttpStatus();
        Boolean isHttpConflictAndRetry =
            httpCode == HttpStatusCodes.STATUS_CODE_CONFLICT && webClient.getStowOverwrite();
        if (isHttpConflictAndRetry) {
          try {
            InputStream deleteInputStream = readBackupExceptionally();
            webClient.delete(deleteInputStream);
            log.debug("stowOverwrite: instance deleted");
          } catch (DicomWebException innerDwe) {
            MonitoringService.addEvent(Event.CSTORE_ERROR);
            throw new CompletionException(innerDwe);
          }
        }

        if (filterHttpCode(httpCode) || isHttpConflictAndRetry) {
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
          } else {
            MonitoringService.addEvent(Event.CSTORE_ERROR);
            throwOnNoResendAttemptsLeft(dwe, uniqueFileName);
          }
        } else {
          MonitoringService.addEvent(Event.CSTORE_ERROR);
          throwOnHttpFilterFail(dwe, dwe.getHttpStatus());
        }
      }
    }
  }

  private CompletableFuture scheduleUploadWithDelay(BackupState backupState, Runnable uploadJob, long delayMillis) throws BackupException {
    String uniqueFileName = backupState.getUniqueFileName();
    log.info("Trying to send data, fileName={}, attempt № {}. ",
        uniqueFileName,
        2 + attemptsAmount - backupState.getAttemptsCountdown());
    if (backupState.decrement()) {
      return CompletableFuture.runAsync(
          uploadJob,
          CompletableFuture.delayedExecutor(
            delayMillis,
            TimeUnit.MILLISECONDS,
            uploadThreadPool));
    } else {
      MonitoringService.addEvent(Event.CSTORE_ERROR);
      throw getNoResendAttemptLeftException(null, uniqueFileName);
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

  private void throwOnNoResendAttemptsLeft(DicomWebException dwe, String uniqueFileName) throws CompletionException {
    throw new CompletionException(getNoResendAttemptLeftException(dwe, uniqueFileName));
  }

  private BackupException getNoResendAttemptLeftException(DicomWebException dwe, String uniqueFileName) {
    String errorMessage = "fileName=" + uniqueFileName + ". No upload attempt left.";
    log.debug(errorMessage);
    if (dwe != null) {
      return new BackupException(dwe.getStatus(), dwe, errorMessage);
    } else {
      return new BackupException(errorMessage);
    }
  }
}
