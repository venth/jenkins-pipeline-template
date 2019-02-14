def call(body) {
    // evaluate the body block, and collect configuration into the object
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    pipeline {
        agent any
        stages {
            stage('checkout git') {
                steps {
                    git branch: pipelineParams.branch, credentialsId: pipelineParams.credentialsId, url: pipelineParams.scmUrl
                }
            }

            stage('build') {
                steps {
                    sh './gradlew clean build'
                }
            }

            stage ('test') {
                steps {
                    parallel (
                        "unit tests": { echo "sh './gradlew test'" },
                        "integration tests": { echo "sh 'mvn integration-test'" }
                    )
                }
            }

            stage('deploy developmentServer'){
                steps {
                    deploy(pipelineParams.developmentServer, pipelineParams.serverPort)
                }
            }

            stage('deploy staging'){
                steps {
                    deploy(pipelineParams.stagingServer, pipelineParams.serverPort)
                }
            }

            stage('deploy production'){
                steps {
                    deploy(pipelineParams.productionServer, pipelineParams.serverPort)
                }
            }
        }
        post {
            failure {
                mail to: pipelineParams.email, subject: 'Pipeline failed', body: "${env.BUILD_URL}"
            }
        }
    }
}
