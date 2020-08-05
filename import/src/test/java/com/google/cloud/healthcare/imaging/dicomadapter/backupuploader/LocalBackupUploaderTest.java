package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
    localBackupUploader = new LocalBackupUploader(BACKUP_PATH_STR);
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
    localBackupUploader.doWriteBackup(getInputStreamFromBytes(BYTE_SEQ_1), UNIQUE_FILE_NAME_1);

    assertThat(Files.exists(UNIQUE_FILE_PATH_1)).isTrue();
    assertThat(Files.readAllBytes(UNIQUE_FILE_PATH_1)).isEqualTo(BYTE_SEQ_1);
  }

  @Test
  public void readWriteAndRemoveDifferentFiles() throws IOException {
    localBackupUploader.doWriteBackup(getInputStreamFromBytes(BYTE_SEQ_1),  UNIQUE_FILE_NAME_1);
    localBackupUploader.doWriteBackup(getInputStreamFromBytes(BYTE_SEQ_2),  UNIQUE_FILE_NAME_2);
    InputStream expectedStreamFile1 = localBackupUploader.doReadBackup(UNIQUE_FILE_NAME_1);
    InputStream expectedStreamFile2 = localBackupUploader.doReadBackup(UNIQUE_FILE_NAME_2);

    assertThat(expectedStreamFile1).isNotNull();
    assertThat(expectedStreamFile2).isNotNull();
    assertThat(expectedStreamFile1).isInstanceOf(InputStream.class);
    assertThat(expectedStreamFile2).isInstanceOf(InputStream.class);
    assertThat(expectedStreamFile1.readAllBytes()).isEqualTo(BYTE_SEQ_1);
    assertThat(expectedStreamFile2.readAllBytes()).isEqualTo(BYTE_SEQ_2);

    localBackupUploader.doRemoveBackup(UNIQUE_FILE_NAME_1);
    localBackupUploader.doRemoveBackup(UNIQUE_FILE_NAME_2);

    assertThat(Files.exists(UNIQUE_FILE_PATH_1)).isFalse();
    assertThat(Files.exists(UNIQUE_FILE_PATH_2)).isFalse();
  }

  @Test
  public void doWriteBackup_Failed_OnInvalidPath() throws IBackupUploader.BackupException {
    localBackupUploader = new LocalBackupUploader("");
    exceptionRule.expect(IBackupUploader.BackupException.class);
    exceptionRule.expectMessage("Error with writing backup file");
    localBackupUploader.doWriteBackup(getInputStreamFromBytes(BYTE_SEQ_1), "");
  }

  @Test
  public void doReadBackup_Failed_OnInvalidPath() throws IOException {
    Files.createDirectories(BACKUP_PATH);

    exceptionRule.expect(IBackupUploader.BackupException.class);
    exceptionRule.expectMessage(
        "Error with reading backup file");
    localBackupUploader.doReadBackup("no_file");
  }

  @Test
  public void removeBackup_Failed_OnInvalidPath() throws IOException {
    Files.createDirectories(BACKUP_PATH);

    exceptionRule.expect(IBackupUploader.BackupException.class);
    exceptionRule.expectMessage("Error with removing backup file.");
    localBackupUploader.doRemoveBackup("some_file");
  }

  private InputStream getInputStreamFromBytes(byte[] seq) {
    return new ByteArrayInputStream(seq);
  }
}