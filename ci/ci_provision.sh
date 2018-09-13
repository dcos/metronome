#!/bin/bash

# we want to share the scripts with Marathon
wget -P ./ci/ https://raw.githubusercontent.com/mesosphere/marathon/master/ci/upgrade.sc
chmod +x ci/upgrade.sc
