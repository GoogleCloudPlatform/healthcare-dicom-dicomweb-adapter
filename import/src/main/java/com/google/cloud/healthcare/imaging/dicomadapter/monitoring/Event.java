// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.healthcare.imaging.dicomadapter.monitoring;

public enum Event implements IMonitoringEvent {
  STARTED(Constants.prefix + "started"),

  CSTORE_REQUEST(Constants.prefix + "cstore_requests"),
  CSTORE_ERROR(Constants.prefix + "cstore_errors"),
  CSTORE_BACKUP_ERROR(Constants.prefix + "cstore_backup_errors"),
  CSTORE_BYTES(Constants.prefix + "cstore_bytes"),
  CSTORE_409_ERROR(Constants.prefix + "cstore_409_error"),
  CSTORE_5xx_ERROR(Constants.prefix + "cstore_5xx_error"),

  CFIND_REQUEST(Constants.prefix + "cfind_requests"),
  CFIND_ERROR(Constants.prefix + "cfind_errors"),
  CFIND_CANCEL(Constants.prefix + "cfind_cancels"),
  CFIND_QIDORS_REQUEST(Constants.prefix + "cfind_qidors_requests"),
  CFIND_QIDORS_ERROR(Constants.prefix + "cfind_qidors_errors"),

  CMOVE_REQUEST(Constants.prefix + "cmove_requests"),
  CMOVE_ERROR(Constants.prefix + "cmove_errors"),
  CMOVE_WARNING(Constants.prefix + "cmove_warnings"),
  CMOVE_CANCEL(Constants.prefix + "cmove_cancels"),
  CMOVE_QIDORS_REQUEST(Constants.prefix + "cmove_qidors_requests"),
  CMOVE_QIDORS_ERROR(Constants.prefix + "cmove_qidors_errors"),
  CMOVE_CSTORE_REQUEST(Constants.prefix + "cmove_cstore_requests"),
  CMOVE_CSTORE_ERROR(Constants.prefix + "cmove_cstore_errors"),
  CMOVE_CSTORE_BYTES(Constants.prefix + "cmove_cstore_bytes"),

  COMMITMENT_REQUEST(Constants.prefix + "commitment_requests"),
  COMMITMENT_ERROR(Constants.prefix + "commitment_errors"),
  COMMITMENT_QIDORS_ERROR(Constants.prefix + "commitment_qidors_errors");

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
