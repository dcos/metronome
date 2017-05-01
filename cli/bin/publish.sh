#!/bin/bash

# env vars
#   PLATFORM (e.g. linux-x86-64)

set -ex -o pipefail

export VERSION="${GIT_BRANCH#refs/tags/}"
export S3_URL="s3://downloads.mesosphere.io/metronome/assets/cli/${VERSION}/${PLATFORM}/dcos-job"

# todo: manage versioning
# echo -e "version = '${VERSION}'\n" > dcos_job/version.py
make clean binary
aws s3 cp dist/dcos-job "${S3_URL}"
