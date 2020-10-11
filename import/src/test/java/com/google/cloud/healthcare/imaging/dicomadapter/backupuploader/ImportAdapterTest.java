package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import com.google.cloud.healthcare.imaging.dicomadapter.ImportAdapter;
import org.junit.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class ImportAdapterTest {
  @Test
  public void shutdownHookCheck_systemExitAfterStart() throws IOException, GeneralSecurityException, InterruptedException {
    String [] args = {"--dimse_aet=IMPORTADAPTER", "--dimse_port=2576", "--persistent_file_storage_location=C:\\workspace\\fork_healthcare-dicom-dicomweb-adapter\\backup",
        "--dicomweb_address='https://healthcare.googleapis.com/v1beta1/projects/dev-idg-uvs/locations/us-central1/datasets/test-multiple-destination/dicomStores/destinationC/dicomWeb'" +
            " --persistent_file_upload_retry_amount=2", "--oauth_scopes=https://www.googleapis.com/auth/cloud-healthcare", "--verbose"};
    ImportAdapter.main(args);
    Thread.sleep(5000);
    System.exit(0);
  }
}
