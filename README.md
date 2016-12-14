# Metronome

Metronome is an [Apache Mesos](http://mesos.apache.org) framework for scheduled jobs.


## Documentation

Metronome documentation is available on the [DC/OS documentation site](https://dcos.io/docs/1.8/usage/jobs).


## Getting Started

Get familiar with Metronome with this step-by-step [Getting Started](https://dcos.github.io/metronome/docs/getting_started.html) guide.

## API Reference

Consult the full [Metronome REST API reference](http://dcos.github.io/metronome/docs/generated/api.html).

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

