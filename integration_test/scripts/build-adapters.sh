#!/bin/bash

set -e
set -o pipefail

apt-get -qq update
apt-get install -y gnupg2

gradle build
cd ./import
gradle build
cd ../export
gradle build
