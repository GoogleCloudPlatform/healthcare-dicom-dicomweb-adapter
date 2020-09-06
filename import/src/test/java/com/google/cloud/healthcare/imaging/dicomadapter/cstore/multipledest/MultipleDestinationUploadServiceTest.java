package com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.IDicomWebClient.DicomWebException;
import com.google.cloud.healthcare.imaging.dicomadapter.AetDictionary;
import com.google.cloud.healthcare.imaging.dicomadapter.AetDictionary.Aet;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.BackupState;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.backup.BackupUploadService;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest.sender.CStoreSender;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest.sender.CStoreSenderFactory;
import com.google.cloud.healthcare.imaging.dicomadapter.cstore.multipledest.IMultipleDestinationUploadService.MultipleDestinationUploadServiceException;
import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class MultipleDestinationUploadServiceTest {

  private final static String SOP_CLASS_UID = "1.2.840.10008.5.1.4.1.1.4";
  private final static String SOP_INSTANCE_UID = "1.0.0.0";
  private static final String BACKUP_FILE_NAME = "fileName";

  @Rule
  public MockitoRule rule = MockitoJUnit.rule();

  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

  @Mock
  private CStoreSenderFactory cStoreSenderFactoryMock;

  @Mock
  private BackupUploadService backupUploadServiceMock;

  @Mock
  private IDicomWebClient dicomWebClientMock;

  @Mock
  private CStoreSender cStoreSenderMock;

  @Mock
  private CompletableFuture healthcareDestUploadFutureMock;

  @Mock
  private CompletableFuture dicomDestUploadFutureMock;

  private MultipleDestinationUploadService multipleDestinationUploadService;
  private InputStream inputStream;

  @Before
  public void before() {
    multipleDestinationUploadService = new MultipleDestinationUploadService(cStoreSenderFactoryMock, backupUploadServiceMock, 0);
    inputStream = new ByteArrayInputStream(new byte[]{1,2,3,4});

    when(cStoreSenderFactoryMock.create()).thenReturn(cStoreSenderMock);
  }

  @After
  public void after() throws IOException {
    inputStream.close();
  }

  @Test(expected = IllegalArgumentException.class)
  public void backupUploadServiceEmpty_exception() throws Exception {
    multipleDestinationUploadService = new MultipleDestinationUploadService(cStoreSenderFactoryMock, null, 0);
    multipleDestinationUploadService.start(ImmutableList.of(), ImmutableList.of(), inputStream, null, null);
  }



  @Test
  public void t1() throws IOException, MultipleDestinationUploadServiceException, DicomWebException, InterruptedException, ExecutionException {
    doReturn(healthcareDestUploadFutureMock).when(backupUploadServiceMock).startUploading(any(IDicomWebClient.class), any(BackupState.class));
    doReturn(dicomDestUploadFutureMock).when(backupUploadServiceMock).startUploading(
        any(CStoreSender.class), any(Aet.class), anyString(), anyString(), any(BackupState.class));

    doAnswer(invocation -> null).when(healthcareDestUploadFutureMock).get();
    doAnswer(invocation -> null).when(dicomDestUploadFutureMock).get();

    multipleDestinationUploadService.start(ImmutableList.of(dicomWebClientMock), ImmutableList.of(), inputStream, SOP_CLASS_UID, BACKUP_FILE_NAME);

    verify(backupUploadServiceMock).createBackup(eq(inputStream), eq(BACKUP_FILE_NAME));
    verify(backupUploadServiceMock).startUploading(eq(dicomWebClientMock), any(BackupState.class));
    verify(backupUploadServiceMock, never()).startUploading(
        eq(cStoreSenderMock), any(Aet.class), eq(SOP_INSTANCE_UID), eq(SOP_CLASS_UID), any(BackupState.class));
    verify(backupUploadServiceMock).removeBackup(eq(BACKUP_FILE_NAME));

//    exceptionRule.expect(IBackupUploader.BackupException.class);
    //exceptionRule.expectMessage(EXPECTED_NO_RESEND_ATTEMPT_LEFT_LOG_MESSAGE);
  }
//
//  @Test
//  public void t1() {
//
//  }
//
//  @Test
//  public void t1() {
//
//  }
//
//  @Test
//  public void t1() {
//
//  }
//
//  @Test
//  public void t1() {
//
//  }
//
//  @Test
//  public void t1() {
//
//  }
//
//  @Test
//  public void t1() {
//
//  }
//
//  @Test
//  public void t1() {
//
//  }
//
//  @Test
//  public void t1() {
//
//  }
}