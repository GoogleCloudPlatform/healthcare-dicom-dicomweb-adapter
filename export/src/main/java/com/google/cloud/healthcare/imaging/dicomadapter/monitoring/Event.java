package com.google.cloud.healthcare.imaging.dicomadapter.monitoring;

public enum Event implements IMonitoringEvent {
  STARTED(Constants.prefix + "started"),

  REQUEST(Constants.prefix + "total_requests"),
  ERROR(Constants.prefix + "total_errors"),
  BYTES(Constants.prefix + "total_bytes");

  private final String metricName;

  Event(String metricName) {
    this.metricName = metricName;
  }

  public String getMetricName() {
    return metricName;
  }

  private static class Constants {
    private static final String prefix = "custom.googleapis.com/dicomadapter/export/";
  }
}
