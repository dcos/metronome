#!/bin/sh
set -e
# check to see if protobuf folder is empty
if [ ! -d "$HOME/protobuf/lib" ]; then
  curl -L -o protobuf-3.3.0.tar.gz https://github.com/google/protobuf/archive/v3.3.0.tar.gz
  tar -xzvf protobuf-3.3.0.tar.gz
  cd protobuf-3.3.0 && ./autogen.sh && ./configure --prefix=$HOME/protobuf && make && make install
else
  echo "Using cached protobuf."
fi
