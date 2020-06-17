package com.google.cloud.healthcare.imaging.dicomadapter;

import com.google.cloud.healthcare.DicomWebClient;
import com.google.cloud.healthcare.DicomWebClientJetty;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DicomWebPathValidationTest {

    @Test
    public void testGooglePath_rootValid() {
        new DicomWebClient(null,
                "https://healthcare.googleapis.com/_version/projects/_project/locations/_location/datasets/_dataset/dicomStores/_store/dicomWeb", "/studies");
    }

    @Test
    public void testGooglePath_rootValidExtraSlash() {
        new DicomWebClient(null,
                "https://healthcare.googleapis.com/_version/projects/_project/locations/_location/datasets/_dataset/dicomStores/_store/dicomWeb/", "/studies");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGooglePath_rootInvalid() {
        new DicomWebClient(null,
                "https://healthcare.googleapis.com/_version/projects/_project/locations/_location/datasets/_dataset/dicomStoresNOPE/_store/dicomWeb", "/studies");
    }

    @Test
    public void testGenericPath_validTypoInBeginning() {
        new DicomWebClient(null,
                "https://healthcare.zoogleapis.com/_version/projects/_project/locations/_location/datasets/_dataset/dicomStores/_store/dicomWeb", "/studies");
    }

    @Test
    public void testGenericPath_validThisIsAlsoTypo() {
        new DicomWebClient(null,
                "http://healthcare.googleapis.com/_version/projects/_project/locations/_location/datasets/_dataset/dicomStores/_store/dicomWeb", "/studies");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGooglePath_rootStudiesInvalid() {
        new DicomWebClient(null,
                "https://healthcare.googleapis.com/_version/projects/_project/locations/_location/datasets/_dataset/dicomStores/_store/dicomWeb/studies", "/studies");
    }

    @Test
    public void testGooglePath_stowValid() {
        new DicomWebClientJetty(null,
                "https://healthcare.googleapis.com/_version/projects/_project/locations/_location/datasets/_dataset/dicomStores/_store/dicomWeb/studies");
    }

    @Test
    public void testGooglePath_stowValidExtraSlash() {
        new DicomWebClientJetty(null,
                "https://healthcare.googleapis.com/_version/projects/_project/locations/_location/datasets/_dataset/dicomStores/_store/dicomWeb/studies/");
    }

    @Test
    public void testGenericPath_rootValid() {
        new DicomWebClient(null,
                "almost/anything/is/valid/", "/studies");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGenericPath_rootInvalid() {
        new DicomWebClient(null,
                "almost/anything/is/valid/except/ending/with/studies", "/studies");
    }

    @Test
    public void testGenericPath_stowStudiesValid() {
        new DicomWebClientJetty(null,
                "almost/anything/is/valid/but/it/must/end/with/studies");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGenericPath_stowStudiesInvalid() {
        new DicomWebClientJetty(null,
                "almost/anything/is/valid/but/not/this");
    }
}
