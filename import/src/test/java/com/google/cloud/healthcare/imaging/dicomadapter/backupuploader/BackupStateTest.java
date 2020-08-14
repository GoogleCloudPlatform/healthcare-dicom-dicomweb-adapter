package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class BackupStateTest {

  private final String UNIQUE_FILE_NAME = "testUniqueFileName";
  private final Integer ATTEMPTS_AMOUNT = 1;

  @Test
  public void decrementsIfAttemptsCountdownMoreThanZero() {
    BackupState backupState = new BackupState(UNIQUE_FILE_NAME, ATTEMPTS_AMOUNT);

    assertThat(backupState.getUniqueFileName()).isEqualTo(UNIQUE_FILE_NAME);
    assertThat(backupState.getAttemptsCountdown()).isEqualTo(ATTEMPTS_AMOUNT);

    boolean decrementSuccess = backupState.decrement();
    assertThat(decrementSuccess).isTrue();
    assertThat(backupState.getAttemptsCountdown()).isEqualTo(ATTEMPTS_AMOUNT - 1);

    decrementSuccess = backupState.decrement();
    assertThat(decrementSuccess).isFalse();
    assertThat(backupState.getAttemptsCountdown()).isEqualTo(ATTEMPTS_AMOUNT - 1);
  }
}