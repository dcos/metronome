#!/bin/bash
set -x +e -o pipefail

# Two parameters are expected: CHANNEL and VARIANT where CHANNEL is the respective PR and
# VARIANT could be one of three custer variants: open, strict or permissive.
if [ "$#" -ne 2 ]; then
    echo "Expected 2 parameters: <channel> and <variant> e.g. si.sh testing/pull/1739 open"
    exit 1
fi

CHANNEL="$1"
VARIANT="$2"

JOB_NAME_SANITIZED=$(echo "$JOB_NAME" | tr -c '[:alnum:]-' '-')
DEPLOYMENT_NAME="$JOB_NAME_SANITIZED-$BUILD_NUMBER"
INFO_PATH="$DEPLOYMENT_NAME.info.json"
ROOT_PATH=$(pwd)

# Change work directory to ./tests
cd tests/system || exit 1

function create-junit-xml {
    local testsuite_name=$1
    local testcase_name=$2
    local error_message=$3

	cat > "$ROOT_PATH/shakedown.xml" <<-EOF
	<testsuites>
	  <testsuite name="$testsuite_name" errors="0" skipped="0" tests="1" failures="1">
	      <testcase classname="$testsuite_name" name="$testcase_name">
	        <failure message="test setup failed">$error_message</failure>
	      </testcase>
	  </testsuite>
	</testsuites>
	EOF
}

function exit-with-cluster-launch-error {
    echo "$1"
    create-junit-xml "dcos-launch" "cluster.create" "$1"
    pipenv run dcos-launch -i "$INFO_PATH" delete
    echo "metronome.build.$JOB_NAME_SANITIZED.cluster_launch.failure"
    exit 0
}

function download-diagnostics-bundle {
	BUNDLE_NAME="$(pipenv run dcos node diagnostics create all | grep -oE 'bundle-.*')"
	echo "Waiting for bundle ${BUNDLE_NAME} to be downloaded"
	STATUS_OUTPUT="$(pipenv run dcos node diagnostics --status)"
	while [[ $STATUS_OUTPUT =~ "is_running: True" ]]; do
		echo "Diagnostics job still running, retrying in 5 seconds."
		sleep 5
		STATUS_OUTPUT="$(pipenv run dcos node diagnostics --status)"
	done
	pipenv run dcos node diagnostics download "${BUNDLE_NAME}" --location=./diagnostics.zip
}

# Install dependencies and expose new PATH value.
# shellcheck source=../../ci/si_install_deps.sh
source "$ROOT_PATH/ci/si_install_deps.sh"

# Launch cluster and run tests if launch was successful.
CLI_TEST_SSH_KEY="$(pwd)/$DEPLOYMENT_NAME.pem"
export CLI_TEST_SSH_KEY

if [ "$VARIANT" == "strict" ]; then
  DCOS_URL="https://$( "$ROOT_PATH/ci/launch_cluster.sh" "$CHANNEL" "$VARIANT" "$DEPLOYMENT_NAME" | tail -1 )"
  wget --no-check-certificate -O fixtures/dcos-ca.crt "$DCOS_URL/ca/dcos-ca.crt"
else
  DCOS_URL="http://$( "$ROOT_PATH/ci/launch_cluster.sh" "$CHANNEL" "$VARIANT" "$DEPLOYMENT_NAME" | tail -1 )"
fi

CLUSTER_LAUNCH_CODE=$?
export DCOS_URL
case $CLUSTER_LAUNCH_CODE in
  0)
      echo "marathon.build.$JOB_NAME_SANITIZED.cluster_launch.success"
      cp -f "$DOT_SHAKEDOWN" "$HOME/.shakedown"
      timeout --preserve-status -s KILL 1.5h make test
      SI_CODE=$?
      if [ ${SI_CODE} -gt 0 ]; then
        echo "marathon.build.$JOB_NAME_SANITIZED.failure"
        download-diagnostics-bundle
      else
        echo "marathon.build.$JOB_NAME_SANITIZED.success"
      fi
      pipenv run dcos-launch -i "$INFO_PATH" delete || true
      exit "$SI_CODE" # Propagate return code.
      ;;
  2) exit-with-cluster-launch-error "Cluster launch failed.";;
  3) exit-with-cluster-launch-error "Cluster did not start in time.";;
  *) exit-with-cluster-launch-error "Unknown error in cluster launch: $CLUSTER_LAUNCH_CODE";;
esac
