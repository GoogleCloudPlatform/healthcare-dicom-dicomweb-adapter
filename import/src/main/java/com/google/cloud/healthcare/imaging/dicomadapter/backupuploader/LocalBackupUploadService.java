package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import com.google.cloud.healthcare.IDicomWebClient;

public class LocalBackupUploadService extends AbstractBackupUploadService {
    public LocalBackupUploadService(String uploadFilePath, int attemptsCount) {
        super(uploadFilePath, attemptsCount);
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
