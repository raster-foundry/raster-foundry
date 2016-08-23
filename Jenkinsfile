node {
  try {
    stage 'checkout'

    checkout scm

    stage 'cibuild'

    wrap([$class: 'AnsiColorBuildWrapper']) {
      sh 'scripts/cibuild'
    }

    stage 'cipublish'

    if (env.BRANCH_NAME == 'develop') {
      withCredentials([[$class: 'StringBinding', credentialsId: 'AWS_ECR_ENDPOINT', variable: 'AWS_ECR_ENDPOINT']]) {
        wrap([$class: 'AnsiColorBuildWrapper']) {
          env.AWS_DEFAULT_REGION = 'us-east-1'

          sh './scripts/cipublish'
        }
      }

      slackSend color: 'good', message: "raster-foundry/${env.BRANCH_NAME} #${env.BUILD_NUMBER}: Success! (<${env.BUILD_URL}|Open>)"
    }
  } catch (err) {
    slackSend color: 'bad', message: "raster-foundry/${env.BRANCH_NAME} #${env.BUILD_NUMBER}: Failure! (<${env.BUILD_URL}|Open>)"

    throw err
  }
}
