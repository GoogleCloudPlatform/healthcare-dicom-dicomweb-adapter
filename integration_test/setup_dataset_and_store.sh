#!/bin/bash
# location dataset store_name
gcloud alpha healthcare datasets describe $2 "--location="$1 "--quiet" >/dev/null
if [ $? -ne 0 ];
then
  gcloud alpha healthcare datasets create $2 "--location="$1 "--quiet" || exit $?
fi

gcloud alpha healthcare dicom-stores describe $3 "--dataset="$2 "--location="$1 "--quiet" >/dev/null
if [ $? -eq 0 ];
then
  gcloud alpha healthcare dicom-stores delete $3 "--dataset="$2 "--location="$1 "--quiet" || exit $?
fi
gcloud alpha healthcare dicom-stores create $3 "--dataset="$2 "--location="$1 "--quiet"

