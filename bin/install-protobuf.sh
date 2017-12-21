#!/bin/sh
set -e
# check to see if protobuf folder is empty
download_url='https://github.com/google/protobuf/releases/download/v3.3.0/protoc-3.3.0-linux-x86_32.zip'
unamestr=`uname`
echo $unamestr
if [[ "$unamestr" == 'Darwin' ]]; then
   download_url='https://github.com/google/protobuf/releases/download/v3.3.0/protoc-3.3.0-osx-x86_64.zip'
fi
if [ ! -d "$HOME/protoc" ]; then
  curl -L -o protoc-3.3.0.zip $download_url
  unzip -d protoc-3.3.0 -a protoc-3.3.0.zip
  mv -v ./protoc-3.3.0 ~/protoc
  rm protoc-3.3.0.zip
  export PATH=~/protoc/bin:$PATH
else
  echo "Using already installed protoc."
fi
