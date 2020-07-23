package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import java.io.IOException;

public interface IBackupUploader {
  void doWriteBackup(byte[] backupData, String uploadFilePath, String uniqueFileName)
      throws BackupExeption;

  byte[] doReadBackup(String uploadFilePath, String uniqueFileName)
      throws BackupExeption; // todo: implement processing of this ex in CStore and Backup Services

  void removeBackup(String uploadFilePath, String uniqueFileName) throws BackupExeption;

  class BackupExeption extends IOException {
    public BackupExeption(String message, Throwable cause) {
      super(message, cause);
    }

    public BackupExeption(String message) {
      super(message);
    }
  }
}
