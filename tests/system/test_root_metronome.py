"""Metronome 'Job' System Integration Tests"""

from common import *
import contextlib
from shakedown import *
from dcos import metronome
from retrying import retry

import time
import uuid


def test_add_job():
    client = metronome.create_client()
    with job(job_no_schedule()):
        job_id = job_no_schedule()['id']
        response = client.get_job(job_id)
        assert response.get('id') == job_id


def test_remove_job():
    client = metronome.create_client()
    client.add_job(job_no_schedule('remove-job'))
    assert client.remove_job('remove-job') is None
    job_exists = False
    try:
        client.get_job('remove-job')
        job_exists = True
    except:
        pass
    assert not job_exists, "Job exists"


def test_list_jobs():
    client = metronome.create_client()
    with job(job_no_schedule('job1')):
        with job(job_no_schedule('job2')):
            jobs = client.get_jobs()
            assert len(jobs) == 2


def test_update_job():
    client = metronome.create_client()
    job_json = job_no_schedule('update-job')
    with job(job_json):
        assert client.get_job('update-job')['description'] == 'electrifying rodent'

        job_json['description'] = 'updated description'
        client.update_job('update-job', job_json)
        assert client.get_job('update-job')['description'] == 'updated description'


def test_add_schedule():
    client = metronome.create_client()
    with job(job_no_schedule('schedule')):
        client.add_schedule('schedule', schedule())
        assert client.get_schedule('schedule', 'nightly')['cron'] == '20 0 * * *'


def test_update_schedule():
    client = metronome.create_client()
    with job(job_no_schedule('schedule')):
        client.add_schedule('schedule', schedule())
        assert client.get_schedule('schedule', 'nightly')['cron'] == '20 0 * * *'
        schedule_json = schedule()
        schedule_json['cron'] = '10 0 * * *'
        client.update_schedule('schedule', 'nightly', schedule_json)
        assert client.get_schedule('schedule', 'nightly')['cron'] == '10 0 * * *'


def test_run_job():
    client = metronome.create_client()
    job_id = uuid.uuid4().hex
    with job(job_no_schedule(job_id)):
        runs = client.get_runs(job_id)
        assert len(runs) == 0

        client.run_job(job_id)
        time.sleep(2)
        assert len(client.get_runs(job_id)) == 1


def test_get_job_run():
    client = metronome.create_client()
    job_id = uuid.uuid4().hex
    with job(job_no_schedule(job_id)):
        client.run_job(job_id)
        time.sleep(2)
        run_id = client.get_runs(job_id)[0]['id']
        run = client.get_run(job_id, run_id)
        assert run['id'] == run_id
        assert run['status'] in ['ACTIVE', 'INITIAL']


def test_stop_job_run():
    client = metronome.create_client()
    job_id = uuid.uuid4().hex
    with job(job_no_schedule(job_id)):
        client.run_job(job_id)
        time.sleep(2)
        assert len(client.get_runs(job_id)) == 1
        run_id = client.get_runs(job_id)[0]['id']
        client.kill_run(job_id, run_id)

        assert len(client.get_runs(job_id)) == 0


def test_remove_schedule():
    client = metronome.create_client()
    with job(job_no_schedule('schedule')):
        client.add_schedule('schedule', schedule())
        assert client.get_schedule('schedule', 'nightly')['cron'] == '20 0 * * *'
        client.remove_schedule('schedule', 'nightly')
        schedule_exists = False
        try:
            client.get_schedule('schedule', 'nightly')
            schedule_exists = True
        except:
            pass
        assert not schedule_exists, "Schedule exists"


def remove_jobs():
    client = metronome.create_client()
    for job in client.get_jobs():
        client.remove_job(job['id'], True)


def test_job_constraints():
    client = metronome.create_client()
    host = get_private_ip()
    job_id = uuid.uuid4().hex
    job_def = job_no_schedule(job_id)
    pin_to_host(job_def, host)
    with job(job_def):
        # on the same node 3x
        for i in range(3):
            client.run_job(job_id)
            time.sleep(2)
            assert len(client.get_runs(job_id)) == 1
            run_id = client.get_runs(job_id)[0]['id']

            @retry(wait_fixed=1000, stop_max_delay=5000)
            def check_tasks():
                task = get_job_tasks(job_id, run_id)[0]
                task_ip = task['statuses'][0]['container_status']['network_infos'][0]['ip_addresses'][0]['ip_address']
                assert task_ip == host

            client.kill_run(job_id, run_id)

        assert len(client.get_runs(job_id)) == 0


def test_docker_job():
    client = metronome.create_client()
    job_id = uuid.uuid4().hex
    job_def = job_no_schedule(job_id)
    add_docker_image(job_def)
    with job(job_def):
        client.run_job(job_id)
        time.sleep(2)
        assert len(client.get_runs(job_id)) == 1


def setup_module(module):
    agents = get_private_agents()
    if len(agents) < 2:
        assert False, "Incorrect Agent count"
    remove_jobs()


@contextlib.contextmanager
def job(job_json):
    job_id = job_json['id']
    client = metronome.create_client()
    client.add_job(job_json)
    try:
        yield
    finally:
        client.remove_job(job_id, True)
