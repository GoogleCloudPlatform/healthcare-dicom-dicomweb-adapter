package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import java.io.IOException;
import java.io.InputStream;

public interface IBackupUploader {

  void doWriteBackup(InputStream inputStream, String uniqueFileName) throws BackupException;

  InputStream doReadBackup(String uniqueFileName) throws BackupException;

  void doRemoveBackup(String uniqueFileName) throws BackupException;

  class BackupException extends IOException {
    public BackupException(String message, Throwable cause) {
      super(message, cause);
    }

    public BackupException(String message) {
      super(message);
    }

    public BackupException(Throwable cause) {
      super(cause);
    }
  }
}
