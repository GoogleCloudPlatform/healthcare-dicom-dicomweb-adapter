#!/bin/bash

set -e
set -o pipefail

apt-get update

# docker
apt-get install -y \
    apt-transport-https \
    ca-certificates \
    gnupg-agent \
    software-properties-common


curl -fsSL https://download.docker.com/linux/debian/gpg | apt-key add -
add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/debian $(lsb_release -cs) stable"
apt-get update
apt-get install -y docker-ce
docker -v

# java
apt install -y openjdk-11-jdk
java -version

# maven
wget https://downloads.apache.org/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.tar.gz
tar xzvf apache-maven-3.6.3-bin.tar.gz -C /opt
export PATH=/opt/apache-maven-3.6.3/bin:$PATH
mvn -v

# gradle
wget https://services.gradle.org/distributions/gradle-6.7-bin.zip
mkdir /opt/gradle
unzip -d /opt/gradle gradle-6.7-bin.zip
ls /opt/gradle/gradle-6.7
export PATH=$PATH:/opt/gradle/gradle-6.7/bin
gradle -v

# netstat
apt install net-tools

# hosts
echo "0.0.0.0 step_1" >> /etc/hosts
echo "192.168.0.1 localhost" >> /etc/hosts

