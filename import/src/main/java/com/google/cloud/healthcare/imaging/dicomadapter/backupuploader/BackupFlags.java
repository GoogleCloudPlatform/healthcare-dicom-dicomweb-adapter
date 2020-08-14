package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import java.util.List;

public class BackupFlags {
  private int attemptsAmount;
  private int minUploadDelay;
  private int maxWaitingTimeBtwUpload;
  private List<Integer> httpErrorCodesToRetry;

  public BackupFlags(int attemptsAmount, int minUploadDelay, int maxWaitingTimeBtwUpload, List<Integer> httpErrorCodesToRetry) {
    this.attemptsAmount = attemptsAmount;
    this.minUploadDelay = minUploadDelay;
    this.maxWaitingTimeBtwUpload = maxWaitingTimeBtwUpload;
    this.httpErrorCodesToRetry = httpErrorCodesToRetry;
  }

  public int getAttemptsAmount() {
    return attemptsAmount;
  }

  public int getMinUploadDelay() {
    return minUploadDelay;
  }

  public int getMaxWaitingTimeBtwUpload() {
    return maxWaitingTimeBtwUpload;
  }

  public List<Integer> getHttpErrorCodesToRetry() {
    return httpErrorCodesToRetry;
  }
}
