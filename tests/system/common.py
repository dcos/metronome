""" Commons for Metronome """

from datetime import timedelta

import json
import shakedown
import shlex
import time
import pytest

from distutils.version import LooseVersion

from dcos import metronome, packagemanager, cosmos, http
from dcos.errors import DCOSException
from json.decoder import JSONDecodeError
from retrying import retry


def job_no_schedule(id='pikachu', cmd='sleep 10000'):
    return {
        'id': id,
        'description': 'electrifying rodent',
        'run': {
            'cmd': cmd,
            'cpus': 0.01,
            'mem': 32,
            'disk': 0
        }
    }


def job_with_secrets(id='pikachu',
                     cmd='echo $SECRET_ENV >> $MESOS_SANDBOX/secret-env; sleep 5',
                     secret_name='secret_name'):
    return {
        'id': id,
        'description': 'electrifying rodent',
        'run': {
            'cmd': cmd,
            'cpus': 0.01,
            'mem': 32,
            'disk': 0,
            "env": {
                "SECRET_ENV": {
                    "secret": "secret1"
                }
            },
            "secrets": {
                "secret1": {
                    "source": secret_name
                }
            }
        }
    }


def job_with_file_based_secret(
        id='pikachu-fbs',
        cmd='cat $MESOS_SANDBOX/secret-file > $MESOS_SANDBOX/fbs-secret; sleep 30',
        secret_name='secret_name'):
    # secret container path can not have '/' prefix for secret, otherwise needs it
    return {
        'id': id,
        'description': 'electrifying rodent',
        'run': {
            'cmd': cmd,
            'cpus': 0.01,
            'mem': 32,
            'disk': 0,
            "volumes": [
                {
                    "containerPath": "secret-file",
                    "secret": "secret1"
                }
            ],
            'ucr': {
                "image": {
                    "id": "busybox"
                }
            },
            "secrets": {
                "secret1": {
                    "source": secret_name
                }
            }
        }
    }


def schedule():
    return {
        "concurrencyPolicy": "ALLOW",
        "cron": "20 0 * * *",
        "enabled": True,
        "id": "nightly",
        "startingDeadlineSeconds": 900,
        "timezone": "UTC"
    }


def pin_to_host(job_def, host):
    job_def['run']['placement'] = {
        "constraints": [{
            "attribute": "hostname",
            "operator": "LIKE",
            "value": host
        }]
    }


def add_docker_image(job_def, image='busybox'):
    job_def['run']['docker'] = {
        "image": image
    }


def add_ucr_image(job_def, image='busybox'):
    job_def['run']['ucr'] = {
        "image": {
          "id": image
        }
    }


def get_private_ip():
    agents = shakedown.get_private_agents()
    for agent in agents:
            return agent


def run_command_on_metronome_leader(command, username=None, key_path=None, noisy=True):
    """ Run a command on the Metronome leader
    """

    return shakedown.run_command(metronome_leader_ip(), command, username, key_path, noisy)


def metronome_leader_ip():
    return shakedown.dcos_dns_lookup('metronome.mesos')[0]['ip']


def constraints(name, operator, value=None):
    constraints = [name, operator]
    if value is not None:
        constraints.append(value)
    return constraints


# this should be migrated to metronome cli
def get_job_tasks(job_id, run_id):
    client = metronome.create_client()
    run = client.get_run(job_id, run_id)
    taskids = []
    for task in run['tasks']:
        taskids.append(task['id'])

    job_tasks = []
    all_job_tasks = shakedown.get_service_tasks('metronome')
    for task in all_job_tasks:
        for taskid in taskids:
            if taskid == task['id']:
                job_tasks.append(task)

    return job_tasks


def wait_for_mesos_endpoint(timeout_sec=timedelta(minutes=5).total_seconds()):
    """Checks the service url if available it returns true, on expiration
    it returns false"""

    return shakedown.time_wait(lambda: shakedown.mesos_available_predicate(), timeout_seconds=timeout_sec)


def ignore_exception(exc):
    """Used with @retrying.retry to ignore exceptions in a retry loop.
       ex.  @retrying.retry( retry_on_exception=ignore_exception)
       It does verify that the object passed is an exception
    """
    return isinstance(exc, Exception)


@retry(wait_exponential_multiplier=1000, wait_exponential_max=5*60*1000, retry_on_exception=ignore_exception)  # 5 mins
def wait_for_metronome():
    """ Waits for the Metronome API url to be available for a given timeout. """
    url = shakedown.dcos_url_path("/service/metronome/v1/jobs")
    try:
        response = http.get(url)
        assert response.status_code == 200, f"Expecting Metronome service to be up but it did not get healthy after 5 minutes. Last response: {response.content}"  # noqa
    except Exception as e:
        assert False, f"Expecting Metronome service to be up but it did not get healthy after 5 minutes. Last exception: {e}"  # noqa


@retry(wait_exponential_multiplier=1000, wait_exponential_max=5*60*1000, retry_on_exception=ignore_exception)  # 5 mins
def wait_for_cosmos():
    """ Waits for the Cosmos API to become responsive. """
    cosmos_pm = packagemanager.PackageManager(cosmos.get_cosmos_url())
    try:
        cosmos_pm.has_capability('METRONOME')
    except Exception as e:
        assert False, f"Expecting Metronome service to be up but it did not get healthy after 5 minutes. Last exception: {e}"  # noqa


def assert_job_run(client, job_id, runs_number=1, active_tasks_number=1):
    """
    Verify that the job has expected number of runs (active and finished) as well as tasks
    :param runs_number: number of runs, both active and finished are considered
    :param active_tasks_number: number of tasks for ACTIVE runs only
    """
    active_job_runs_count = len(client.get_runs(job_id))
    finished_job_runs_count = client.get_job(job_id, ['history'])['history']['successCount']
    # we are verifying both finished as well as active job runs
    assert active_job_runs_count + finished_job_runs_count == runs_number, \
        f"Expecting {runs_number} job run but found {active_job_runs_count} active " \
        f"and {finished_job_runs_count} finished for job {job_id}."
    if active_tasks_number > 0:
        # if this job has only finished runs, the tasks overview is no longer available
        job_run_tasks = client.get_runs(job_id)[0]["tasks"]
        assert len(job_run_tasks) == active_tasks_number, \
            f"Expecting {runs_number} job run task but found {len(job_run_tasks)} for job {job_id}: {job_run_tasks}."


def job_run_predicate(job_id, run_id):
    run = metronome.create_client().get_run(job_id, run_id)
    return run["status"] == "ACTIVE" or run["status"] == "SUCCESS"


def wait_for_job_started(job_id, run_id, timeout=120):
    "Verifies that a job with given run_id is in state running or finished. "
    shakedown.time_wait(lambda: job_run_predicate(job_id, run_id), timeout)


def assert_wait_for_no_additional_tasks(client, job_id, timeout=20, tasks_count=1):
    """ Starting Metronome and all its actors takes some time and there is no way how to query API
        to figure out it finished. Here we wait for given time and then assert that expected tasks count matches.
        This covers a bug regression of METRONOME-100
    """
    time.sleep(timeout)
    assert_job_run(client, job_id, active_tasks_number=tasks_count)


def not_required_masters_exact_count(count):
    """ Returns True if the number of masters is equal to
    the count.  This is useful in using pytest skipif such as:
    `pytest.mark.skipif('required_masters(3)')` which will skip the test if
    the number of masters is only 1.
    :param count: the number of required masters.
    """
    master_count = len(shakedown.get_all_masters())
    # reverse logic (skip if less than count)
    # returns True if less than count
    return master_count != count


def masters_exact(count):
    return pytest.mark.skipif('not_required_masters_exact_count({})'.format(count))


def install_enterprise_cli_package():
    """Install `dcos-enterprise-cli` package. It is required by the `dcos security`
       command to create secrets, manage service accounts etc.
    """
    print('Installing dcos-enterprise-cli package')
    cmd = 'package install dcos-enterprise-cli --cli --yes'
    stdout, stderr, return_code = shakedown.run_dcos_command(cmd, raise_on_error=True)


def is_enterprise_cli_package_installed():
    """Returns `True` if `dcos-enterprise-cli` package is installed."""
    stdout, stderr, return_code = shakedown.run_dcos_command('package list --json')
    print('package list command returned code:{}, stderr:{}, stdout: {}'.format(return_code, stderr, stdout))
    try:
        result_json = json.loads(stdout)
    except JSONDecodeError as error:
        raise DCOSException('Could not parse: "{}"'.format(stdout))(error)
    return any(cmd['name'] == 'dcos-enterprise-cli' for cmd in result_json)


def has_secret(secret_name):
    """Returns `True` if the secret with given name exists in the vault.
       This method uses `dcos security secrets` command and assumes that `dcos-enterprise-cli`
       package is installed.

       :param secret_name: secret name
       :type secret_name: str
    """
    stdout, stderr, return_code = shakedown.run_dcos_command('security secrets list / --json')
    if stdout:
        result_json = json.loads(stdout)
        return secret_name in result_json
    return False


def delete_secret(secret_name):
    """Delete a secret with a given name from the vault.
       This method uses `dcos security org` command and assumes that `dcos-enterprise-cli`
       package is installed.

       :param secret_name: secret name
       :type secret_name: str
    """
    print('Removing existing secret {}'.format(secret_name))
    stdout, stderr, return_code = shakedown.run_dcos_command('security secrets delete {}'.format(secret_name))
    assert return_code == 0, "Failed to remove existing secret"


def get_metronome_info():
    url = shakedown.dcos_url_path("/service/metronome/info")
    response = http.get(url)
    return response.json()


def get_metronome_version():
    info = get_metronome_info()
    return LooseVersion(info.get("version"))


# add to shakedown
def metronome_version_less_than(version):
    """ Returns True if metronome has a version less than {version}.
        :param version: required version
        :type: string
        :return: True if version < MoM version
        :rtype: bool
    """
    return get_metronome_version() < LooseVersion(version)


def create_secret(name, value=None, description=None):
    """Create a secret with a passed `{name}` and optional `{value}`.
       This method uses `dcos security secrets` command and assumes that `dcos-enterprise-cli`
       package is installed.

       :param name: secret name
       :type name: str
       :param value: optional secret value
       :type value: str
       :param description: option secret description
       :type description: str
    """
    print('Creating new secret {}:{}'.format(name, value))

    value_opt = '-v {}'.format(shlex.quote(value)) if value else ''
    description_opt = '-d "{}"'.format(description) if description else ''

    stdout, stderr, return_code = shakedown.run_dcos_command('security secrets create {} {} "{}"'.format(
        value_opt,
        description_opt,
        name), print_output=True)
    assert return_code == 0, "Failed to create a secret"


def get_metronome_endpoint(path, metronome_name='metronome'):
    """Returns the url for the metronome endpoint."""
    return shakedown.dcos_url_path('service/{}/{}'.format(metronome_name, path))


def metronome_version():
    """Returns the JSON of verison information for Metronome.
    """
    response = http.get(get_metronome_endpoint('info'))
    return response.json()


def cluster_info():
    print("DC/OS: {}, in {} mode".format(shakedown.dcos_version(), shakedown.ee_version()))
    agents = shakedown.get_private_agents()
    print("Agents: {}".format(len(agents)))

    about = metronome_version()
    print("Marathon version: {}".format(about))
