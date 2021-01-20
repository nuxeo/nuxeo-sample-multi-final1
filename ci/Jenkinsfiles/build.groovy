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
 *     Thomas Roger <troger@nuxeo.com>
 *     Kevin Leturc <kleturc@nuxeo.com>
 *     Anahide Tchertchian <atchertchian@nuxeo.com>
 */
properties([
  [$class: 'GithubProjectProperty', projectUrlStr: 'https://github.com/nuxeo/nuxeo-sample-multi-final1'],
  [$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', daysToKeepStr: '60', numToKeepStr: '60', artifactNumToKeepStr: '5']],
  disableConcurrentBuilds(),
])

void setGitHubBuildStatus(String context, String message, String state) {
  step([
    $class: 'GitHubCommitStatusSetter',
    reposSource: [$class: 'ManuallyEnteredRepositorySource', url: 'https://github.com/nuxeo/nuxeo-sample-multi-final1'],
    contextSource: [$class: 'ManuallyEnteredCommitContextSource', context: context],
    statusResultSource: [$class: 'ConditionalStatusResultSource', results: [[$class: 'AnyBuildResult', message: message, state: state]]],
  ])
}

void getNuxeoImageVersion() {
  return sh(returnStdout: true, script: 'mvn org.apache.maven.plugins:maven-help-plugin:3.1.0:evaluate -Dexpression=nuxeo.platform.version -q -DforceStdout').trim();
}

String getVersion(referenceBranch) {
  String version = readMavenPom().getVersion()
  return (BRANCH_NAME == referenceBranch) ? version : version + "-${BRANCH_NAME}-${BUILD_NUMBER}"
}

String getCommitSha1() {
  return sh(returnStdout: true, script: 'git rev-parse HEAD').trim();
}

void dockerPull(String image) {
  sh "docker pull ${image}"
}

void dockerRun(String image, String command) {
  sh "docker run --rm ${image} ${command}"
}

void dockerTag(String image, String tag) {
  sh "docker tag ${image} ${tag}"
}

void dockerPush(String image) {
  sh "docker push ${image}"
}

void dockerDeploy(String imageName) {
  String imageTag = "${ORG}/${imageName}:${VERSION}"
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
  environment {
    MAVEN_OPTS = "$MAVEN_OPTS -Xms2g -Xmx2g  -XX:+TieredCompilation -XX:TieredStopAtLevel=1"
    MAVEN_ARGS = '-B -nsu'
    REFERENCE_BRANCH = 'master'
    SCM_REF = "${getCommitSha1()}"
    VERSION = "${getVersion(REFERENCE_BRANCH)}"
    PERSISTENCE = "${BRANCH_NAME == REFERENCE_BRANCH}"
    NUXEO_DOCKER_REGISTRY = 'docker-private.packages.nuxeo.com'
    PREVIEW_NAMESPACE = "nuxeo-sample-final1-${BRANCH_NAME.toLowerCase()}"
    // APP_NAME and ORG needed for PR preview
    APP_NAME = 'nuxeo-sample-final1'
    ORG = 'nuxeo'
    DOCKER_IMAGE_NAME = "${APP_NAME}"
  }
  stages {
    stage('Set Labels') {
      steps {
        container('maven') {
          echo """
          ----------------------------------------
          Set Kubernetes resource labels
          ----------------------------------------
          """
          echo "Set label 'branch: ${BRANCH_NAME}' on pod ${NODE_NAME}"
          sh """
            kubectl label pods ${NODE_NAME} branch=${BRANCH_NAME}
          """
        }
      }
    }
    stage('Compile') {
      steps {
        setGitHubBuildStatus('compile', 'Compile', 'PENDING')
        container('maven') {
          echo """
          ----------------------------------------
          Compile
          ----------------------------------------"""
          echo "MAVEN_OPTS=$MAVEN_OPTS"
          sh "mvn ${MAVEN_ARGS} -V -DskipDocker install"
        }
      }
      post {
        always {
          archiveArtifacts artifacts: '**/target/*.jar, **/target/nuxeo-*-package-*.zip'
        }
        success {
          setGitHubBuildStatus('compile', 'Compile', 'SUCCESS')
        }
        unsuccessful {
          setGitHubBuildStatus('compile', 'Compile', 'FAILURE')
        }
      }
    }
    stage('Build Docker Image') {
      when {
        anyOf {
          branch 'PR-*'
          branch "${REFERENCE_BRANCH}"
        }
      }
      steps {
        setGitHubBuildStatus('docker/build', 'Build Docker Image', 'PENDING')
        container('maven') {
          echo """
          ------------------------------------------
          Build Final Sample Docker Image
          ------------------------------------------
          Image tag: ${VERSION}
          Registry: ${DOCKER_REGISTRY}
          """
          withEnv(["NUXEO_IMAGE_VERSION=${getNuxeoImageVersion()}"]) {
            withCredentials([string(credentialsId: 'instance-clid', variable: 'INSTANCE_CLID')]) {
              script {
                // build and push Docker images to the Jenkins X internal Docker registry
                def dockerPath = 'docker'
                sh "envsubst < ${dockerPath}/skaffold.yaml > ${dockerPath}/skaffold.yaml~gen"
                sh """#!/bin/bash +x
                  CLID=\$(echo -e "${INSTANCE_CLID}" | sed ':a;N;\$!ba;s/\\n/--/g') skaffold build -f ${dockerPath}/skaffold.yaml~gen
                """
                def image = "${DOCKER_REGISTRY}/${ORG}/${DOCKER_IMAGE_NAME}:${VERSION}"
                sh """
                  # waiting skaffold + kaniko + container-stucture-tests issue
                  #  see https://github.com/GoogleContainerTools/skaffold/issues/3907
                  docker pull ${image}
                  container-structure-test test --image ${image} --config ${dockerPath}/test/*
                """
              }
            }
          }
        }
      }
      post {
        success {
          setGitHubBuildStatus('docker/build', 'Build Docker Image', 'SUCCESS')
        }
        unsuccessful {
          setGitHubBuildStatus('docker/build', 'Build Docker Image', 'FAILURE')
        }
      }
    }
    stage('Test Docker Image') {
      steps {
        setGitHubBuildStatus('docker/test', 'Test Docker Image', 'PENDING')
        container('maven') {
          echo """
          ----------------------------------------
          Test Docker image
          ----------------------------------------
          """
          script {
            image = "${DOCKER_REGISTRY}/${ORG}/${DOCKER_IMAGE_NAME}:${VERSION}"
            echo "Test ${image}"
            dockerPull(image)
            echo 'Run image'
            dockerRun(image, 'nuxeoctl start')
          }
        }
      }
      post {
        success {
          setGitHubBuildStatus('docker/test', 'Test Docker Image', 'SUCCESS')
        }
        unsuccessful {
          setGitHubBuildStatus('docker/test', 'Test Docker Image', 'FAILURE')
        }
      }
    }
    stage('Deploy Preview') {
      when {
          // disable on reference branch for now
          //branch "${REFERENCE_BRANCH}"
          allOf {
            branch 'PR-*'
            expression {
              return pullRequest.labels.contains('preview')
            }
          }
      }
      steps {
        setGitHubBuildStatus('preview', 'Deploy Preview', 'PENDING')
        container('maven') {
          echo """
          ----------------------------------------
          Deploy preview environment
          ----------------------------------------"""
          dir('helm/preview') {

            script {
              // first substitute docker image names and versions
              sh """
                cp values.yaml values.yaml.tosubst
                DOCKER_IMAGE_NAME=${DOCKER_IMAGE_NAME} envsubst < values.yaml.tosubst > values.yaml
              """

              // second create target namespace (if doesn't exist) and copy secrets to target namespace
              String currentNs = sh(returnStdout: true, script: 'jx -b ns | sed -r "s/^Using namespace \'([^\']+)\'.+\\$/\\1/"').trim()
              boolean nsExists = sh(returnStatus: true, script: "kubectl get namespace ${PREVIEW_NAMESPACE}") == 0
              // Only used with jx preview on pr branches
              String noCommentOpt = '';
              if (nsExists) {
                noCommentOpt = '--no-comment'
                // Previous preview deployment needs to be scaled to 0 to be replaced correctly
                sh "kubectl --namespace ${PREVIEW_NAMESPACE} scale deployment preview --replicas=0"
              } else {
                sh "kubectl create namespace ${PREVIEW_NAMESPACE}"
              }
              try {
                boolean isReferenceBranch = BRANCH_NAME == REFERENCE_BRANCH
                sh "kubectl --namespace platform get secret kubernetes-docker-cfg -ojsonpath='{.data.\\.dockerconfigjson}' | base64 --decode > /tmp/config.json"
                sh """kubectl create secret generic kubernetes-docker-cfg \
                    --namespace=${PREVIEW_NAMESPACE} \
                    --from-file=.dockerconfigjson=/tmp/config.json \
                    --type=kubernetes.io/dockerconfigjson --dry-run -o yaml | kubectl apply -f -"""
                String previewCommand = isReferenceBranch ?
                  // To avoid jx gc cron job, reference branch previews are deployed by calling jx step helm install instead of jx preview
                  "jx step helm install --namespace ${PREVIEW_NAMESPACE} --name ${PREVIEW_NAMESPACE} --verbose ."
                  // When deploying a pr preview, we use jx preview which gc the merged pull requests
                  : "jx preview --namespace ${PREVIEW_NAMESPACE} --verbose --source-url=https://github.com/nuxeo/nuxeo-sample-multi-final1 --preview-health-timeout 15m ${noCommentOpt}"

                // third build and deploy the chart
                // we use jx preview that gc the merged pull requests
                sh """
                  helm init --client-only --stable-repo-url=https://charts.helm.sh/stable
                  helm repo add local-jenkins-x http://jenkins-x-chartmuseum:8080
                  jx step helm build --verbose
                  mkdir target && helm template . --output-dir target
                  ${previewCommand}
                """
                if (isReferenceBranch) {
                  // When not using jx preview, we need to expose the nuxeo url by hand
                  url = sh(returnStdout: true, script: "kubectl get svc --namespace ${PREVIEW_NAMESPACE} preview -o go-template='{{index .metadata.annotations \"fabric8.io/exposeUrl\"}}'")
                  echo """
                    ----------------------------------------
                    Preview available at: ${url}
                    ----------------------------------------"""
                }
              } catch (err) {
                echo "Error while deploying preview environment: ${err}"
                if (!nsExists) {
                  echo "Deleting namespace ${PREVIEW_NAMESPACE}"
                  sh "kubectl delete namespace ${PREVIEW_NAMESPACE}"
                }
                throw err
              }
            }
          }
        }
      }
      post {
        always {
          archiveArtifacts allowEmptyArchive: true, artifacts: '**/requirements.lock, **/charts/*.tgz, **/target*/**/*.yaml'
        }
        success {
          setGitHubBuildStatus('preview', 'Deploy Preview', 'SUCCESS')
        }
        unsuccessful {
          setGitHubBuildStatus('preview', 'Deploy Preview', 'FAILURE')
        }
      }
    }
  }
  post {
    always {
      script {
        if (BRANCH_NAME == REFERENCE_BRANCH) {
          // update JIRA issue
          step([$class: 'JiraIssueUpdater', issueSelector: [$class: 'DefaultIssueSelector'], scm: scm])
        }
      }
    }
  }
}
