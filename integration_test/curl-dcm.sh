#!/bin/bash
# url, output_file
curl -X GET \
     -H "Authorization: Bearer "$(gcloud auth print-access-token) \
     $1 \
     |tail -c+137 | head -c-68 >$2
