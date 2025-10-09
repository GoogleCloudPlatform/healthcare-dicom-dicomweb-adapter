#!/bin/bash

set -e
set -o pipefail

export PATH=/opt/dcm4che/bin:$PATH
tmp_dir=$(mktemp -d)

dcm2json --with-bulkdata $PWD/$1 > $tmp_dir/got.json
dcm2json --with-bulkdata $PWD/$2 > $tmp_dir/want.json

# We ignore the Implementation Version Name when comparing files
diff \
    <(jq --sort-keys 'del(.["00020013"], .["00081190"], .["00080056"])' $tmp_dir/want.json) \
    <(jq --sort-keys 'del(.["00020013"], .["00081190"], .["00080056"])' $tmp_dir/got.json)

rm -rf $tmp_dir
