package com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.AetDictionary;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.BackupState;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.IBackupUploadService;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.IBackupUploader.BackupException;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest.sender.CStoreSender;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest.sender.CStoreSenderFactory;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.Event;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.MonitoringService;
import com.google.common.collect.ImmutableList;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultipleDestinationUploadService implements IMultipleDestinationUploadService {

  private static Random rand = new Random();
  private Logger log = LoggerFactory.getLogger(MultipleDestinationUploadService.class);

  private CStoreSenderFactory cStoreSenderFactory;
  private IBackupUploadService backupUploadService;
  private int attemptsAmount;
  private HashMap<Integer, HashMap<String, List<CompletableFuture>>> lazyFutureResolvers;
  private Boolean autoAckCStore;

  public MultipleDestinationUploadService(
      CStoreSenderFactory cStoreSenderFactory,
      IBackupUploadService backupUploadService,
      int attemptsAmount,
      Boolean autoAckCStore) {
    this.cStoreSenderFactory = cStoreSenderFactory;
    this.backupUploadService = backupUploadService;
    this.attemptsAmount = attemptsAmount;
    this.autoAckCStore = autoAckCStore;
    this.lazyFutureResolvers = new HashMap<Integer, HashMap<String, List<CompletableFuture>>>();
  }

  @Override
  public void start(
      ImmutableList<IDicomWebClient> healthcareDestinations,
      ImmutableList<AetDictionary.Aet> dicomDestinations,
      InputStream inputStream,
      String sopClassUID,
      String sopInstanceUID,
      int associationId)
      throws MultipleDestinationUploadServiceException {
    if (backupUploadService == null) {
      throw new IllegalArgumentException("backupUploadService is null. Some flags not set.");
    }

    List<Throwable> asyncUploadProcessingExceptions = new ArrayList<>();
    String uniqueFileName = String.format("%s_%s.dcm", sopInstanceUID, rand.nextInt(1000));

    try {
      backupUploadService.createBackup(inputStream, uniqueFileName);
    } catch (BackupException be) {
      MonitoringService.addEvent(Event.CSTORE_BACKUP_ERROR);
      log.error("{} processing failed.", this.getClass().getSimpleName(), be);
      throw new MultipleDestinationUploadServiceException(be);
    }

    List<CompletableFuture> uploadFutures = new ArrayList<>();

    CompletableFuture healthcareUploadFuture;
    for (IDicomWebClient healthcareDest : healthcareDestinations) {
      try {
        healthcareUploadFuture =
            backupUploadService.startUploading(
                healthcareDest, new BackupState(uniqueFileName, attemptsAmount));

        uploadFutures.add(healthcareUploadFuture);
      } catch (BackupException be) {
        log.error("Async upload to healthcareDest task not started.", be);
        asyncUploadProcessingExceptions.add(be);
      }
    }

    if (dicomDestinations.isEmpty() == false) {
      CStoreSender cStoreSender = cStoreSenderFactory.create();

      CompletableFuture dicomUploadFuture;
      for (AetDictionary.Aet dicomDest : dicomDestinations) {
        try {
          dicomUploadFuture =
              backupUploadService.startUploading(
                  cStoreSender,
                  dicomDest,
                  sopInstanceUID,
                  sopClassUID,
                  new BackupState(uniqueFileName, attemptsAmount));

          uploadFutures.add(dicomUploadFuture);
        } catch (BackupException be) {
          log.error("Async upload to dicomDest task not started.", be);
          asyncUploadProcessingExceptions.add(be);
        }
      }
    }

    // Don't wait on upload when we are auto-acknowledging and add lazy resolvers to cleanup the
    // files when the association closes.
    if (autoAckCStore) {
      addLazyResolvers(associationId, uniqueFileName, uploadFutures);
    } else {
      for (CompletableFuture uploadFuture : uploadFutures) {
        try {
          uploadFuture.get();
        } catch (ExecutionException eex) {
          log.error("Exception on asyncUpload Job processing.", eex);
          asyncUploadProcessingExceptions.add(eex.getCause());
        } catch (InterruptedException ie) {
          log.error("CStoreSender task interrupted. Upload tasks cancelled.", ie);
          Thread.currentThread().interrupt();
          throw new MultipleDestinationUploadServiceException(ie);
        }
      }
      if (asyncUploadProcessingExceptions.isEmpty()) {
        backupUploadService.removeBackup(uniqueFileName);
      }
    }

    if (!asyncUploadProcessingExceptions.isEmpty()) {
      log.error(
          "Exception messages of the upload async jobs:\n{}",
          asyncUploadProcessingExceptions.stream()
              .map(t -> t.getMessage())
              .collect(Collectors.joining("\n")));
      throw new MultipleDestinationUploadServiceException(asyncUploadProcessingExceptions.get(0));
    }
  }

  @Override
  public void cleanup(int associationId) {
    removeLazyResolvers(associationId);
  }

  /* Wait on all futures for this association and delete files with no exceptions. */
  private void waitOnUploadFutures(String uniqueFileName, List<CompletableFuture> futures) {
    List<Throwable> exceptions = new ArrayList<>();
    for (CompletableFuture uploadFuture : futures) {
      try {
        uploadFuture.get();
      } catch (ExecutionException eex) {
        exceptions.add(eex.getCause());
      } catch (InterruptedException iex) {
        Thread.currentThread().interrupt();
        exceptions.add(new MultipleDestinationUploadServiceException(iex).getCause());
      }
    }
    // If there are no exceptions, then we remove the file. Otherwise leave it for follow-up.
    if (exceptions.isEmpty()) {
      backupUploadService.removeBackup(uniqueFileName);
    } else {
      log.error(
          "Exception messages of the upload async jobs:\n{}",
          exceptions.stream().map(t -> t.getMessage()).collect(Collectors.joining("\n")));
    }
  }

  private void addLazyResolvers(
      int associationId, String uniqueFileName, List<CompletableFuture> futures) {
    HashMap<String, List<CompletableFuture>> resolverMap =
        lazyFutureResolvers.getOrDefault(
            associationId, new HashMap<String, List<CompletableFuture>>());
    resolverMap.put(uniqueFileName, futures);
    if (!lazyFutureResolvers.containsKey(associationId)) {
      lazyFutureResolvers.put(associationId, resolverMap);
    }
  }

  private void removeLazyResolvers(int associationId) {
    HashMap<String, List<CompletableFuture>> resolverMap = lazyFutureResolvers.get(associationId);
    if (resolverMap != null) {
      log.debug("Cleaning up " + resolverMap.size() + " files for association " + associationId);
      resolverMap.forEach((uniqueFileName, futures) -> {
            waitOnUploadFutures(uniqueFileName, futures);
          });
      lazyFutureResolvers.remove(associationId);
    }
  }
}
