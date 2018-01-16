""" Commons for Metronome """

from datetime import timedelta
from dcos import http

import shakedown

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
    print("http")
    print(url)
    try:
        response = http.get(url)
        print(response)
        return response.status_code == 200
    except Exception as e:
        print(e)
        return False

def metronome_api_url():
    return shakedown.dcos_url_path("/service/metronome/v1/jobs")


def assert_one_job_run_with_one_task(client, job_id):
    job_runs_count = len(client.get_runs(job_id))
    assert job_runs_count == 1, "Expecting 1 job run but found {} for job {}.".format(job_runs_count, job_id)
    job_run_tasks = client.get_runs(job_id)[0]["tasks"]
    assert len(job_run_tasks) == 1, "Expecting 1 job run task but found {} for job {}: {}.".format(len(job_run_tasks), job_id, job_run_tasks)
