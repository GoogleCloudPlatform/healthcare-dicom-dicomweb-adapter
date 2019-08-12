#!/bin/bash
# apiStage project location dataset store adapter_port finish_port storescp_host store_scp_port commitmentscu_host commitmentscu_port
gradle run -w -Dexec.args='--dimse_aet=IMPORTADAPTER --dimse_port='$6' --dicomweb_address=https://healthcare.googleapis.com/'$1'/projects/'$2'/locations/'$3'/datasets/'$4'/dicomStores/'$5'/dicomWeb --aet_dictionary_inline=[{"name":"STORESCP","host":'$8',"port":'$9'},{"name":"STGCMTSCU","host":'${10}',"port":'${11}'},] --verbose' &
apt-get -qq update
apt-get -qq install -y netcat-openbsd
echo 'Waiting for connection on port '$7' to finish adapter-main step'
nc -l -p $7
echo 'Finishing adapter-main step'
