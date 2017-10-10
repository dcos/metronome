
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
