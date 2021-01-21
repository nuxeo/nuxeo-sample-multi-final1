/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Antoine Taillefer <ataillefer@nuxeo.com>
 *     Kevin Leturc <kleturc@nuxeo.com>
 *     Anahide Tchertchian
 */
properties([
  [$class: 'GithubProjectProperty', projectUrlStr: 'https://github.com/nuxeo/nuxeo-sample-multi-final1'],
  [$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', daysToKeepStr: '60', numToKeepStr: '60', artifactNumToKeepStr: '5']],
  disableConcurrentBuilds(),
])

void getCurrentVersion() {
  return readMavenPom().getVersion()
}

void getReleaseVersion(givenVersion, version) {
  if (givenVersion.isEmpty()) {
    return version.replace('-SNAPSHOT', '')
  }
  return givenVersion
}

void getNuxeoVersion(version) {
  if (version.isEmpty()) {
    container('maven') {
      return sh(returnStdout: true, script: 'mvn org.apache.maven.plugins:maven-help-plugin:3.1.0:evaluate -Dexpression=nuxeo.platform.version -q -DforceStdout').trim()
    }
  }
  return version
}

void replacePomProperty(name, value) {
  // only replace the first occurrence
  sh("perl -i -pe '!\$x && s|<${name}>.*?</${name}>|<${name}>${value}</${name}>| && (\$x=1)' pom.xml")
}

void getMavenReleaseOptions(Boolean skipTests) {
  def options = ' '
  if (skipTests) {
    return options + ' -DskipTests'
  }
  return options
}

void dockerPull(String image) {
  sh "docker pull ${image}"
}

void dockerTag(String image, String tag) {
  sh "docker tag ${image} ${tag}"
}

void dockerPush(String image) {
  sh "docker push ${image}"
}

void dockerDeploy(String releaseVersion) {
  String imageTag = "${ORG}/${DOCKER_IMAGE_NAME}:${releaseVersion}"
  String internalImage = "${DOCKER_REGISTRY}/${imageTag}"
  String image = "${NUXEO_DOCKER_REGISTRY}/${imageTag}"
  echo "Push ${image}"
  dockerPull(internalImage)
  dockerTag(internalImage, image)
  dockerPush(image)
}

pipeline {

  agent {
    label 'jenkins-nuxeo-package-11'
  }

  options {
    skipStagesAfterUnstable()
  }

  parameters {
    string(name: 'BRANCH_NAME', defaultValue: 'master', description: 'The branch to release')
    string(name: 'RELEASE_VERSION', defaultValue: '', description: 'Release version (optional)')
    string(name: 'NEXT_VERSION', defaultValue: '', description: 'Next version (next minor version if unset)')
    string(name: 'STUDIO_PROJECT_VERSION', defaultValue: '', description: 'Version of the Studio project dependency (unchanged if unset). Use keywords `MAJOR`, `MINOR` or `PATCH` for a release to be performed automatically')
    string(name: 'NEXT_STUDIO_PROJECT_VERSION', defaultValue: '', description: 'Next version of the Studio project version dependency (unchanged if unset)')
    string(name: 'COMMON_VERSION', defaultValue: '', description: 'Version of the Common Sample package dependency (unchanged if unset)')
    string(name: 'NEXT_COMMON_VERSION', defaultValue: '', description: 'Next version of the Common Sample package dependency (unchanged if unset)')
    string(name: 'NUXEO_VERSION', defaultValue: '', description: 'Version of the Nuxeo Server dependency (unchanged if unset)')
    booleanParam(name: 'NUXEO_VERSION_IS_PROMOTED', defaultValue: true, description: 'Uncheck if releasing a RC version, against a non-promoted Nuxeo build')
    string(name: 'NEXT_NUXEO_VERSION', defaultValue: '', description: 'Next version of the Nuxeo Server dependency (unchanged if unset)')
    string(name: 'JIRA_ISSUE', defaultValue: '', description: 'Id of the Jira issue for this release')
    booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: 'Skip all tests')
    booleanParam(name: 'DRY_RUN', defaultValue: true, description: 'Dry run (warning: Studio project automated release would still be performed)')
  }

  environment {
    CURRENT_VERSION = getCurrentVersion()
    RELEASE_VERSION = getReleaseVersion(params.RELEASE_VERSION, CURRENT_VERSION)
    MAVEN_ARGS = '-B -nsu -Prelease'
    MAVEN_RELEASE_OPTIONS = getMavenReleaseOptions(params.SKIP_TESTS)
    CONNECT_PROD_URL = 'https://connect.nuxeo.com/nuxeo'
    STUDIO_PROJECT_RELEASE_URL = 'https://connect.nuxeo.com/nuxeo/site/studio/v2/project/nuxeo-final-test-project/releases'
    NUXEO_IMAGE_VERSION = getNuxeoVersion(params.NUXEO_VERSION)
    NUXEO_DOCKER_REGISTRY = 'docker-private.packages.nuxeo.com'
    VERSION = "${RELEASE_VERSION}"
    DRY_RUN = "${params.DRY_RUN}"
    BRANCH_NAME = "${params.BRANCH_NAME}"
    ORG = 'nuxeo'
    DOCKER_IMAGE_NAME = "nuxeo-sample-final1"
  }

  stages {

    stage('Check Parameters') {
      steps {
        script {
          echo """
          ----------------------------------------
          Branch name:                '${BRANCH_NAME}'

          Current version:            '${CURRENT_VERSION}'
          Release version:            '${RELEASE_VERSION}'
          Next version:               '${params.NEXT_VERSION}'

          Studio project version:     '${params.STUDIO_PROJECT_VERSION}'
          Next Studio project version:'${params.NEXT_STUDIO_PROJECT_VERSION}'

          Common package version:     '${params.COMMON_VERSION}'
          Next Common package version:'${params.NEXT_COMMON_VERSION}'

          Nuxeo version:              '${params.NUXEO_VERSION}'
          Nuxeo version is promoted?  '${params.NUXEO_VERSION_IS_PROMOTED}'
          Next Nuxeo version:         '${params.NEXT_NUXEO_VERSION}'

          Jira issue:                 '${params.JIRA_ISSUE}'

          Skip tests:                 '${params.SKIP_TESTS}'
          Skip functional tests:      '${params.SKIP_FUNCTIONAL_TESTS}'

          Dry run:                    '${params.DRY_RUN}'
          ----------------------------------------
          """
          if (!params.NUXEO_VERSION_IS_PROMOTED && !RELEASE_VERSION.contains('RC')) {
            currentBuild.result = 'ABORTED';
            def message = 'Can only release a RC against a non-promoted Nuxeo version'
            currentBuild.description = "${message}"
            echo "Aborting release with message: ${message}"
            error(currentBuild.description)
          }
          currentBuild.description = "Releasing version ${RELEASE_VERSION}"
        }
      }
    }

    stage('Set Kubernetes labels') {
      steps {
        container('maven') {
          echo """
          ----------------------------------------
          Set Kubernetes labels
          ----------------------------------------
          """
          echo "Set label 'branch: ${BRANCH_NAME}' on pod ${NODE_NAME}"
          sh """
            kubectl label pods ${NODE_NAME} branch=${BRANCH_NAME}
          """
        }
      }
    }

    stage('Update version') {
      steps {
        container('maven') {
          script {
            echo """
            ----------------------------------------
            Update version on branch ${BRANCH_NAME}
            ----------------------------------------
            New version: ${RELEASE_VERSION}
            """
            sh """
              git checkout ${BRANCH_NAME}
            """
            if (!params.NUXEO_VERSION_IS_PROMOTED) {
              sh """
                # hack: use nuxeo-ecm instead of nuxeo-parent to retrieve a non-promoted nuxeo version
                perl -i -pe 's|<artifactId>nuxeo-parent</artifactId>|<artifactId>nuxeo-ecm</artifactId>|' pom.xml
              """
            }
            if (!params.NUXEO_VERSION.isEmpty()) {
              replacePomProperty('version', params.NUXEO_VERSION)
            }
            if (!params.COMMON_VERSION.isEmpty()) {
              replacePomProperty('sample.common.package.version', params.COMMON_VERSION)
            }
            def studioVersion = "${params.STUDIO_PROJECT_VERSION}".trim()
            if (!studioVersion.isEmpty()) {
              def doRelease = studioVersion.equals('MAJOR') || studioVersion.equals('MINOR') || studioVersion.equals('PATCH')
              def studioReleaseVersion;
              if (doRelease) {
                echo """
                ----------------------------------------
                Release Studio Project
                ----------------------------------------"""
                withCredentials([usernameColonPassword(credentialsId: 'connect-prod', variable: 'CONNECT_PASS')]) {
                  def curlCommand = "curl -s -X POST -H 'Content-Type: application/json' -u '$CONNECT_PASS' -d '{ \"revision\": \"master\", \"versionName\": \"${studioVersion}\" }' '$STUDIO_PROJECT_RELEASE_URL'"
                  def response = sh(script: curlCommand, returnStdout: true).trim()
                  def json = readJSON text: response
                  studioReleaseVersion = json.version
                  if (studioReleaseVersion == null) {
                    echo "Version cannot be parsed from response: ${response}"
                    currentBuild.description = 'Error releasing Studio project'
                    error(currentBuild.description)
                  }
                }
              } else {
                studioReleaseVersion = studioVersion
              }
              echo """
              ----------------------------------------
              Replace Studio Project Version: ${studioReleaseVersion}
              ----------------------------------------"""
              replacePomProperty('studio.project.version', studioReleaseVersion)
            }

            sh """
              # project version
              mvn ${MAVEN_ARGS} versions:set -DnewVersion=${RELEASE_VERSION} -DgenerateBackupPoms=false
            """
          }
        }
      }
    }

    stage('Release') {
      steps {
        container('maven') {
          script {
            echo """
            -------------------------------------------------
            Release Project
            -------------------------------------------------
            """
            sh """
              mvn ${MAVEN_ARGS} ${MAVEN_RELEASE_OPTIONS} install
            """

            echo """
            ----------------------------------------
            Build Docker image
            ----------------------------------------
            Image tag: ${RELEASE_VERSION}
            Registry: ${DOCKER_REGISTRY}
            Nuxeo Image tag: ${NUXEO_IMAGE_VERSION}
            """
            def dockerPath = 'docker'
            // push Image to the Jenkins X internal Docker registry
            sh "envsubst < ${dockerPath}/skaffold.yaml > ${dockerPath}/skaffold.yaml~gen"
            retry(2) {
              sh "skaffold build -f ${dockerPath}/skaffold.yaml~gen"
            }
            sh """
              # waiting skaffold + kaniko + container-stucture-tests issue
              #  see https://github.com/GoogleContainerTools/skaffold/issues/3907
              docker pull ${DOCKER_REGISTRY}/nuxeo/nuxeo-explorer:${RELEASE_VERSION}
              container-structure-test test --image ${DOCKER_REGISTRY}/${ORG}/${DOCKER_IMAGE_NAME}:${RELEASE_VERSION} --config ${dockerPath}/test/*
            """

            def message = "Release ${RELEASE_VERSION}"
            if (!params.JIRA_ISSUE.isEmpty()) {
              message = "${params.JIRA_ISSUE}: ${message}"
            }
            sh """
              git commit -a -m "${message}"
              git tag -a v${RELEASE_VERSION} -m "${message}"
            """

            if (env.DRY_RUN != "true") {
              sh """
                jx step git credentials
                git config credential.helper store

                git push origin v${RELEASE_VERSION}
              """
            }
          }
        }
      }
      post {
        always {
          archiveArtifacts allowEmptyArchive: true, artifacts: '**/*.jar, **/target/nuxeo-*-package-*.zip, **/target/**/*.log, **/target/*.png, **/target/*.html'
          junit allowEmptyResults: true, testResults: '**/target/failsafe-reports/*.xml, **/target/surefire-reports/*.xml'
        }
      }
    }

    stage('Deploy Maven Artifacts') {
      when {
        not {
          environment name: 'DRY_RUN', value: 'true'
        }
      }
      steps {
        container('maven') {
          echo """
          ----------------------------------------
          Deploy Maven Artifacts
          ----------------------------------------"""
          sh "mvn ${MAVEN_ARGS} -DskipTests -DskipDocker deploy"
        }
      }
    }

    stage('Upload Nuxeo Packages') {
      when {
        // maybe there is no point of pushing this package to the Connect private marketplace
        // not {
        //   environment name: 'DRY_RUN', value: 'true'
        // }
        expression {
          return false
        }
      }
      steps {
        container('maven') {
          echo """
          ----------------------------------------
          Upload Nuxeo Packages to ${CONNECT_PROD_URL}
          ----------------------------------------"""
          withCredentials([usernameColonPassword(credentialsId: 'connect-prod', variable: 'CONNECT_PASS')]) {
            sh """
              PACKAGES_TO_UPLOAD="**/target/nuxeo-*-package*.zip"
              for file in \$PACKAGES_TO_UPLOAD ; do
                curl --fail -i -u "$CONNECT_PASS" -F package=@\$(ls \$file) "$CONNECT_PROD_URL""/site/marketplace/upload?batch=true&orgId=nuxeo&restrictedToOrgs=nuxeo" ;
              done
            """
          }
        }
      }
    }

    stage('Promote Docker Image') {
      when {
        not {
          environment name: 'DRY_RUN', value: 'true'
        }
      }
      steps {
        container('maven') {
          echo """
          -----------------------------------------------
          Tag Docker Image with version ${RELEASE_VERSION}
          -----------------------------------------------
          """
          dockerDeploy("${RELEASE_VERSION}")
        }
      }
    }

    stage('Bump branch version') {
      steps {
        container('maven') {
          script {
            sh "git checkout ${BRANCH_NAME}"
            // increment minor version
            def nextVersion = "${params.NEXT_VERSION}"
            if (nextVersion.isEmpty()) {
              nextVersion = sh(returnStdout: true, script: "perl -pe 's/\\b(\\d+)(?=\\D*\$)/\$1+1/e' <<< ${CURRENT_VERSION}").trim()
            }
            echo """
            -----------------------------------------------
            Update ${BRANCH_NAME} version from ${CURRENT_VERSION} to ${nextVersion}
            -----------------------------------------------
            """
            if (!params.NUXEO_VERSION_IS_PROMOTED) {
              sh """
                # hack: replace back nuxeo-ecm replacement
                perl -i -pe 's|<artifactId>nuxeo-ecm</artifactId>|<artifactId>nuxeo-parent</artifactId>|' pom.xml
              """
            }
            def nextNuxeoVersion = "${params.NEXT_NUXEO_VERSION}"
            if (!nextNuxeoVersion.isEmpty()) {
              replacePomProperty('version', nextNuxeoVersion)
            }
            def nextCommonVersion = "${params.NEXT_COMMON_VERSION}"
            if (!nextCommonVersion.isEmpty()) {
              replacePomProperty('sample.common.package.version', nextCommonVersion)
            }
            def nextStudioVersion = "${params.NEXT_STUDIO_PROJECT_VERSION}"
            if (!nextStudioVersion.isEmpty()) {
              replacePomProperty('studio.project.version', nextStudioVersion)
            }

            def message = "Post release ${RELEASE_VERSION}, update ${CURRENT_VERSION} to ${nextVersion}"
            if (!params.JIRA_ISSUE.isEmpty()) {
              message = "${params.JIRA_ISSUE}: ${message}"
            }
            sh """
              # project version
              mvn ${MAVEN_ARGS} versions:set -DnewVersion=${nextVersion} -DgenerateBackupPoms=false

              git commit -a -m "${message}"
            """
            currentBuild.description = "Released version ${RELEASE_VERSION}"

            if (env.DRY_RUN != "true") {
              sh """
                jx step git credentials
                git config credential.helper store

                git push origin ${BRANCH_NAME}
              """
            }
          }
        }
      }
    }

  }

}
