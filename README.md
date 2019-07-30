# Metronome [![Issues](https://img.shields.io/badge/Issues-JIRA-ff69b4.svg?style=flat)](https://jira.mesosphere.com/issues/?jql=project%20%3D%20DCOS_OSS%20AND%20component%20%3D%20metronome)

Metronome is an [Apache Mesos](http://mesos.apache.org) framework for scheduled jobs.


## Documentation

Metronome documentation is available on the [Metronome Project Site](https://dcos.github.io/metronome/) or [DC/OS documentation site](https://dcos.io/docs/1.10/deploying-jobs/).


## Issue Tracking
Metronome issues are tracked as JIRA tickets in Mesosphere's [on-premise JIRA instance](https://jira.mesosphere.com/issues/?jql=project%20%3D%20DCOS_OSS%20AND%20component%20%3D%20metronome) that anyone is able to view and add to using GitHub SSO. If you create a ticket, please set component `metronome`.


## Installation

The by far easiest way to get Metronome running is to use DC/OS.

### Manual setup

#### Dependencies

When rolling the deployment of Metronome yourself make sure to align with the dependency on [Marathon](https://github.com/mesosphere/marathon) to match the version noted in the release notes of Metronome.

#### Download

Releases can be downloaded by finding out the version number and short commit hash from the [github releases](https://github.com/dcos/metronome/releases) to form the url like

`https://s3.amazonaws.com/downloads.mesosphere.io/metronome/builds/0.6.33-b28106a/metronome-0.6.33-b28106a.tgz`

#### Installation

It is assumed that you have a running Mesos and Marathon setup. https://mesosphere.github.io/marathon/docs/ details how to setup Mesos and Marathon.

You can start Metronome via systemd after unarchiving the download to e.g. `/opt/mesosphere/metronome`

```
[Unit]
Description=Metronome
After=network.target
Wants=network.target
[Service]
EnvironmentFile=-/etc/sysconfig/metronome
ExecStart=/opt/mesosphere/metronome/bin/metronome
Restart=always
RestartSec=20
[Install]
WantedBy=multi-user.target
```

Configuration in the case of above systemd file happens via EnfironmentFile at `/etc/sysconfig/metronome`

```
# configure the url to reach zookeeper on your managers
METRONOME_ZK_URL=zk://manager0:2181,manager1:2181,manager2:2181/metronome
# configure the url to reach the mesos zookeeper state
METRONOME_MESOS_MASTER_URL=zk://manager0:2181,manager1:2181,manager2:2181/mesos
# in case you have configured mesos roles and/or authentication
# METRONOME_MESOS_ROLE=metronome
# METRONOME_MESOS_AUTHENTICATION_ENABLED=true
# METRONOME_MESOS_AUTHENTICATION_PRINCIPAL=metronome
METRONOME_MESOS_AUTHENTICATION_SECRET_FILE=/etc/mesos/metronome.secret
# configures url of Metronome web interface
METRONOME_WEB_UI_URL=127.0.0.1:9999/ui
```


## Getting Started

Get familiar with Metronome with this step-by-step [Getting Started](https://dcos.io/docs/1.10/deploying-jobs/) guide.

## API Reference

Consult the full [Metronome REST API reference](http://dcos.github.io/metronome/docs/generated/api.html).

An unofficial Go client library, [metronome-client](https://github.com/mindscratch/metronome-client) has been created for the v1 API.

## Contributing

We heartily welcome external contributions to Metronome's codebase and documentation.
Please see our [Contributor Guidelines](https://dcos.github.io/metronome/docs/contributing.html).


### Building from Source

To build Metronome from source, check out this repo and use sbt to build a universal package:

        git clone https://github.com/dcos/metronome.git
        cd metronome
        sbt universal:packageBin

In order to build from source you will need protobuf version 2.6.1.  This can be installed by executing the `./bin/install-protobuf.sh`.   This will install protobuf to `$HOME/protobuf`.    You will need `$HOME/protobuf/bin` in your path ( `export PATH=~/protobuf/bin:$PATH`).

### Running in Development Mode

Mesos local mode allows you to run Metronome without launching a full Mesos
cluster. It is meant for experimentation and not recommended for production
use. Note that you still need to run ZooKeeper for storing state. The following
command launches Metronome on Mesos in *local mode*.

    sbt run

If you want to run Metronome against a real Mesos cluster, you can use the following command.

    ./run.sh

The script is already pre-filled with a default values for zookeeper and mesos running locally. You can specify your own like this:

    ./run.sh "zk://127.0.0.1:2181/metronome" "127.0.0.1:5050" "8989"


 ## Example Job with Placement Constraint

 ```
 {
    "id": "sample-job",
    "description": "A sample job that sleeps",
    "run": {
	"cmd": "sleep 1000",
	"cpus": 0.01,
	"mem": 32,
	"disk": 0,
	"placement": {
	    "constraints": [
		{
		    "attribute": "hostname",
		    "operator": "LIKE",
		    "value": "<host-name>"
		}
	    ]
	}
    },
    "schedules": [
         {
             "id": "sample-schedule",
             "enabled": true,
             "cron": "0 0 * * *",
             "concurrencyPolicy": "ALLOW"
         }
     ]
}
 ```
 This job will sleep every day at midnight and will land on the host defined by `<host-name>` which could be the hostname or IP of a node in the cluster.  If you don't care where it lands in the cluster remove the `placement`
element.

## Help

Have you found an issue? Feel free to report it using our [JIRA](https://jira.mesosphere.com/issues/?jql=project%20%3D%20DCOS_OSS%20AND%20component%20%3D%20metronome). Please set component `metronome` for issues related to Metronome.
In order to speed up response times, please provide as much information on how to reproduce the problem as possible.
