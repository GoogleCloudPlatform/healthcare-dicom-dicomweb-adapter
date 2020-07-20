package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

public interface IBackupUploader {
    void doWriteBackup(byte [] backupData, String uploadFilePath);
    byte [] doReadBackup(String uploadFilePath);
    void removeBackup(String uploadFilePath);
}
