package com.google.cloud.healthcare;

import com.google.common.base.CharMatcher;

public class Util {

  public static String trim(String value) {
    return CharMatcher.is('/').trimFrom(value);
  }
}
