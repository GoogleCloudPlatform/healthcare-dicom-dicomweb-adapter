#!/bin/bash
# imageProject commitShortSha

base_name="gcr.io/${1}/healthcare-api-dicom-dicomweb-adapter-"

build_adapter () {
  gradle dockerBuildImage
  adapter_name=${base_name}${1}
  version=$(gradle printVersion  | grep -Eo "[0-9]{1,3}\.[0-9]{1,3}" | head -1)
  postfix=$(gcloud container images list-tags "${adapter_name}" --sort-by=~timestamp | grep " $version" |  grep -Eo "([0-9]{1,3}\.){2}[0-9]{1,3}" | head -1 | grep -Eo "[0-9]{1,3}" | tail -1)
  postfix=$(($postfix+1))
  gradle dockerBuildImage -Pdocker_tag=${adapter_name}:$version.$postfix
  gradle dockerBuildImage -Pdocker_tag=${adapter_name}:$2
}
apt-get -qq update
apt-get install -y gnupg2
# we need gcloud and 5.6-jdk11 in same image next 4 lines adds google's package storage and installs gcloud by this manual https://cloud.google.com/sdk/docs/quickstart-debian-ubuntu
echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] http://packages.cloud.google.com/apt cloud-sdk main" | tee -a /etc/apt/sources.list.d/google-cloud-sdk.list
curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key --keyring /usr/share/keyrings/cloud.google.gpg add -
apt-get -qq update
apt-get install google-cloud-sdk -y

gradle build
cd ./import
build_adapter import $2
cd ../export
build_adapter export $2
