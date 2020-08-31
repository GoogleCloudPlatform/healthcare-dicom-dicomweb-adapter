package com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup;

public class DelayCalculator {

  private static final double DELAY_CALCULATION_BASE = 2D;
  private static final long MILS_MUL = 1000L;

  private final int minUploadDelay;
  private final int maxWaitingTimeBtwUpload;

  public DelayCalculator(int minUploadDelay, int maxWaitingTimeBtwUpload) {
    this.minUploadDelay = minUploadDelay;
    this.maxWaitingTimeBtwUpload = maxWaitingTimeBtwUpload;
  }

  /**
   * Calculates delay in millis. The result depends on attemptsLeft argument in exponential manner.
   * @param attemptsLeft used for calculation ot the pow in the formula.
   * @return delay in millis
   */
  public long getExponentialDelayMillis(int attemptsLeft, int attemptsAmount) {
    if (attemptsLeft < 1) {
      return minUploadDelay;
    }
    if (attemptsLeft > attemptsAmount) {
      return maxWaitingTimeBtwUpload;
    }
    long delay = (long) (minUploadDelay + (Math.round(Math.pow(DELAY_CALCULATION_BASE, attemptsAmount - attemptsLeft + 1)) - DELAY_CALCULATION_BASE) * MILS_MUL);
    return (delay >= maxWaitingTimeBtwUpload) ? maxWaitingTimeBtwUpload : delay;
  }
}
