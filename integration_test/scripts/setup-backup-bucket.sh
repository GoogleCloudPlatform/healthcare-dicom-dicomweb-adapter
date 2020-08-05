#!/bin/bash
# project location bucket
gsutil du -s gs://$3"/ --quiet" >/dev/null
if [ $? -ne 0 ];
then
  gsutil mb -p $1 -l $2 -b on "gs://"$3 || exit $?
fi