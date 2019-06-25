# Integration test

Creates a dicom store, builds & runs adapter, builds & runs store-scu against it, downloads resulting dicom file from healthcare api and compares with original, deletes dicom store.

## How to run

Local run:

```shell
cloud-build-local --config=integration_test/cloudbuild-integration-test.yaml --dryrun=false .
```

Cloud run:

```shell
gcloud builds submit --config=integration_test/cloudbuild-integration-test.yaml .
```

Can add --substitutions=_STORE_NAME=your_custom_name parameter to use different name for test storage.
Without this, concurrent integration tests will fail.

