package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.util.List;
import org.apache.http.client.utils.URIBuilder;

public class GcpBackupUploader extends AbstractBackupUploader {
  private String projectName;
  private String bucketName;
  private String uploadObject;
  private Storage storage;

  private static final String ENV_CREDS = "GOOGLE_APPLICATION_CREDENTIALS";

  public GcpBackupUploader(String uploadFilePath) throws IOException {
    super(uploadFilePath);
    parseUploadFilePath(getUploadFilePath());
    storage = getStorage();
  }

  public GcpBackupUploader(String uploadFilePath, Storage storage) throws IOException {
    super(uploadFilePath);
    parseUploadFilePath(getUploadFilePath());
    this.storage = storage;
  }

  @Override
  public void doWriteBackup(InputStream backupData, String uniqueFileName)
          throws BackupException {
    try {
      if (backupData == null) {
        throw new BackupException("Backup data is null");
      }
      validatePathParameter(uniqueFileName, "unique file name");
      BlobId blobId = BlobId.of(bucketName, getFullUploadObject(uniqueFileName));
      BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
      storage.createFrom(blobInfo, backupData);
    } catch (Exception e) {
      throw new BackupException("Error with writing backup file: " + e.getMessage(), e);
    }
  }

  @Override
  public InputStream doReadBackup(String uniqueFileName) throws BackupException {
    try {
      validatePathParameter(uniqueFileName, "unique file name");
      Blob blob = storage.get(BlobId.of(bucketName, getFullUploadObject(uniqueFileName)));
      ReadChannel channel = blob.reader();
      return Channels.newInputStream(channel);
    } catch (Exception e) {
      throw new BackupException("Error with reading backup file: " + e.getMessage(), e);
    }
  }

  @Override
  public void doRemoveBackup(String uniqueFileName) throws BackupException {
    try {
      validatePathParameter(uniqueFileName, "unique file name");
      storage.delete(bucketName, getFullUploadObject(uniqueFileName));
    } catch (Exception e) {
      throw new BackupException("Error with removing backup file: " + e.getMessage(), e);
    }
  }

  private void parseUploadFilePath(String uploadFilePath) throws GcpUriParseException {
    try {
      validatePathParameter(uploadFilePath, "upload file path");
      List<String> segments = new URIBuilder().setPath(getUploadFilePath()).getPathSegments();
      projectName = segments.get(2);
      bucketName = segments.get(3);
      uploadObject = new URIBuilder()
              .setPathSegments(segments.subList(4, segments.size()))
              .getPath().substring(1);
      validatePathParameter(projectName, "project name");
      validatePathParameter(bucketName, "bucket name");
      validatePathParameter(uploadObject, "upload object");
    } catch (Exception e) {
      throw new GcpUriParseException("Invalid upload path: " + e.getMessage(), e);
    }
  }

  public String getProjectName() {
    return projectName;
  }

  public String getBucketName() {
    return bucketName;
  }

  public String getUploadObject() {
    return uploadObject;
  }

  public Credentials getCredential(String env) throws IOException {
    return GoogleCredentials
            .fromStream(new FileInputStream(System.getenv(env)));
  }

  private String getFullUploadObject(String uniqueFileName) {
    return new URIBuilder().setPathSegments(uploadObject, uniqueFileName)
            .getPath().substring(1);
  }

  private Storage getStorage() throws IOException {
    return StorageOptions.newBuilder().setCredentials(getCredential(ENV_CREDS))
            .setProjectId(projectName).build().getService();
  }

  class GcpUriParseException extends IOException {
    public GcpUriParseException(String message, Throwable cause) {
      super(message, cause);
    }

    public GcpUriParseException(String message) {
      super(message);
    }
  }
}
