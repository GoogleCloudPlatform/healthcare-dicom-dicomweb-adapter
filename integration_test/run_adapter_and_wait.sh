#!/bin/bash
# project location dataset store adapter_port finish_port
gradle run -Dexec.args='--dimse_aet=IMPORTADAPTER --dimse_port='$5' --dicomweb_addr=https://healthcare.googleapis.com/v1beta1/projects/'$1'/locations/'$2'/datasets/'$3'/dicomStores/'$4'/dicomWeb --dicomweb_stow_path=/studies --dimse_cmove_aet=CMOVESUB --verbose' &
apt-get update
apt-get install -y netcat-openbsd
echo 'Waiting for connection on port '$6' to finish step'
nc -l -p $6
echo 'Finishing adapter run'
