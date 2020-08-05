package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.Event;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.MonitoringService;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;

import java.io.InputStream;

public interface IBackupUploadService {

  BackupState createBackup(InputStream inputStream, String uniqueFileName) throws IBackupUploader.BackupException;

  InputStream getBackupStream(String uniqueFileName) throws IBackupUploader.BackupException;

  void startUploading(IDicomWebClient webClient, BackupState backupState) throws IBackupUploader.BackupException;

  void removeBackup(String uniqueFileName);

  static boolean filterHttpCode409(int actualHttpStatus, Logger log) {
    boolean httpStatus409 = actualHttpStatus == HttpStatus.CONFLICT_409;
    if (httpStatus409) {
      MonitoringService.addEvent(Event.CSTORE_409_ERROR);
      log.error("C-STORE request failed. Got http error with 409.");
    }
    return httpStatus409;
  }

  static boolean filterHttpCode500Plus(int actualHttpStatus, Logger log) {
    boolean httpStatusMoreThan500 = actualHttpStatus > HttpStatus.INTERNAL_SERVER_ERROR_500;
    if (httpStatusMoreThan500) {
      MonitoringService.addEvent(Event.CSTORE_5xx_ERROR);
      log.error("C-STORE request failed. Got http error with status 5xx.");
    }
    return httpStatusMoreThan500;
  }
}
