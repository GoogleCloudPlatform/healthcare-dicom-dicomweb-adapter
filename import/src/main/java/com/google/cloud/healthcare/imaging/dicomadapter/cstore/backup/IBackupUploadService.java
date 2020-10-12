package com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.AetDictionary;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.BackupState;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.IBackupUploader.BackupException;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest.sender.CStoreSender;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

public interface IBackupUploadService {

  void createBackup(InputStream inputStream, String uniqueFileName) throws BackupException;

  CompletableFuture startUploading(IDicomWebClient webClient, BackupState backupState) throws BackupException;
  CompletableFuture startUploading(CStoreSender cStoreSender, AetDictionary.Aet target, String sopInstanceUid, String sopClassUid,
                                   BackupState backupState) throws BackupException;

  void removeBackup(String uniqueFileName);
}
