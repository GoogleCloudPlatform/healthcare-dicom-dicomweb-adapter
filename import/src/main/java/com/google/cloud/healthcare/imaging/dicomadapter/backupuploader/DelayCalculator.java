package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

public class DelayCalculator {

    private static final double DELAY_CALCULATION_BASE = 2d;
    private static final long MILS_MUL = 1000l;

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
        if (attemptsLeft <= attemptsAmount) {
            delay = minUploadDelay + Math.round(Math.pow(DELAY_CALCULATION_BASE, attemptsLeft) - DELAY_CALCULATION_BASE)*MILS_MUL;
        } else {
            delay = minUploadDelay + Math.round(Math.pow(DELAY_CALCULATION_BASE, attemptsAmount) - DELAY_CALCULATION_BASE)*MILS_MUL;
        }
        long resultDelay = (delay >= (long)maxWaitingTimeBtwUpload)? (long)maxWaitingTimeBtwUpload : delay;
        System.out.println("resultDelay=" + resultDelay);
        return resultDelay;
    }

    public int getAttemptsAmount() {
        return attemptsAmount;
    }
}
