package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import com.google.auth.Credentials;
import com.google.cloud.storage.Storage;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.google.common.truth.Truth.assertThat;

public class GcpBackupUploaderTest {
    private static GcpBackupUploader gcpBackupUploader;
    private static Storage localStorage;
    private static byte[] BYTE_SEQ_1 = new byte[]{0, 1, 2, 5, 4, 3, 5, 4, 2, 0, 4, 5, 4, 7};
    private static byte[] BYTE_SEQ_2 = new byte[]{1, 5, 7, 3, 5, 4, 0, 1, 3};
    private static byte[] BYTES_SEQ = new byte[]{0, 1, 2, 5, 4, 3, 5, 4, 2, 0, 4, 5, 4, 7};
    private static String UPLOAD_PATH = "";

    private static final String UNIQUE_FILE_NAME_1 = "uniq1";
    private static final String UNIQUE_FILE_NAME_2 = "uniq2";
    private static final String NOT_EXISTS_UPLOAD_PATH = "gs://testing-healthcare-adapter/some-backup";
    private static final String UNIQ_NAME = "uniq";
    private static final String UNIQ_NAME_REMOVE = "uniq_remove";
    private static final String AUTH_TYPE = "OAuth2";
    private static final String GCP_PROJECT_ID = "test-project-id";
    private static final String OAUTHSCOPES = "https://www.googleapis.com/auth/cloud-healthcare";
    private static final String UPLOAD_PATH_EMPTY_BUCKET_NAME = "gs:/// ";
    private static final String UPLOAD_PATH_SPACE_BUCKET_NAME = "gs:// / ";
    private static final String UPLOAD_PATH_EMPTY_UPLOAD_OBJECT = "gs:///some//";
    private static final String UPLOAD_PATH_SPACE_UPLOAD_OBJECT = "gs://some/ ";
    private static final String UPLOAD_OBJECT = "test-backup";
    private static final String BUCKET_NAME = "test-bucket";

    @BeforeClass
    public static void setUp() {
        try {
            UPLOAD_PATH = "gs://".concat(BUCKET_NAME).concat("/test-backup");
            localStorage = new FakeStorage();
            gcpBackupUploader = new GcpBackupUploader(UPLOAD_PATH, GCP_PROJECT_ID, localStorage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Rule
        public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void parseUri() throws IOException {
        gcpBackupUploader = new GcpBackupUploader(UPLOAD_PATH, GCP_PROJECT_ID, localStorage);

        assertThat(gcpBackupUploader.getProjectId()).isEqualTo(GCP_PROJECT_ID);
        assertThat(gcpBackupUploader.getBucketName()).isEqualTo(BUCKET_NAME);
        assertThat(gcpBackupUploader.getUploadFolder()).isEqualTo(UPLOAD_OBJECT);
    }

    @Test
    public void parseUri_Failed_OnInvalidUploadPath() throws IOException {
        exceptionRule.expect(GcpBackupUploader.GcpUriParseException.class);
        exceptionRule.expectMessage("Invalid upload path");

        new GcpBackupUploader("", GCP_PROJECT_ID, localStorage);
    }

    @Test
    public void parseUri_Failed_OnSpaceUploadPath() throws IOException {
        exceptionRule.expect(GcpBackupUploader.GcpUriParseException.class);
        exceptionRule.expectMessage("Invalid upload path");

        new GcpBackupUploader(" ", GCP_PROJECT_ID, localStorage);
    }

    @Test
    public void parseUri_Failed_OnInvalidBucketName() throws IOException {
        exceptionRule.expect(GcpBackupUploader.GcpUriParseException.class);
        exceptionRule.expectMessage("Invalid upload path");

        new GcpBackupUploader(UPLOAD_PATH_EMPTY_BUCKET_NAME, GCP_PROJECT_ID, localStorage);
    }

    @Test
    public void parseUri_Failed_OnSpaceBucketName() throws IOException {
        exceptionRule.expect(GcpBackupUploader.GcpUriParseException.class);
        exceptionRule.expectMessage("Invalid upload path");

        new GcpBackupUploader(UPLOAD_PATH_SPACE_BUCKET_NAME, GCP_PROJECT_ID, localStorage);
    }

    @Test
    public void parseUri_Failed_OnInvalidUploadObject() throws IOException {
        exceptionRule.expect(GcpBackupUploader.GcpUriParseException.class);
        exceptionRule.expectMessage("Invalid upload path");

        new GcpBackupUploader(UPLOAD_PATH_EMPTY_UPLOAD_OBJECT, GCP_PROJECT_ID, localStorage);
    }

    @Test
    public void parseUri_Failed_OnSpaceUploadObject() throws IOException {
        exceptionRule.expect(GcpBackupUploader.GcpUriParseException.class);
        exceptionRule.expectMessage("Invalid upload path");

        new GcpBackupUploader(UPLOAD_PATH_SPACE_UPLOAD_OBJECT, GCP_PROJECT_ID, localStorage);
    }

    @Test
    public void getCredential() throws IOException {
        gcpBackupUploader = new GcpBackupUploader(UPLOAD_PATH, GCP_PROJECT_ID, OAUTHSCOPES);
        Credentials creds = gcpBackupUploader.getCredential(OAUTHSCOPES);

        assertThat(creds).isInstanceOf(Credentials.class);
        assertThat(creds).isNotNull();
        assertThat(creds.getAuthenticationType()).isEqualTo(AUTH_TYPE);
    }

    @Test
    public void getCredential_Failed_OnEmptyEnv() throws IOException {
        exceptionRule.expect(IBackupUploader.BackupException.class);
        exceptionRule.expectMessage("oauthScopes is blank");
        gcpBackupUploader.getCredential("");
    }

    @Test
    public void readWriteAndRemoveDifferentFiles() throws IOException {
        gcpBackupUploader = new GcpBackupUploader(UPLOAD_PATH, GCP_PROJECT_ID, localStorage);
        gcpBackupUploader.doWriteBackup(getInputStreamFromBytes(BYTE_SEQ_1), UNIQUE_FILE_NAME_1);
        gcpBackupUploader.doWriteBackup(getInputStreamFromBytes(BYTE_SEQ_2), UNIQUE_FILE_NAME_2);
        InputStream inputStream1 = gcpBackupUploader.doReadBackup(UNIQUE_FILE_NAME_1);
        InputStream inputStream2 = gcpBackupUploader.doReadBackup(UNIQUE_FILE_NAME_2);

        assertThat(inputStream1.readAllBytes()).isEqualTo(BYTE_SEQ_1);
        assertThat(inputStream2.readAllBytes()).isEqualTo(BYTE_SEQ_2);

        gcpBackupUploader.doRemoveBackup(UNIQUE_FILE_NAME_1);
        gcpBackupUploader.doRemoveBackup(UNIQUE_FILE_NAME_2);
    }

    @Test
    public void doWriteBackup_Failed_OnInvalidBackupData() throws IBackupUploader.BackupException {
        exceptionRule.expect(IBackupUploader.BackupException.class);
        exceptionRule.expectMessage("Backup data is null");

        gcpBackupUploader.doWriteBackup(null, UNIQ_NAME);
    }

    @Test
    public void doWriteBackup_Failed_OnEmptyUniquePath() throws IBackupUploader.BackupException {
        exceptionRule.expect(IBackupUploader.BackupException.class);
        exceptionRule.expectMessage("Invalid upload path, parameter - unique file name is blank.");

        gcpBackupUploader.doWriteBackup(getInputStreamFromBytes(BYTES_SEQ), "");
    }

    @Test
    public void doReadBackup_Failed_OnEmptyUniqueFileName() throws IBackupUploader.BackupException {
        exceptionRule.expect(IBackupUploader.BackupException.class);
        exceptionRule.expectMessage("Invalid upload path, parameter - unique file name is blank.");

        gcpBackupUploader.doReadBackup( "");
    }

    @Test
    public void removeBackup_Failed_OnEmptyUniqueFileName() throws IBackupUploader.BackupException {
        exceptionRule.expect(IBackupUploader.BackupException.class);
        exceptionRule.expectMessage("Invalid upload path, parameter - unique file name is blank.");

        gcpBackupUploader.doRemoveBackup("");
    }

    @Test
    public void removeBackup_Failed_OnNotExistsUploadPath() throws IOException {
       new GcpBackupUploader(NOT_EXISTS_UPLOAD_PATH, GCP_PROJECT_ID, localStorage).doRemoveBackup(UNIQ_NAME_REMOVE);
    }

    private InputStream getInputStreamFromBytes(byte[] seq) {
        return new ByteArrayInputStream(seq);
    }
}
