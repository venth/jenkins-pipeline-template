def call(body) {
    // evaluate the body block, and collect configuration into the object
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()


    def label = "mypod-${UUID.randomUUID().toString()}"
    podTemplate(label: label, containers: [
        containerTemplate(
                name: 'java',
                image: 'openjdk:11-jre',
                ttyEnabled: true, command: 'cat',
                volumes: [hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')])]) {

            pipeline {
                    stage('checkout git') {
                            git branch: pipelineParams.branch, credentialsId: pipelineParams.credentialsId, url: pipelineParams.scmUrl
                    }

                    node(label) {
                        container('java') {
                            stage('build') {
                                    sh './gradlew clean build'
                            }
                        }
                    }

                    stage ('test') {
                            parallel (
                                    "unit tests": { echo "sh './gradlew test'" },
                                    "integration tests": { echo "sh 'mvn integration-test'" }
                            )
                    }

                    stage('deploy developmentServer'){
                            deploy(pipelineParams.developmentServer, pipelineParams.serverPort)
                    }

                    stage('deploy staging'){
                            deploy(pipelineParams.stagingServer, pipelineParams.serverPort)
                    }

                    stage('deploy production'){
                            deploy(pipelineParams.productionServer, pipelineParams.serverPort)
                    }
                post {
                    failure {
                        mail to: pipelineParams.email, subject: 'Pipeline failed', body: "${env.BUILD_URL}"
                    }
                }
            }
        }
}
