package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import static com.google.cloud.healthcare.imaging.dicomadapter.backupuploader.IBackupUploader.BackupException;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.IDicomWebClient.DicomWebException;
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

@RunWith(JUnit4.class)
public class BackupUploadServiceTest {

  private final byte [] BACKUP_BYTES = {1, 2, 3, 4};
  private final String UNIQUE_FILE_NAME = "testUniqueFileName";
  private final int ATTEMPTS_AMOUNT_ZERO = 0;
  private final int ATTEMPTS_AMOUNT_ONE = 1;
  private final int ATTEMPTS_AMOUNT_TWO = 2;

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
  private BackupState spyBackupState = new BackupState(UNIQUE_FILE_NAME, ATTEMPTS_AMOUNT_TWO);

  private BackupUploadService backupUploadService;

  @Before
  public void before() {
    when(delayCalculatorMock.getAttemptsAmount()).thenReturn(ATTEMPTS_AMOUNT_ONE);
    backupUploadService = new BackupUploadService(backupUploaderMock, delayCalculatorMock);
    backupInputStream = new ByteArrayInputStream(BACKUP_BYTES);
  }

  @After
  public void after() throws IOException {
    backupInputStream.close();
  }

  @Test
  public void createBackup_success() throws BackupException {
    BackupState actualBackupState = backupUploadService.createBackup(backupInputStream, UNIQUE_FILE_NAME);

    verify(backupUploaderMock).doWriteBackup(eq(backupInputStream), eq(UNIQUE_FILE_NAME));
    assertThat(actualBackupState.getAttemptsCountdown()).isEqualTo(ATTEMPTS_AMOUNT_ONE);
    assertThat(actualBackupState.getUniqueFileName()).isEqualTo(UNIQUE_FILE_NAME);
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
  public void startUploading_firstTry_success() throws IOException, DicomWebException {
    doNothing().when(webClientMock).stowRs(any(InputStream.class));
    when(delayCalculatorMock.getExponentialDelayMillis(anyInt())).thenReturn(0L);
    when(backupUploaderMock.doReadBackup(eq(UNIQUE_FILE_NAME))).thenReturn(backupInputStream);

    backupUploadService.startUploading(webClientMock, spyBackupState);

    ArgumentCaptor<InputStream> argumentCaptor = ArgumentCaptor.forClass(InputStream.class);

    InOrder inOrder = Mockito.inOrder(webClientMock, backupUploaderMock);

    inOrder.verify(webClientMock).stowRs(argumentCaptor.capture());
    byte [] actualBytes = argumentCaptor.getValue().readNBytes(5);
    assertThat(Arrays.equals(actualBytes, BACKUP_BYTES)).isTrue();

    inOrder.verify(backupUploaderMock).doRemoveBackup(eq(UNIQUE_FILE_NAME));
  }

  @Test
  public void startUploading_DicomWebExceptionOnStowRs_secondTry_success() throws DicomWebException, IOException {
    doThrow(new DicomWebException("Reason")).doNothing().when(webClientMock).stowRs(any(InputStream.class));
    when(delayCalculatorMock.getExponentialDelayMillis(anyInt())).thenReturn(0L);
    when(backupUploaderMock.doReadBackup(eq(UNIQUE_FILE_NAME))).thenReturn(backupInputStream);

    backupUploadService.startUploading(webClientMock, spyBackupState);

    ArgumentCaptor<InputStream> argumentCaptor = ArgumentCaptor.forClass(InputStream.class);

    verify(backupUploaderMock, times(2)).doReadBackup(eq(UNIQUE_FILE_NAME));
    verify(webClientMock, times(2)).stowRs(argumentCaptor.capture());
    byte [] actualBytes = argumentCaptor.getValue().readNBytes(5);
    assertThat(Arrays.equals(actualBytes, BACKUP_BYTES)).isTrue();

    verify(backupUploaderMock, times(1)).doRemoveBackup(eq(UNIQUE_FILE_NAME));
  }

  @Test
  public void startUploading_BackupExceptionOnDoReadBackup_failed() throws BackupException {
    String exMsg = "reason";
    when(delayCalculatorMock.getExponentialDelayMillis(anyInt())).thenReturn(100L);
    when(backupUploaderMock.doReadBackup(eq(UNIQUE_FILE_NAME))).thenThrow(new BackupException(exMsg));

    exceptionRule.expect(BackupException.class);
    exceptionRule.expectMessage(exMsg);

    backupUploadService.startUploading(webClientMock, spyBackupState);
  }

  @Test
  public void startUploading_noMoreTry_BackupException() throws BackupException {
    spyBackupState = new BackupState(UNIQUE_FILE_NAME, ATTEMPTS_AMOUNT_ZERO);

    when(backupUploaderMock.doReadBackup(eq(UNIQUE_FILE_NAME))).thenReturn(backupInputStream);

    exceptionRule.expect(BackupException.class);
    exceptionRule.expectMessage("sopInstanceUID=" + UNIQUE_FILE_NAME + ". No resend attempt left.");

    backupUploadService.startUploading(webClientMock, spyBackupState);
  }

  @Test
  public void startUploading_lastAttempt_noNewSchedule_BackupException() throws IOException, DicomWebException {
    spyBackupState = new BackupState(UNIQUE_FILE_NAME, ATTEMPTS_AMOUNT_ONE);

    doThrow(new DicomWebException("Reason2")).doNothing().when(webClientMock).stowRs(any(InputStream.class));
    when(delayCalculatorMock.getExponentialDelayMillis(anyInt())).thenReturn(0L);
    when(backupUploaderMock.doReadBackup(eq(UNIQUE_FILE_NAME))).thenReturn(backupInputStream);

    exceptionRule.expect(BackupException.class);
    exceptionRule.expectMessage("sopInstanceUID=" + UNIQUE_FILE_NAME + ". No resend attempt left.");

    backupUploadService.startUploading(webClientMock, spyBackupState);

    verify(backupUploaderMock, times(1)).doReadBackup(eq(UNIQUE_FILE_NAME));
    verify(webClientMock, times(1)).stowRs(any(InputStream.class));
    verify(backupUploaderMock, never()).doRemoveBackup(eq(UNIQUE_FILE_NAME));
  }

  @Test
  public void startUploading_DicomWebException409Code_failed() throws DicomWebException, IOException {
    doThrow(new DicomWebException("conflictTestCode409", HttpStatus.CONFLICT_409, HttpStatusCodes.STATUS_CODE_BAD_REQUEST))
        .when(webClientMock).stowRs(any(InputStream.class));
    when(delayCalculatorMock.getExponentialDelayMillis(anyInt())).thenReturn(0L);
    when(backupUploaderMock.doReadBackup(eq(UNIQUE_FILE_NAME))).thenReturn(backupInputStream);

    exceptionRule.expect(BackupException.class);
    exceptionRule.expectMessage("Failed on httpStatus=409; sopInstanceUID=" + UNIQUE_FILE_NAME);

    backupUploadService.startUploading(webClientMock, spyBackupState);

    verify(backupUploaderMock, times(2)).doWriteBackup(any(InputStream.class), eq(UNIQUE_FILE_NAME));
    verify(backupUploaderMock, times(2)).doReadBackup(eq(UNIQUE_FILE_NAME));
    verify(webClientMock, times(2)).stowRs(any(InputStream.class));
    verify(backupUploaderMock, times(1)).doRemoveBackup(eq(UNIQUE_FILE_NAME));
  }

  @Test
  public void startUploading_DicomWebException500CodeThen501Code_noMoreTry_BackupException() throws IOException, DicomWebException {
      doThrow(new DicomWebException("testCode500", HttpStatus.INTERNAL_SERVER_ERROR_500, HttpStatusCodes.STATUS_CODE_SERVER_ERROR))
        .doThrow(new DicomWebException("testCode502", HttpStatus.BAD_GATEWAY_502, HttpStatusCodes.STATUS_CODE_BAD_GATEWAY))
        .doNothing()
            .when(webClientMock).stowRs(any(InputStream.class));
    when(delayCalculatorMock.getExponentialDelayMillis(anyInt())).thenReturn(0L);
    when(backupUploaderMock.doReadBackup(eq(UNIQUE_FILE_NAME))).thenReturn(backupInputStream);

    exceptionRule.expect(BackupException.class);
    exceptionRule.expectMessage("sopInstanceUID=" + UNIQUE_FILE_NAME + ". No resend attempt left.");

    backupUploadService.startUploading(webClientMock, spyBackupState);

    verify(backupUploaderMock, times(3)).doWriteBackup(any(InputStream.class), eq(UNIQUE_FILE_NAME));
    verify(backupUploaderMock, times(3)).doReadBackup(eq(UNIQUE_FILE_NAME));
    verify(webClientMock, times(3)).stowRs(any(InputStream.class));
    verify(backupUploaderMock, times(1)).doRemoveBackup(eq(UNIQUE_FILE_NAME));
  }
}
