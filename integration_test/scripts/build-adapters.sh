#!/bin/bash

set -e
set -o pipefail

export PATH=$PATH:/opt/gradle/gradle-6.9/bin

apt-get -qq update
apt-get install -y gnupg2
# we need gcloud and 5.6-jdk11 in same image next 4 lines adds google's package storage and installs gcloud by this manual https://cloud.google.com/sdk/docs/quickstart-debian-ubuntu
echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] http://packages.cloud.google.com/apt cloud-sdk main" | tee -a /etc/apt/sources.list.d/google-cloud-sdk.list
curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key --keyring /usr/share/keyrings/cloud.google.gpg add -
apt-get -qq update
apt-get install google-cloud-sdk -y

gradle build
cd ./import
gradle build
cd ../export
gradle build
