#!/bin/bash
# location dataset store_name
gcloud alpha healthcare dicom-stores delete $3 "--dataset="$2 "--location="$1 "--quiet" | echo
