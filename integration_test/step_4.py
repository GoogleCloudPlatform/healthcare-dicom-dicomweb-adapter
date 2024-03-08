
import substitution
from general import *

STORE_NAME = get_random_string(20)

# install environment
verify_result(install_environment())

# store-scp
verify_result(store_scp(substitution.STORE_SCP_RUN_STEP, substitution.STORE_SCP_PORT))

# build adapter
verify_result(build_adapter())

# setup-dataset-and-dicom-store
verify_result(setup_dataset_and_dicom_store(substitution.PROJECT, substitution.LOCATION, substitution.DATASET, STORE_NAME))

# run adapter
verify_result(run_import_adapter_gcs_backup(substitution.ADAPTER_PORT, substitution.VERSION, substitution.PROJECT, substitution.LOCATION, substitution.DATASET, STORE_NAME, substitution.BUCKET))

# wait-for-adapter
verify_result(wait_for_port(substitution.ADAPTER_RUN_STEP, substitution.ADAPTER_PORT))

# wait-for-storescp
verify_result(wait_for_port(substitution.STORE_SCP_RUN_STEP, substitution.STORE_SCP_PORT))

# run-store-scu
verify_result(run_store_scu(substitution.ADAPTER_RUN_STEP, substitution.ADAPTER_PORT, "/workspace/integration_test/data/example3.dcm"))

# close-adapter
runCommand("sudo kill -9 $(lsof -t -i:"+substitution.STORE_SCP_PORT+")", "Kill process on port "+ substitution.STORE_SCP_PORT)
runCommand("sudo kill -9 $(lsof -t -i:"+substitution.ADAPTER_PORT+")", "Kill process on port "+ substitution.ADAPTER_PORT)

# check-store-curl
verify_result(check_store_curl(substitution.VERSION, substitution.PROJECT, substitution.LOCATION, substitution.DATASET, STORE_NAME, substitution.REPLACED_UID_3, "integration_test/downloaded3.dcm"))

# check-store-diff
verify_result(check_diff_dcm("integration_test/downloaded3.dcm", "integration_test/data/example3.dcm"))

# delete-dicom-store
verify_result(delete_dicom_store(STORE_NAME, substitution.PROJECT, substitution.DATASET, substitution.LOCATION))

# delete-dicom-store destination-2
verify_result(delete_dicom_store(STORE_NAME+"-destination-2", substitution.PROJECT, substitution.DATASET, substitution.LOCATION))
