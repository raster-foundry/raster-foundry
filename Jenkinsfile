#!groovy

node {
  currentBuild.result = 'SUCCESS'

  try {
    stage 'checkout'

    checkout scm

    stage 'cibuild'

    wrap([$class: 'AnsiColorBuildWrapper']) {
      sh 'scripts/cibuild'
    }

    if (env.BRANCH_NAME == 'develop' || env.BRANCH_NAME.startsWith('release/')) {
      env.AWS_DEFAULT_REGION = 'us-east-1'
      env.RF_SETTINGS_BUCKET = 'rasterfoundry-staging-config-us-east-1'

      stage 'cipublish'

      withCredentials([[$class: 'StringBinding',
                        credentialsId: 'AWS_ECR_ENDPOINT',
                        variable: 'AWS_ECR_ENDPOINT']]) {
        wrap([$class: 'AnsiColorBuildWrapper']) {
          sh './scripts/cipublish'
        }
      }

      stage 'infra'

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
  } catch (err) {
    currentBuild.result = 'FAILURE'

    throw err
  } finally {
    def buildEmoji = (currentBuild.result == 'SUCCESS') ? ':thumbsup:' : ':jenkins-angry:'
    def buildColor = (currentBuild.result == 'SUCCESS') ? 'good' : 'danger'

    def slackMessage = "${buildEmoji} *raster-foundry (${env.BRANCH_NAME}) #${env.BUILD_NUMBER}*"
    if (env.CHANGE_TITLE) {
      slackMessage += "\n${env.CHANGE_TITLE} - ${env.CHANGE_AUTHOR}"
    }
    slackMessage += "\n<${env.BUILD_URL}|View Build>"

    if (currentBuild.result == 'FAILURE' ||
        (currentBuild.result == 'SUCCESS' &&
         (env.BRANCH_NAME == 'develop'
          || env.BRANCH_NAME.startsWith('release/')
          || env.BRANCH_NAME.startsWith('PR-')))) {
      slackSend color: buildColor, message: slackMessage
    }
  }
}
