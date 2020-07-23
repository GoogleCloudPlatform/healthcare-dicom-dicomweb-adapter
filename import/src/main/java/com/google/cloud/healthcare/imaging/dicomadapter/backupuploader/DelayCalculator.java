package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

public class DelayCalculator {

  private static final long DELAY_CALCULATION_BASE = 2L;
  private static final long MILS_MUL = 1000L;

  private int attemptsAmount;
  private int minUploadDelay;
  private int maxWaitingTimeBtwUpload;

  /**
   * Creates instance of DelayCalculator with flags values from application configuration.
   * @param attemptsAmount amount of attempts.
   * @param minUploadDelay left border of final delay result.
   * @param maxWaitingTimeBtwUpload right border of final delay result.
   */
  public DelayCalculator(int attemptsAmount, int minUploadDelay, int maxWaitingTimeBtwUpload) {
    this.attemptsAmount = attemptsAmount;
    this.minUploadDelay = minUploadDelay;
    this.maxWaitingTimeBtwUpload = maxWaitingTimeBtwUpload;
  }

  /**
   * Calculates delay in millis. The result depends on attemptsLeft argument in exponential manner.
   * @param attemptsLeft used for calculation ot the pow in the formula.
   * @return delay in millis
   */
  public long getExponentialDelayMillis(int attemptsLeft) {
    long delay = 0;
    if (attemptsLeft <= attemptsAmount) {
      delay = minUploadDelay + Math.round(Math.pow(DELAY_CALCULATION_BASE, attemptsLeft) - DELAY_CALCULATION_BASE) * MILS_MUL;
    } else {
      delay = minUploadDelay + Math.round(Math.pow(DELAY_CALCULATION_BASE, attemptsAmount) - DELAY_CALCULATION_BASE) * MILS_MUL;
    }
    long resultDelay = (delay >= (long) maxWaitingTimeBtwUpload) ? (long) maxWaitingTimeBtwUpload : delay;

    return resultDelay;
  }

  public int getAttemptsAmount() {
    return attemptsAmount;
  }
}
