
import substitution
from general import *

STORE_NAME = get_random_string(20)

# clear data
clear_data()

# install environment
verify_result(install_environvent())

# clone-dcm4che
verify_result(clone_dcm4che())

# checkout-dcm4che-tag
verify_result(checkout_dcm4che_tag())

# build-tools
verify_result(build_tools())

# store-scp
verify_result(store_scp(substitution.STORE_SCP_RUN_STEP, substitution.STORE_SCP_PORT, substitution.CLOSE_STORE_SCP_PORT))

# build adapter
verify_result(build_adapter())

# setup-dataset-and-dicom-store
#     Create two dicom stores for this test, each with a random name.
verify_result(setup_dataset_and_dicom_store(substitution.PROJECT, substitution.LOCATION, substitution.DATASET, STORE_NAME))

# run adapter
verify_result(run_import_adapter_gcs_backup(substitution.ADAPTER_PORT, substitution.VERSION, substitution.PROJECT, substitution.LOCATION, substitution.DATASET, STORE_NAME, substitution.BUCKET))

# wait-for-adapter
verify_result(wait_for_port(substitution.ADAPTER_RUN_STEP, substitution.ADAPTER_PORT))

# wait-for-storescp
verify_result(wait_for_port(substitution.STORE_SCP_RUN_STEP, substitution.STORE_SCP_PORT))

# run-store-scu
verify_result(run_store_scu(substitution.ADAPTER_RUN_STEP, substitution.ADAPTER_PORT))

# close-adapter
runCommand("sudo kill -9 $(lsof -t -i:"+substitution.STORE_SCP_PORT+")", "Kill process on port "+ substitution.STORE_SCP_PORT)
runCommand("sudo kill -9 $(lsof -t -i:"+substitution.ADAPTER_PORT+")", "Kill process on port "+ substitution.ADAPTER_PORT)

# close-store-scp
verify_result(close_store_scp(substitution.STORE_SCP_RUN_STEP, substitution.CLOSE_STORE_SCP_PORT))

# check-store-curl
verify_result(check_store_curl(substitution.VERSION, substitution.PROJECT, substitution.LOCATION, substitution.DATASET, STORE_NAME, substitution.REPLACED_UID))

# check-store-diff
#check_store_diff("integration_test/data/example.dcm")
verify_result(check_store_diff("integration_test/downloaded.dcm"))

# delete-dicom-store
verify_result(delete_dicom_store(STORE_NAME, substitution.PROJECT, substitution.DATASET, substitution.LOCATION))

# delete-dicom-store destination-2
verify_result(delete_dicom_store_destination2(STORE_NAME, substitution.PROJECT, substitution.DATASET, substitution.LOCATION))

# THE END?