package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

public abstract class AbstractBackupUploader implements IBackupUploader {
  private String uploadFilePath;

  public AbstractBackupUploader(String uploadFilePath) {
    this.uploadFilePath = uploadFilePath;
  }

  public String getUploadFilePath() {
    return uploadFilePath;
  }
}
