# Version 0.3.0

Diff [0.2.4-0.3.0](https://github.com/dcos/metronome/compare/v0.2.4...v0.3.0)

## Features

* Upgraded to a released version of Marathon Lib [v1.3.13](https://github.com/mesosphere/marathon/releases/tag/v1.3.13)
* Updates to dependencies (including an Akka update to fix [schedule time wrap around bug](https://github.com/akka/akka/issues/20424))
* Added `/info` end point for metronome version information

## Bugs and Tracking

* #150 Added `/info` endpoint
* [MARATHON_EE-1717](https://jira.mesosphere.com/browse/MARATHON_EE-1717) 60s min between reschedules
* [MARATHON_EE-1725](https://jira.mesosphere.com/browse/MARATHON_EE-1725)
* [MARATHON_EE-1726](https://jira.mesosphere.com/browse/MARATHON_EE-1726) Upgrade Marathon libraries and dependencies


## Download

https://s3.amazonaws.com/downloads.mesosphere.io/metronome/releases/0.3.0/metronome-0.3.0.tgz
sha: f1a85ee638bc5b31dcd5594da4e84ca3e7a36451

# Version 0.2.4

diff from [0.2.3-0.2.4](https://github.com/dcos/metronome/compare/87976...23fe8ca)

## Fixes

- Upgraded to cron-utils 6.0.4, fixes issues with cron calculations enabling crons such as `0 9 1-7 * 1-5` as mon-fri the first week of the month only.
- Documentation and Job placement examples provided.


# Version 0.2

Prepare Metronome for DC/OS 1.9.

## Overview

### Service integration tests

We now have a suite of integration tests for DC/OS that runs in our CI.


## Fixes
- Fix #96 Change constraints fields names to match schema.
- Fix #102 API Examples update: test.com -> example.com
- Fix #107 Add /v0/scheduled-jobs raml documentation.


# Version 0.1

Genesis
