#!/bin/bash
# ADAPTER_PORT VERSION PROJECT LOCATION DATASET STORE_NAME STORE_SCP_RUN_STEP STORE_SCP_PORT COMMITMENT_SCU_STEP COMMITMENT_SCU_PORT
set -e
set -o pipefail

export PATH=$PATH:/opt/gradle/gradle-6.9/bin
cd /workspace/import

gradle run \
 -Dorg.dcm4che3.imageio.codec.ImageReaderFactory=com/google/cloud/healthcare/imaging/dicomadapter/transcoder/ImageReaderFactory.properties \
 -Dorg.dcm4che3.imageio.codec.ImageWriterFactory=com/google/cloud/healthcare/imaging/dicomadapter/transcoder/ImageWriterFactory.properties \
 -Dexec.args="--dimse_aet=IMPORTADAPTER --dimse_port=${1} --dicomweb_address=https://healthcare.googleapis.com/${2}/projects/${3}/locations/${4}/datasets/${5}/dicomStores/${6}/dicomWeb --monitoring_project_id=${3} --aet_dictionary_inline=\"[{\"name\":\"STORESCP\",\"host\":${7},\"port\":${8}},{\"name\":\"STGCMTSCU\",\"host\":${9},\"port\":${10}},]\" --destination_config_inline=\"[{\"filter\":\"Modality=MG\",\"dicomweb_destination\":\"https://healthcare.googleapis.com/${2}/projects/${3}/locations/${4}/datasets/${5}/dicomStores/${6}-destination-2/dicomWeb\"}]\" --redact_remove_list=ContentTime,ContentDate --store_compress_to_transfer_syntax=1.2.840.10008.1.2.4.90 --verbose" &
