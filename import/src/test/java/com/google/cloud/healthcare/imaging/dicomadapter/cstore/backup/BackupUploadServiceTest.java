package com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup;

import static com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.IBackupUploader.BackupException;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.IDicomWebClient.DicomWebException;
import com.google.common.collect.ImmutableList;
import org.dcm4che3.net.Status;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RunWith(JUnit4.class)
public class BackupUploadServiceTest {

  private final byte [] BACKUP_BYTES = {1, 2, 3, 4};
  private final String UNIQUE_FILE_NAME = "testUniqueFileName";

  // Test emulation of the situation, when first send already done and flag persistent_file_upload_retry_amount=0
  private final int ATTEMPTS_AMOUNT_MINUS_ONE = -1;
  private final int ATTEMPTS_AMOUNT_ZERO = 0;
  private final int ATTEMPTS_AMOUNT_ONE = 1;
  private final String EXPECTED_NO_RESEND_ATTEMPT_LEFT_LOG_MESSAGE = "sopInstanceUID=" + UNIQUE_FILE_NAME + ". No upload attempt left.";

  private InputStream backupInputStream;

  @Rule
  public MockitoRule rule = MockitoJUnit.rule();

  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

  @Mock
  private IBackupUploader backupUploaderMock;

  @Mock
  private DelayCalculator delayCalculatorMock;

  @Mock
  private IDicomWebClient webClientMock;

  @Spy
  private BackupState spyBackupState = new BackupState(UNIQUE_FILE_NAME, ATTEMPTS_AMOUNT_ONE);

  private BackupUploadService backupUploadService;

  @Before
  public void before() {
    backupUploadService =
        new BackupUploadService(
            backupUploaderMock, ATTEMPTS_AMOUNT_ZERO, ImmutableList.of(), delayCalculatorMock);
    backupInputStream = new ByteArrayInputStream(BACKUP_BYTES);
  }

  @After
  public void after() throws IOException {
    backupInputStream.close();
  }

  @Test
  public void createBackup_success() throws BackupException {
    backupUploadService.createBackup(backupInputStream, UNIQUE_FILE_NAME);

    verify(backupUploaderMock).doWriteBackup(eq(backupInputStream), eq(UNIQUE_FILE_NAME));
  }

  @Test
  public void createBackup_failOnBackupException() throws BackupException {
    String exceptionMsg = "exMsg";

    exceptionRule.expect(BackupException.class);
    exceptionRule.expectMessage(exceptionMsg);

    doThrow(new BackupException(exceptionMsg)).when(backupUploaderMock).doWriteBackup(isA(InputStream.class), anyString());
    backupUploadService.createBackup(backupInputStream, UNIQUE_FILE_NAME);
  }

  @Test
  public void removeBackup_success() throws BackupException {
    backupUploadService.removeBackup(UNIQUE_FILE_NAME);

    verify(backupUploaderMock).doRemoveBackup(eq(UNIQUE_FILE_NAME));
  }

  @Test
  public void removeBackup_BackupException_notRethrowUpper() throws BackupException {
    doThrow(new BackupException("Err Message")).when(backupUploaderMock).doRemoveBackup(eq(UNIQUE_FILE_NAME));

    backupUploadService.removeBackup(UNIQUE_FILE_NAME);
    verify(backupUploaderMock).doRemoveBackup(eq(UNIQUE_FILE_NAME));
  }

  @Test
  public void startUploading_firstTry_success() throws Exception{
    doNothing().when(webClientMock).stowRs(any(InputStream.class));
    when(backupUploaderMock.doReadBackup(eq(UNIQUE_FILE_NAME))).thenReturn(backupInputStream);

    startUploadingAndWaitCompletion();

    ArgumentCaptor<InputStream> argumentCaptor = ArgumentCaptor.forClass(InputStream.class);

    InOrder inOrder = Mockito.inOrder(webClientMock, backupUploaderMock);

    inOrder.verify(webClientMock).stowRs(argumentCaptor.capture());
    byte [] actualBytes = argumentCaptor.getValue().readNBytes(5);
    assertThat(Arrays.equals(actualBytes, BACKUP_BYTES)).isTrue();
  }

  @Test
  public void startUploading_DicomWebExceptionOnStowRs_secondTry_success() throws Exception {
    doThrow(new DicomWebException("Reason", HttpStatus.INTERNAL_SERVER_ERROR_500, Status.ProcessingFailure))
        .doNothing().when(webClientMock).stowRs(any(InputStream.class));
    when(backupUploaderMock.doReadBackup(eq(UNIQUE_FILE_NAME))).thenReturn(backupInputStream);

    startUploadingAndWaitCompletion();

    ArgumentCaptor<InputStream> argumentCaptor = ArgumentCaptor.forClass(InputStream.class);

    verify(backupUploaderMock, times(2)).doReadBackup(eq(UNIQUE_FILE_NAME));
    verify(webClientMock, times(2)).stowRs(argumentCaptor.capture());
    byte [] actualBytes = argumentCaptor.getValue().readNBytes(5);
    assertThat(Arrays.equals(actualBytes, BACKUP_BYTES)).isTrue();

    verify(backupUploaderMock, never()).doRemoveBackup(eq(UNIQUE_FILE_NAME));
  }

  @Test
  public void startUploading_BackupExceptionOnDoReadBackup_failed() throws Exception {
    String exMsg = "reason";
    when(backupUploaderMock.doReadBackup(eq(UNIQUE_FILE_NAME))).thenThrow(new BackupException(exMsg));

    catchAndAssertBackupExceptionOnStartUploading(exMsg, null,1);
  }

  @Test
  public void startUploading_noMoreTry_BackupException() throws BackupException {
    spyBackupState = new BackupState(UNIQUE_FILE_NAME, ATTEMPTS_AMOUNT_MINUS_ONE);

    when(backupUploaderMock.doReadBackup(eq(UNIQUE_FILE_NAME))).thenReturn(backupInputStream);

    exceptionRule.expect(BackupException.class);
    exceptionRule.expectMessage(EXPECTED_NO_RESEND_ATTEMPT_LEFT_LOG_MESSAGE);

    backupUploadService.startUploading(webClientMock, spyBackupState);

    verify(backupUploaderMock, never()).doRemoveBackup(eq(UNIQUE_FILE_NAME));
  }

  @Test
  public void startUploading_lastAttempt_noNewSchedule_BackupException() throws IOException, DicomWebException {
    spyBackupState = new BackupState(UNIQUE_FILE_NAME, ATTEMPTS_AMOUNT_ZERO);

    doThrow(new DicomWebException("Reason2", HttpStatus.INTERNAL_SERVER_ERROR_500, Status.ProcessingFailure))
        .doNothing().when(webClientMock).stowRs(any(InputStream.class));
    when(backupUploaderMock.doReadBackup(eq(UNIQUE_FILE_NAME))).thenReturn(backupInputStream);

    catchAndAssertBackupExceptionOnStartUploading(EXPECTED_NO_RESEND_ATTEMPT_LEFT_LOG_MESSAGE, Status.ProcessingFailure, 1);

    verify(backupUploaderMock).doReadBackup(eq(UNIQUE_FILE_NAME));
    verify(webClientMock).stowRs(any(InputStream.class));
    verify(backupUploaderMock, never()).doRemoveBackup(eq(UNIQUE_FILE_NAME));
  }

  @Test
  public void startUploading_DicomWebException409Code_failed() throws DicomWebException, IOException {
    doThrow(new DicomWebException("conflictTestCode409", HttpStatus.CONFLICT_409, Status.ProcessingFailure))
        .when(webClientMock).stowRs(any(InputStream.class));
    when(backupUploaderMock.doReadBackup(eq(UNIQUE_FILE_NAME))).thenReturn(backupInputStream);

    catchAndAssertBackupExceptionOnStartUploading("Not retried due to HTTP code=" + HttpStatus.CONFLICT_409,
        Status.ProcessingFailure, 1);

    verify(backupUploaderMock).doReadBackup(eq(UNIQUE_FILE_NAME));
    verify(webClientMock).stowRs(any(InputStream.class));
    verify(backupUploaderMock, never()).doRemoveBackup(eq(UNIQUE_FILE_NAME));
  }

  @Test
  public void startUploading_DicomWebException500CodeThen501Code_noMoreTry_BackupException() throws IOException, DicomWebException {
      doThrow(new DicomWebException("testCode500", HttpStatus.INTERNAL_SERVER_ERROR_500, Status.ProcessingFailure))
        .doThrow(new DicomWebException("testCode502", HttpStatus.BAD_GATEWAY_502, Status.ProcessingFailure))
        .doNothing()
            .when(webClientMock).stowRs(any(InputStream.class));
    when(backupUploaderMock.doReadBackup(eq(UNIQUE_FILE_NAME))).thenReturn(backupInputStream);

    catchAndAssertBackupExceptionOnStartUploading(EXPECTED_NO_RESEND_ATTEMPT_LEFT_LOG_MESSAGE, Status.ProcessingFailure, 2);

    verify(backupUploaderMock, times(2)).doReadBackup(eq(UNIQUE_FILE_NAME));
    verify(webClientMock, times(2)).stowRs(any(InputStream.class));
    verify(backupUploaderMock, never()).doRemoveBackup(eq(UNIQUE_FILE_NAME));
  }

  @Test
  public void startUploading_retryOn_DicomWebException408Code_Success() throws Exception {
    backupUploadService =
        new BackupUploadService(
            backupUploaderMock, ATTEMPTS_AMOUNT_ONE, ImmutableList.of(408), delayCalculatorMock);

    doThrow(new DicomWebException(" Request Timeout 408", HttpStatus.REQUEST_TIMEOUT_408, HttpStatusCodes.STATUS_CODE_BAD_REQUEST))
        .doNothing()
        .when(webClientMock).stowRs(any(InputStream.class));
    when(backupUploaderMock.doReadBackup(eq(UNIQUE_FILE_NAME))).thenReturn(backupInputStream);

    startUploadingAndWaitCompletion();

    verify(backupUploaderMock, times(2)).doReadBackup(eq(UNIQUE_FILE_NAME));
    verify(webClientMock, times(2)).stowRs(any(InputStream.class));
    verify(backupUploaderMock, never()).doRemoveBackup(eq(UNIQUE_FILE_NAME));
  }

  private void catchAndAssertBackupExceptionOnStartUploading(String expectedMessage, Integer expectedDicomStatus, int recursiveCallTimes) {
    Throwable actualException = null;
    try {
      startUploadingAndWaitCompletion();
    } catch (Exception e) {
      actualException = e;
      for (int i = 0; i < recursiveCallTimes; i++){
        actualException = actualException.getCause();
      }
    }
    assertThat(actualException).isInstanceOf(BackupException.class);
    assertThat(actualException).hasMessageThat().contains(expectedMessage);
    if (expectedDicomStatus != null) {

      assertThat(((BackupException) actualException).getDicomStatus()).isEqualTo(expectedDicomStatus);
    }
  }

  private void startUploadingAndWaitCompletion() throws BackupException, InterruptedException, ExecutionException {
    CompletableFuture completableFuture = backupUploadService.startUploading(webClientMock, spyBackupState);
    completableFuture.get();
  }
}
