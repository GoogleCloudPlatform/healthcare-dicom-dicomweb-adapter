package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

public class GcpBackupUploader implements IBackupUploader {

  @Override
  public void doWriteBackup(byte[] backupData, String uploadFilePath, String uniqueFileName)
      throws BackupExeption {
    // todo: implement_me
  }

  @Override
  public byte[] doReadBackup(String uploadFilePath, String uniqueFileName) throws BackupExeption {
    return new byte[0];
    // todo: implement_me
  }

  @Override
  public void removeBackup(String uploadFilePath, String uniqueFileName) throws BackupExeption {
    // todo: implement_me
  }
}
