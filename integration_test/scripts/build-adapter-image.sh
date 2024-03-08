#!/bin/bash
# imageProject commitShortSha

set -e
set -o pipefail

export PATH=$PATH:/opt/gradle/gradle-6.9/bin
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

gradle build
cd ./import
build_adapter import $2
cd ../export
build_adapter export $2
