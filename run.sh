#!/bin/bash

ZK_URL="${1:-zk://127.0.0.1:2181/metronome}"
MESOS_MASTER_URL="${2:-127.0.0.1:5050}"
HTTP_PORT="${3:-9000}"

sbt universal:packageBin
ZIP_COUNT=`ls target/universal/metronome*.zip | wc -l`
if [ ${ZIP_COUNT} -gt 1 ]; then
    echo "Multiple metronome zip files inside /target/universal. Run 'sbt clean' or remove one manually.".
    exit 1
fi
# unpack the package
unzip -o -d target/universal -a target/universal/metronome-*.zip
chmod +x target/universal/metronome-*/bin/metronome
LIBPROCESS_IP=127.0.0.1 ./target/universal/metronome-*/bin/metronome -d -v -Dmetronome.framework.name=metronome-dev -Dmetronome.zk.url=$ZK_URL -Dmetronome.mesos.master.url=$MESOS_MASTER_URL -Dplay.server.http.port=$HTTP_PORT