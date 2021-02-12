#!/bin/bash
# url, output_file

curl -X GET \
     -H "Authorization: Bearer "$(gcloud auth print-access-token) \
     -H "Accept: application/dicom; transfer-syntax=*" \
     $1 --output $2

