package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LocalBackupUploadService extends AbstractBackupUploadService {
    public LocalBackupUploadService(String uploadFilePath, DelayCalculator delayCalculator) {
        super(uploadFilePath, delayCalculator);
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
    public void removeBackup(String uploadFilePath) throws BackupExeption {
        try {
            Files.delete(Paths.get(uploadFilePath));
        }catch (IOException e){
            throw new BackupExeption("Error with removing temporary file", e);
        }
    }
}
