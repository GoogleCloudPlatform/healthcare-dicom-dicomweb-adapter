#!/bin/bash
# host port
# busybox
apt-get update
apt-get install -y busybox
apt-get -qq install -y netcat-openbsd
echo 'LISTEN PORTS LIST'
netstat -ntlp | grep LISTEN

echo 'waiting for' $1 $2
while :
do
  nc -z $1 $2
  if [ $? -eq 0 ];
  then
    break
  fi
  sleep 1
done
echo 'connected to' $1 $2
