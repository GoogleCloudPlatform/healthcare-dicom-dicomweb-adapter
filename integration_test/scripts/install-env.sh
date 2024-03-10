#!/bin/bash

set -e
set -o pipefail

apt-get update

# docker
apt-get install -y \
    apt-transport-https \
    ca-certificates \
    gnupg-agent \
    software-properties-common \
    lsof \
    jq \
    lsb-release


curl -fsSL https://get.docker.com | sh
docker -v

curl -sSL https://sdk.cloud.google.com | bash -s -- --install-dir=/opt/gcloud --disable-prompts
ln -s /opt/gcloud/google-cloud-sdk/bin/* /usr/bin/
gcloud --version

# dcm4che
wget https://www.dcm4che.org/maven2/org/dcm4che/dcm4che-assembly/5.25.2/dcm4che-assembly-5.25.2-bin.zip
unzip dcm4che-assembly-5.25.2-bin.zip -d /opt 
mv /opt/dcm4che-5.25.2 /opt/dcm4che
export PATH=/opt/dcm4che/bin:$PATH

# yq
wget https://github.com/mikefarah/yq/releases/download/v4.42.1/yq_linux_amd64 -O /usr/bin/yq
chmod +x /usr/bin/yq

# netstat
apt install net-tools


