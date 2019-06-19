#!/bin/bash
# host port
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
