# Next

# 0.6.NEXT

* When querying run detail with `embed=history`, `successfulFinishedRuns` and `failedFinishedRuns` contains new field `tasks` which is an array of taskIds of that finished run. This allow people to query task ids even for finished job runs.
* Fixed a bug when task status was not updated after the task turned running (when querying embed=activeRuns).

# 0.6.21

* [DCOS_OSS-5020](https://jira.mesosphere.com/browse/DCOS_OSS-5020) Add missing HTTP metrics in Metronome.

# 0.6.18

* [DCOS_OSS-4636](https://jira.mesosphere.com/browse/DCOS_OSS-4636) Failure when restart policy is `ON_FAILURE`.  This bug was introduced through the fix of another bug regarding stopping invalid extra instances of a job run.  Metronome should not check the launch queue when a restart is invoked.

## New features
- Added new metric `metronome.uptime.gauge.seconds`

# 0.6.12

## Bug fixes
* [DCOS_OSS-4978](https://jira.mesosphere.com/browse/DCOS_OSS-4978) Allow using `IS` operator when creating jobs. This was broken since introduction of the `IS` operator, which replaced `EQ` but was not a valid schema value.

# 0.6.11

Updated Marathon dependency to 1.7.202

# 0.6.10

Updated Marathon dependency to 1.7.188

# 0.5.71

Metronome uses Marathon as a library for scheduling. We have bumped the dependency to the current Marathon, which is 1.7.183.
This brings a lot of bug fixes and new features from the last 3 versions of Marathon. At the same time, it allows us to add UCR and secrets support.

## Breaking changes

Metronome 0.5.71 contains new Metrics endpoint with new metrics exposed that should allow you to monitor Metronome more easily. For detailed information please refer to the Metrics page in our docs.

## IS replaces EQ operator

In order to bring better alignment between Marathon and Metronome, the `EQ` constraint operator has been replaced with `IS`. The change is semantic; Job definitions using `EQ` will continue to function the same and are transparently mapped to the new operator with the same constraint behavior.

If you post the following Job definition:

```json
{
  "description": "constraint example",
  "id": "constraint-example",
  "run": {
    ...
    "placement": {
      "constraints": [{"attribute": "@region", "operator": "EQ", "value": "us-east-1"}]
    }
  }
}
```

When you ask for it back, the operator will be "IS":

```json
{
  "description": "constraint example",
  "id": "constraint-example",
  "run": {
    ...
    "placement": {
      "constraints": [{"attribute": "@region", "operator": "IS", "value": "us-east-1"}]
    }
  }
}
```

Previous jobs are automatically migrated as well.

## New features
* [DCOS_OSS-4344](https://jira.mesosphere.com/browse/DCOS_OSS-4344) Support UCR
* [DCOS_OSS-4464](https://jira.mesosphere.com/browse/DCOS_OSS-4464) EQ operator is replaced with IS (in backward compatible way)
* [DCOS_OSS-4446](https://jira.mesosphere.com/browse/DCOS_OSS-4446) Support file based secrets
* [DCOS_OSS-4440](https://jira.mesosphere.com/browse/DCOS_OSS-4440) GPU support

## Bug fixes
* [DCOS_OSS-4024](https://jira.mesosphere.com/browse/DCOS_OSS-4024) Use newer Caffeine dependency
* [DCOS_OSS-4239](https://jira.mesosphere.com/browse/DCOS_OSS-4239) Crash when Zookeeper connection fails to establish

# Version 0.4.4

## Bugs and Tracking

* [#244](https://github.com/dcos/metronome/pull/244) Wait for all parts of migration to be finished.

Diff [0.4.2-0.4.3](https://github.com/dcos/metronome/compare/v0.4.3...0.4.4)

# Version 0.4.3

## Bugs and Tracking

* [#234](https://github.com/dcos/metronome/pull/234) Exit when cannot load state from ZK.
* [#230](https://github.com/dcos/metronome/pull/230) Gracefully handle errors during task launching.
* [DCOS_OSS-2564](https://jira.mesosphere.com/browse/DCOS_OSS-2564) Docker params support.

Diff [0.4.2-0.4.3](https://github.com/dcos/metronome/compare/v0.4.2...0.4.3)

# Version 0.4.2

## Features
* [METRONOME-248](https://jira.mesosphere.com/browse/METRONOME-248) Environment variable secrets exposed via API

## Bugs fixed
* [METRONOME-218](https://jira.mesosphere.com/browse/METRONOME-218) Improved behavior for situations when the underlying zookeeper node content is corrupt - we now fail loud and early

Diff [0.4.1-0.4.2](https://github.com/dcos/metronome/compare/v0.4.1...v0.4.2)


## Breaking changes

### Command line parameters
Command line parameter `task.lost.expunge.gc` was removed because the underlying algorithm change and this
one no longer has any effect.

### Metrics
We moved to a different Metrics library and the metrics are not always compatible or the same as existing metrics;
however, the metrics are also now more accurate, use less memory, and are expected to get better throughout the release.
Where it was possible, we maintained the original metric names/groupings/etc, but some are in new locations or have
slightly different semantics. Any monitoring dashboards should be updated.

For Metronome specific metrics, you can find your old metrics under in the same path, only prefixed with "service"
so e.g. 'dcos.metronome.jobspec.impl.JobSpecServiceActor.receiveTimer' is now
'service.dcos.metronome.jobspec.impl.JobSpecServiceActor.receiveTimer'.

The format of the v1/metrics endpoint also changed in a backward incompatible manner - please see the documentation
for the current way the metrics are served.

# Version 0.4.1

## Bugs and Tracking

* [METRONOME-222](https://jira.mesosphere.com/browse/METRONOME-222) CMD or Docker is Required.
* [METRONOME-236](https://jira.mesosphere.com/browse/METRONOME-236) Additional CRON validation to prevent system lock up.

Diff [0.4.0-0.4.1](https://github.com/dcos/metronome/compare/v0.4.0...4cf60b24)

# Version 0.4.0

## Features

* [METRONOME-190](https://jira.mesosphere.com/browse/METRONOME-190) Added launch queue
* [METRONOME-194](https://jira.mesosphere.com/browse/METRONOME-194) Support FORBID Concurrency Policy

## Bugs and Tracking

* [METRONOME-100](https://jira.mesosphere.com/browse/METRONOME-100) Metronome restart causes duplication of jobrun
* [METRONOME-191](https://jira.mesosphere.com/browse/METRONOME-191) Implement startingDeadlineTimeout

Diff [0.3.4-0.4.0](https://github.com/dcos/metronome/compare/releases/0.3...1457e6)

The launch queue (`/v1/queue`) provides a way to see jobs which have been scheduled to launch but are still not launched on the cluster.
This is usually because there is not enough resources or constraints are not met.

The FORBID concurrency policy allows `"concurrencyPolicy": "FORBID"` to be added to a schedule. This restricts launching of a scheduled jobrun when previous run is still active. In that case it will not launch nor will be queued to launch. The job will be rescheduled for the next CRON time.

# Version 0.3.5

Diff [0.3.4-0.3.5](https://github.com/dcos/metronome/compare/v0.3.4...8bbfda7d6b84a70b4ede28770eae64aeb1b3654)

## Bugs and Tracking

* [METRONOME-236](https://jira.mesosphere.com/browse/METRONOME-236) Additional CRON validation to prevent system lock up.

# Version 0.3.4

Diff [0.3.3-0.3.4](https://github.com/dcos/metronome/compare/v0.3.3...4dcb0dddc6e13f24eff1e3e6502213437a6392d8)

## Bugs and Tracking

* [METRONOME-207](https://jira.mesosphere.com/browse/METRONOME-207) V0 Endpoint needs to support ForcePullImage

# Version 0.3.3

Diff [0.3.2-0.3.3](https://github.com/dcos/metronome/compare/v0.3.2...0e28f5653f2ee8726c8e1f6499063af19e435f39)

## Bugs and Tracking

* [METRONOME-188](https://jira.mesosphere.com/browse/METRONOME-188) Updated to Protocol Buffers v.3.3.0
* [METRONOME-196](https://jira.mesosphere.com/browse/METRONOME-196) ForcePullImage should not be required

# Version 0.3.1

Diff [0.2.4-0.3.1](https://github.com/dcos/metronome/compare/v0.2.4...v0.3.1)

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

https://s3.amazonaws.com/downloads.mesosphere.io/metronome/releases/0.3.1/metronome-0.3.1.tgz
sha: f6fd3d48a889ea19cb13dfd908a82e53c03ffab1

# Version 0.2.4

diff from [0.2.3-0.2.4](https://github.com/dcos/metronome/compare/87976...23fe8ca)

## Fixes

* Upgraded to cron-utils 6.0.4, fixes issues with cron calculations enabling crons such as `0 9 1-7 * 1-5` as mon-fri the first week of the month only.
* Documentation and Job placement examples provided.

# Version 0.2

Prepare Metronome for DC/OS 1.9.

## Overview

### Service integration tests

We now have a suite of integration tests for DC/OS that runs in our CI.

## Fixes

* Fix #96 Change constraints fields names to match schema.
* Fix #102 API Examples update: test.com -> example.com
* Fix #107 Add /v0/scheduled-jobs raml documentation.

# Version 0.1

Genesis
