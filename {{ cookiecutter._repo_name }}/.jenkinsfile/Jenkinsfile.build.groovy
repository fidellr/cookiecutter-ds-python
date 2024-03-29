#!/usr/bin/env groovy
@Library("porter-jenkins-lib@master") _

// GKE deployment setting for the application
def appName = "{{ cookiecutter._repo_name }}"
def environment = "stg" //rmi build always in stage

def progressDeadlineSeconds = 180

coreJenkinsWorkerNode(
        environment: environment,
        appName: appName,
        slackChannel: "#data-jenkins-hub",
        // The gke cluster where the training will be executed
        // Set to default
        clusterProjectId: "tvlk-data-mlplatform-prod",
        clusterName: "rm-training",
        clusterZone: "asia-southeast1-a",

        additionalContainers:[
            containerTemplate(name: 'python', image: 'python:3.6', ttyEnabled: true, command: 'python')
        ]
){
    stage('Checkout') {
        checkout scm
    }

    def commitId = sh(returnStdout: true, script: "git rev-parse HEAD").trim().take(6)
    def plainTextName = "raring-meerkat-model-builder.json"
    def cipherTextName = "raring-meerkat-model-builder.json.enc"
    def cipherTextLocation = "gs://raring-meerkat-common-kms/${cipherTextName}"

    currentBuild.displayName = "#$BUILD_NUMBER ${commitId}"
    currentBuild.description = "Commit Hash: ${commitId}"

    // UNCOMMENT THIS SECTION IF YOU WANT TO ADD UNIT TEST
    // stage('Test') {
    //     container('python') {
    //         //install the requirement from this app
    //         sh "pip install -r requirements.txt"
    //         sh "pip install -r test-requirements.txt"

    //         // run unit test here, using pytest or unittest
    //         sh "pytest tests"
    //     }
    // }

    stage('Build') {
        withCredentials([string(credentialsId: 'rmi-access-token', variable: 'rmiAccessToken')]) {
            sh "gsutil cp ${cipherTextLocation} ."
            sh """
                gcloud kms decrypt \
                    --location global \
                    --keyring raring-meerkat-common \
                    --key model-builder \
                    --ciphertext-file ${cipherTextName} \
                    --plaintext-file ${plainTextName}
            """
            sh "wget https://storage.googleapis.com/rmi-releases/master-cd/latest/linux/amd64/rmi.tar.gz"
            sh "tar xzf rmi.tar.gz"
            sh "chmod +x rmi"
            sh "GOOGLE_APPLICATION_CREDENTIALS=${plainTextName} ./rmi version"
            sh "RMI_ACCESS_TOKEN=${rmiAccessToken} GOOGLE_APPLICATION_CREDENTIALS=${plainTextName} ./rmi build"
        }
    }
}