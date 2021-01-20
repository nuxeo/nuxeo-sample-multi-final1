# Nuxeo Sample for Multiple Projects

This plugin aim to show how to build and structure a sample project relying on multiple projects (including Studio
projects).

[Continuous Integration](https://jenkins.platform.dev.nuxeo.com/job/nuxeo/job/nuxeo-sample-multi-projects/)

## Code Structure

The "final1" project relies on "common" 1st level project, was copied from "vertical" 2nd level project on Studio side.

Its package sets the common package as a package dependency.

Note that dedicated common and vertical packages are needed to be referenced in the final studio project
application definition too.

## Packaging and Deployment

TODO

## Building

> **Required Configuration**:
> Maven Repositories:
>
> *maven-internal*: Credentials Required, available on [packages.nuxeo.com](https://packages.nuxeo.com)
>
> ```xml
> <server>
>   <id>maven-internal</id>
>   <username>username</username>
>   <password>{9iw/tVNC+AgHtewewqeqidsa/3nWQFgKfeweqzGWpAyHIAuCull3IrrMOT8V112368sgw=}</password>
> </server>
> ```
>
> *nuxeo-studio*: NOS Studio Maven repository. Use account username and an [application token](https://doc.nuxeo.com/studio/token-management/) as password.
>
> ```xml
> <server>
>   <id>nuxeo-studio</id>
>   <username>username</username>
>   <password>{mXjWZLPWowewfa+aZIYrewfds+fsdfq6bRNYVLMn53iqO5cw5xEewqrFUrewr/Szpf}</password>
> </server>
> ```
>
> Can be automatically set using `nuxeo studio link` command from [Nuxeo CLI](https://github.com/nuxeo/nuxeo-cli).

```bash
mvn clean install
```

### Requirements

See [CORG/Compiling Nuxeo from sources](http://doc.nuxeo.com/x/xION)

## Licensing

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

## About Nuxeo

Nuxeo dramatically improves how content-based applications are built, managed and deployed, making customers more agile, innovative and successful. Nuxeo provides a next generation, enterprise ready platform for building traditional and cutting-edge content oriented applications. Combining a powerful application development environment with SaaS-based tools and a modular architecture, the Nuxeo Platform and Products provide clear business value to some of the most recognizable brands including Verizon, Electronic Arts, Sharp, FICO, the U.S. Navy, and Boeing. Nuxeo is headquartered in New York and Paris. More information is available at www.nuxeo.com.
