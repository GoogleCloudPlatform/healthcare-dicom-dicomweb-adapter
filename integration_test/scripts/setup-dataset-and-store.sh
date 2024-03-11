#!/bin/bash
# project location dataset store_name
gcloud healthcare datasets describe $3 "--project="$1 "--location="$2 "--quiet" >/dev/null
if [ $? -ne 0 ];
then
  gcloud healthcare datasets create $3 "--project="$1 "--location="$2 "--quiet" || exit $?
fi

setup_store () {
  gcloud healthcare dicom-stores describe $4 "--project="$1 "--location="$2 "--dataset="$3 \
   "--quiet" >/dev/null
  if [ $? -eq 0 ];
  then
    gcloud healthcare dicom-stores delete $4 "--project="$1 "--location="$2 "--dataset="$3 \
     "--quiet" || exit $?
  fi
  gcloud healthcare dicom-stores create $4 "--project="$1 "--location="$2 "--dataset="$3 \
   "--quiet"
}

setup_store $1 $2 $3 $4 &&
setup_store $1 $2 $3 $4"-destination-2"


