package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import java.io.IOException;

public interface IBackupUploader {

  void doWriteBackup(byte[] backupData, String uniqueFileName) throws BackupException;

  byte[] doReadBackup(String uniqueFileName) throws BackupException;

  void removeBackup(String uniqueFileName) throws BackupException;

  class BackupException extends IOException {
    public BackupException(String message, Throwable cause) {
      super(message, cause);
    }

    public BackupException(String message) {
      super(message);
    }
  }
}
