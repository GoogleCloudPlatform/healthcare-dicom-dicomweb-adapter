package com.google.cloud.healthcare.imaging.dicomadapter.monitoring;

public enum Event implements IMonitoringEvent {
  STARTED(Constants.prefix + "started"),

  CSTORE_REQUEST(Constants.prefix + "cstore_requests"),
  CSTORE_ERROR(Constants.prefix + "cstore_errors"),
  CSTORE_BYTES(Constants.prefix + "cstore_bytes"),

  CFIND_REQUEST(Constants.prefix + "cfind_requests"),
  CFIND_ERROR(Constants.prefix + "cfind_errors"),
  CFIND_CANCEL(Constants.prefix + "cfind_cancels"),
  CFIND_QIDORS_REQUEST(Constants.prefix + "cfind_qidors_requests"),
  CFIND_QIDORS_ERROR(Constants.prefix + "cfind_qidors_errors");

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
