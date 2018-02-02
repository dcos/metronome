""" Commons for Metronome """

from datetime import timedelta
from dcos import http

import shakedown
import time
import pytest

from dcos import metronome


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


def get_private_ip():
    agents = shakedown.get_private_agents()
    for agent in agents:
            return agent


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


def metronome_available_predicate():
    url = metronome_api_url()
    try:
        response = http.get(url)
        return response.status_code == 200
    except Exception as e:
        return False


def metronome_api_url():
    return shakedown.dcos_url_path("/service/metronome/v1/jobs")


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
