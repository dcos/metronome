#!/bin/bash

VERSION="${1:-3.3.0}"

set -e
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
