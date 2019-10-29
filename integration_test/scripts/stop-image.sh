#!/bin/bash
# imageProject commitShortSha

docker stop --time 100 $(docker ps -q --filter ancestor=gcr.io/${1}/healthcare-api-dicom-dicomweb-adapter-import:${2})