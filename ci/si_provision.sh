#!/bin/bash

# we want to share the cluster launch scripts and si_pipeline scripts with Marathon
wget -P ./ci/ https://raw.githubusercontent.com/mesosphere/marathon/master/ci/launch_cluster.sh
wget -P ./ci/ https://raw.githubusercontent.com/mesosphere/marathon/master/ci/si_pipeline.sh
wget -P ./ci/ https://raw.githubusercontent.com/mesosphere/marathon/master/ci/si_install_deps.sh
chmod +x ci/launch_cluster.sh
chmod +x ci/si_pipeline.sh
chmod +x ci/si_install_deps.sh
