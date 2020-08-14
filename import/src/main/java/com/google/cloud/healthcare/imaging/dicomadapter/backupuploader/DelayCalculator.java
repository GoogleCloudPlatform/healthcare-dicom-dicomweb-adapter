package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

public class DelayCalculator {

  private static final double DELAY_CALCULATION_BASE = 2D;
  private static final long MILS_MUL = 1000L;

  /**
   * Calculates delay in millis. The result depends on attemptsLeft argument in exponential manner.
   * @param attemptsLeft used for calculation ot the pow in the formula.
   * @return delay in millis
   */
  public long getExponentialDelayMillis(int attemptsLeft, BackupFlags backupFlags) {
    int maxWaitingTimeBtwUpload = backupFlags.getMaxWaitingTimeBtwUpload();
    if (attemptsLeft < 1) {
      return backupFlags.getMinUploadDelay();
    }
    if (attemptsLeft > backupFlags.getAttemptsAmount()) {
      return maxWaitingTimeBtwUpload;
    }
    long delay = (long) (backupFlags.getMinUploadDelay() + (Math.round(Math.pow(DELAY_CALCULATION_BASE, backupFlags.getAttemptsAmount() - attemptsLeft + 1)) - DELAY_CALCULATION_BASE) * MILS_MUL);
    return (delay >= maxWaitingTimeBtwUpload) ? maxWaitingTimeBtwUpload : delay;
  }
}
