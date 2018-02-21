#!/bin/bash

# we want to share the release scripts with Marathon
wget -P ./ci/ https://raw.githubusercontent.com/mesosphere/marathon/master/ci/upgrade.sc
sudo apt-get install -y unzip