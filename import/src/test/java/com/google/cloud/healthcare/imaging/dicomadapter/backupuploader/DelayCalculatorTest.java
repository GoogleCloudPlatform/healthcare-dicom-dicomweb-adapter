package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

public class DelayCalculatorTest {
  private BackupFlags backupFlags;
  private DelayCalculator calc;

  @Before
  public void setup() {
    backupFlags = new BackupFlags(5, 100, 5000, new ArrayList<>());
    calc = new DelayCalculator();
  }

  @Test
  public void delayOnFirstTry() {
    assertThat(calc.getExponentialDelayMillis(5, backupFlags)).isEqualTo(100L);
  }

  @Test
  public void delayOnLastTry() {
    assertThat(calc.getExponentialDelayMillis(1, backupFlags)).isEqualTo(5000L);
  }

  @Test
  public void delayOnIntermediateTry() {
    assertThat(calc.getExponentialDelayMillis(4, backupFlags)).isEqualTo(2100L);
  }

  @Test
  public void delayWithNegativeValue() {
    assertThat(calc.getExponentialDelayMillis(-3, backupFlags)).isEqualTo(100L);
  }

  @Test
  public void delayWithInvavidAttemptValue() {
    assertThat(calc.getExponentialDelayMillis(20, backupFlags)).isEqualTo(5000L);
  }
}
