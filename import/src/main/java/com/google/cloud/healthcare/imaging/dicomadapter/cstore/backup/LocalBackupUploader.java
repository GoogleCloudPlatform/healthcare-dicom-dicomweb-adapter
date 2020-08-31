package com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class LocalBackupUploader extends AbstractBackupUploader {

  public LocalBackupUploader(String uploadFilePath) {
    super(uploadFilePath);
  }

  @Override
  public void doWriteBackup(InputStream inputStream, String uniqueFileName) throws BackupException {
    try {
      validatePathParameter(uniqueFileName, "unique file name");
      Files.createDirectories(Paths.get(getUploadFilePath()));

      Files.copy(inputStream, Paths.get(getUploadFilePath(), uniqueFileName), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException ex) {
      throw new BackupException("Error with writing backup file.", ex);
    }
  }

  @Override
  public InputStream doReadBackup(String uniqueFileName) throws BackupException {
    try {
      return Files.newInputStream(Paths.get(getUploadFilePath(), uniqueFileName));
    } catch (IOException ex) {
      throw new BackupException("Error with reading backup file : " + ex.getMessage(), ex);
    }
  }

  @Override
  public void doRemoveBackup(String uniqueFileName) throws BackupException {
    try {
      Files.delete(Paths.get(getUploadFilePath(), uniqueFileName));
    } catch (IOException e) {
      throw new BackupException("Error with removing backup file.", e);
    }
  }
}
