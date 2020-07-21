package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class LocalBackupUploadService extends AbstractBackupUploadService {
    public LocalBackupUploadService(String uploadFilePath, int attemptsCount) {
        super(uploadFilePath, attemptsCount);
    }

    public LocalBackupUploadService(String uploadFilePath, int attemptsCount,
                                    int minUploadDelay, int maxWaitingTimeBtwUploads) {
        super(uploadFilePath, attemptsCount, minUploadDelay, maxWaitingTimeBtwUploads);
    }

    @Override
    public void doWriteBackup(byte[] backupData, String uploadFilePath) throws BackupExeption {
        try (FileOutputStream fos = new FileOutputStream(uploadFilePath)) {
            fos.write(backupData, 0, backupData.length);
        } catch (IOException ex){
            throw new BackupExeption("Error with writing backup file", ex);
        }
    }

    @Override
    public byte[] doReadBackup(String uploadFilePath) throws BackupExeption {
        try (FileInputStream fin = new FileInputStream(uploadFilePath)) {
            byte[] buffer = new byte[fin.available()];
            fin.read(buffer, 0, fin.available());
            return buffer;
        } catch (IOException ex) {
            throw new BackupExeption("Error with reading backup file", ex);
        }
    }

    @Override
    public void removeBackup(String uploadFilePath) {
        // todo: implement_me
    }
}
