package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import com.google.cloud.healthcare.IDicomWebClient;

public interface IBackupUploadService {
  BackupState createBackup(byte[] backupData, String uniqueFileName)
      throws IBackupUploader.BackupExeption;

  void startUploading(IDicomWebClient webClient, BackupState backupState)
      throws IBackupUploader.BackupExeption;
}
