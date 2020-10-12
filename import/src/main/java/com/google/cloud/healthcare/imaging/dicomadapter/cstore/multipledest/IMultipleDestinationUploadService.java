package com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.AetDictionary;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.IBackupUploader;
import com.google.common.collect.ImmutableList;
import java.io.InputStream;

public interface IMultipleDestinationUploadService {

  void start(ImmutableList<IDicomWebClient> healthcareDestinations,
             ImmutableList<AetDictionary.Aet> dicomDestinations,
             InputStream inputStream,
             String sopClassUID,
             String sopInstanceUID) throws MultipleDestinationUploadServiceException;

  class MultipleDestinationUploadServiceException extends Exception {

    private Integer dicomStatus;

    public MultipleDestinationUploadServiceException(Throwable cause) {
      super(cause);
    }

    public MultipleDestinationUploadServiceException(IBackupUploader.BackupException be) {
     super(be);
     this.dicomStatus = be.getDicomStatus();
    }

    public Integer getDicomStatus() {
      return dicomStatus;
    }
  }
}
