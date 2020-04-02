package com.google.cloud.healthcare;

import com.google.common.base.CharMatcher;

public class StringUtil {

  public static String trim(String value) {
    return CharMatcher.is('/').trimFrom(value);
  }

  public static String joinPath(String serviceUrlPrefix, String path){
    return StringUtil.trim(serviceUrlPrefix) + "/" + StringUtil.trim(path);
  }
}
