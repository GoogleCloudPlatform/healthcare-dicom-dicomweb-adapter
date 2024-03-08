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


curl -fsSL https://download.docker.com/linux/debian/gpg | apt-key add -
add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/debian $(lsb_release -cs) stable"
apt-get update
apt-get install -y docker-ce
docker -v

# java
apt install -y openjdk-11-jdk
java -version

# dcm4che
wget https://www.dcm4che.org/maven2/org/dcm4che/dcm4che-assembly/5.25.2/dcm4che-assembly-5.25.2-bin.zip
unzip dcm4che-assembly-5.25.2-bin.zip -d /opt 
mv /opt/dcm4che-5.25.2 /opt/dcm4che
export PATH=/opt/dcm4che/bin:$PATH

# gradle
wget https://services.gradle.org/distributions/gradle-6.9-bin.zip
mkdir /opt/gradle
unzip -d /opt/gradle gradle-6.9-bin.zip
ls /opt/gradle/gradle-6.9
export PATH=$PATH:/opt/gradle/gradle-6.9/bin
gradle -v

# yq
wget https://github.com/mikefarah/yq/releases/download/v4.42.1/yq_linux_amd64 -O /usr/bin/yq
chmod +x /usr/bin/yq

# netstat
apt install net-tools


