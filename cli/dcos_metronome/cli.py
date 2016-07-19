#
#    Copyright (C) 2015 Mesosphere, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""DCOS Metronome
Usage:
    dcos metronome --help
    dcos metronome --version

Options:
    --help                  Show this screen
    --version               Show version
"""

import click
import dcos_metronome.metronome_utils as mu
import pkg_resources
from dcos_metronome import version


@click.group()
def cli():
    pass


@cli.group(invoke_without_command=True)
@click.option('--version', is_flag=True)
@click.option('--name', help='Name of the Metronome instance to query')
@click.option('--config-schema',
              help='Prints the config schema for metronome.', is_flag=True)
def metronome(version, name, config_schema):
    """CLI Module for interaction with a DCOS Metronome service"""
    if version:
        print_version()
    if name:
        mu.set_fwk_name(name)
    if config_schema:
        print_schema()


def print_version():
    print('dcos-metronome version {}'.format(version.version))


def print_schema():
    schema = pkg_resources.resource_string(
        'dcos_metronome',
        'data/config-schema/metronome.json'
    ).decode('utf-8')
    print(schema)


@metronome.command()
def help():
    print("Usage: dcos help metronome")


@metronome.group()
@click.option('--name', help='Name of the Metronome instance to query')
def config(name):
    """Service configuration maintenance"""
    if name:
        mu.set_fwk_name(name)


def main():
    cli(obj={})


if __name__ == "__main__":
    main()
