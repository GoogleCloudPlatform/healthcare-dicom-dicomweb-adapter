package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

public class DelayCalculator {

    private static final double DELAY_CALCULATION_BASE = 2D;
    private static final long MILS_MUL = 1000L;

    private int attemptsAmount;
    private int minUploadDelay;
    private int maxWaitingTimeBtwUpload;

    public DelayCalculator(int attemptsAmount, int minUploadDelay, int maxWaitingTimeBtwUpload) {
        this.attemptsAmount = attemptsAmount;
        this.minUploadDelay = minUploadDelay;
        this.maxWaitingTimeBtwUpload = maxWaitingTimeBtwUpload;
    }

    public long getExponentialDelayMillis(int attemptsLeft) {
        long delay = 0;
        if (attemptsLeft < 1) return minUploadDelay;
        if (attemptsLeft > attemptsAmount) return maxWaitingTimeBtwUpload;
        delay = (long) (minUploadDelay + (Math.round(Math.pow(DELAY_CALCULATION_BASE, attemptsAmount - attemptsLeft + 1)) - DELAY_CALCULATION_BASE)*MILS_MUL);
        return (delay >= maxWaitingTimeBtwUpload)?maxWaitingTimeBtwUpload : delay;
    }

    public int getAttemptsAmount() {
        return attemptsAmount;
    }
}
