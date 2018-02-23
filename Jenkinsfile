#!/usr/bin/env groovy

@Library('sec_ci_libs@v2-latest') _

def master_branches = ["master", ] as String[]

ansiColor('gnome-terminal') {
    // using mesos node because it's a lightweight alpine docker image instead of full VM
    node('mesos') {
      stage("Verify author") {
        user_is_authorized(master_branches, '8b793652-f26a-422f-a9ba-0d1e47eb9d89', '#marathon-dev')
      }
    }
    node('JenkinsMarathonCI-Debian9-2018-02-09') {
      stage('Run Pipeline') {
        try {
            checkout scm
            sh "ci/ci_provision.sh"
            sh "bin/install-protobuf.sh"
            sh "sudo python -m pip install flake8"
            sh "sudo \"PATH=\$PATH:\$HOME/protobuf/bin\" -E ci/pipeline jenkins"
        } finally {
            junit(allowEmptyResults: true, testResults: '*/target/test-reports/*.xml')
            archive includes: "ci-${env.BUILD_TAG}.log.tar.gz"
            archive includes: "ci-${env.BUILD_TAG}.log"  // Only in case the build was  aborted and the logs weren't zipped
        }
      }
    }
}
