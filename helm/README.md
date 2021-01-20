# Preview Helm Chart

## About

The intent of the [preview](./preview) Helm chart is to deploy preview environments when building pull requests.

The chart has the following requirements:

- [nuxeo](https://github.com/nuxeo/nuxeo-helm-chart)
- [exposecontroller](https://github.com/jenkins-x/exposecontroller), to generate the Ingress objects
