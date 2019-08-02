#!/bin/bash
# apiStage project location dataset store adapter_port finish_port storescp_host store_scp_port
gradle run -Dexec.args='--dimse_aet=IMPORTADAPTER --dimse_port='$6' --dicomweb_addr=https://healthcare.googleapis.com/'$1'/projects/'$2'/locations/'$3'/datasets/'$4'/dicomStores/'$5'/dicomWeb --dicomweb_stow_path=/studies --aet_dictionary_inline=[{"name":"STORESCP","host":'$8',"port":'$9'}] --verbose' &
apt-get update
apt-get install -y netcat-openbsd
echo 'Waiting for connection on port '$7' to finish adapter-main step'
nc -l -p $7
echo 'Finishing adapter-main step'
