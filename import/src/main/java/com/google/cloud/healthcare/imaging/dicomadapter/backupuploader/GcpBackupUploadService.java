package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import com.google.cloud.healthcare.IDicomWebClient;

public class GcpBackupUploadService extends AbstractBackupUploadService {
    public GcpBackupUploadService(IDicomWebClient dicomWebClient, String uploadStorageLocation, int uploadRetryAmount) {
        super(dicomWebClient, uploadStorageLocation, uploadRetryAmount);
    }

    @Override
    public void createBackup(byte[] backupData) {
        // todo: implement_me
    }

    @Override
    public void startUploading() {
        // todo: implement_me
    }
}