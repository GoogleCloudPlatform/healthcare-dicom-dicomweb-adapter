package com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup;

public class BackupState {
  private String uniqueFileName;
  private int attemptsCountdown = 1;

  public BackupState(String uniqueFileName, int attemptsCountdown) {
    this.uniqueFileName = uniqueFileName;
    this.attemptsCountdown += attemptsCountdown;
  }

  public String getUniqueFileName() {
    return uniqueFileName;
  }

  public int getAttemptsCountdown() {
    return attemptsCountdown;
  }

  /**
   * Decrements attemptsCountdown field value if it`s value more then zero.
   * @return true if decremented, false if not.
   */
  public boolean decrement() {
    if (attemptsCountdown > 0) {
      attemptsCountdown--;
      return true;
    }
    return false;
  }
}
