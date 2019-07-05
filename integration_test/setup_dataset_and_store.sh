#!/bin/bash
# project location dataset store_name
gcloud alpha healthcare datasets describe $3 "--project="$1 "--location="$2 "--quiet" >/dev/null
if [ $? -ne 0 ];
then
  gcloud alpha healthcare datasets create $3 "--project="$1 "--location="$2 "--quiet" || exit $?
fi

gcloud alpha healthcare dicom-stores describe $4 "--project="$1 "--location="$2 "--dataset="$3 \
 "--quiet" >/dev/null
if [ $? -eq 0 ];
then
  gcloud alpha healthcare dicom-stores delete $4 "--project="$1 "--location="$2 "--dataset="$3 \
   "--quiet" || exit $?
fi
gcloud alpha healthcare dicom-stores create $4 "--project="$1 "--location="$2 "--dataset="$3 \
 "--quiet"

