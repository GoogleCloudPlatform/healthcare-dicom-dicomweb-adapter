package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

public class GcpBackupUploader extends AbstractBackupUploader {

  public GcpBackupUploader(String uploadFilePath) {
    super(uploadFilePath);
  }

  @Override
  public void doWriteBackup(byte[] backupData, String uniqueFileName)
      throws BackupException {
    // todo: implement_me
  }

  @Override
  public byte[] doReadBackup(String uniqueFileName) throws BackupException {
    return new byte[0];
    // todo: implement_me
  }

  @Override
  public void removeBackup(String uniqueFileName) throws BackupException {
    // todo: implement_me
  }
}
