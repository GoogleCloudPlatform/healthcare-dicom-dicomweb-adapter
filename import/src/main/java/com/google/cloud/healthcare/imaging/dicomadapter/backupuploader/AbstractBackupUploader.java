package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import org.apache.commons.lang3.StringUtils;

public abstract class AbstractBackupUploader implements IBackupUploader {

  private String uploadFilePath;

  public AbstractBackupUploader(String uploadFilePath) {
    this.uploadFilePath = uploadFilePath;
  }

  public String getUploadFilePath() {
    return uploadFilePath;
  }

  public void validatePathParameter(String parameterValue, String parameterName) throws BackupException {
    if (StringUtils.isBlank(parameterValue)) {
      throw new BackupException("Invalid upload path, parameter - " + parameterName + " is blank.");
    }
  }
}
