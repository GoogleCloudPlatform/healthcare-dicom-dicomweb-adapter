package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import java.io.InputStream;

public class GcpBackupUploader extends AbstractBackupUploader {

  public GcpBackupUploader(String uploadFilePath) {
    super(uploadFilePath);
  }

  @Override
  public void doWriteBackup(InputStream inputStream, String uniqueFileName) throws BackupException {
    
  }

  @Override
  public InputStream doReadBackup(String uniqueFileName) throws BackupException {
    return null;
  }

  @Override
  public void doRemoveBackup(String uniqueFileName) throws BackupException {

  }
}
