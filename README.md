# Metronome

Metronome is an [Apache Mesos](http://mesos.apache.org) framework for scheduled jobs.


## Documentation

Metronome documentation is available on the [DC/OS documentation site](https://dcos.io/docs/1.8/usage/jobs).


## Issue Tracking
We have been tracking issues in multiple places, which has made it hard for us to prioritize, and consolidate duplicates.

In order to address these challenges, on March 6th we will be converting all GitHub issues to public JIRA tickets in Mesosphere's [on-premise JIRA instance](https://jira.mesosphere.com/) that anyone will be able to view and add to using GitHub SSO. This will not only help the Marathon/Metronome teams, it will increase transparency, allowing the community to check on sprints and the order of the ticket backlog.

Please have a look here for more information: https://groups.google.com/forum/#!topic/marathon-framework/khtvf-ifnp8

## Getting Started

Get familiar with Metronome with this step-by-step [Getting Started](https://dcos.github.io/metronome/docs/getting_started.html) guide.

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

    ./bin/metronome -Dmetronome.mesos.master.url=local


## Help

Have you found an issue? Feel free to report it using our [Issues](https://github.com/dcos/metronome/issues) page.
In order to speed up response times, please provide as much information on how to reproduce the problem as possible. 

