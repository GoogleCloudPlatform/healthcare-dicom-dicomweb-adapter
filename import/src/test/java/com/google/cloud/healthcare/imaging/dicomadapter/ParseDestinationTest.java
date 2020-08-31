package com.google.cloud.healthcare.imaging.dicomadapter;

import com.google.cloud.healthcare.DicomWebClientJetty;
import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.StringUtil;
import org.junit.Test;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

public class ParseDestinationTest {
  private final static String JSON = "[{\"filter\": \"AETitle=DEVICE_A\", \"dicomweb_destination\": \"https://healthcare.googleapis.com/v1beta1/projects/your-project/locations/your-location/datasets/your-dataset/dicomStores/your-store/dicomWeb\" }, {\"filter\": \"AETitle=DEVICE_B\", \"name\": \"DEVICE_B\", \"host\": \"192.168.0.1\", \"port\": 11114 }, {\"filter\": \"AETitle=DEVICE_C\", \"dicomweb_destination\": \"https://healthcare.googleapis.com/v1beta1/projects/your-project/locations/your-location/datasets/your-dataset/dicomStores/your-store/dicomWeb\" }]";
  private final String DICOM_TITLE = "AETitle=DEVICE_B";
  private final String HEALTHCARE_TITLE = "AETitle=DEVICE_A";
  private final String VALID_HOST = "192.168.0.1";
  private final String VALID_NAME = "DEVICE_B";
  private final int VALID_PORT = 11114;

  @Test
  public void parseDestination() throws IOException {
    ImportAdapter.Pair<Map<DestinationFilter, AetDictionary.Aet>, Map<DestinationFilter, IDicomWebClient>> dsc
        = ImportAdapter.configureMultipleDestinationTypesMap(JSON, "", "", null);
    Map<DestinationFilter, AetDictionary.Aet> dicomMap = dsc.getLeft();
    assertThat(dicomMap.size()).isEqualTo(1);
    AetDictionary.Aet aet = dicomMap.get(new DestinationFilter(StringUtil.trim(DICOM_TITLE)));
    assertThat(aet.getHost()).isEqualTo(VALID_HOST);
    assertThat(aet.getName()).isEqualTo(VALID_NAME);
    assertThat(aet.getPort()).isEqualTo(VALID_PORT);

    Map<DestinationFilter, IDicomWebClient> healthcareMap = dsc.getRight();
    assertThat(healthcareMap.size()).isEqualTo(2);
    DicomWebClientJetty dicomWebClientJetty =
        (DicomWebClientJetty) healthcareMap.get(new DestinationFilter(StringUtil.trim(HEALTHCARE_TITLE)));
    assertThat(dicomWebClientJetty).isNotNull();
  }
}