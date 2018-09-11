#!/bin/bash

# we want to share the release scripts with Marathon
sudo apt-get install -y wget unzip
wget -P ./ci/ https://raw.githubusercontent.com/mesosphere/marathon/master/ci/upgrade.sc
chmod +x ci/upgrade.sc