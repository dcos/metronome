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
            withCredentials(
            [ [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'mesosphere-ci-marathon', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'],
              [$class: 'FileBinding', credentialsId: '11fcc957-5156-4470-ae34-d433da88248a', variable: 'DOT_SHAKEDOWN']
            ]) {
                sh "bin/install-protobuf.sh"
                sh "sudo PATH=\$PATH:\$HOME/protobuf/bin ci/pipeline jenkins"
            }
        } finally {
            junit(allowEmptyResults: true, testResults: 'target/test-reports/*.xml')
            junit(allowEmptyResults: true, testResults: 'tests/integration/target/test-reports/*.xml')
            archive includes: "*sandboxes.tar.gz"
            archive includes: "*log.tar.gz"
        }
      }
    }
}
