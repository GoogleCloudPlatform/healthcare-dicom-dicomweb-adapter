#!/bin/bash
# run_step scp_port scp_finish_port
cd /workspace/dcm4che/dcm4che-tool/dcm4che-tool-storescp
mvn exec:java -Dexec.mainClass=org.dcm4che3.tool.storescp.StoreSCP -Dexec.args='--bind STORESCP@'$1':'$2' --directory /workspace/integration_test/storescp-data --accept-unknown' &
apt-get update
apt-get install -y netcat-openbsd
echo 'Waiting for connection on port '$3' to finish store-scp step'
nc -l -p $3
echo 'Finishing store-scp step'
