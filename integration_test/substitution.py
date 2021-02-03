VERSION = 'v1'
PROJECT = 'gcp-healthcare-oss-test'
IMAGEPROJECT = 'cloud-healthcare-containers'
LOCATION = 'us-central1'
DATASET = "healthcare-dicom-dicomweb-adapter-test"
STORE_NAME = 'integration-test-store'
BUCKET = 'dicomweb-import-adapter-integration-test'
ADAPTER_PORT = '2575'
STORE_SCP_PORT = '2576'
CLOSE_STORE_SCP_PORT = '3001'
COMMITMENT_SCU_PORT = '4000'
ADAPTER_RUN_STEP = '0.0.0.0'
STORE_SCP_RUN_STEP = '0.0.0.0'
COMMITMENT_SCU_STEP = '0.0.0.0'
# Deid redactor replaces Instance/Series/Study UIDs with pseudo-random value seeded from original.
# This value corresponds to '111'. UID replacement behavior is not configurable in redactor.
REPLACED_UID = '2.25.140302709094137852884202099990798014056'
