package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import com.google.cloud.healthcare.IDicomWebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public abstract class AbstractBackupUploadService implements IBackupUploadService, IBackupUploader {

    private final DelayCalculator delayCalculator;
    private final int attemptsAmount;

    private Logger log = LoggerFactory.getLogger(this.getClass());
    private String uploadFilePath;

    public AbstractBackupUploadService(String uploadFilePath, DelayCalculator delayCalculator) {
        this.uploadFilePath = uploadFilePath;
        this.delayCalculator = delayCalculator;
        this.attemptsAmount = delayCalculator.getAttemptsAmount();
    }

    @Override
    public BackupState createBackup(byte[] backupData) throws BackupExeption {
        doWriteBackup(backupData, uploadFilePath);
        return new BackupState(uploadFilePath, attemptsAmount);
    }

    @Override //todo: guard code from second method call
    public void startUploading(IDicomWebClient webClient, BackupState backupState) throws BackupExeption {
        byte[] bytes = doReadBackup(backupState.getDownloadFilePath());

        int uploadAttemptsCountdown = backupState.getAttemptsCountdown();
        if (uploadAttemptsCountdown > 0) {
            scheduleUploadWithDelay(webClient, bytes, backupState);
        }
    }

    private void scheduleUploadWithDelay(IDicomWebClient webClient, byte [] bytes, BackupState backupState) {
        if (backupState.decrement()) {
            int attemptNumber = attemptsAmount - backupState.getAttemptsCountdown();
            log.info("Trying to resend data. Attempt № {}. data={}", attemptNumber, bytes);
            CompletableFuture<Optional<Exception>> completableFuture = CompletableFuture.supplyAsync(() -> {
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                        webClient.stowRs(bais);
                        log.debug("Resend attempt № {} - successful.", attemptNumber);
                    } catch (IOException | IDicomWebClient.DicomWebException ex) {
                        log.error("Resend attempt № {} - failed.", attemptsAmount - backupState.getAttemptsCountdown(), ex);
                        return Optional.ofNullable(ex);
                    }
                    return Optional.empty();
                },
                CompletableFuture.delayedExecutor(
                        delayCalculator.getExponentialDelayMillis(backupState.getAttemptsCountdown()),
                        TimeUnit.MILLISECONDS)
            )
                .thenApply(r -> {
                    if (r.isEmpty()) { //backup upload success
                        removeBackup(backupState.getDownloadFilePath());
                    } else if (r.get() instanceof IDicomWebClient.DicomWebException) {
                        if (backupState.getAttemptsCountdown() > 0) {
                            scheduleUploadWithDelay(webClient, bytes, backupState);
                        }
                    }
                    return null;
                });
        } else {
            log.info("Backup resend attempts exhausted.");
        }
    }
}