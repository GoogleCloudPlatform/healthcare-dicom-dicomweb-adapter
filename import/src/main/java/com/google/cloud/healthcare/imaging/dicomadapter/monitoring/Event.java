package com.google.cloud.healthcare.imaging.dicomadapter.monitoring;

public enum Event implements IMonitoringEvent {
  STARTED(Constants.prefix + "started"),

  CSTORE_REQUEST(Constants.prefix + "cstore_requests"),
  CSTORE_ERROR(Constants.prefix + "cstore_errors"),
  CSTORE_BYTES(Constants.prefix + "cstore_bytes");

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
