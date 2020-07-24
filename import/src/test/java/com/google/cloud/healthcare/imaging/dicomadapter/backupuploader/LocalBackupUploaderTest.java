package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LocalBackupUploaderTest {
  private static byte[] BYTE_SEQ_1 = new byte[] {0, 1, 2, 5, 4, 3, 5, 4, 2, 0, 4, 5, 4, 7};
  private static byte[] BYTE_SEQ_2 = new byte[] {1, 5, 7, 3, 5, 4, 0, 1, 3};

  private static final String UNIQUE_FILE_NAME_1 = "uniq1";
  private static final String UNIQUE_FILE_NAME_2 = "uniq2";
  private static final String BACKUP_PATH_PREFIX = "backup";
  private static final String BACKUP_PATH_STR = BACKUP_PATH_PREFIX + "/path";

  private static final Path BACKUP_PATH = Paths.get(BACKUP_PATH_STR);
  private static final Path UNIQUE_FILE_PATH_1 = Paths.get(BACKUP_PATH_STR, UNIQUE_FILE_NAME_1);
  private static final Path UNIQUE_FILE_PATH_2 = Paths.get(BACKUP_PATH_STR, UNIQUE_FILE_NAME_2);

  private IBackupUploader localBackupUploader;

  @Rule public ExpectedException exceptionRule = ExpectedException.none();

  @Before
  public void setUp() {
    localBackupUploader = new LocalBackupUploader();
  }

  /**
   * Clears all possible backup data after each test.
   */
  @After
  public void clearData() throws IOException {
    Files.deleteIfExists(UNIQUE_FILE_PATH_1);
    Files.deleteIfExists(UNIQUE_FILE_PATH_2);
    Files.deleteIfExists(Paths.get(BACKUP_PATH_STR));
    Files.deleteIfExists(Paths.get(BACKUP_PATH_PREFIX));
  }

  @Test
  public void doWriteBackupWithBackupPathCreation() throws IOException {
    localBackupUploader.doWriteBackup(BYTE_SEQ_1, BACKUP_PATH_STR, UNIQUE_FILE_NAME_1);

    assertThat(Files.exists(UNIQUE_FILE_PATH_1)).isTrue();
    assertThat(Files.readAllBytes(UNIQUE_FILE_PATH_1)).isEqualTo(BYTE_SEQ_1);
  }

  @Test
  public void readWriteAndRemoveDifferentFiles() throws IBackupUploader.BackupException {
    localBackupUploader.doWriteBackup(BYTE_SEQ_1, BACKUP_PATH_STR, UNIQUE_FILE_NAME_1);
    localBackupUploader.doWriteBackup(BYTE_SEQ_2, BACKUP_PATH_STR, UNIQUE_FILE_NAME_2);
    byte[] expectedBytesFile1 = localBackupUploader.doReadBackup(BACKUP_PATH_STR, UNIQUE_FILE_NAME_1);
    byte[] expectedBytesFile2 = localBackupUploader.doReadBackup(BACKUP_PATH_STR, UNIQUE_FILE_NAME_2);

    assertThat(expectedBytesFile1).isEqualTo(BYTE_SEQ_1);
    assertThat(expectedBytesFile2).isEqualTo(BYTE_SEQ_2);

    localBackupUploader.removeBackup(BACKUP_PATH_STR, UNIQUE_FILE_NAME_1);
    localBackupUploader.removeBackup(BACKUP_PATH_STR, UNIQUE_FILE_NAME_2);

    assertThat(Files.exists(UNIQUE_FILE_PATH_1)).isFalse();
    assertThat(Files.exists(UNIQUE_FILE_PATH_2)).isFalse();
  }

  @Test
  public void doWriteBackup_Failed_OnInvalidPath() throws IBackupUploader.BackupException {
    exceptionRule.expect(IBackupUploader.BackupException.class);
    exceptionRule.expectMessage("Error with writing backup file");
    localBackupUploader.doWriteBackup(BYTE_SEQ_1, "", "");
  }

  @Test
  public void doReadBackup_Failed_OnInvalidPath() throws IOException {
    Files.createDirectories(BACKUP_PATH);

    exceptionRule.expect(IBackupUploader.BackupException.class);
    exceptionRule.expectMessage(
        "Error with reading backup file");
    localBackupUploader.doReadBackup(BACKUP_PATH_STR, "no_file");
  }

  @Test
  public void removeBackup_Failed_OnInvalidPath() throws IOException {
    Files.createDirectories(BACKUP_PATH);

    exceptionRule.expect(IBackupUploader.BackupException.class);
    exceptionRule.expectMessage("Error with removing backup file.");
    localBackupUploader.removeBackup(BACKUP_PATH_STR, "some_file");
  }

  @Test
  public void doReadBackup_Failed_OnEmptyFile() throws IOException {
    Files.createDirectories(BACKUP_PATH);
    byte [] emptyBytes = {};
    Files.write(UNIQUE_FILE_PATH_2, emptyBytes);

    exceptionRule.expect(IBackupUploader.BackupException.class);
    exceptionRule.expectMessage("Error with reading backup file : No data in backup file.");
    localBackupUploader.doReadBackup(BACKUP_PATH_STR, UNIQUE_FILE_NAME_2);
  }
}
