package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class LocalBackupUploader implements IBackupUploader {

  @Override
  public void doWriteBackup(byte[] backupData, String uploadFilePath, String uniqueFileName)
      throws BackupException {
    try {
      Files.createDirectories(Paths.get(uploadFilePath));
      try (FileOutputStream fos =
          new FileOutputStream(Paths.get(uploadFilePath, uniqueFileName).toFile())) {
        fos.write(backupData, 0, backupData.length);
      }
    } catch (IOException ex) {
      throw new BackupException("Error with writing backup file.", ex);
    }
  }

  @Override
  public byte[] doReadBackup(String uploadFilePath, String uniqueFileName) throws BackupException {
    try (FileInputStream fin =
        new FileInputStream(Paths.get(uploadFilePath, uniqueFileName).toFile())) {
      byte[] buffer = new byte[fin.available()];
      fin.read(buffer, 0, fin.available());
      if (buffer.length == 0) {
        throw new BackupException("No data in backup file.");
      }
      return buffer;
    } catch (IOException ex) {
      throw new BackupException("Error with reading backup file : " + ex.getMessage(), ex);
    }
  }

  @Override
  public void removeBackup(String uploadFilePath, String uniqueFileName) throws BackupException {
    try {
      Files.delete(Paths.get(uploadFilePath, uniqueFileName));
    } catch (IOException e) {
      throw new BackupException("Error with removing backup file.", e);
    }
  }
}
