#!/usr/bin/env groovy

ansiColor('gnome-terminal') {
    node('jdk8-scala') {
      stage('Run Pipeline') {
        try {
            checkout scm
            sh "ping -c 1 leader.mesos"
            sh "PATH=\$PATH:\$HOME/protobuf/bin sbt clean test"
        } finally {
            junit(allowEmptyResults: true, testResults: '*/target/test-reports/*.xml')
        }
      }
    }
}
