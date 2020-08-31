package com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.BackupState;
import org.junit.Test;

public class BackupStateTest {

  private final String UNIQUE_FILE_NAME = "testUniqueFileName";
  private final Integer ATTEMPTS_AMOUNT = 1;

  @Test
  public void decrementsIfAttemptsCountdownMoreThanZero() {
    BackupState backupState = new BackupState(UNIQUE_FILE_NAME, ATTEMPTS_AMOUNT);

    assertThat(backupState.getUniqueFileName()).isEqualTo(UNIQUE_FILE_NAME);
    assertThat(backupState.getAttemptsCountdown()).isEqualTo(2);

    boolean decrementSuccess = backupState.decrement();
    assertThat(decrementSuccess).isTrue();
    assertThat(backupState.getAttemptsCountdown()).isEqualTo(1);

    decrementSuccess = backupState.decrement();
    assertThat(decrementSuccess).isTrue();
    assertThat(backupState.getAttemptsCountdown()).isEqualTo(0);

    decrementSuccess = backupState.decrement();
    assertThat(decrementSuccess).isFalse();
    assertThat(backupState.getAttemptsCountdown()).isEqualTo(0);
  }
}