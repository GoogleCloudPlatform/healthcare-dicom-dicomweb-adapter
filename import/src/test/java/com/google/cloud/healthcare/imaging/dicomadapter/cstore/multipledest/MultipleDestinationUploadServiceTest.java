package com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.AetDictionary.Aet;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.BackupState;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.IBackupUploadService;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.IBackupUploader.BackupException;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest.sender.CStoreSender;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest.sender.CStoreSenderFactory;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest.IMultipleDestinationUploadService.MultipleDestinationUploadServiceException;
import com.google.common.collect.ImmutableList;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class MultipleDestinationUploadServiceTest {

  private final static int ASSOCIATION_ID = 1;
  private final static String SOP_CLASS_UID = "1.2.840.10008.5.1.4.1.1.4";
  private final static String SOP_INSTANCE_UID = "1.0.0.0";
  private final static String DICOM_DEST_UPLOAD_FAILED_MSG = "DicomDest upload failed";
  private final static String HEALTH_DEST_UPLOAD_FAILED_MSG = "HealthDest upload failed";
  private final static String HEALTH_DEST_EXCEPTION_EXCEPTION_MSG = "ExceptionEx thrown form other async run thread. healthDest";
  private final static String DICOM_DEST_EXCEPTION_EXCEPTION_MSG = "ExceptionEx thrown form other async run thread. dicomDest";

  @Rule
  public MockitoRule rule = MockitoJUnit.rule();

  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

  @Mock
  private CStoreSenderFactory cStoreSenderFactoryMock;

  @Mock
  private IBackupUploadService backupUploadServiceMock;

  @Mock
  private IDicomWebClient dicomWebClientMock;

  @Mock
  private CStoreSender cStoreSenderMock;

  @Mock
  private CompletableFuture healthcareDestUploadFutureMock;

  @Mock
  private CompletableFuture dicomDestUploadFutureMock;

  @Mock
  private Aet aetMock;

  private MultipleDestinationUploadService multipleDestinationUploadService;
  private InputStream inputStream;

  @Before
  public void before() {
    multipleDestinationUploadService = new MultipleDestinationUploadService(cStoreSenderFactoryMock, backupUploadServiceMock, 0, false);
    inputStream = new ByteArrayInputStream(new byte[]{1,2,3,4});

    when(cStoreSenderFactoryMock.create()).thenReturn(cStoreSenderMock);
  }

  @After
  public void after() throws IOException {
    inputStream.close();
  }

  @Test(expected = IllegalArgumentException.class)
  public void backupUploadServiceEmpty_exception() throws Exception {
    multipleDestinationUploadService = new MultipleDestinationUploadService(cStoreSenderFactoryMock, null, 0, false);
    multipleDestinationUploadService.start(ImmutableList.of(), ImmutableList.of(), inputStream, null, null, 0);
  }

  @Test
  public void exceptionOnCreateBackup_exception() throws Exception {
    exceptionRule.expect(MultipleDestinationUploadServiceException.class);
    exceptionRule.expectCause(instanceOf(BackupException.class));

    doThrow(new BackupException("createBackupFailed")).when(backupUploadServiceMock).createBackup(any(), any());

    multipleDestinationUploadService.start(ImmutableList.of(), ImmutableList.of(), inputStream, null, null, 0);
  }

  @Test
  public void healthDestPresent_success() throws Exception {
    doReturn(healthcareDestUploadFutureMock).when(backupUploadServiceMock).startUploading(any(IDicomWebClient.class), any(BackupState.class));

    doAnswer(invocation -> null).when(healthcareDestUploadFutureMock).get();

    multipleDestinationUploadService.start(ImmutableList.of(dicomWebClientMock), ImmutableList.of(), inputStream, SOP_CLASS_UID, SOP_INSTANCE_UID, ASSOCIATION_ID);

    verifyCreateBackup();
    verifyHealthDestStartUploading(1);
    verifyDicomDestStartUploadingNever();
    verifyRemoveBackup();
  }

  @Test
  public void bothDestPresent_success() throws Exception {
    doReturn(healthcareDestUploadFutureMock).when(backupUploadServiceMock).startUploading(any(IDicomWebClient.class), any(BackupState.class));
    doReturn(dicomDestUploadFutureMock).when(backupUploadServiceMock).startUploading(
        any(CStoreSender.class), any(Aet.class), anyString(), anyString(), any(BackupState.class));

    doAnswer(invocation -> null).when(healthcareDestUploadFutureMock).get();
    doAnswer(invocation -> null).when(dicomDestUploadFutureMock).get();

    multipleDestinationUploadService.start(ImmutableList.of(dicomWebClientMock), ImmutableList.of(aetMock, aetMock), inputStream, SOP_CLASS_UID, SOP_INSTANCE_UID, ASSOCIATION_ID);

    verifyCreateBackup();
    verifyHealthDestStartUploading(1);
    verifyDicomDestStartUploading(2);
    verifyRemoveBackup();
  }

  @Test
  public void dicomDestPresent_success() throws Exception {
    doReturn(dicomDestUploadFutureMock).when(backupUploadServiceMock).startUploading(
        any(CStoreSender.class), any(Aet.class), anyString(), anyString(), any(BackupState.class));

    doAnswer(invocation -> null).when(dicomDestUploadFutureMock).get();

    multipleDestinationUploadService.start(ImmutableList.of(), ImmutableList.of(aetMock), inputStream, SOP_CLASS_UID, SOP_INSTANCE_UID, ASSOCIATION_ID);

    verifyCreateBackup();
    verifyHealthDestStartUploadingNever();
    verifyDicomDestStartUploading(1);
    verifyRemoveBackup();
  }

  @Test
  public void backupExOnHealthDestUpload_uploadDicomDestAndException() throws Exception {
    exceptionRule.expect(MultipleDestinationUploadServiceException.class);
    exceptionRule.expectCause(instanceOf(BackupException.class));

    doThrow(new BackupException(HEALTH_DEST_UPLOAD_FAILED_MSG)).when(backupUploadServiceMock).startUploading(any(IDicomWebClient.class), any(BackupState.class));
    doReturn(dicomDestUploadFutureMock).when(backupUploadServiceMock).startUploading(
        any(CStoreSender.class), any(Aet.class), anyString(), anyString(), any(BackupState.class));

    doAnswer(invocation -> null).when(dicomDestUploadFutureMock).get();

    multipleDestinationUploadService.start(ImmutableList.of(dicomWebClientMock), ImmutableList.of(aetMock), inputStream, SOP_CLASS_UID, SOP_INSTANCE_UID, ASSOCIATION_ID);

    verifyCreateBackup();
    verifyHealthDestStartUploadingNever();
    verifyDicomDestStartUploading(1);
    verifyRemoveBackupNever();
  }

  @Test
  public void backupExOnDicomDestUpload_uploadHealthDestAndException() throws Exception {
    exceptionRule.expect(MultipleDestinationUploadServiceException.class);
    exceptionRule.expectCause(instanceOf(BackupException.class));

    doReturn(healthcareDestUploadFutureMock).when(backupUploadServiceMock).startUploading(any(IDicomWebClient.class), any(BackupState.class));
    doThrow(new BackupException(DICOM_DEST_UPLOAD_FAILED_MSG)).when(backupUploadServiceMock).startUploading(
        any(CStoreSender.class), any(Aet.class), anyString(), anyString(), any(BackupState.class));

    doAnswer(invocation -> null).when(healthcareDestUploadFutureMock).get();

    multipleDestinationUploadService.start(ImmutableList.of(dicomWebClientMock), ImmutableList.of(aetMock), inputStream, SOP_CLASS_UID, SOP_INSTANCE_UID, ASSOCIATION_ID);

    verifyCreateBackup();
    verifyHealthDestStartUploading(1);
    verifyDicomDestStartUploadingNever();
    verifyRemoveBackupNever();
  }

  @Test
  public void backupExOnDicomDestAndHealthDestUpload_firstException() throws Exception {
    exceptionRule.expect(MultipleDestinationUploadServiceException.class);
    exceptionRule.expectCause(new CauseMatcher(BackupException.class, HEALTH_DEST_UPLOAD_FAILED_MSG));

    doThrow(new BackupException(HEALTH_DEST_UPLOAD_FAILED_MSG)).when(backupUploadServiceMock).startUploading(any(IDicomWebClient.class), any(BackupState.class));
    doThrow(new BackupException(DICOM_DEST_UPLOAD_FAILED_MSG)).when(backupUploadServiceMock).startUploading(
        any(CStoreSender.class), any(Aet.class), anyString(), anyString(), any(BackupState.class));

    multipleDestinationUploadService.start(ImmutableList.of(dicomWebClientMock), ImmutableList.of(aetMock), inputStream, SOP_CLASS_UID, SOP_INSTANCE_UID, ASSOCIATION_ID);

    verifyCreateBackup();
    verifyHealthDestStartUploadingNever();
    verifyDicomDestStartUploadingNever();
    verifyRemoveBackupNever();
  }

  @Test
  public void executionExceptionsOnFutureGet_firstException() throws Exception {
    exceptionRule.expect(MultipleDestinationUploadServiceException.class);
    exceptionRule.expectCause(new CauseMatcher(BackupException.class, HEALTH_DEST_EXCEPTION_EXCEPTION_MSG));

    doReturn(healthcareDestUploadFutureMock).when(backupUploadServiceMock).startUploading(any(IDicomWebClient.class), any(BackupState.class));
    doReturn(dicomDestUploadFutureMock).when(backupUploadServiceMock).startUploading(
        any(CStoreSender.class), any(Aet.class), anyString(), anyString(), any(BackupState.class));

    doThrow(new ExecutionException(new BackupException(HEALTH_DEST_EXCEPTION_EXCEPTION_MSG))).when(healthcareDestUploadFutureMock).get();
    doThrow(new ExecutionException(new BackupException(DICOM_DEST_EXCEPTION_EXCEPTION_MSG))).when(dicomDestUploadFutureMock).get();

    multipleDestinationUploadService.start(ImmutableList.of(dicomWebClientMock), ImmutableList.of(aetMock, aetMock), inputStream, SOP_CLASS_UID, SOP_INSTANCE_UID, ASSOCIATION_ID);

    verifyCreateBackup();
    verifyHealthDestStartUploadingNever();
    verifyDicomDestStartUploadingNever();
    verifyRemoveBackupNever();
  }

  @Test
  public void backupExOnHealthDestUpload_interruptedExceptionsOnFutureGet_interruptedException() throws Exception {
    exceptionRule.expect(MultipleDestinationUploadServiceException.class);
    exceptionRule.expectCause(instanceOf(InterruptedException.class));

    doThrow(new BackupException(HEALTH_DEST_UPLOAD_FAILED_MSG)).when(backupUploadServiceMock).startUploading(any(IDicomWebClient.class), any(BackupState.class));
    doReturn(dicomDestUploadFutureMock).when(backupUploadServiceMock).startUploading(
        any(CStoreSender.class), any(Aet.class), anyString(), anyString(), any(BackupState.class));

    doThrow(new InterruptedException()).when(dicomDestUploadFutureMock).get();

    multipleDestinationUploadService.start(ImmutableList.of(dicomWebClientMock), ImmutableList.of(aetMock), inputStream, SOP_CLASS_UID, SOP_INSTANCE_UID, ASSOCIATION_ID);

    verifyCreateBackup();
    verifyHealthDestStartUploadingNever();
    verifyDicomDestStartUploadingNever();
    verifyRemoveBackupNever();
  }

  private void verifyHealthDestStartUploadingNever() throws BackupException {
    verify(backupUploadServiceMock, never()).startUploading(eq(dicomWebClientMock), any(BackupState.class));
  }

  private void verifyHealthDestStartUploading(int times) throws BackupException {
    verify(backupUploadServiceMock, times(times)).startUploading(eq(dicomWebClientMock), any(BackupState.class));
  }

  private void verifyDicomDestStartUploading(int times) throws BackupException {
    verify(backupUploadServiceMock, times(times)).startUploading(eq(cStoreSenderMock), any(Aet.class), eq(SOP_INSTANCE_UID), eq(SOP_CLASS_UID), any(BackupState.class));
  }

  private void verifyDicomDestStartUploadingNever() throws BackupException {
    verify(backupUploadServiceMock, never()).startUploading(eq(cStoreSenderMock), any(Aet.class), eq(SOP_INSTANCE_UID), eq(SOP_CLASS_UID), any(BackupState.class));
  }

  private void verifyCreateBackup() throws BackupException {
    verify(backupUploadServiceMock).createBackup(eq(inputStream), eq(SOP_INSTANCE_UID));
  }

  private void verifyRemoveBackup() {
    verify(backupUploadServiceMock).removeBackup(eq(SOP_INSTANCE_UID));
  }

  private void verifyRemoveBackupNever() {
    verify(backupUploadServiceMock, never()).removeBackup(eq(SOP_INSTANCE_UID));
  }

  private static class CauseMatcher extends TypeSafeMatcher<Throwable> {

    private final Class<? extends Throwable> type;
    private final String expectedMessage;

    public CauseMatcher(Class<? extends Throwable> type, String expectedMessage) {
      this.type = type;
      this.expectedMessage = expectedMessage;
    }

    @Override
    protected boolean matchesSafely(Throwable item) {
      return item.getClass().isAssignableFrom(type)
          && item.getMessage().contains(expectedMessage);
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("expects type ")
          .appendValue(type)
          .appendText(" and a message ")
          .appendValue(expectedMessage);
    }
  }
}