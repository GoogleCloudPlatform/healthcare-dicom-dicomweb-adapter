#!/bin/bash
# imageProject publishFlag

base_name="gcr.io/${1}/healthcare-api-dicom-dicomweb-adapter-"

publish_adapter () {
  adapter_name=${base_name}${1}
  docker push $adapter_name
}
if [ "$2" == "true" ]
then
  publish_adapter import
  publish_adapter export
fi
