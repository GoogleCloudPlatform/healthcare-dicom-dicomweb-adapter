package com.google.cloud.healthcare;

import com.google.common.base.CharMatcher;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.TagUtils;

public class StringUtil {

  public static String trim(String value) {
    return CharMatcher.is('/').trimFrom(value);
  }

  public static String joinPath(String serviceUrlPrefix, String path) {
    return StringUtil.trim(serviceUrlPrefix) + "/" + StringUtil.trim(path);
  }

  public static String getTagValueAsStringOrException(Attributes attrs, int tag)
      throws IllegalArgumentException {
    String val = attrs.getString(tag, null);
    if (val == null) {
      throw new IllegalArgumentException("Invalid value for tag " + TagUtils.toHexString(tag));
    }
    return val;
  }
}
