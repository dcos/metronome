# Metronome [![Issues](https://img.shields.io/badge/Issues-JIRA-ff69b4.svg?style=flat)](https://jira.mesosphere.com/issues/?jql=project%20%3D%20DCOS_OSS%20AND%20component%20%3D%20metronome)

Metronome is an [Apache Mesos](http://mesos.apache.org) framework for scheduled jobs.


## Documentation

Metronome documentation is available on the [Metronome Project Site](https://dcos.github.io/metronome/) or [DC/OS documentation site](https://dcos.io/docs/1.10/deploying-jobs/).


## Issue Tracking
Metronome issues are tracked as JIRA tickets in Mesosphere's [on-premise JIRA instance](https://jira.mesosphere.com/issues/?jql=project%20%3D%20DCOS_OSS%20AND%20component%20%3D%20metronome) that anyone is able to view and add to using GitHub SSO. If you create a ticket, please set component `metronome`.

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
