package com.google.cloud.healthcare;

import com.google.cloud.healthcare.DicomWebClient;
import com.google.cloud.healthcare.DicomWebClientJetty;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DicomWebPathValidationTest {

    @Test
    public void testGooglePath_rootValid() {
        DicomWebValidation.validatePath(
                "https://healthcare.googleapis.com/_version/projects/_project/locations/_location/datasets/_dataset/dicomStores/_store/dicomWeb", DicomWebValidation.DICOMWEB_ROOT_VALIDATION);
    }

    @Test
    public void testGooglePath_rootValidExtraSlash() {
        DicomWebValidation.validatePath(
                "https://healthcare.googleapis.com/_version/projects/_project/locations/_location/datasets/_dataset/dicomStores/_store/dicomWeb/", DicomWebValidation.DICOMWEB_ROOT_VALIDATION);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGooglePath_rootInvalid() {
        DicomWebValidation.validatePath(
                "https://healthcare.googleapis.com/_version/projects/_project/locations/_location/datasets/_dataset/dicomStoresNOPE/_store/dicomWeb", DicomWebValidation.DICOMWEB_ROOT_VALIDATION);
    }

    @Test
    public void testGenericPath_validTypoInBeginning() {
        DicomWebValidation.validatePath(
                "https://healthcare.zoogleapis.com/_version/projects/_project/locations/_location/datasets/_dataset/dicomStores/_store/dicomWeb", DicomWebValidation.DICOMWEB_ROOT_VALIDATION);
    }

    @Test
    public void testGenericPath_validThisIsAlsoTypo() {
        DicomWebValidation.validatePath(
                "http://healthcare.googleapis.com/_version/projects/_project/locations/_location/datasets/_dataset/dicomStores/_store/dicomWeb", DicomWebValidation.DICOMWEB_ROOT_VALIDATION);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGooglePath_rootStudiesInvalid() {
        DicomWebValidation.validatePath(
                "https://healthcare.googleapis.com/_version/projects/_project/locations/_location/datasets/_dataset/dicomStores/_store/dicomWeb/studies", DicomWebValidation.DICOMWEB_ROOT_VALIDATION);
    }

    @Test
    public void testGenericPath_rootValid() {
        DicomWebValidation.validatePath(
                "almost/anything/is/valid/", DicomWebValidation.DICOMWEB_ROOT_VALIDATION);
    }

}
