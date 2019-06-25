package com.google.cloud.healthcare.imaging.dicomadapter.monitoring;

public enum Event implements IMonitoringEvent {
  STARTED(Constants.prefix + "started"),

  CFIND_REQUEST(Constants.prefix + "cfind_requests"),
  CMOVE_REQUEST(Constants.prefix + "cmove_requests"),
  CSTORE_REQUEST(Constants.prefix + "cstore_requests"),

  CFIND_ERROR(Constants.prefix + "cfind_errors"),
  CMOVE_ERROR(Constants.prefix + "cmove_errors"),
  CSTORE_ERROR(Constants.prefix + "cstore_errors"),

  CFIND_CANCEL(Constants.prefix + "cfind_cancels"),
  CMOVE_CANCEL(Constants.prefix + "cmove_cancels"),

  CMOVE_WARNING(Constants.prefix + "cmove_warnings"),

  CSTORE_BYTES(Constants.prefix + "cstore_bytes"),

  CFIND_SUB_QIDORS_REQUEST(Constants.prefix + "cfind_sub_qidors_requests"),
  CMOVE_SUB_CSTORE_REQUEST(Constants.prefix + "cmove_sub_cstore_requests"),

  CFIND_SUB_QIDORS_ERROR(Constants.prefix + "cfind_sub_qidors_errors"),
  CMOVE_SUB_CSTORE_ERROR(Constants.prefix + "cmove_sub_cstore_errors"),

  CMOVE_SUB_CSTORE_BYTES(Constants.prefix + "cmove_sub_cstore_bytes");

  private final String metricName;

  Event(String metricName) {
    this.metricName = metricName;
  }

  public String getMetricName() {
    return metricName;
  }

  private static class Constants {
    private static final String prefix = "custom.googleapis.com/dicomadapter/import/";
  }
}
