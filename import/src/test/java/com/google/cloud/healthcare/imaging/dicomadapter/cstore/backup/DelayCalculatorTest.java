package com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.DelayCalculator;
import org.junit.Before;
import org.junit.Test;

public class DelayCalculatorTest {

  private DelayCalculator calc;

  @Before
  public void setup() {

    calc = new DelayCalculator(100, 5000);
  }

  @Test
  public void delayOnFirstTry() {
    assertThat(calc.getExponentialDelayMillis(5, 5)).isEqualTo(100L);
  }

  @Test
  public void delayOnLastTry() {
    assertThat(calc.getExponentialDelayMillis(1, 5)).isEqualTo(5000L);
  }

  @Test
  public void delayOnIntermediateTry() {
    assertThat(calc.getExponentialDelayMillis(4, 5)).isEqualTo(2100L);
  }

  @Test
  public void delayWithNegativeValue() {
    assertThat(calc.getExponentialDelayMillis(-3, 5)).isEqualTo(100L);
  }

  @Test
  public void delayWithInvavidAttemptValue() {
    assertThat(calc.getExponentialDelayMillis(20, 5)).isEqualTo(5000L);
  }
}
