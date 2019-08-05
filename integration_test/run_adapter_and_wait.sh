#!/bin/bash
# apiStage project location dataset store adapter_port finish_port
gradle run -Dexec.args='--dimse_aet=IMPORTADAPTER --dimse_port='$6' --dicomweb_address=https://healthcare.googleapis.com/'$1'/projects/'$2'/locations/'$3'/datasets/'$4'/dicomStores/'$5'/dicomWeb --verbose' &
apt-get update
apt-get install -y netcat-openbsd
echo 'Waiting for connection on port '$7' to finish step'
nc -l -p $7
echo 'Finishing adapter run'
