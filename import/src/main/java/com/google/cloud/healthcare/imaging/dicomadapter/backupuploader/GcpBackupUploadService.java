package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import com.google.cloud.healthcare.IDicomWebClient;

public class GcpBackupUploadService extends AbstractBackupUploadService {
    public GcpBackupUploadService(String uploadFilePath, int attemptsCount,
                                  int minUploadDelay, int maxWaitingTimeBtwUploads) {
        super(uploadFilePath, attemptsCount, minUploadDelay, maxWaitingTimeBtwUploads);
    }

    @Override
    public void doWriteBackup(byte[] backupData, String uploadFilePath) {
        // todo: implement_me
    }

    @Override
    public byte[] doReadBackup(String uploadFilePath) {
        return new byte[0];
        // todo: implement_me
    }

    @Override
    public void removeBackup(String uploadFilePath) {
        // todo: implement_me
    }
}
