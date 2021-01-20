# nuxeo-customer-project-sample Docker Image

This module is responsible for building the nuxeo-customer-project-sample Docker image.

## Requirements

Studio package is added as a package dependency to the

- `NUXEO_CLID` environment variable contains `instance.clid` content while replacing `\n` carret return by `--`.

Locally, the image can be built with Maven:

```bash
# Using GNU sed
NUXEO_CLID=$(cat /my-env/instance.clid | sed ':a;N;$!ba;s/\n/--/g') mvn clean install

# Portable Version
NUXEO_CLID=$(cat /my-env/instance.clid | sed -e ':a' -e 'N;$!ba' -e 's/\n/--/') mvn clean install
```

It's possible to skip Docker build by setting default `skipDocker` property value to `true` in `pom.xml` file.

```bash
# Skipping Docker build
mvn -DskipDocker=true clean install
```
