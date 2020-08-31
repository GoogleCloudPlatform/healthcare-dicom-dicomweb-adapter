package com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.AetDictionary;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.IBackupUploader;
import com.google.common.collect.ImmutableList;
import java.io.InputStream;

public interface IMultipleDestinationSendService {

  void start(ImmutableList<IDicomWebClient> healthcareDestinations,
             ImmutableList<AetDictionary.Aet> dicomDestinations,
             InputStream inputStream,
             String sopClassUID,
             String sopInstanceUID) throws IBackupUploader.BackupException, MultipleDestinationSendService.MultipleDestinationSendServiceException;
}
