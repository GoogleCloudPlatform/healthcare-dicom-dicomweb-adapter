package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import com.google.cloud.healthcare.IDicomWebClient;
import java.io.InputStream;

public interface IBackupUploadService {

  BackupState createBackup(InputStream inputStream, String uniqueFileName) throws IBackupUploader.BackupException;

  InputStream getBackupStream(String uniqueFileName) throws IBackupUploader.BackupException;

  void startUploading(IDicomWebClient webClient, BackupState backupState) throws IBackupUploader.BackupException;

  void removeBackup(BackupState backupState);
}
