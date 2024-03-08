#!/bin/bash

set -e
set -o pipefail

export PATH=/opt/dcm4che/bin:$PATH
storescp --bind STORESCP@$1:$2 --directory /workspace/integration_test/storescp-data --accept-unknown &
apt-get -qq update
apt-get -qq install -y netcat-openbsd
echo 'Waiting for connection on port '$3' to finish store-scp step'
echo 'Finishing store-scp step'
