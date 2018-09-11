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
    node('JenkinsMarathonCI-Debian9-2018-04-09') {
      stage('Run Pipeline') {
        try {
            checkout scm
            sh "bin/install-protobuf.sh"
            sh "PATH=\$PATH:\$HOME/protobuf/bin sudo ci/pipeline jenkins"
        } finally {
            junit(allowEmptyResults: true, testResults: 'target/test-reports/*.xml')
            junit(allowEmptyResults: true, testResults: 'tests/integration/target/test-reports/*.xml')
            archive includes: "*sandboxes.tar.gz"
            archive includes: "*log.tar.gz"
        }
      }
    }
}
