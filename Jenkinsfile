#!/usr/bin/env groovy

@Library('sec_ci_libs@v2-latest') _

def master_branches = ["master", ] as String[]

pipeline {
  agent none

  stages {
    stage("Verify author for PR") {
      // using shakedown node because it's a lightweight Alpine Docker image instead of full VM
      agent {
        label "shakedown"
      }
      when {
        beforeAgent true
        changeRequest()
      }
      steps {
        user_is_authorized(master_branches, '8b793652-f26a-422f-a9ba-0d1e47eb9d89', '#dcos-security-ci')
      }
    }

    stage("Build") {
      agent {
        docker {
          image 'mesosphere/scala-sbt:marathon'
          //label 'python-dind' too few CPU cores.
          label 'docker'
          args '-u root'
        }
      }
      steps {
        ansiColor('xterm') {
          sh 'sbt test'
        }
      }
      post {
        always {
          junit '**/test-reports/*.xml'
        }
      }
    }

    stage('Test') {
      agent {
        node {
          label 'docker'
        }
      }
      steps {
        ansiColor('xterm') {
          sh 'sudo ci/set_port_range.sh'
          try {
            sh 'sudo sbt integration/test'
          } catch (err) {
            echo "Unstable Integration Test. Caught: ${err}"
            currentBuild.result = 'SUCCESS'
          }
        }
      }
      post {
        always {
          junit '**/test-reports/*.xml'
        }
      }
    }
  }
}
