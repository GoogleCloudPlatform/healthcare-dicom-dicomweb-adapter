package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

public class DelayCalculator {

    private static final double DELAY_CALCULATION_BASE = 2d;

    public static long getExponentialDelayMillis(int attemptsLeft, int attemptsAmount) {
        if (attemptsLeft <= attemptsAmount) {
            return Math.round(Math.pow(DELAY_CALCULATION_BASE, attemptsLeft) - DELAY_CALCULATION_BASE);
        } else {
            return Math.round(Math.pow(DELAY_CALCULATION_BASE, attemptsAmount) - DELAY_CALCULATION_BASE);
        }
    }
}
