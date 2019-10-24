#!/bin/bash

ZK_URL="${1:-zk://127.0.0.1:2181/metronome}"
MESOS_MASTER_URL="${2:-127.0.0.1:5050}"
HTTP_PORT="${3:-9000}"

if [ -z "$NOBUILD" ]; then
  sbt stage
fi
LIBPROCESS_IP=127.0.0.1 ./target/universal/stage/bin/metronome -d -v -Dmetronome.framework.name=metronome-dev -Dmetronome.zk.url=$ZK_URL -Dmetronome.mesos.master.url=$MESOS_MASTER_URL -Dplay.server.http.port=$HTTP_PORT
