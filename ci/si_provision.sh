#!/bin/bash

# we want to share the cluster launch scripts and si_pipeline scripts with Marathon
wget https://raw.githubusercontent.com/mesosphere/marathon/master/ci/launch_cluster.sh
wget https://raw.githubusercontent.com/mesosphere/marathon/master/ci/si_pipeline.sh
chmod +x launch_cluster.sh
chmod +x si_pipeline.sh