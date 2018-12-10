"""Metronome 'Job' System Integration Tests"""

import contextlib
import uuid
import time

from datetime import timedelta

import common
import shakedown
import pytest

from common import job_no_schedule, schedule, not_required_masters_exact_count  # NOQA F401
from shakedown import dcos_version_less_than, marthon_version_less_than, required_masters, required_public_agents  # NOQA F401
from dcos import metronome
from retrying import retry

# DC/OS 1.8 is when Metronome was added.  Skip prior clusters
pytestmark = [pytest.mark.skipif("shakedown.dcos_version_less_than('1.8')")]
metronone_0_6_5 = pytest.mark.skipif('common.metronome_version_less_than("0.6.5")')


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
    except Exception:
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


def test_disable_schedule():
    """ Confirms that a schedule runs when enabled but then stops firing
        when the schedule is disabled.
    """
    client = metronome.create_client()
    job_id = 'schedule-disabled-{}'.format(uuid.uuid4().hex)
    job_json = job_no_schedule(job_id)
    with job(job_json):
        # indent
        job_schedule = schedule()
        job_schedule['cron'] = '* * * * *'  # every minute
        client.add_schedule(job_id, job_schedule)

        # sleep until we run
        time.sleep(timedelta(minutes=1.1).total_seconds())
        runs = client.get_runs(job_id)
        run_count = len(runs)
        # there is a race condition where this could be 1 or 2
        # both are ok... what matters is that after disabled, that there are
        # no more
        assert run_count > 0

        # update enabled = False
        job_schedule['enabled'] = False
        client.update_schedule(job_id, 'nightly', job_schedule)

        # wait for the next run
        time.sleep(timedelta(minutes=1.5).total_seconds())
        runs = client.get_runs(job_id)
        # make sure there are no more than the original count
        assert len(runs) == run_count


def test_disable_schedule_recovery_from_master_bounce():
    """ Confirms that a schedule runs when enabled but then stops firing
        when the schedule is disabled.
    """
    client = metronome.create_client()
    job_id = 'schedule-disabled-{}'.format(uuid.uuid4().hex)
    job_json = job_no_schedule(job_id)
    with job(job_json):
        # indent
        job_schedule = schedule()
        job_schedule['cron'] = '* * * * *'  # every minute
        client.add_schedule(job_id, job_schedule)

        # sleep until we run
        time.sleep(timedelta(minutes=1.1).total_seconds())
        runs = client.get_runs(job_id)
        run_count = len(runs)
        # there is a race condition where this could be 1 or 2
        # both are ok... what matters is that after disabled, that there are
        # no more
        assert run_count > 0

        # update enabled = False
        job_schedule['enabled'] = False
        client.update_schedule(job_id, 'nightly', job_schedule)

        # bounce metronome master
        common.run_command_on_metronome_leader('sudo /sbin/shutdown -r now')
        common.wait_for_cosmos()
        common.wait_for_metronome()

        # wait for the next run
        time.sleep(timedelta(minutes=1.5).total_seconds())
        runs = client.get_runs(job_id)
        # make sure there are no more than the original count
        assert len(runs) == run_count


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
        except Exception:
            pass
        assert not schedule_exists, "Schedule exists"


def remove_jobs():
    client = metronome.create_client()
    for job in client.get_jobs():
        client.remove_job(job['id'], True)


def test_job_constraints():
    client = metronome.create_client()
    host = common.get_private_ip()
    job_id = uuid.uuid4().hex
    job_def = job_no_schedule(job_id)
    common.pin_to_host(job_def, host)
    with job(job_def):
        # on the same node 3x
        for i in range(3):
            client.run_job(job_id)
            time.sleep(2)
            assert len(client.get_runs(job_id)) == 1
            run_id = client.get_runs(job_id)[0]['id']

            @retry(wait_fixed=1000, stop_max_delay=5000)
            def check_tasks():
                task = common.get_job_tasks(job_id, run_id)[0]
                task_ip = task['statuses'][0]['container_status']['network_infos'][0]['ip_addresses'][0]['ip_address']
                assert task_ip == host

            client.kill_run(job_id, run_id)

        assert len(client.get_runs(job_id)) == 0


def test_docker_job():
    client = metronome.create_client()
    job_id = uuid.uuid4().hex
    job_def = job_no_schedule(job_id)
    common.add_docker_image(job_def)
    with job(job_def):
        client.run_job(job_id)
        time.sleep(2)
        assert len(client.get_runs(job_id)) == 1


@metronone_0_6_5
def test_ucr_job():
    client = metronome.create_client()
    job_id = uuid.uuid4().hex
    job_def = job_no_schedule(job_id)
    common.add_ucr_image(job_def)
    with job(job_def):
        client.run_job(job_id)
        time.sleep(2)
        assert len(client.get_runs(job_id)) == 1


@shakedown.dcos_1_10
@pytest.mark.skipif("shakedown.ee_version() is None")
def test_secret_env_var(secret_fixture):

    secret_name, secret_value = secret_fixture

    client = metronome.create_client()
    job_id = uuid.uuid4().hex
    job_def = common.job_with_secrets(job_id, secret_name=secret_name)

    with job(job_def):
        run_id = client.run_job(job_id)['id']
        common.wait_for_job_started(job_id, run_id, timeout=timedelta(minutes=5).total_seconds())

        @retry(wait_fixed=1000, stop_max_delay=5000)
        def job_run_has_secret():
            stdout, stderr, return_code = shakedown.run_dcos_command("task log --all {} secret-env".format(run_id))
            logged_secret = stdout.rstrip()
            assert secret_value == logged_secret, ("secret value in stdout log incorrect or missing. "
                                                   "'{}' should be '{}'").format(logged_secret, secret_value)

        job_run_has_secret()


@metronone_0_6_5
@pytest.mark.skipif("shakedown.ee_version() is None")
def test_secret_file(secret_fixture):

    secret_name, secret_value = secret_fixture

    client = metronome.create_client()
    job_id = uuid.uuid4().hex
    job_def = common.job_with_file_based_secret(job_id, secret_name=secret_name)

    with job(job_def):
        run_id = client.run_job(job_id)['id']
        common.wait_for_job_started(job_id, run_id, timeout=timedelta(minutes=5).total_seconds())

        @retry(wait_fixed=1000, stop_max_delay=5000)
        def job_run_has_secret():
            stdout, stderr, return_code = shakedown.run_dcos_command("task log --all {} fbs-secret".format(run_id))
            logged_secret = stdout.rstrip()
            assert secret_value == logged_secret, ("secret value in stdout log incorrect or missing. "
                                                   "'{}' should be '{}'").format(logged_secret, secret_value)

        job_run_has_secret()


@shakedown.dcos_1_11
def test_metronome_shutdown_with_no_extra_tasks():
    """ Test for METRONOME-100 regression
        When Metronome is restarted it incorrectly started another task for already running job run task.
    """
    client = metronome.create_client()
    job_id = "metronome-shutdown-{}".format(uuid.uuid4().hex)
    with job(job_no_schedule(job_id)):
        # run a job before we shutdown Metronome
        run_id = client.run_job(job_id)["id"]
        common.wait_for_job_started(job_id, run_id)
        common.assert_job_run(client, job_id)

        # restart metronome process
        common.run_command_on_metronome_leader('sudo systemctl restart dcos-metronome')
        common.wait_for_metronome()

        # verify that no extra job runs were started when Metronome was restarted
        common.assert_wait_for_no_additional_tasks(tasks_count=1, client=client, job_id=job_id)


def setup_module(module):
    common.wait_for_metronome()
    common.wait_for_cosmos()
    agents = shakedown.get_private_agents()
    if len(agents) < 2:
        assert False, f"Incorrect Agent count. Expecting at least 2 agents, but have {len(agents)}"
    remove_jobs()


@contextlib.contextmanager
def job(job_json):
    job_id = job_json['id']
    client = metronome.create_client()
    client.add_job(job_json)
    try:
        yield
    finally:
        try:
            client.remove_job(job_id, True)
        except Exception as e:
            print(e)


@pytest.fixture(scope="function")
def secret_fixture():
    common.wait_for_cosmos()
    if not common.is_enterprise_cli_package_installed():
        common.install_enterprise_cli_package()

    secret_name = '/mysecret'
    secret_value = 'super_secret_password'
    common.create_secret(secret_name, secret_value)
    yield secret_name, secret_value
    common.delete_secret(secret_name)
