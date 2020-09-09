package com.google.cloud.healthcare.imaging.dicomadapter.cstore.destination;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.AetDictionary;
import com.google.cloud.healthcare.imaging.dicomadapter.DestinationFilter;
import com.google.cloud.healthcare.imaging.dicomadapter.ImportAdapter.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CountingInputStream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.io.DicomInputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class DestinationClientFactoryTest {

  private final String DEFAULT_DESTINATION_CONFIG_FILTER = "SOPInstanceUID=1.0.0.0&StudyDate=18921109";
  private final String DESTINATION_CONFIG_FILTER_WITH_AE_TITLE_FIRST = "AETitle=testCallingAet1&SOPInstanceUID=1.0.0.0";
  private final String DESTINATION_CONFIG_FILTER_WITH_AE_TITLE_SECOND = "AETitle=testCallingAet2&SOPInstanceUID=1.0.0.0";
  private final String CALLING_AE_TITLE_FIRST = "testCallingAet1";
  private final String CALLING_AE_TITLE_SECOND = "testCallingAet2";
  private final byte [] BYTES_TO_SEND = new byte [] {1,2,3,4};
  private InputStream inputStream;

  @Rule
  public MockitoRule rule = MockitoJUnit.rule();

  @Mock
  private IDicomWebClient dicomWebClientMockFirst;

  @Mock
  private IDicomWebClient dicomWebClientMockSecond;

  @Mock
  private IDicomWebClient dicomWebClientMockThird;

  @Mock
  private IDicomWebClient defaultDicomWebClientMock;

  @Mock
  private AetDictionary.Aet aetMockFirst;

  @Mock
  private AetDictionary.Aet aetMockSecond;

  @Mock
  private AetDictionary.Aet aetMockThird;

  @Mock
  private DicomInputStream dicomInputStreamMock;

  @Mock
  private Attributes attributesMock;

  @Before
  public void before() {
    inputStream = new ByteArrayInputStream(BYTES_TO_SEND);
  }

  @After
  public void after() throws IOException {
    inputStream.close();
  }

  @Test
  public void singleDestinationClientFactory_healthDestinationsPresent_success() throws IOException {
    ImmutableList<Pair<DestinationFilter, IDicomWebClient>> healthDestinations = ImmutableList.of(
        new Pair(
            new DestinationFilter(DEFAULT_DESTINATION_CONFIG_FILTER),
            dicomWebClientMockFirst));

    SingleDestinationClientFactory destinationFactory = new SingleDestinationClientFactory(
        healthDestinations,
        defaultDicomWebClientMock);

    SingleDestinationClientFactory destinationFactorySpy = spy(destinationFactory);

    doReturn(dicomInputStreamMock).when(destinationFactorySpy).createDicomInputStream(any(InputStream.class));
    doReturn(attributesMock).when(dicomInputStreamMock).readDataset(anyInt(), anyInt());
    doReturn(true).when(attributesMock).matches(any(Attributes.class), anyBoolean(), anyBoolean());

    DestinationHolder destinationHolder = destinationFactorySpy.create(CALLING_AE_TITLE_FIRST, inputStream);

    assertThat(destinationHolder.getSingleDestination()).isEqualTo(dicomWebClientMockFirst);
    assertThat(destinationHolder.getCountingInputStream()).isInstanceOf(CountingInputStream.class);
  }

  @Test
  public void singleDestinationClientFactory_defaultWebClientUsage_success() throws IOException {

    SingleDestinationClientFactory destinationFactory = new SingleDestinationClientFactory(
        null,
        defaultDicomWebClientMock);

    DestinationHolder destinationHolder = destinationFactory.create(
        CALLING_AE_TITLE_FIRST,
        inputStream);

    assertThat(destinationHolder.getSingleDestination()).isEqualTo(defaultDicomWebClientMock);
    assertThat(destinationHolder.getCountingInputStream()).isInstanceOf(CountingInputStream.class);
    assertThat(destinationHolder.getCountingInputStream().readAllBytes()).isEqualTo(BYTES_TO_SEND);
  }

  @Test
  public void multipleDestinationClientFactory_BothDestinationsPresent_success() throws IOException {
    ImmutableList<Pair<DestinationFilter, IDicomWebClient>> healthcareDestinations = ImmutableList.of(
        new Pair(new DestinationFilter(DESTINATION_CONFIG_FILTER_WITH_AE_TITLE_FIRST), dicomWebClientMockFirst),  // match aet + attrs
        new Pair(new DestinationFilter(DESTINATION_CONFIG_FILTER_WITH_AE_TITLE_FIRST), dicomWebClientMockSecond), // match aet + attrs
        new Pair(new DestinationFilter(DESTINATION_CONFIG_FILTER_WITH_AE_TITLE_SECOND), dicomWebClientMockThird), // not math
        new Pair(new DestinationFilter(DEFAULT_DESTINATION_CONFIG_FILTER), dicomWebClientMockSecond)); // aet null / match attrs

    ImmutableList<Pair<DestinationFilter, AetDictionary.Aet>> dicomDestinations = ImmutableList.of(
        new Pair(new DestinationFilter(DESTINATION_CONFIG_FILTER_WITH_AE_TITLE_SECOND), aetMockThird),  // not math
        new Pair(new DestinationFilter(DESTINATION_CONFIG_FILTER_WITH_AE_TITLE_FIRST), aetMockFirst),   // match aet + attrs
        new Pair(new DestinationFilter(DEFAULT_DESTINATION_CONFIG_FILTER), aetMockFirst),               // aet null / match attrs
        new Pair(new DestinationFilter(DESTINATION_CONFIG_FILTER_WITH_AE_TITLE_FIRST), aetMockSecond)); // match aet + attrs

    MultipleDestinationClientFactory destinationFactory = new MultipleDestinationClientFactory(
        healthcareDestinations,
        dicomDestinations,
        defaultDicomWebClientMock);

    MultipleDestinationClientFactory destinationFactorySpy = spy(destinationFactory);

    doReturn(dicomInputStreamMock).when(destinationFactorySpy).createDicomInputStream(any(InputStream.class));
    doReturn(attributesMock).when(dicomInputStreamMock).readDataset(anyInt(), anyInt());
    doReturn(true).when(attributesMock).matches(any(Attributes.class), anyBoolean(), anyBoolean());

    DestinationHolder destinationHolder = destinationFactorySpy.create(CALLING_AE_TITLE_FIRST, inputStream);

    assertThat(destinationHolder.getHealthcareDestinations()).containsExactly(dicomWebClientMockFirst, dicomWebClientMockSecond, dicomWebClientMockSecond);
    assertThat(destinationHolder.getDicomDestinations()).containsExactly(aetMockFirst, aetMockFirst, aetMockSecond);
    assertThat(destinationHolder.getCountingInputStream()).isInstanceOf(CountingInputStream.class);
  }

  @Test
  public void multipleDestinationClientFactory_dicomDestinationsPresent_success() throws IOException {
    ImmutableList<Pair<DestinationFilter, AetDictionary.Aet>> dicomDestinations = ImmutableList.of(
        new Pair(new DestinationFilter(DESTINATION_CONFIG_FILTER_WITH_AE_TITLE_FIRST), aetMockFirst), //match aet + attrs fails
        new Pair(new DestinationFilter(DEFAULT_DESTINATION_CONFIG_FILTER), aetMockSecond));           // aet null + match attrs
    MultipleDestinationClientFactory destinationFactory = new MultipleDestinationClientFactory(
        null,
        dicomDestinations,
        defaultDicomWebClientMock);

    MultipleDestinationClientFactory destinationFactorySpy = spy(destinationFactory);

    doReturn(dicomInputStreamMock).when(destinationFactorySpy).createDicomInputStream(any(InputStream.class));
    doReturn(attributesMock).when(dicomInputStreamMock).readDataset(anyInt(), anyInt());
    doReturn(false)
        .doReturn(true).when(attributesMock).matches(any(Attributes.class), anyBoolean(), anyBoolean());

    DestinationHolder destinationHolder = destinationFactorySpy.create(CALLING_AE_TITLE_FIRST, inputStream);

    assertThat(destinationHolder.getHealthcareDestinations()).containsExactly(defaultDicomWebClientMock);
    assertThat(destinationHolder.getDicomDestinations()).containsExactly(aetMockSecond);
    assertThat(destinationHolder.getCountingInputStream()).isInstanceOf(CountingInputStream.class);
  }

  @Test
  public void multipleDestinationClientFactory_healthDestinationsPresent_success() throws IOException {
    ImmutableList<Pair<DestinationFilter, IDicomWebClient>> healthcareDestinations = ImmutableList.of(
        new Pair(new DestinationFilter(DESTINATION_CONFIG_FILTER_WITH_AE_TITLE_FIRST), dicomWebClientMockFirst), //match aet + attrs fails
        new Pair(new DestinationFilter(DEFAULT_DESTINATION_CONFIG_FILTER), dicomWebClientMockSecond));           // aet null + match attrs

    MultipleDestinationClientFactory destinationFactory = new MultipleDestinationClientFactory(
        healthcareDestinations,
        null,
        defaultDicomWebClientMock);

    MultipleDestinationClientFactory destinationFactorySpy = spy(destinationFactory);

    doReturn(dicomInputStreamMock).when(destinationFactorySpy).createDicomInputStream(any(InputStream.class));
    doReturn(attributesMock).when(dicomInputStreamMock).readDataset(anyInt(), anyInt());
    doReturn(false)
        .doReturn(true).when(attributesMock).matches(any(Attributes.class), anyBoolean(), anyBoolean());

    DestinationHolder destinationHolder = destinationFactorySpy.create(CALLING_AE_TITLE_FIRST, inputStream);

    assertThat(destinationHolder.getHealthcareDestinations()).containsExactly(dicomWebClientMockSecond);
    assertThat(destinationHolder.getDicomDestinations()).isEmpty();
    assertThat(destinationHolder.getCountingInputStream()).isInstanceOf(CountingInputStream.class);
  }

   @Test
  public void multipleDestinationClientFactory_defaultWebClientUsage_success() throws IOException {
    MultipleDestinationClientFactory destinationFactory = new MultipleDestinationClientFactory(
        null,
        null,
        defaultDicomWebClientMock);

    DestinationHolder destinationHolder = destinationFactory.create(CALLING_AE_TITLE_FIRST, inputStream);

    assertThat(destinationHolder.getHealthcareDestinations()).containsExactly(defaultDicomWebClientMock);
    assertThat(destinationHolder.getDicomDestinations()).isEmpty();
    assertThat(destinationHolder.getCountingInputStream()).isInstanceOf(CountingInputStream.class);
  }
}