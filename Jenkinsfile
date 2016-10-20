#!groovy

node {
  try {
    // Checkout the proper revision into the workspace.
    stage('checkout') {
      checkout scm
    }

    // Execute `cibuild` wrapped within a plugin that translates
    // ANSI color codes to something that renders inside the Jenkins
    // console.
    stage('cibuild') {
      wrap([$class: 'AnsiColorBuildWrapper']) {
        sh 'scripts/cibuild'
      }
    }

    if (env.BRANCH_NAME == 'develop' || env.BRANCH_NAME.startsWith('release/')) {
      env.AWS_DEFAULT_REGION = 'us-east-1'
      env.RF_SETTINGS_BUCKET = 'rasterfoundry-staging-config-us-east-1'

      // Publish container images built and tested during `cibuild`
      // to the private Amazon Container Registry tagged with the
      // first seven characters of the revision SHA.
      stage('cipublish') {
        // Decode the `AWS_ECR_ENDPOINT` credential stored within
        // Jenkins. In includes the Amazon ECR registry endpoint.
        withCredentials([[$class: 'StringBinding',
                          credentialsId: 'AWS_ECR_ENDPOINT',
                          variable: 'AWS_ECR_ENDPOINT']]) {
          wrap([$class: 'AnsiColorBuildWrapper']) {
            sh './scripts/cipublish'
          }
        }
      }

      // Plan and apply the current state of the instracture as
      // outlined by the `develop` branch of the `raster-foundry-deployment`
      // repository.
      //
      // Also, use the container image revision referenced above to
      // cycle in the newest version of the application into Amazon
      // ECS.
      stage('infra') {
        checkout scm: [$class: 'GitSCM',
                       branches: [[name: 'develop']],
                       extensions: [[$class: 'RelativeTargetDirectory',
                                     relativeTargetDir: 'raster-foundry-deployment']],
                       userRemoteConfigs: [[credentialsId: '3bc1e878-814a-43d1-864e-2e378ebddb0f',
                                            url: 'https://github.com/azavea/raster-foundry-deployment.git']]]

        dir('raster-foundry-deployment') {
          wrap([$class: 'AnsiColorBuildWrapper']) {
            sh './scripts/infra plan'
            sh './scripts/infra apply'
          }
        }
      }
    }
  } catch (err) {
    // Some exception was raised in the `try` block above. Assemble
    // an appropirate error message for Slack.
    def slackMessage = ":jenkins-angry: *raster-foundry (${env.BRANCH_NAME}) #${env.BUILD_NUMBER}*"
    if (env.CHANGE_TITLE) {
      slackMessage += "\n${env.CHANGE_TITLE} - ${env.CHANGE_AUTHOR}"
    }
    slackMessage += "\n<${env.BUILD_URL}|View Build>"
    slackSend color: 'danger', message: slackMessage

    // Re-raise the exception so that the failure is propagated to
    // Jenkins.
    throw err
  } finally {
    // Pass or fail, ensure that the services and networks
    // created by Docker Compose are torn down.
    sh 'docker-compose down -v'
  }
}
