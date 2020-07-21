package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

public class DelayCalculator {

    private static final double DELAY_CALCULATION_BASE = 2d;
    private static final long MILS_MUL = 1000l;

    public static long getExponentialDelayMillis(int attemptsLeft, int attemptsAmount) {
        if (attemptsLeft <= attemptsAmount) {
            return Math.round(Math.pow(DELAY_CALCULATION_BASE, attemptsLeft) - DELAY_CALCULATION_BASE)*MILS_MUL;
        } else {
            return Math.round(Math.pow(DELAY_CALCULATION_BASE, attemptsAmount) - DELAY_CALCULATION_BASE)*MILS_MUL;
        }
    }

    public static long getExponentialDelayMillis(int attemptsLeft, int attemptsAmount,
                                                         int minUploadDelay, int maxWaitingTimeBtwUploads) {
        long delay = 0;
        if (attemptsLeft <= attemptsAmount) {
            delay = minUploadDelay + Math.round(Math.pow(DELAY_CALCULATION_BASE, attemptsLeft) - DELAY_CALCULATION_BASE);
        } else {
            delay = minUploadDelay + Math.round(Math.pow(DELAY_CALCULATION_BASE, attemptsAmount) - DELAY_CALCULATION_BASE);
        }
        return (delay >= maxWaitingTimeBtwUploads * 1l)? maxWaitingTimeBtwUploads*1l : delay;
    }
}
