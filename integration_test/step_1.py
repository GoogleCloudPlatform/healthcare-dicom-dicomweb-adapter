import substitution
from general import *

STORE_NAME = get_random_string(20)
SHORT_SHA = get_short_sha()
IMAGEPROJECT = get_imageproject()

verify_result(change_permission())

# install environment
verify_result(install_environment())

# store-scp
verify_result(store_scp(substitution.STORE_SCP_RUN_STEP, substitution.STORE_SCP_PORT))

# build adapter
verify_result(build_adapter())

# setup-dataset-and-dicom-store
verify_result(setup_dataset_and_dicom_store(substitution.PROJECT, substitution.LOCATION, substitution.DATASET, STORE_NAME))

# build adapter image
verify_result(build_adapter_image(IMAGEPROJECT, SHORT_SHA))

# run adapter
verify_result(run_import_adapter(substitution.ADAPTER_PORT, substitution.VERSION, substitution.PROJECT, substitution.LOCATION, substitution.DATASET, STORE_NAME, substitution.STORE_SCP_RUN_STEP, substitution.STORE_SCP_PORT, substitution.COMMITMENT_SCU_STEP, substitution.COMMITMENT_SCU_PORT))

# wait-for-adapter
verify_result(wait_for_port(substitution.ADAPTER_RUN_STEP, substitution.ADAPTER_PORT))

# wait-for-storescp
verify_result(wait_for_port(substitution.STORE_SCP_RUN_STEP, substitution.STORE_SCP_PORT))

# run-store-scu
verify_result(run_store_scu(substitution.ADAPTER_RUN_STEP, substitution.ADAPTER_PORT, "/workspace/integration_test/data/example.dcm"))

# run-store-scu-destination2
verify_result(run_store_scu(substitution.STORE_SCP_RUN_STEP, substitution.ADAPTER_PORT, "/workspace/integration_test/data/example-mg.dcm"))

# run-find-scu-instance
verify_result(run_find_scu_instance(substitution.STORE_SCP_RUN_STEP, substitution.ADAPTER_PORT))

# run-find-scu-series
verify_result(run_find_scu_series(substitution.STORE_SCP_RUN_STEP, substitution.ADAPTER_PORT))

# run-find-scu-study
verify_result(run_find_scu_study(substitution.STORE_SCP_RUN_STEP, substitution.ADAPTER_PORT))

# run-move-scu
verify_result(run_move_scu(substitution.STORE_SCP_RUN_STEP, substitution.ADAPTER_PORT))

# run-commitment-scu
verify_result(run_commitment_scu(substitution.STORE_SCP_RUN_STEP, substitution.ADAPTER_PORT, substitution.COMMITMENT_SCU_PORT, "/workspace/integration_test/data/example-redacted-jp2k.dcm"))

# close-adapter
runCommand("sudo kill -9 $(lsof -t -i:"+substitution.STORE_SCP_PORT+")", "Kill process on port "+ substitution.STORE_SCP_PORT)
runCommand("sudo kill -9 $(lsof -t -i:"+substitution.ADAPTER_PORT+")", "Kill process on port "+ substitution.ADAPTER_PORT)

# check-store-curl
verify_result(check_store_curl(substitution.VERSION, substitution.PROJECT, substitution.LOCATION, substitution.DATASET, STORE_NAME, substitution.REPLACED_UID, "integration_test/downloaded.dcm"))

# check-store-diff
verify_result(check_diff_dcm("integration_test/downloaded.dcm", "integration_test/data/example-redacted-jp2k.dcm"))

# # check-store-curl-destination-2
verify_result(check_store_curl(substitution.VERSION, substitution.PROJECT, substitution.LOCATION, substitution.DATASET, STORE_NAME+"-destination-2", substitution.REPLACED_UID, "integration_test/downloaded-destination-2.dcm"))

# check-store-diff-destination-2
verify_result(check_diff_dcm("integration_test/downloaded-destination-2.dcm", "integration_test/data/example-redacted-mg-jp2k.dcm"))

# check-find-diff-instance
verify_result(check_diff("integration_test/findscu-instance-result1.xml", "integration_test/data/findscu-instance-expected.xml"))

# check-find-diff-series
verify_result(check_diff("integration_test/findscu-series-result1.xml", "integration_test/data/findscu-series-expected.xml"))

# check-find-diff-study
verify_result(check_diff("integration_test/findscu-study-result1.xml", "integration_test/data/findscu-study-expected.xml"))

# check-move-diff
verify_result(check_diff_dcm("integration_test/storescp-data/"+substitution.REPLACED_UID, "integration_test/data/example-redacted-moved-jp2k.dcm"))

# check-commitment-diff
verify_result(check_commitment_diff())

# delete-dicom-store
verify_result(delete_dicom_store(STORE_NAME, substitution.PROJECT, substitution.DATASET, substitution.LOCATION))

# delete-dicom-store destination-2
verify_result(delete_dicom_store(STORE_NAME+"-destination-2", substitution.PROJECT, substitution.DATASET, substitution.LOCATION))
