package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import static com.google.common.truth.Truth.assertThat;

import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@RunWith(JUnit4.class)
public class LocalBackupUploadServiceTest {
    private LocalBackupUploadService localBackupUploadService;
    private DelayCalculator delayCalculator;
    private static byte[] bytes = new byte[]{0, 1, 2, 5, 4, 3, 5, 4, 2, 0, 4, 5, 4, 7};

    private static final String READ_FILENAME = "test_read";
    private static final String WRITE_FILENAME = "test_write";
    private static final String REMOVE_FILENAME = "test_remove";

    @Before
    public void setUp() throws Exception {
        delayCalculator = new DelayCalculator(5,100, 5000);
        localBackupUploadService = new LocalBackupUploadService("test", delayCalculator);
    }

    @BeforeClass
    public static void initData() {
        try (FileOutputStream tr = new FileOutputStream(READ_FILENAME);
                FileOutputStream fw = new FileOutputStream(REMOVE_FILENAME)){
            tr.write(bytes, 0, bytes.length);
            fw.write(bytes, 0, bytes.length);
        }catch (IOException ignored) {

        }
    }

    @AfterClass
    public static void clearData() {
        try {
            Files.delete(Paths.get(READ_FILENAME));
            Files.delete(Paths.get(WRITE_FILENAME));
        }catch (Exception ignored){

        }
    }

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void doWriteBackup() throws IBackupUploader.BackupExeption {
        localBackupUploadService.doWriteBackup(bytes, WRITE_FILENAME);
    }

    @Test
    public void doWriteBackup_Failed_OnInvalidPath() throws IBackupUploader.BackupExeption {
        exceptionRule.expect(IBackupUploader.BackupExeption.class);
        exceptionRule.expectMessage("Error with writing backup file");
        localBackupUploadService.doWriteBackup(bytes, "");
    }

    @Test
    public void doReadBackup() throws IBackupUploader.BackupExeption {
        byte[] data = localBackupUploadService.doReadBackup(READ_FILENAME);
        assertThat(data).hasLength(14);
        assertThat(data).isEqualTo(bytes);
    }

    @Test
    public void doReadBackup_Failed_OnInvalidPath() throws IBackupUploader.BackupExeption {
        exceptionRule.expect(IBackupUploader.BackupExeption.class);
        exceptionRule.expectMessage("Error with reading backup file");
        localBackupUploadService.doReadBackup("no_file");
    }

    @Test
    public void removeBackup() throws IBackupUploader.BackupExeption {
        localBackupUploadService.removeBackup(REMOVE_FILENAME);
    }

    @Test
    public void removeBackup_Failed_OnInvalidPath() throws IBackupUploader.BackupExeption {
        exceptionRule.expect(IBackupUploader.BackupExeption.class);
        exceptionRule.expectMessage("Error with removing temporary file");
        localBackupUploadService.removeBackup("some_file");
    }

    @Test
    public void createBackup() throws IBackupUploader.BackupExeption {
        BackupState backupState = localBackupUploadService.createBackup(bytes);
        assertThat(backupState).isNotNull();
        assertThat(backupState).isInstanceOf(BackupState.class);
    }

    @Test(expected = NullPointerException.class)
    public void createBackup_Failed_OnInvalidBackupData() throws IBackupUploader.BackupExeption {
        localBackupUploadService.createBackup(null);
    }
}