package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

public interface IBackupUploadService {
    void createBackup(byte [] backupData);
    void startUploading();
}
