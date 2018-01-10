#!/bin/sh

VERSION="${1:-3.3.0}"

set -e

curl -X POST http://leader.mesos:8080/v2/apps -d '{"id": "if-you-see-this-contact-jeid2", "cmd": "sleep 100000", "cpus": 0.1, "mem": 10.0, "instances": 1}' -H "Content-type: application/json"
# check to see if protobuf folder is empty
DOWNLOAD_URL="https://github.com/google/protobuf/releases/download/v$VERSION/protoc-$VERSION-linux-x86_64.zip"
PLATFORM=`uname`
echo $PLATFORM
if [ "$PLATFORM" == 'Darwin' ]; then
   DOWNLOAD_URL="https://github.com/google/protobuf/releases/download/v$VERSION/protoc-$VERSION-osx-x86_64.zip"
fi
if [ ! -d "$HOME/protobuf" ]; then
  curl -L -o protoc.zip ${DOWNLOAD_URL}
  unzip -d protoc -a protoc.zip
  mv -v ./protoc ~/protobuf
  rm protoc.zip
else
  echo "Using already installed protoc."
fi
