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

    @Test
    public void testValidateWadoRsInstancePath_storeRelativeValid() {
        DicomWebValidation.validateWadoRsInstancePath("/studies/1.2.3/series/4.5.6/instances/7.8.9");
        DicomWebValidation.validateWadoRsInstancePath("studies/1.2.3/series/4.5.6/instances/7.8.9");
    }

    @Test
    public void testValidateWadoRsInstancePath_resourceRelativeValid() {
        DicomWebValidation.validateWadoRsInstancePath(
                "projects/my-project/locations/us-central1/datasets/my-dataset/dicomStores/my-store/dicomWeb/studies/1.2.3/series/4.5.6/instances/7.8.9");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateWadoRsInstancePath_crossProjectTraversalRejected() {
        DicomWebValidation.validateWadoRsInstancePath(
                "../../../../../../../../target-project/locations/us-central1/datasets/d/dicomStores/s/dicomWeb/studies/1/series/2/instances/3");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateWadoRsInstancePath_siblingStoreTraversalRejected() {
        DicomWebValidation.validateWadoRsInstancePath(
                "../../other-store/dicomWeb/studies/1/series/2/instances/3");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateWadoRsInstancePath_percentEncodedTraversalRejected() {
        DicomWebValidation.validateWadoRsInstancePath(
                "studies/%2e%2e/series/2/instances/3");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateWadoRsInstancePath_encodedSlashRejected() {
        DicomWebValidation.validateWadoRsInstancePath(
                "studies/1/series/2/instances/..%2f..%2fother");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateWadoRsInstancePath_queryStringRejected() {
        DicomWebValidation.validateWadoRsInstancePath(
                "studies/1/series/2/instances/3?foo=bar");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateWadoRsInstancePath_fragmentRejected() {
        DicomWebValidation.validateWadoRsInstancePath(
                "studies/1/series/2/instances/3#frag");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateWadoRsInstancePath_nonInstancePathRejected() {
        DicomWebValidation.validateWadoRsInstancePath("studies/1/series/2");
    }

}
