package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import java.io.IOException;

public interface IBackupUploader {
    void doWriteBackup(byte [] backupData, String uploadFilePath) throws BackupExeption;
    byte [] doReadBackup(String uploadFilePath) throws BackupExeption; //todo: implement processing of this ex in CStore and Backup Services
    void removeBackup(String uploadFilePath);

    class BackupExeption extends IOException {
        public BackupExeption(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
