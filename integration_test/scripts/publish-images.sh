#!/bin/bash
# imageProject publishFlag repositoryString ACCESS_TOKEN_BASE64 PROJECT_ID KMS_LOCATION KMS_KEYRING KMS_KEY

repositoryString=$3
PROJECT_ID=$4

# repositoryString contains "githubUser_githubRepo" if repository is stored by user
# and just "githubRepo" if stored by organization https://cloud.google.com/cloud-build/docs/configuring-builds/substitute-variable-values
IFS='_' read -r -a repoArray <<< "$repositoryString"
githubUser="${repoArray[1]}"
githubRepo="${repoArray[2]}"
if [ -z "$githubUser" ]
then
    githubUser="GoogleCloudPlatform"
    githubRepo=$3
fi

base_name="gcr.io/${1}/healthcare-api-dicom-dicomweb-adapter-"

publish_adapter () {
  adapter_name=${base_name}${1}
  docker push $adapter_name > /tmp/docker-pub-stdout.txt 2> /tmp/docker-pub-stderr.txt
  echo "https://${adapter_name}:${2}"
}
if [ "$2" == "true" ]
then
  # getting local tag version by filtering adapter image from list of images, getting tags
  # and filtering version tag by `grep "\."`
  # have to do this because docker does not allow to search through tags in local storage
  version=`docker images | grep $base_name | awk '{print $2}' | grep "\." | head -n 1`
  version=${version[0]}
  import=$(publish_adapter import $version)
  cat /tmp/docker-pub-stdout.txt
  cat /tmp/docker-pub-stderr.txt
  export=$(publish_adapter export $version)
  cat /tmp/docker-pub-stdout.txt
  cat /tmp/docker-pub-stderr.txt
  body="$import\n$export"
  echo {\"tag_name\": \"$version\",\"name\": \"$version\",\"body\": \"$body\"} > /workspace/request.json
  responseCode=$(curl -# -XPOST -H "Authorization: Bearer $GH_TOKEN" -H 'Content-Type:application/json' -H 'Accept:application/json' -w "%{http_code}" --data-binary @/workspace/request.json \
  https://api.github.com/repos/$githubUser/$githubRepo/releases -o response.json)
  if [ $responseCode -ne 201   ]; then
    cat response.json
    exit 1;
  fi

fi
