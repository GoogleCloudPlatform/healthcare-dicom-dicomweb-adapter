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

    private final int attemptsCount;
    private int minUploadDelay;
    private int maxWaitingTimeBtwUploads;

    {
        minUploadDelay = 100;
        maxWaitingTimeBtwUploads = 5000;
    }

    private Logger log = LoggerFactory.getLogger(this.getClass());
    private String uploadFilePath;

    public AbstractBackupUploadService(String uploadFilePath, int attemptsCount) {
        this.uploadFilePath = uploadFilePath;
        this.attemptsCount = attemptsCount;
    }

    public AbstractBackupUploadService(String uploadFilePath, int attemptsCount,
                                      int minUploadDelay, int maxWaitingTimeBtwUploads) {
        this.uploadFilePath = uploadFilePath;
        this.attemptsCount = attemptsCount;
        this.minUploadDelay = minUploadDelay;
        this.maxWaitingTimeBtwUploads = maxWaitingTimeBtwUploads;
    }

    @Override
    public BackupState createBackup(byte[] backupData) throws BackupExeption {
        doWriteBackup(backupData, uploadFilePath);
        return new BackupState(uploadFilePath, attemptsCount);
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
            log.info("Trying to upload data. {} attempt. data={}", attemptsCount - backupState.getAttemptsCountdown(), bytes);
            CompletableFuture<Optional<Exception>> completableFuture = CompletableFuture.supplyAsync(() -> {
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                        webClient.stowRs(bais);
                    } catch (IOException | IDicomWebClient.DicomWebException ex) {
                        log.error("{} attempt of data upload is failed.", attemptsCount - backupState.getAttemptsCountdown(), ex);
                        return Optional.ofNullable(ex);
                    }
                    return Optional.empty();
                },
                CompletableFuture.delayedExecutor(
                        DelayCalculator.getExponentialDelayMillis(backupState.getAttemptsCountdown(),
                                attemptsCount, minUploadDelay, maxWaitingTimeBtwUploads),
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
        }
    }
}