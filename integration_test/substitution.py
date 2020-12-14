VERSION = 'v1'
PROJECT = 'gcp-healthcare-oss-test'
IMAGEPROJECT = 'cloud-healthcare-containers'
LOCATION = 'us-central1'
DATASET = "healthcare-dicom-dicomweb-adapter-test"
STORE_NAME = 'integration-test-store'
BUCKET = 'dicomweb-import-adapter-integration-test'
ADAPTER_PORT = '2575'
STORE_SCP_PORT = '2576'
# ADAPTER_PORT_HTTP2 = '2577'
CLOSE_STORE_SCP_PORT = '3001'
COMMITMENT_SCU_PORT = '4000'
KMS_LOCATION = 'global'
KMS_KEYRING = 'default'
KMS_KEY = 'github-robot-access-token'
ACCESS_TOKEN_BASE64 = 'CiQAM/SK3FUc1t+CnHDdgRzbc556FIyHddxRpsnolmSKfpiZ66sSUQDrEGO9gz15JIulryNagWzUOGbBEAaC04y85J8fNRjJZ8T8ntzh6Kt0Sa+GCG+3n5xSQdDJdj6xOG0LfVzvU+/K3mZ1KJlIcd0jiCeBrjYLlw=='
# Cloud build names containers that run steps: step_0, step_1 etc. These are also their dns names on docker network.
# Update to correct value if adding/removing steps before adapter run.
ADAPTER_RUN_STEP = '0.0.0.0'
STORE_SCP_RUN_STEP = '0.0.0.0'
COMMITMENT_SCU_STEP = '0.0.0.0'
# Deid redactor replaces Instance/Series/Study UIDs with pseudo-random value seeded from original.
# This value corresponds to '111'. UID replacement behavior is not configurable in redactor.
REPLACED_UID = '2.25.140302709094137852884202099990798014056'
