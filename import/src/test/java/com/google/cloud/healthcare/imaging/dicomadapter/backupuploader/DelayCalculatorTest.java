package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Before;
import org.junit.Test;

public class DelayCalculatorTest {
  DelayCalculator calc;

  @Before
  public void setup() {
    calc = new DelayCalculator(5, 100, 5000);
  }

  @Test
  public void delayOnFirstTry() {
    assertThat(calc.getExponentialDelayMillis(5)).isEqualTo(100L);
  }

  @Test
  public void delayOnLastTry() {
    assertThat(calc.getExponentialDelayMillis(1)).isEqualTo(5000L);
  }

  @Test
  public void delayOnIntermediateTry() {
    assertThat(calc.getExponentialDelayMillis(4)).isEqualTo(2100L);
  }

  @Test
  public void delayWithNegativeValue() {
    assertThat(calc.getExponentialDelayMillis(-3)).isEqualTo(100L);
  }

  @Test
  public void delayWithInvavidAttemptValue() {
    assertThat(calc.getExponentialDelayMillis(20)).isEqualTo(5000L);
  }
}
