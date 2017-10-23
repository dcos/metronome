#!/usr/bin/env groovy

ansiColor('gnome-terminal') {
    node('jdk8-scala') {
      stage('Run Pipeline') {
        try {
            checkout scm
            sh "bin/install-protobuf.sh"
            sh "sbt clean test"
        } finally {
            junit(allowEmptyResults: true, testResults: 'target/test-reports/*.xml')
        }
      }
    }
}
