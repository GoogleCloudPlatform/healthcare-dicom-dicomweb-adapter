package com.google.cloud.healthcare.imaging.dicomadapter;

import com.google.cloud.healthcare.imaging.dicomadapter.ImportAdapter.Pair;
import com.google.cloud.healthcare.imaging.dicomadapter.AetDictionary.Aet;
import com.google.cloud.healthcare.DicomWebClientJetty;
import com.google.cloud.healthcare.IDicomWebClient;
import com.google.common.collect.ImmutableList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class ParseDestinationTest {
  private final static String JSON = "[" +
      "{'filter': 'AETitle=DEVICE_A', 'dicomweb_destination': 'https://healthcare.googleapis.com/v1beta1/projects/your-project/locations/your-location/datasets/your-dataset/dicomStores/your-store/dicomWeb' }, " +
      "{'filter': 'AETitle=DEVICE_B', 'name': 'DEVICE_B', 'host': '192.168.0.1', 'port': 11114 }, " +
      "{'filter': 'AETitle=DEVICE_C', 'dicomweb_destination': 'https://healthcare.googleapis.com/v1beta1/projects/your-project/locations/your-location/datasets/your-dataset/dicomStores/your-store/dicomWeb' }, " +
      "{'filter': 'AETitle=DEVICE_C', 'dicomweb_destination': 'https://healthcare.googleapis.com/v1beta1/projects/your-project/locations/your-location/datasets/your-dataset/dicomStores/your-second-store/dicomWeb' }" +
      "]";
  private final static String JSON_WITHOUT_FILTER_KEY = "[{'name': 'DEVICE_A', 'host': 'localhost', 'port': 11113},{'name': 'DEVICE_B', 'host': '192.168.0.1', 'port': 11114}]";
  private final String DICOM_TITLE = "AETitle=DEVICE_B";
  private final String HEALTHCARE_TITLE_A = "AETitle=DEVICE_A";
  private final String HEALTHCARE_TITLE_C = "AETitle=DEVICE_C";
  private final String VALID_HOST = "192.168.0.1";
  private final String VALID_NAME = "DEVICE_B";
  private final int VALID_PORT = 11114;

  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

  @Test
  public void parseDestination() throws IOException {
    Pair<ImmutableList<Pair<DestinationFilter, IDicomWebClient>>,
        ImmutableList<Pair<DestinationFilter, AetDictionary.Aet>>> dsc = ImportAdapter.configureMultipleDestinationTypesMap(
            JSON, "", "", null);

    ImmutableList<Pair<DestinationFilter, IDicomWebClient>> healthDistList = dsc.getLeft();
    assertThat(healthDistList).hasSize(3);

    assertDestFilterAndClient(healthDistList.get(0), HEALTHCARE_TITLE_A);
    assertDestFilterAndClient(healthDistList.get(1), HEALTHCARE_TITLE_C);
    assertDestFilterAndClient(healthDistList.get(2), HEALTHCARE_TITLE_C);

    ImmutableList<Pair<DestinationFilter, AetDictionary.Aet>> dicomList = dsc.getRight();
    assertThat(dicomList).hasSize(1);
    Pair<DestinationFilter, AetDictionary.Aet> dicomDestPair = dicomList.get(0);
    Aet aet = dicomDestPair.getRight();

    assertThat(dicomDestPair.getLeft()).isEqualTo(new DestinationFilter((DICOM_TITLE)));
    assertThat(aet.getHost()).isEqualTo(VALID_HOST);
    assertThat(aet.getName()).isEqualTo(VALID_NAME);
    assertThat(aet.getPort()).isEqualTo(VALID_PORT);
  }

  @Test
  public void parseDestinationWithoutFilterKey() throws IOException {
    exceptionRule.expect(IOException.class);
    exceptionRule.expectMessage("Mandatory key absent: filter");

    ImportAdapter.configureMultipleDestinationTypesMap(JSON_WITHOUT_FILTER_KEY, "", "", null);
  }

  private void assertDestFilterAndClient(Pair<DestinationFilter, IDicomWebClient> distToClient, String aet) {
    assertThat(distToClient.getLeft()).isEqualTo(new DestinationFilter(aet));
    assertThat(distToClient.getRight()).isInstanceOf(DicomWebClientJetty.class);
    assertThat(distToClient.getRight()).isNotNull();
  }
}