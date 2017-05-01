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
"""DCOS Job
Usage:
    dcos job --help
    dcos job --version

Options:
    --help                  Show this screen
    --version               Show version
"""

import click
import pkg_resources
from dcos_job import version


@click.group()
def cli():
    pass


@cli.group(invoke_without_command=True)
@click.option('--version', is_flag=True)
@click.option('--config-schema',
              help='Prints the config schema for job.', is_flag=True)
def job(version, config_schema):
    """CLI Module for interaction with a DCOS Job service"""
    if version:
        print_version()
    if config_schema:
        print_schema()


def print_version():
    print('dcos job cli version {}'.format(version.version))


def print_schema():
    schema = pkg_resources.resource_string(
        'dcos_job',
        'data/config-schema/job.json'
    ).decode('utf-8')
    print(schema)


def main():
    cli(obj={})


if __name__ == "__main__":
    main()
