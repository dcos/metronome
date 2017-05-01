#!/bin/bash -e
#
#    Copyright (C) 2015 Mesosphere, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

echo "Building binary..."
pyinstaller binary/binary.spec

docker-check() {
  time=2
  command="/bin/sh -c \"docker ps\""

  if hash expect 2>/dev/null; then
    expect -c "set echo \"-noecho\"; set timeout $time; spawn -noecho $command; expect timeout { exit 1 } eof { exit 0 }"

    if [ $? = 1 ] ; then
      echo "Docker execution timed out. Make sure docker-machine start docker-vm is started."
      exit 0;
    fi
   fi
}

if [ "$(uname)" == "Darwin" ]; then
    # Do something under Mac OS X platform
    mkdir -p dist/darwin
    mv dist/dcos-job dist/darwin
    shasum -a 256 dist/darwin/dcos-job | awk '{print $1}' > dist/darwin/dcos-job.sha
    echo "Darin Build Complete!"

    # linux build on a darwin plaform if docker runs
    docker-check
    docker rmi -f job-binary || true
    docker rm job-binary || true
    docker build -f binary/Dockerfile.linux-binary -t job-binary .
    docker run --name job-binary job-binary
    mkdir -p dist/linux
    docker cp job-binary:/dcos-job/dist/linux dist/

elif [ "$(expr substr $(uname -s) 1 5)" == "Linux" ]; then
      # Do something under GNU/Linux platform  #statements
      mkdir -p dist/linux
      mv dist/dcos-job dist/linux
      sha256sum dist/linux/dcos-job | awk '{print $1}' > dist/linux/dcos-job.sha
      echo "Linux Build Complete"
fi

echo "Build finished!"
