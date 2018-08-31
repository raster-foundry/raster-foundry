#!groovy

node {
  try {
    // Checkout the proper revision into the workspace.
    stage('checkout') {
      checkout scm
    }

    env.AWS_DEFAULT_REGION = 'us-east-1'
    env.RF_ARTIFACTS_BUCKET = 'rasterfoundry-global-artifacts-us-east-1'

    // Execute `cibuild` wrapped within a plugin that translates
    // ANSI color codes to something that renders inside the Jenkins
    // console.
    stage('cibuild') {
      env.RF_SETTINGS_BUCKET = 'rasterfoundry-testing-config-us-east-1'

      wrap([$class: 'AnsiColorBuildWrapper']) {
        sh 'scripts/cibuild'
      }
    }

    env.RF_SETTINGS_BUCKET = 'rasterfoundry-staging-config-us-east-1'

    if (env.BRANCH_NAME == 'develop' || env.BRANCH_NAME =~ /test\//) {
        env.RF_DOCS_BUCKET = 'rasterfoundry-staging-docs-site-us-east-1'
        env.RF_DEPLOYMENT_BRANCH = 'develop'
        env.RF_DEPLOYMENT_ENVIRONMENT = "Staging"

      // Publish container images built and tested during `cibuild`
      // to the private Amazon Container Registry tagged with the
      // first seven characters of the revision SHA.
      stage('cipublish') {
        // Decode the `AWS_ECR_ENDPOINT` credential stored within
        // Jenkins. In includes the Amazon ECR registry endpoint.
        withCredentials([[$class: 'StringBinding',
                          credentialsId: 'AWS_ECR_ENDPOINT',
                          variable: 'AWS_ECR_ENDPOINT'], 
                          [$class: 'StringBinding',
                          credentialsId: 'SONATYPE_USERNAME',
                          variable: 'SONATYPE_USERNAME'],
                          [$class: 'StringBinding',
                          credentialsId: 'SONATYPE_PASSWORD',
                          variable: 'SONATYPE_PASSWORD'],
                          [$class: 'StringBinding',
                          credentialsId: 'PGP_HEX_KEY',
                          variable: 'PGP_HEX_KEY'],
                          [$class: 'StringBinding',
                          credentialsId: 'PGP_PASSPHRASE',
                          variable: 'PGP_PASSPHRASE']]) {
          wrap([$class: 'AnsiColorBuildWrapper']) {
            sh './scripts/cipublish'
          }
        }
      }

      // Plan and apply the current state of the instracture as
      // outlined by the `master` branch of the deployment repository.
      //
      // Also, use the container image revision referenced above to
      // cycle in the newest version of the application into Amazon
      // ECS.
      stage('infra') {
        // Use `git` to get the primary repository's current commmit SHA and
        // set it as the value of the `GIT_COMMIT` environment variable.
        env.GIT_COMMIT = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()

        checkout scm: [$class: 'GitSCM',
                       branches: [[name: env.RF_DEPLOYMENT_BRANCH]],
                       extensions: [[$class: 'RelativeTargetDirectory',
                                     relativeTargetDir: 'raster-foundry-deployment']],
                       userRemoteConfigs: [[credentialsId: '3bc1e878-814a-43d1-864e-2e378ebddb0f',
                                            url: 'https://github.com/azavea/raster-foundry-deployment.git']]]

        dir('raster-foundry-deployment') {
          wrap([$class: 'AnsiColorBuildWrapper']) {
            sh 'docker-compose -f docker-compose.yml -f docker-compose.ci.yml run --rm terraform ./scripts/infra plan'
            withCredentials([[$class: 'StringBinding',
                              credentialsId: 'ROLLBAR_ACCESS_TOKEN',
                              variable: 'ROLLBAR_ACCESS_TOKEN']]) {
              sh 'docker-compose -f docker-compose.yml -f docker-compose.ci.yml run --rm terraform ./scripts/infra apply'
            }
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
    sh 'docker-compose down -v --remove-orphans'
  }
}
